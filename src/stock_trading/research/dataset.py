# AI_GENERATE_START-------------- ----------------------------------------
from __future__ import annotations

import logging
import shutil
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from qlib.contrib.data.handler import Alpha158
from qlib.contrib.data.loader import Alpha158DL
from qlib.data.dataset.processor import Processor

from stock_trading.market.domain import normalize_instrument, trade_dates
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.transform import (
    MINUTE5_FEATURE_FIELDS,
    require_window_contract,
)
from stock_trading.utils.logging_utils import set_log_context, should_log_progress
from stock_trading.research.config import (
    MIN_QLIB_TRADING_DAYS,
    ModelSpec,
    expected_qlib_contract,
)

LOGGER = logging.getLogger(__name__)

BASIC_FEATURE_FIELDS = (
    "turnover_rate",
    "volume_ratio",
    "log_circulating_market_value",
    "signed_log_pe_ttm",
    "signed_log_pb_mrq",
    "signed_log_ps_ttm",
    "signed_log_pcf_ncf_ttm",
)


class DailyAlpha158(Alpha158):
    """消费标准日线和同日已发布基础指标的 Alpha158。"""

    def get_feature_config(self):
        fields, names = super().get_feature_config()
        fields.extend(f"${field}" for field in BASIC_FEATURE_FIELDS)
        names.extend(field.upper() for field in BASIC_FEATURE_FIELDS)
        return fields, names


class DailyMinute5PathAlpha158(Alpha158):
    """权威日线 Alpha158、基础指标和 5 分钟路径特征。"""

    def __init__(self, *args, **kwargs):
        infer_processors = list(kwargs.pop("infer_processors", ()))
        infer_processors.append(Minute5CrossSectionalNormalizer())
        super().__init__(*args, infer_processors=infer_processors, **kwargs)

    def get_feature_config(self):
        fields, names = Alpha158DL.get_feature_config(
            {
                "kbar": {},
                "price": {
                    "windows": [0],
                    "feature": ["OPEN", "HIGH", "LOW", "VWAP"],
                },
                "rolling": {},
            }
        )
        extra_fields = BASIC_FEATURE_FIELDS + MINUTE5_FEATURE_FIELDS
        fields.extend(f"${field}" for field in extra_fields)
        names.extend(field.upper() for field in extra_fields)
        return fields, names


class Minute5CrossSectionalNormalizer(Processor):
    """按交易日稳健标准化分钟路径字段，避免极端值主导模型。"""

    _FIELDS = frozenset(field.upper() for field in MINUTE5_FEATURE_FIELDS)

    def __call__(self, frame: pd.DataFrame) -> pd.DataFrame:
        columns = [
            column
            for column in frame.columns
            if isinstance(column, tuple)
            and len(column) >= 2
            and column[0] == "feature"
            and str(column[-1]).upper() in self._FIELDS
        ]
        if not columns:
            return frame
        values = frame.loc[:, columns].apply(pd.to_numeric, errors="coerce")
        log_columns = [
            column
            for column in columns
            if "VOLUME" in str(column[-1]).upper()
            or "AMOUNT" in str(column[-1]).upper()
        ]
        if log_columns:
            values.loc[:, log_columns] = np.sign(values[log_columns]) * np.log1p(
                values[log_columns].abs()
            )

        normalized_array = values.to_numpy(dtype="float64", copy=True)
        datetime_values = values.index.get_level_values("datetime")
        for day in datetime_values.unique():
            positions = np.flatnonzero(datetime_values == day)
            block = normalized_array[positions, :]
            lower = np.nanquantile(block, 0.01, axis=0)
            upper = np.nanquantile(block, 0.99, axis=0)
            block = np.clip(block, lower, upper)
            order = np.argsort(np.argsort(block, axis=0), axis=0)
            counts = np.sum(np.isfinite(block), axis=0)
            normalized_array[positions, :] = (
                (order + 1) / np.maximum(counts, 1) - 0.5
            ) * 3.46
        # MultiIndex 列逐列按位置回写，避免 pandas 将整块矩阵序列化到单个单元。
        for index, column in enumerate(columns):
            frame.iloc[:, frame.columns.get_loc(column)] = normalized_array[:, index].astype(
                "float32"
            )
        return frame


def build_labels(
    observation: pd.DataFrame,
    calendar: pd.DatetimeIndex,
    spec: ModelSpec,
) -> pd.Series:
    """严格按模型绑定的唯一策略生成 T+1 标签。"""
    if observation.empty:
        return pd.Series(index=calendar, dtype="float32", name="label")
    if spec.data_level == "L1":
        return _daily_vwap_labels(observation, calendar)
    if spec.data_level == "L2":
        return _daily_vwap_labels(observation, calendar)
    raise ValueError(f"当前阶段不支持的数据等级：{spec.data_level}")


def _daily_vwap_labels(
    observation: pd.DataFrame, calendar: pd.DatetimeIndex
) -> pd.Series:
    if not {"amount", "volume"}.issubset(observation.columns):
        raise ValueError("L1 标签缺少 amount/volume 日线字段")
    amount = pd.to_numeric(observation["amount"], errors="coerce").reindex(calendar)
    volume = pd.to_numeric(observation["volume"], errors="coerce").reindex(calendar)
    daily_vwap = amount / volume.where(volume.gt(0))
    buy_vwap = daily_vwap.shift(-1)
    sell_vwap = daily_vwap.shift(-2)
    labels = (sell_vwap / buy_vwap - 1.0).where(
        daily_vwap.gt(0) & buy_vwap.gt(0) & sell_vwap.gt(0)
    )
    return labels.astype("float32").rename("label")


def _window_locked(
    code: str,
    daily: pd.DataFrame,
    window_price: pd.Series,
    direction: int,
) -> pd.Series:
    """窗口价格触碰涨跌停即视为该方向无法成交。"""
    rate = 0.20 if code.startswith(("sz.300", "sh.688")) else 0.10
    limit = np.floor(
        pd.to_numeric(daily["preclose"], errors="coerce").to_numpy(dtype="float64")
        * (1.0 + direction * rate)
        * 100.0
        + 0.5
        + 1e-9
    ) / 100.0
    prices = pd.to_numeric(window_price, errors="coerce").to_numpy(dtype="float64")
    touched = prices >= limit - 0.001 if direction > 0 else prices <= limit + 0.001
    return pd.Series(touched, index=daily.index)


def _minute5_window_labels(
    observation: pd.DataFrame, calendar: pd.DatetimeIndex
) -> pd.Series:
    required = {
        "full_complete",
        "buy_vwap",
        "sell_vwap",
        "buy_complete",
        "sell_complete",
        "buy_locked",
        "sell_locked",
    }
    if not required.issubset(observation.columns):
        raise ValueError("L2 标签缺少完整日、买卖窗口或涨跌停锁死字段")
    full_complete = observation["full_complete"].reindex(calendar).fillna(False)
    buy_complete = observation["buy_complete"].reindex(calendar).shift(-1).fillna(False)
    sell_complete = observation["sell_complete"].reindex(calendar).shift(-2).fillna(False)
    buy_locked = observation["buy_locked"].reindex(calendar).shift(-1).fillna(True)
    sell_locked = observation["sell_locked"].reindex(calendar).shift(-2).fillna(True)
    buy_vwap = pd.to_numeric(observation["buy_vwap"], errors="coerce").reindex(calendar).shift(-1)
    sell_vwap = pd.to_numeric(observation["sell_vwap"], errors="coerce").reindex(calendar).shift(-2)
    labels = (sell_vwap / buy_vwap - 1.0).where(
        full_complete.astype(bool)
        & buy_complete.astype(bool)
        & sell_complete.astype(bool)
        & ~buy_locked.astype(bool)
        & ~sell_locked.astype(bool)
        & buy_vwap.gt(0)
        & sell_vwap.gt(0)
    )
    return labels.astype("float32").rename("label")


def _minute5_horizon_labels(
    observation: pd.DataFrame, calendar: pd.DatetimeIndex
) -> pd.DataFrame:
    required = {
        "full_complete",
        "buy_vwap",
        "sell_vwap",
        "buy_complete",
        "sell_complete",
        "buy_locked",
        "sell_locked",
    }
    if not required.issubset(observation.columns):
        raise ValueError("L2 标签缺少完整日、买卖窗口或涨跌停锁死字段")
    full_complete = observation["full_complete"].reindex(calendar).fillna(False)
    buy_complete = observation["buy_complete"].reindex(calendar).shift(-1).fillna(False)
    buy_locked = observation["buy_locked"].reindex(calendar).shift(-1).fillna(True)
    buy_vwap = pd.to_numeric(observation["buy_vwap"], errors="coerce").reindex(calendar).shift(-1)
    labels: dict[str, pd.Series] = {}
    for horizon in (1, 2, 3):
        sell_complete = observation["sell_complete"].reindex(calendar).shift(-(horizon + 1)).fillna(False)
        sell_locked = observation["sell_locked"].reindex(calendar).shift(-(horizon + 1)).fillna(True)
        sell_vwap = pd.to_numeric(observation["sell_vwap"], errors="coerce").reindex(calendar).shift(-(horizon + 1))
        labels[f"label_h{horizon}"] = (
            sell_vwap / buy_vwap - 1.0
        ).where(
            full_complete.astype(bool)
            & buy_complete.astype(bool)
            & sell_complete.astype(bool)
            & ~buy_locked.astype(bool)
            & ~sell_locked.astype(bool)
            & buy_vwap.gt(0)
            & sell_vwap.gt(0)
        ).astype("float32")
    return pd.DataFrame(labels, index=calendar)


def _minute5_dynamic_labels(
    observation: pd.DataFrame, calendar: pd.DatetimeIndex
) -> pd.Series:
    horizons = _minute5_horizon_labels(observation, calendar)
    return horizons.max(axis=1, skipna=False).astype("float32").rename("label")


QLIB_BASE_FIELDS = (
    "open", "high", "low", "close", "volume", "amount", "vwap", "factor",
)
DAILY_QLIB_FIELDS = QLIB_BASE_FIELDS + BASIC_FEATURE_FIELDS + ("label",)
MINUTE5_QLIB_FIELDS = (
    QLIB_BASE_FIELDS
    + BASIC_FEATURE_FIELDS
    + MINUTE5_FEATURE_FIELDS
    + ("label", "label_h1", "label_h2", "label_h3")
)

LABEL_FIELDS = ("label", "label_h1", "label_h2", "label_h3")


def _official_calendar(
    start_date: date, end_date: date,
) -> pd.DatetimeIndex:
    """缺行情不能压缩交易日；缺年度权威日历时明确要求补齐。"""
    if start_date > end_date:
        return pd.DatetimeIndex([])
    return pd.DatetimeIndex([
        value
        for year in range(start_date.year, end_date.year + 1)
        for value in trade_dates(year)
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
        calendar = _official_calendar(start_date, end_date)
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
            valid = frame.dropna(subset=["close"])
            if valid.empty:
                LOGGER.warning("股票范围内无有效日线，已跳过：%s", code)
                continue
            aligned = frame.reindex(calendar)
            labels = build_labels(frame, calendar, self._spec)
            aligned["label"] = labels.reindex(calendar)
            if self._spec.data_level == "L2":
                horizons = _minute5_horizon_labels(frame, calendar)
                for field in LABEL_FIELDS[1:]:
                    aligned[field] = horizons[field]
            qlib_code = normalize_instrument(code)
            self._write_features(temporary, qlib_code, aligned)
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
        daily = self._market_store.read_daily_history(code).set_index("trade_date")
        daily = daily[
            (daily.index.date >= start_date)
            & (daily.index.date <= end_date)
            & daily["tradestatus"].astype(str).eq("1")
            & ~daily["is_st"].astype(str).eq("1")
        ]
        factors = self._market_store.read_factors(code).set_index("operate_date")
        adjusted = self._same_day_daily_features(daily, factors)
        if self._feature_frequency == "daily":
            return self._attach_basic_features(code, adjusted)

        if self._feature_frequency == "minute5":
            source = self._market_store.read_minute_summaries(
                code, start_date, end_date
            ).set_index("trade_date")
            require_window_contract(source)
            source = source[source["full_complete"].fillna(False).astype(bool)]
            source = source[source.index.isin(adjusted.index)]
            adjusted = adjusted.reindex(source.index)
            for field in MINUTE5_FEATURE_FIELDS:
                adjusted[field] = source[field].astype(float)
            raw = daily.reindex(source.index)
            adjusted["buy_locked"] = _window_locked(
                code, raw, source["buy_low"], 1
            )
            adjusted["sell_locked"] = _window_locked(
                code, raw, source["sell_high"], -1
            )
        else:
            raise ValueError(f"当前阶段不支持的数据频率：{self._feature_frequency}")

        factor = adjusted["factor"]
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
        for source_field, feature_field in (
            ("pe_ttm", "signed_log_pe_ttm"),
            ("pb_mrq", "signed_log_pb_mrq"),
            ("ps_ttm", "signed_log_ps_ttm"),
            ("pcf_ncf_ttm", "signed_log_pcf_ncf_ttm"),
        ):
            values = pd.to_numeric(aligned[source_field], errors="coerce")
            adjusted[feature_field] = np.sign(values) * np.log1p(values.abs())
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
        new_calendar = _official_calendar(new_start, end_date)
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
                labels = build_labels(full, calendar, self._spec).reindex(calendar)
                horizon_labels = (
                    _minute5_horizon_labels(full, calendar)
                    if self._spec.data_level == "L2"
                    else pd.DataFrame(index=calendar)
                )
                all_labels = {"label": labels} | {
                    field: horizon_labels[field]
                    for field in LABEL_FIELDS[1:]
                    if field in horizon_labels
                }
                feature_fields = tuple(
                    field for field in self._qlib_fields if field not in LABEL_FIELDS
                )
                if instrument in existing_instruments:
                    if new_calendar.empty:
                        latest = full.reindex(calendar[-1:])
                        for field in feature_fields:
                            self._overwrite_last_field(
                                temporary, instrument, field, latest[field]
                            )
                    else:
                        aligned = full.reindex(new_calendar)
                        for field in feature_fields:
                            self._append_field(
                                temporary, instrument, field, aligned[field]
                            )
                    for field, values in all_labels.items():
                        label_values = values.reindex(calendar)
                        if new_calendar.empty:
                            self._overwrite_last_field(
                                temporary, instrument, field, label_values.iloc[-1:]
                            )
                        else:
                            self._append_field(
                                temporary, instrument, field, label_values.reindex(new_calendar)
                            )
                    valid = full.dropna(subset=["close"])
                    old_start, old_end = intervals[instrument]
                    intervals[instrument] = (
                        old_start,
                        max(old_end, valid.index.max().date())
                        if not valid.empty
                        else old_end,
                    )
                else:
                    valid = full.dropna(subset=["close"])
                    if valid.empty:
                        LOGGER.warning("新增股票范围内无有效日线，已跳过：%s", code)
                        continue
                    aligned = full.reindex(calendar)
                    for field, values in all_labels.items():
                        aligned[field] = values
                    self._write_features(temporary, instrument, aligned)
                    intervals[instrument] = (
                        valid.index.min().date(),
                        valid.index.max().date(),
                    )

                for field, values in all_labels.items():
                    self._write_field(temporary, instrument, field, values)
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

# AI_GENERATE_END--------------- ----------------------------------------
