# AI_GENERATE_START------- ----------------------------------------
from __future__ import annotations

import logging
import shutil
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from stock_trading.utils.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.trade_calendar import trade_dates
from stock_trading.market.minute5_summary import require_window_contract
from stock_trading.utils.logging_utils import set_log_context, should_log_progress
from stock_trading.research.labels import build_labels
from stock_trading.research.features import BASIC_FEATURE_FIELDS
from stock_trading.research.qlib_contract import MIN_QLIB_TRADING_DAYS, expected_qlib_contract
from stock_trading.research.model_config import ModelSpec

LOGGER = logging.getLogger(__name__)
QLIB_BASE_FIELDS = (
    "open", "high", "low", "close", "volume", "amount", "vwap", "factor",
)
DAILY_QLIB_FIELDS = QLIB_BASE_FIELDS + BASIC_FEATURE_FIELDS + ("label",)
MINUTE5_QLIB_FIELDS = QLIB_BASE_FIELDS + BASIC_FEATURE_FIELDS + (
    "full_return", "full_range", "full_close_vwap",
    "full_last30_return", "label",
)


def _official_calendar(
    start_date: date, end_date: date, calendar_dir: Path,
) -> pd.DatetimeIndex:
    """缺行情不能压缩交易日；缺年度权威日历时明确要求补齐。"""
    if start_date > end_date:
        return pd.DatetimeIndex([])
    return pd.DatetimeIndex([
        value
        for year in range(start_date.year, end_date.year + 1)
        for value in trade_dates(year, calendar_dir)
        if start_date <= value <= end_date
    ])


class QlibBinaryStore:
    """从权威 Parquet 确定性准备最小 Qlib 文件数据源。"""

    def __init__(
        self,
        market_store: ParquetMarketStore,
        target: Path,
        spec: ModelSpec,
    ) -> None:
        self._market_store = market_store
        self._target = target
        self._spec = spec
        self._feature_frequency = spec.feature_frequency
        self._qlib_fields = {
            "daily": DAILY_QLIB_FIELDS,
            "minute5": MINUTE5_QLIB_FIELDS,
        }[self._feature_frequency]
        self._expected_contract = expected_qlib_contract(spec)
        self._basic_by_code: dict[str, pd.DataFrame] = {}

    def prepare(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        force: bool = False,
    ) -> dict[str, object]:
        """有效时跳过，只增加新日期或新股票，必要时全量重建。"""
        existing_range = self.date_range()
        existing_instruments = self.instruments()
        expected = {normalize_instrument(code) for code in codes}
        try:
            existing_days = len(self._read_calendar(self._target))
        except (OSError, ValueError):
            existing_days = 0
        structure_valid = (
            existing_instruments is not None
            and existing_days > 0
            and all(
                self._feature_files_valid(instrument, existing_days)
                for instrument in existing_instruments
            )
        )
        if (
            force
            or existing_range is None
            or existing_instruments is None
            or not structure_valid
            or self._contract_version() != self._expected_contract
            or existing_range[0] > start_date
            or not existing_instruments.issubset(expected)
        ):
            result = self.rebuild(codes, start_date, end_date)
            return {"mode": "rebuilt", "skipped": False} | result

        if (
            existing_range == (start_date, end_date)
            and existing_instruments == expected
        ):
            return {
                "mode": "reused",
                "skipped": True,
                "codes": len(codes),
                "calendarDays": existing_days,
                "startDate": existing_range[0].isoformat(),
                "endDate": existing_range[1].isoformat(),
                "path": str(self._target),
            }

        effective_start = existing_range[0]
        effective_end = max(existing_range[1], end_date)
        result = self._increment(
            codes,
            effective_start,
            effective_end,
            existing_range,
            existing_instruments,
        )
        mode = (
            "incremental"
            if result["appendedDays"] or result["newInstruments"]
            else "refreshed"
        )
        return {"mode": mode, "skipped": False} | result

    def rebuild(
        self, codes: list[str], start_date: date, end_date: date
    ) -> dict[str, object]:
        self._load_basic_cache(codes, start_date, end_date)
        frames: dict[str, pd.DataFrame] = {}
        total_codes = len(codes)
        set_log_context(step="1/3", progress="0/-", target="Qlib 日线")
        LOGGER.info("开始读取并复权日线：股票数=%d", total_codes)
        for index, code in enumerate(codes, start=1):
            frames[code] = self._adjusted_daily(code, start_date, end_date)
            if should_log_progress(index, total_codes):
                set_log_context(progress=f"{index}/{total_codes}", target=code)
                LOGGER.info("Qlib 日线处理完成")
        calendar = _official_calendar(start_date, end_date, self._market_store.root / "calendar")
        if len(calendar) < MIN_QLIB_TRADING_DAYS:
            raise ValueError(f"Qlib calendar requires at least {MIN_QLIB_TRADING_DAYS} trading days for Alpha158")

        temporary = self._target.with_name(f".{self._target.name}.building")
        shutil.rmtree(temporary, ignore_errors=True)
        (temporary / "calendars").mkdir(parents=True)
        (temporary / "instruments").mkdir(parents=True)
        (temporary / "features").mkdir(parents=True)
        self._write_calendar(temporary, calendar)

        instruments: list[tuple[str, str, str]] = []
        label_count = 0
        set_log_context(step="2/3", progress="0/-", target="Qlib 特征")
        LOGGER.info("开始生成标签和特征：交易日数=%d", len(calendar))
        for index, (code, frame) in enumerate(frames.items(), start=1):
            aligned = frame.reindex(calendar)
            labels = build_labels(frame, calendar, self._spec)
            aligned["label"] = labels.reindex(calendar)
            qlib_code = normalize_instrument(code)
            self._write_features(temporary, qlib_code, aligned)
            valid = frame.dropna(subset=["close"])
            if valid.empty:
                raise ValueError(f"股票没有可用 Qlib 日线：{code}")
            instruments.append(
                (
                    qlib_code,
                    valid.index.min().strftime("%Y-%m-%d"),
                    valid.index.max().strftime("%Y-%m-%d"),
                )
            )
            label_count += int(aligned["label"].notna().sum())
            if should_log_progress(index, total_codes):
                set_log_context(progress=f"{index}/{total_codes}", target=code)
                LOGGER.info("Qlib 特征写入完成：累计标签=%d", label_count)

        instrument_path = temporary / "instruments" / "all.txt"
        instrument_path.write_text(
            "".join(f"{code}\t{start}\t{end}\n" for code, start, end in instruments),
            encoding="utf-8",
        )
        self._write_contract(temporary)
        self._replace_target(temporary)
        set_log_context(step="3/3", progress="-/-", target=str(self._target))
        LOGGER.info("Qlib 制品切换完成")
        return {
            "codes": len(codes),
            "calendarDays": len(calendar),
            "startDate": calendar.min().strftime("%Y-%m-%d"),
            "endDate": calendar.max().strftime("%Y-%m-%d"),
            "labels": label_count,
            "path": str(self._target),
        }

    def is_valid(
        self, codes: list[str], start_date: date, end_date: date
    ) -> bool:
        """检查 Qlib 日历、股票清单和特征文件是否覆盖目标范围。"""
        calendar_path = self._target / "calendars" / "day.txt"
        instruments_path = self._target / "instruments" / "all.txt"
        try:
            calendar = [
                date.fromisoformat(value)
                for value in calendar_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            ]
            instruments = {
                value.split("\t", maxsplit=1)[0]
                for value in instruments_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            }
        except (OSError, ValueError):
            return False
        expected = {normalize_instrument(code) for code in codes}
        return (
            len(calendar) >= MIN_QLIB_TRADING_DAYS
            and start_date <= calendar[0] <= start_date + timedelta(days=15)
            and calendar[-1] == end_date
            and instruments == expected
            and self._contract_version() == self._expected_contract
            and all(
                self._feature_files_valid(instrument, len(calendar))
                for instrument in expected
            )
        )

    def is_date_ready(self, target_date: date) -> bool:
        """检查目标交易日及现有股票特征是否可直接用于预测。"""
        instruments = self.available_instruments(target_date)
        return instruments is not None and len(instruments) >= 100

    def available_instruments(self, target_date: date) -> set[str] | None:
        """返回目标日期可读取完整特征的股票集合。"""
        calendar_path = self._target / "calendars" / "day.txt"
        instruments_path = self._target / "instruments" / "all.txt"
        try:
            calendar_values = [
                date.fromisoformat(value)
                for value in calendar_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            ]
            calendar = set(calendar_values)
            instruments = {
                value.split("\t", maxsplit=1)[0]
                for value in instruments_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            }
        except (OSError, ValueError):
            return None
        if (
            target_date not in calendar
            or self._contract_version() != self._expected_contract
        ):
            return None
        return {
            instrument
            for instrument in instruments
            if self._feature_files_valid(instrument, len(calendar_values))
        }

    def date_range(self) -> tuple[date, date] | None:
        """返回现有 Qlib 日历范围；文件不存在或无效时返回空。"""
        calendar_path = self._target / "calendars" / "day.txt"
        try:
            calendar = [
                date.fromisoformat(value)
                for value in calendar_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            ]
        except (OSError, ValueError):
            return None
        if not calendar or calendar != sorted(set(calendar)):
            return None
        return calendar[0], calendar[-1]

    def instruments(self) -> set[str] | None:
        """返回现有 Qlib 股票集合；文件不存在或格式无效时返回空。"""
        instruments_path = self._target / "instruments" / "all.txt"
        try:
            instruments = {
                value.split("\t", maxsplit=1)[0]
                for value in instruments_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            }
            return {normalize_instrument(value) for value in instruments}
        except (OSError, ValueError):
            return None

    def _feature_files_valid(
        self, instrument: str, calendar_days: int | None = None
    ) -> bool:
        directory = self._target / "features" / instrument.lower()
        try:
            return all(
                (directory / f"{field}.day.bin").is_file()
                and (directory / f"{field}.day.bin").stat().st_size > 4
                and (
                    calendar_days is None
                    or (directory / f"{field}.day.bin").stat().st_size
                    == (calendar_days + 1) * 4
                )
                for field in self._qlib_fields
            )
        except OSError:
            return False

    def _adjusted_daily(
        self, code: str, start_date: date, end_date: date
    ) -> pd.DataFrame:
        if self._feature_frequency == "daily":
            daily = self._market_store.read_daily_history(code).set_index("trade_date")
            daily = daily[
                (daily.index.date >= start_date)
                & (daily.index.date <= end_date)
                & daily["tradestatus"].astype(str).eq("1")
                & ~daily["is_st"].astype(str).eq("1")
            ]
            factors = self._market_store.read_factors(code).set_index("operate_date")
            adjusted = self._same_day_daily_features(daily, factors)
            return self._attach_basic_features(code, adjusted)

        if self._feature_frequency == "minute5":
            source = self._market_store.read_minute_summaries(
                code, start_date, end_date
            ).set_index("trade_date")
            require_window_contract(source)
            source = source[source["full_complete"].fillna(False).astype(bool)]
            factors = self._market_store.read_factors(code).set_index("operate_date")
            factor = self._adjustment_factor(factors, source.index, code)
            adjusted = pd.DataFrame(index=source.index)
            for field in ("open", "high", "low", "close"):
                adjusted[field] = source[f"full_{field}"].astype(float) * factor
            adjusted["volume"] = source["full_volume"].astype(float) / factor
            adjusted["amount"] = source["full_amount"].astype(float)
            adjusted["vwap"] = adjusted["amount"] / adjusted["volume"].replace(0, np.nan)
            adjusted["factor"] = factor
            for field in (
                "full_return",
                "full_range",
                "full_close_vwap",
                "full_last30_return",
            ):
                adjusted[field] = source[field].astype(float)
        else:
            raise ValueError(f"当前阶段不支持的数据频率：{self._feature_frequency}")

        adjusted["full_complete"] = True
        adjusted["buy_complete"] = source["buy_complete"].fillna(False).astype(bool)
        adjusted["sell_complete"] = source["sell_complete"].fillna(False).astype(bool)
        adjusted["buy_vwap"] = pd.to_numeric(
            source["buy_vwap"], errors="coerce"
        ) * factor
        adjusted["sell_vwap"] = pd.to_numeric(
            source["sell_vwap"], errors="coerce"
        ) * factor
        return self._attach_basic_features(code, adjusted)

    @staticmethod
    def _adjustment_factor(
        factors: pd.DataFrame,
        target_index: pd.DatetimeIndex,
        code: str,
    ) -> pd.Series:
        factors = factors.sort_index()
        cumulative = pd.to_numeric(
            factors["cumulative_factor"], errors="coerce"
        ).reindex(target_index, method="ffill")
        if cumulative.isna().any() or cumulative.le(0).any():
            raise ValueError(f"复权因子不能覆盖模型特征范围：{code}")
        baseline = pd.to_numeric(
            factors["cumulative_factor"], errors="coerce"
        ).dropna()
        if baseline.empty or not float(baseline.iloc[0]) > 0:
            raise ValueError(f"复权因子基准值无效：{code}")
        return cumulative / float(baseline.iloc[0])

    def _load_basic_cache(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> None:
        frame = self._market_store.read_basic_panel(
            start_date, end_date, set(codes)
        )
        if frame.empty:
            raise ValueError("L1 basic 数据为空，无法准备模型特征")
        self._basic_by_code = {
            str(code): values.set_index("trade_date").sort_index()
            for code, values in frame.groupby("code", sort=False)
        }

    def _attach_basic_features(
        self, code: str, adjusted: pd.DataFrame
    ) -> pd.DataFrame:
        basic = self._basic_by_code.get(code)
        if basic is None:
            for field in BASIC_FEATURE_FIELDS:
                adjusted[field] = np.nan
            return adjusted
        aligned = basic.reindex(adjusted.index)
        adjusted["turnover_rate"] = pd.to_numeric(
            aligned["turnover_rate"], errors="coerce"
        )
        adjusted["volume_ratio"] = pd.to_numeric(
            aligned["volume_ratio"], errors="coerce"
        )
        circulating = pd.to_numeric(
            aligned["circulating_market_value"], errors="coerce"
        )
        adjusted["log_circulating_market_value"] = np.log1p(
            circulating.where(circulating.gt(0))
        )
        return adjusted

    @staticmethod
    def _same_day_daily_features(
        daily: pd.DataFrame,
        factors: pd.DataFrame,
    ) -> pd.DataFrame:
        """仅使用排名日 D 收盘后已经完整落库的日线字段。"""
        daily = daily.sort_index().copy()
        factor = QlibBinaryStore._adjustment_factor(
            factors, daily.index, "daily"
        )
        source = pd.DataFrame(index=daily.index)
        for field in ("open", "high", "low", "close"):
            source[field] = pd.to_numeric(
                daily[field], errors="coerce"
            ) * factor
        source["volume"] = pd.to_numeric(
            daily["volume"], errors="coerce"
        ) / factor
        source["amount"] = pd.to_numeric(daily["amount"], errors="coerce")
        source["vwap"] = source["amount"] / source["volume"].replace(0, np.nan)
        source["factor"] = factor
        return source

    @staticmethod
    def _write_calendar(target: Path, calendar: pd.DatetimeIndex) -> None:
        (target / "calendars" / "day.txt").write_text(
            "".join(f"{value.strftime('%Y-%m-%d')}\n" for value in calendar),
            encoding="utf-8",
        )

    def _increment(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        existing_range: tuple[date, date],
        existing_instruments: set[str],
    ) -> dict[str, object]:
        self._load_basic_cache(codes, start_date, end_date)
        existing_calendar = self._read_calendar(self._target)
        new_start = existing_range[1] + timedelta(days=1)
        new_frames = {
            code: self._adjusted_daily(code, new_start, end_date)
            for code in codes
        }
        new_calendar = _official_calendar(new_start, end_date, self._market_store.root / "calendar")
        new_instruments = {
            normalize_instrument(code) for code in codes
        } - existing_instruments
        calendar = existing_calendar.append(new_calendar)
        temporary = self._target.with_name(f".{self._target.name}.building")
        shutil.rmtree(temporary, ignore_errors=True)
        shutil.copytree(self._target, temporary)
        intervals = self._read_instrument_intervals(self._target)
        label_count = 0
        total_codes = len(codes)
        set_log_context(step="1/2", progress="0/-", target="Qlib 增量")
        LOGGER.info(
            "开始增量准备 Qlib：新增交易日=%d；新增股票=%d",
            len(new_calendar),
            len(new_instruments),
        )
        try:
            for index, code in enumerate(codes, start=1):
                instrument = normalize_instrument(code)
                full = self._adjusted_daily(code, start_date, end_date)
                if instrument in existing_instruments:
                    if new_calendar.empty:
                        latest = full.reindex(calendar[-1:])
                        for field in self._qlib_fields[:-1]:
                            self._overwrite_last_field(
                                temporary, instrument, field, latest[field]
                            )
                    else:
                        aligned = new_frames[code].reindex(new_calendar)
                        for field in self._qlib_fields[:-1]:
                            self._append_field(
                                temporary, instrument, field, aligned[field]
                            )
                    valid = new_frames[code].dropna(subset=["close"])
                    old_start, old_end = intervals[instrument]
                    intervals[instrument] = (
                        old_start,
                        max(old_end, valid.index.max().date())
                        if not valid.empty
                        else old_end,
                    )
                else:
                    aligned = full.reindex(calendar)
                    labels = build_labels(full, calendar, self._spec)
                    aligned["label"] = labels.reindex(calendar)
                    self._write_features(temporary, instrument, aligned)
                    valid = full.dropna(subset=["close"])
                    if valid.empty:
                        raise ValueError(f"新增股票没有可用 Qlib 日线：{code}")
                    intervals[instrument] = (
                        valid.index.min().date(),
                        valid.index.max().date(),
                    )

                labels = build_labels(full, calendar, self._spec).reindex(calendar)
                self._write_field(temporary, instrument, "label", labels)
                label_count += int(labels.notna().sum())
                if should_log_progress(index, total_codes):
                    set_log_context(progress=f"{index}/{total_codes}", target=code)
                    LOGGER.info("Qlib 增量股票处理完成")

            self._write_calendar(temporary, calendar)
            self._write_instrument_intervals(temporary, intervals)
            self._write_contract(temporary)
            prepared = QlibBinaryStore(self._market_store, temporary, self._spec)
            if not prepared.is_valid(codes, start_date, end_date):
                raise ValueError("Qlib 增量制品检查失败")
            self._replace_target(temporary)
        finally:
            shutil.rmtree(temporary, ignore_errors=True)
        set_log_context(step="2/2", progress="-/-", target=str(self._target))
        return {
            "codes": len(codes),
            "calendarDays": len(calendar),
            "appendedDays": len(new_calendar),
            "newInstruments": len(new_instruments),
            "labels": label_count,
            "startDate": calendar.min().strftime("%Y-%m-%d"),
            "endDate": calendar.max().strftime("%Y-%m-%d"),
            "path": str(self._target),
        }

    def _write_features(self, target: Path, code: str, frame: pd.DataFrame) -> None:
        for field in self._qlib_fields:
            QlibBinaryStore._write_field(target, code, field, frame[field])

    @staticmethod
    def _write_field(
        target: Path, code: str, field: str, values: pd.Series
    ) -> None:
        directory = target / "features" / code.lower()
        directory.mkdir(parents=True, exist_ok=True)
        payload = values.to_numpy(dtype="float32")
        np.hstack(([0.0], payload)).astype("<f4").tofile(
            directory / f"{field}.day.bin"
        )

    @staticmethod
    def _append_field(
        target: Path, code: str, field: str, values: pd.Series
    ) -> None:
        path = target / "features" / code.lower() / f"{field}.day.bin"
        with path.open("ab") as stream:
            stream.write(values.to_numpy(dtype="<f4").tobytes())

    @staticmethod
    def _overwrite_last_field(
        target: Path, code: str, field: str, values: pd.Series
    ) -> None:
        path = target / "features" / code.lower() / f"{field}.day.bin"
        payload = values.to_numpy(dtype="<f4")
        if len(payload) != 1:
            raise ValueError(f"Qlib 最新字段刷新行数异常：{code}:{field}")
        with path.open("r+b") as stream:
            stream.seek(-4, 2)
            stream.write(payload.tobytes())

    @staticmethod
    def _read_calendar(target: Path) -> pd.DatetimeIndex:
        return pd.DatetimeIndex(
            pd.to_datetime(
                [
                    value
                    for value in (target / "calendars" / "day.txt")
                    .read_text(encoding="utf-8")
                    .splitlines()
                    if value.strip()
                ]
            )
        )

    @staticmethod
    def _read_instrument_intervals(
        target: Path,
    ) -> dict[str, tuple[date, date]]:
        result: dict[str, tuple[date, date]] = {}
        for line in (target / "instruments" / "all.txt").read_text(
            encoding="utf-8"
        ).splitlines():
            if not line.strip():
                continue
            instrument, start, end = line.split("\t")
            result[normalize_instrument(instrument)] = (
                date.fromisoformat(start),
                date.fromisoformat(end),
            )
        return result

    @staticmethod
    def _write_instrument_intervals(
        target: Path, intervals: dict[str, tuple[date, date]]
    ) -> None:
        (target / "instruments" / "all.txt").write_text(
            "".join(
                f"{instrument}\t{start.isoformat()}\t{end.isoformat()}\n"
                for instrument, (start, end) in sorted(intervals.items())
            ),
            encoding="utf-8",
        )

    def _contract_version(self) -> str | None:
        try:
            return (self._target / ".contract-version").read_text(
                encoding="ascii"
            ).strip()
        except OSError:
            return None

    def _write_contract(self, target: Path) -> None:
        (target / ".contract-version").write_text(
            self._expected_contract, encoding="ascii"
        )

    def _replace_target(self, temporary: Path) -> None:
        backup = self._target.with_name(f".{self._target.name}.previous")
        shutil.rmtree(backup, ignore_errors=True)
        if self._target.exists():
            self._target.replace(backup)
        try:
            temporary.replace(self._target)
        except BaseException:
            if backup.exists() and not self._target.exists():
                backup.replace(self._target)
            raise
        shutil.rmtree(backup, ignore_errors=True)

# AI_GENERATE_END-------- ----------------------------------------
