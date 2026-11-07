# AI_GENERATE_START-------- --------------------------------------------------------------
from __future__ import annotations

from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date, timedelta
import logging
from pathlib import Path
from uuid import uuid4

import pandas as pd
import pyarrow.parquet as pq

from stock_trading.market.minute5_summary import summarize_trading_windows
from stock_trading.contracts import WINDOW_CONTRACT
from stock_trading.market.minute1_summary import (
    summarize_minute1_days,
)
from stock_trading.market.models import (
    CumulativeAdjustmentFactor,
    DailyBasic,
    DailyBar,
    MinuteBar,
    StockBasic,
)
from stock_trading.utils.logging_utils import set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)
RAW_MINUTE_RETENTION_DAYS = 90
MINUTE_SUMMARY_RETENTION_DAYS = 366 * 5


@dataclass(frozen=True)
class CollectionPlan:
    code: str
    needs_daily: bool
    needs_factor: bool
    needs_minute5: bool


@dataclass(frozen=True)
class ResearchDataAudit:
    pending: tuple[CollectionPlan, ...]
    valid_codes: tuple[str, ...]
    model_codes: tuple[str, ...]
    tradable_codes: tuple[str, ...]

    @property
    def complete(self) -> bool:
        return not self.pending


class ParquetMarketStore:
    """管理统一基础信息、日线、复权因子和5分钟行情。"""

    def __init__(self, root: Path) -> None:
        self._root = root

    @property
    def root(self) -> Path:
        return self._root

    def write_stock_basics(self, stocks: list[StockBasic]) -> Path:
        """原子写入当前股票基础信息快照。"""
        target = self._root / "basic" / "stocks.parquet"
        return self._write(target, [stock.as_record() for stock in stocks])

    def read_stock_basics(self) -> pd.DataFrame:
        """读取股票基础信息快照。"""
        target = self._root / "basic" / "stocks.parquet"
        frame = _read_parquet_file(target)
        for field in ("ipo_date", "out_date", "snapshot_date"):
            frame[field] = pd.to_datetime(frame[field], errors="coerce")
        return frame.sort_values("code").reset_index(drop=True)

    def stock_basics_valid(self, snapshot_date: date) -> bool:
        """检查基础信息文件是否可读、字段完整且属于目标快照日。"""
        try:
            frame = self.read_stock_basics()
        except (OSError, ValueError, KeyError):
            return False
        required = {
            "code", "code_name", "ipo_date", "out_date", "security_type",
            "market_category", "status", "trade_status", "snapshot_date",
        }
        if (
            not required.issubset(frame.columns)
            or len(frame) < 100
            or not frame["code"].astype(str).str.match(r"^(sh|sz)\.\d{6}$").all()
            or frame["trade_status"].fillna("").astype(str).eq("1").sum() < 100
        ):
            return False
        return (
            frame["snapshot_date"].notna().all()
            and frame["snapshot_date"].dt.date.eq(snapshot_date).all()
        )

    def write_daily_history(self, code: str, bars: list[DailyBar]) -> Path:
        target = self._root / "daily" / f"code={code}" / "bars.parquet"
        records = [_without(bar.as_record(), "code") for bar in bars]
        return self._merge_history(target, records, "trade_date")

    def write_daily_snapshot(self, trade_date: date, bars: list[DailyBar]) -> Path:
        """保存指定交易日的全市场日线横截面。"""
        target = (
            self._root
            / "daily"
            / f"trade_date={trade_date.isoformat()}"
            / "bars.parquet"
        )
        return self._write(target, [bar.as_record() for bar in bars])

    def write_daily_basics(
        self, trade_date: date, values: list[DailyBasic]
    ) -> Path:
        """保存指定交易日的换手率、量比和市值横截面。"""
        target = (
            self._root
            / "daily_basic"
            / f"trade_date={trade_date.isoformat()}"
            / "indicators.parquet"
        )
        return self._write(target, [value.as_record() for value in values])

    def daily_basic_rows(self, trade_date: date, codes: set[str]) -> int:
        """返回已完整覆盖目标股票的每日基础指标行数。"""
        target = (
            self._root
            / "daily_basic"
            / f"trade_date={trade_date.isoformat()}"
            / "indicators.parquet"
        )
        try:
            frame = _read_parquet_file(target)
            required = {
                "trade_date",
                "code",
                "turnover_rate",
                "volume_ratio",
                "total_market_value",
                "circulating_market_value",
            }
            dates = pd.to_datetime(frame["trade_date"], errors="raise").dt.date
        except (OSError, ValueError, KeyError, AttributeError):
            return 0
        available = set(frame["code"].dropna().astype(str))
        if (
            frame.empty
            or not required.issubset(frame.columns)
            or not dates.eq(trade_date).all()
            or not codes.issubset(available)
        ):
            return 0
        return len(codes)

    def write_factors(
        self,
        code: str,
        factors: list[CumulativeAdjustmentFactor],
    ) -> Path:
        """保存 TuShare 每日累计复权因子。"""
        target = self._root / "factor" / f"code={code}" / "factors.parquet"
        records = [_without(factor.as_record(), "code") for factor in factors]
        return self._merge_history(target, records, "operate_date")

    def write_minute_history(
        self, code: str, bars: list[MinuteBar], reference_date: date
    ) -> dict[str, object]:
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = pd.DataFrame.from_records([bar.as_record() for bar in bars])
        existing = self.read_minutes(code)
        frames = [value for value in (existing, incoming) if not value.empty]
        return self._store_minute_frames(
            code, pd.concat(frames, ignore_index=True), reference_date
        )

    def write_minute1_history(self, code: str, bars: list[MinuteBar]) -> Path:
        """按股票和月份保存券商 1 分钟标准行情及日级特征摘要。"""
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = _normalize_minute1_frame(
            pd.DataFrame.from_records([bar.as_record() for bar in bars]), code
        )
        written: list[Path] = []
        summary_sources: list[pd.DataFrame] = []
        months = incoming["trade_date"].dt.strftime("%Y-%m")
        for month, values in incoming.groupby(months, sort=True):
            target = self._minute1_partition_path(code, str(month))
            existing = (
                _normalize_minute1_frame(_read_parquet_file(target), code)
                if target.is_file()
                else pd.DataFrame()
            )
            merged = _normalize_minute1_frame(
                pd.concat(
                    [frame for frame in (existing, values) if not frame.empty],
                    ignore_index=True,
                ),
                code,
            )
            self._write_frame(target, merged)
            written.append(target)
            summary_sources.append(merged)
        self._write_minute1_summaries(
            code,
            summarize_minute1_days(
                pd.concat(summary_sources, ignore_index=True)
            ),
        )
        return written[-1]

    def read_daily_history(self, code: str) -> pd.DataFrame:
        target = self._root / "daily" / f"code={code}" / "bars.parquet"
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_factors(self, code: str) -> pd.DataFrame:
        target = self._root / "factor" / f"code={code}" / "factors.parquet"
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["operate_date"] = pd.to_datetime(frame["operate_date"])
        return frame.sort_values("operate_date").reset_index(drop=True)

    def read_minutes(self, code: str) -> pd.DataFrame:
        target = self._root / "minute5" / f"code={code}" / "bars.parquet"
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        for field in ("bar_start", "bar_end"):
            frame[field] = pd.to_datetime(frame[field], utc=True).dt.tz_convert(
                "Asia/Shanghai"
            )
        return frame.sort_values("bar_end").reset_index(drop=True)

    def read_minute1_history(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        directory = self._root / "minute" / f"code={code}"
        paths = sorted(directory.glob("month=*/bars.parquet"))
        if start_date is not None or end_date is not None:
            start_month = start_date.strftime("%Y-%m") if start_date else None
            end_month = end_date.strftime("%Y-%m") if end_date else None
            paths = [
                path
                for path in paths
                if (
                    start_month is None
                    or path.parent.name.removeprefix("month=") >= start_month
                )
                and (
                    end_month is None
                    or path.parent.name.removeprefix("month=") <= end_month
                )
            ]
        if not paths:
            return pd.DataFrame()
        frame = _normalize_minute1_frame(
            pd.concat([_read_parquet_file(path) for path in paths], ignore_index=True),
            code,
        )
        if start_date is not None:
            frame = frame[frame["trade_date"].dt.date >= start_date]
        if end_date is not None:
            frame = frame[frame["trade_date"].dt.date <= end_date]
        return frame.reset_index(drop=True)

    def read_minute1_summaries(self, code: str) -> pd.DataFrame:
        target = self._minute1_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_minute_summaries(self, code: str) -> pd.DataFrame:
        target = self._minute_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        current = (
            frame["window_contract"].eq(WINDOW_CONTRACT)
            if "window_contract" in frame
            else pd.Series(False, index=frame.index)
        )
        if not current.all():
            old_dates = set(frame.loc[~current, "trade_date"])
            raw = self.read_minutes(code)
            rebuilt = summarize_trading_windows(raw) if not raw.empty else pd.DataFrame()
            if not rebuilt.empty:
                rebuilt = rebuilt[rebuilt["trade_date"].isin(old_dates)]
            missing = old_dates - (set(rebuilt["trade_date"]) if not rebuilt.empty else set())
            if missing:
                LOGGER.warning(
                    "旧分钟摘要不能用于下午执行合同：股票=%s；缺原始数据日期=%s至%s，共%d日，请补采5分钟数据",
                    code, min(missing).date(), max(missing).date(), len(missing),
                )
            valid_frames = [part for part in (frame.loc[current], rebuilt) if not part.empty]
            frame = pd.concat(valid_frames, ignore_index=True) if valid_frames else frame.iloc[0:0]
        return frame.sort_values("trade_date").reset_index(drop=True)

    def daily_history_valid(self, code: str, start_date: date, end_date: date) -> bool:
        """检查日线文件可读、字段完整且覆盖研究窗口。"""
        try:
            frame = self.read_daily_history(code)
            pd.to_datetime(frame["trade_date"], errors="raise")
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {
            "code", "trade_date", "open", "high", "low", "close", "volume",
            "tradestatus", "is_st",
        }
        return (
            required.issubset(frame.columns)
            and not frame.empty
        )

    def factors_valid(self, code: str, end_date: date) -> bool:
        """检查复权因子文件可读、字段完整且日期不超过目标日。"""
        try:
            frame = self.read_factors(code)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {"code", "operate_date", "cumulative_factor"}
        if not required.issubset(frame.columns):
            return False
        dates = pd.to_datetime(frame["operate_date"], errors="coerce").dt.date
        return bool(
            not frame.empty
            and dates.notna().all()
            and dates.le(end_date).any()
            and frame["cumulative_factor"].astype(float).gt(0).all()
        )

    def minute_summaries_valid(
        self,
        code: str,
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> bool:
        try:
            summaries = self.read_minute_summaries(code)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {
            "trade_date",
            "morning_complete",
            "sell_complete",
            "buy_complete",
            "morning_open",
            "morning_high",
            "morning_low",
            "morning_close",
            "morning_volume",
            "morning_amount",
            "morning_vwap",
            "sell_vwap",
            "buy_vwap",
        }
        if not required.issubset(summaries.columns) or summaries.empty:
            return False
        dates = summaries["trade_date"].dt.date
        window = summaries[dates.between(start_date, end_date)]
        try:
            daily = self._eligible_daily_rows(self.read_daily_history(code))
            daily_dates = pd.to_datetime(daily["trade_date"], errors="raise").dt.date
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        expected_history = set(
            daily_dates[
                daily_dates.between(start_date, end_date)
                & daily_dates.lt(end_date)
            ]
        )
        return self._minute_window_usable(
            window, end_date, expected_history, require_full_end
        )

    @staticmethod
    def _minute_window_usable(
        window: pd.DataFrame,
        end_date: date,
        expected_history: set[date] | None = None,
        require_full_end: bool = False,
    ) -> bool:
        if window.empty:
            return False
        dates = window["trade_date"].dt.date
        current = window[dates == end_date]
        history = window[dates < end_date]
        if current.empty or not current["morning_complete"].fillna(False).astype(bool).all():
            return False
        if require_full_end and not bool(
            current["buy_complete"].fillna(False).astype(bool).all()
            and current["sell_complete"].fillna(False).astype(bool).all()
        ):
            return False
        complete_history = history[
            history["morning_complete"].fillna(False).astype(bool)
            & history["buy_complete"].fillna(False).astype(bool)
            & history["sell_complete"].fillna(False).astype(bool)
        ]
        complete_dates = set(complete_history["trade_date"].dt.date)
        return bool((expected_history or set()).issubset(complete_dates))

    def valid_daily_codes(
        self, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回日线和复权数据均通过窗口校验的股票编号。"""
        return [
            code for code in codes
            if self.daily_history_valid(code, start_date, end_date)
            and self.factors_valid(code, end_date)
        ]

    def valid_research_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> list[str]:
        """返回日线、复权因子和分钟窗口均覆盖研究范围的股票编号。"""
        daily = set(self.valid_daily_codes(codes, start_date, end_date))
        researchable = set(self.model_research_codes(codes, start_date, end_date))
        return [
            code
            for code in codes
            if code in daily
            and code in researchable
            and self.minute_summaries_valid(
                code, start_date, end_date, require_full_end
            )
        ]

    def audit_research_data(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        coverage_ranges: Mapping[str, tuple[date, date, bool]] | None = None,
    ) -> ResearchDataAudit:
        """单次读取研究文件，生成采集计划和后续初始化股票池。"""
        pending: list[CollectionPlan] = []
        valid_codes: list[str] = []
        model_codes: list[str] = []
        tradable_codes: list[str] = []
        daily_required = {
            "code", "trade_date", "open", "high", "low", "close", "volume",
            "tradestatus", "is_st",
        }
        factor_required = {"code", "operate_date", "cumulative_factor"}
        total = len(codes)
        for index, code in enumerate(codes, start=1):
            code_start, code_end, exact_end = (
                coverage_ranges.get(code, (start_date, end_date, True))
                if coverage_ranges is not None
                else (start_date, end_date, True)
            )
            try:
                daily = self.read_daily_history(code)
                daily_dates = pd.to_datetime(
                    daily["trade_date"], errors="raise"
                ).dt.date
            except (OSError, ValueError, KeyError, AttributeError):
                daily = pd.DataFrame()
                daily_dates = pd.Series(dtype="object")
            raw_window_dates = daily_dates[
                daily_dates.between(code_start, code_end)
            ]
            daily_valid = daily_required.issubset(daily.columns) and (
                not raw_window_dates.empty
                and raw_window_dates.min() <= code_start + timedelta(days=15)
                and (
                    raw_window_dates.max() == code_end
                    if exact_end
                    else raw_window_dates.max() >= code_end - timedelta(days=15)
                )
            )

            try:
                factors = self.read_factors(code)
                factor_dates = pd.to_datetime(
                    factors["operate_date"], errors="coerce"
                ).dt.date
                factor_valid = (
                    factor_required.issubset(factors.columns)
                    and not factors.empty
                    and factor_dates.notna().all()
                    and factors["cumulative_factor"].astype(float).gt(0).all()
                    and (
                        raw_window_dates.empty
                        or factor_dates.min() <= raw_window_dates.min()
                    )
                )
            except (OSError, ValueError, KeyError, AttributeError):
                factor_valid = False

            eligible = (
                self._eligible_daily_rows(daily)
                if daily_required.issubset(daily.columns)
                else pd.DataFrame()
            )
            eligible_dates = (
                daily_dates.loc[eligible.index]
                if not eligible.empty
                else pd.Series(dtype="object")
            )
            daily_window = eligible[eligible_dates.between(code_start, code_end)]
            try:
                summaries = self.read_minute_summaries(code)
                summary_dates = summaries["trade_date"].dt.date
                summary_window = summaries[
                    summary_dates.between(code_start, code_end)
                ]
                expected_history = set(
                    eligible_dates[
                        eligible_dates.between(code_start, code_end)
                        & eligible_dates.lt(code_end)
                    ]
                )
                minute_valid = self._minute_window_usable(
                    summary_window,
                    code_end,
                    expected_history,
                    require_full_end=code_end < end_date,
                )
            except (OSError, ValueError, KeyError, AttributeError):
                minute_valid = False
            data_valid = daily_valid and factor_valid and minute_valid
            if data_valid:
                valid_codes.append(code)
                if not daily_window.empty:
                    model_codes.append(code)
            if not eligible.empty and eligible_dates.eq(end_date).any():
                tradable_codes.append(code)
            if not data_valid:
                pending.append(
                    CollectionPlan(
                        code=code,
                        needs_daily=not daily_valid,
                        needs_factor=not factor_valid,
                        needs_minute5=not minute_valid,
                    )
                )
            if should_log_progress(index, total):
                set_log_context(progress=f"{index}/{total}", target=code)
                LOGGER.info("本地研究数据检查完成")

        return ResearchDataAudit(
            pending=tuple(pending),
            valid_codes=tuple(valid_codes),
            model_codes=tuple(model_codes),
            tradable_codes=tuple(tradable_codes),
        )

    def model_research_codes(
        self, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回研究窗口内至少存在一个非 ST 可交易日的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = self._eligible_daily_rows(self.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.between(start_date, end_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

    def tradable_codes_on(self, codes: list[str], trade_date: date) -> list[str]:
        """返回指定交易日具备有效非 ST 日线事实的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = self._eligible_daily_rows(self.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.eq(trade_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

    def minute5_codes_on(self, codes: list[str], trade_date: date) -> list[str]:
        result: list[str] = []
        for code in codes:
            try:
                summaries = self.read_minute_summaries(code)
                dates = summaries["trade_date"].dt.date
                rows = summaries[dates == trade_date]
                if (
                    not rows.empty
                    and rows["morning_complete"].fillna(False).astype(bool).all()
                ):
                    result.append(code)
            except (OSError, ValueError, KeyError, AttributeError):
                continue
        return result

    def minute1_codes_on(self, codes: list[str], trade_date: date) -> list[str]:
        """返回指定交易日存在有效成交事实的 1 分钟行情股票。"""
        return self.minute1_codes_between(codes, trade_date, trade_date)

    def minute1_codes_between(
        self, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回指定日期范围内存在有效成交事实的 1 分钟行情股票。"""
        result: list[str] = []
        for code in codes:
            try:
                summaries = self.read_minute1_summaries(code)
                dates = summaries["trade_date"].dt.date
                rows = summaries[(dates >= start_date) & (dates <= end_date)]
                if (
                    not rows.empty
                    and rows["minute1_complete"].fillna(False).astype(bool).any()
                ):
                    result.append(code)
            except (OSError, ValueError, KeyError, AttributeError):
                continue
        return result

    def minute1_complete_dates(
        self, code: str, start_date: date, end_date: date
    ) -> set[date]:
        """返回具备完整241根标准分钟线的交易日。"""
        summaries = self.read_minute1_summaries(code)
        if summaries.empty:
            return set()
        dates = summaries["trade_date"].dt.date
        selected = summaries[(dates >= start_date) & (dates <= end_date)]
        if selected.empty:
            return set()
        return set(
            selected.loc[
                selected["minute1_complete"].fillna(False).astype(bool),
                "trade_date",
            ].dt.date
        )

    def valid_minute1_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int,
    ) -> list[str]:
        """返回达到分钟训练历史门槛的股票。"""
        return [
            code
            for code in codes
            if len(self.minute1_complete_dates(code, start_date, end_date))
            >= minimum_days
        ]

    def cleanup_minutes(self, reference_date: date) -> dict[str, int]:
        root = self._root / "minute5"
        directories = sorted(root.glob("code=*")) if root.is_dir() else []
        before_raw_rows = 0
        after_raw_rows = 0
        before_summary_rows = 0
        after_summary_rows = 0
        for directory in directories:
            code = directory.name.removeprefix("code=")
            minutes = self.read_minutes(code)
            if minutes.empty:
                continue
            result = self._store_minute_frames(code, minutes, reference_date)
            before_raw_rows += int(result["beforeRawRows"])
            after_raw_rows += int(result["rawRows"])
            before_summary_rows += int(result["beforeSummaryRows"])
            after_summary_rows += int(result["summaryRows"])
        return {
            "codes": len(directories),
            "beforeRawRows": before_raw_rows,
            "rawRows": after_raw_rows,
            "deletedRawRows": max(0, before_raw_rows - after_raw_rows),
            "beforeSummaryRows": before_summary_rows,
            "summaryRows": after_summary_rows,
            "deletedSummaryRows": max(
                0, before_summary_rows - after_summary_rows
            ),
        }

    @staticmethod
    def _eligible_daily_rows(frame: pd.DataFrame) -> pd.DataFrame:
        return frame[
            (frame["tradestatus"].astype(str) == "1")
            & (frame["is_st"].astype(str) != "1")
            & (frame["close"].astype(float) > 0)
            & (frame["volume"].astype(float) > 0)
        ]

    def _merge_history(
        self,
        target: Path,
        records: list[dict[str, object]],
        date_field: str,
    ) -> Path:
        if not records:
            raise ValueError(f"Refusing to write empty market history: {target}")
        frames = [pd.DataFrame.from_records(records)]
        existing = pd.DataFrame()
        if target.is_file():
            try:
                existing = _read_parquet_file(target)
            except Exception:
                existing = pd.DataFrame()
        if not existing.empty:
            frames.insert(0, existing)
        merged = pd.concat(frames, ignore_index=True)
        merged[date_field] = pd.to_datetime(
            merged[date_field], errors="raise"
        ).dt.strftime("%Y-%m-%d")
        merged = (
            merged.drop_duplicates(subset=[date_field], keep="last")
            .sort_values(date_field)
            .reset_index(drop=True)
        )
        return self._write(target, merged.to_dict(orient="records"))

    def _store_minute_frames(
        self, code: str, minutes: pd.DataFrame, reference_date: date
    ) -> dict[str, object]:
        frame = minutes.copy()
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        for field in ("bar_start", "bar_end"):
            frame[field] = pd.to_datetime(frame[field], utc=True).dt.tz_convert(
                "Asia/Shanghai"
            )
        frame = (
            frame.drop_duplicates(subset=["bar_end"], keep="last")
            .sort_values("bar_end")
            .reset_index(drop=True)
        )
        generated = summarize_trading_windows(frame)
        # 保留未重采的历史摘要原件；读取端只允许新合同或原始数据重算结果。
        existing_path = self._minute_summary_path(code)
        existing = _read_parquet_file(existing_path) if existing_path.is_file() else pd.DataFrame()
        if not existing.empty:
            existing["trade_date"] = pd.to_datetime(existing["trade_date"], errors="raise")
        before_raw_rows = len(frame)
        before_summary_rows = len(existing)
        summaries = [value for value in (existing, generated) if not value.empty]
        summary = pd.concat(summaries, ignore_index=True) if summaries else generated
        for field in ("morning_complete", "sell_complete", "buy_complete"):
            summary[field] = summary[field].fillna(False).astype(bool)
        summary = (
            summary.drop_duplicates(subset=["trade_date"], keep="last")
            .sort_values("trade_date")
            .reset_index(drop=True)
        )
        summary_cutoff = reference_date - timedelta(days=MINUTE_SUMMARY_RETENTION_DAYS)
        summary = summary[summary["trade_date"].dt.date >= summary_cutoff]
        raw_cutoff = reference_date - timedelta(days=RAW_MINUTE_RETENTION_DAYS - 1)
        raw = frame[frame["trade_date"].dt.date >= raw_cutoff]
        raw_path = self._root / "minute5" / f"code={code}" / "bars.parquet"
        summary_path = self._minute_summary_path(code)
        if raw.empty:
            raw_path.unlink(missing_ok=True)
        else:
            self._write(raw_path, raw.to_dict(orient="records"))
        if summary.empty:
            summary_path.unlink(missing_ok=True)
        else:
            self._write(summary_path, summary.to_dict(orient="records"))
        return {
            "rawPath": str(raw_path),
            "summaryPath": str(summary_path),
            "rawRows": len(raw),
            "summaryRows": len(summary),
            "beforeRawRows": before_raw_rows,
            "beforeSummaryRows": before_summary_rows,
        }

    def _minute_summary_path(self, code: str) -> Path:
        return self._root / "minute5_summary" / f"code={code}" / "windows.parquet"

    def _minute1_partition_path(self, code: str, month: str) -> Path:
        return (
            self._root
            / "minute"
            / f"code={code}"
            / f"month={month}"
            / "bars.parquet"
        )

    def _minute1_summary_path(self, code: str) -> Path:
        return self._root / "minute1_summary" / f"code={code}" / "features.parquet"

    def _write_minute1_summaries(
        self, code: str, generated: pd.DataFrame
    ) -> Path:
        target = self._minute1_summary_path(code)
        existing = self.read_minute1_summaries(code)
        frames = [frame for frame in (existing, generated) if not frame.empty]
        if not frames:
            raise ValueError(f"Refusing to write empty minute1 summary: {code}")
        summary = pd.concat(frames, ignore_index=True)
        summary["trade_date"] = pd.to_datetime(
            summary["trade_date"], errors="raise"
        )
        summary = (
            summary.drop_duplicates(subset=["trade_date"], keep="last")
            .sort_values("trade_date")
            .reset_index(drop=True)
        )
        return self._write_frame(target, summary)

    @staticmethod
    def _write(target: Path, rows: list[dict[str, object]]) -> Path:
        if not rows:
            raise ValueError(f"Refusing to write an empty market data partition: {target}")
        return ParquetMarketStore._write_frame(
            target, pd.DataFrame.from_records(rows)
        )

    @staticmethod
    def _write_frame(target: Path, frame: pd.DataFrame) -> Path:
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
        try:
            frame.to_parquet(temporary, index=False)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return target


def _without(record: dict[str, object], *fields: str) -> dict[str, object]:
    return {key: value for key, value in record.items() if key not in fields}


def _read_parquet_file(path: Path) -> pd.DataFrame:
    """读取单个文件，避免 key=value 父目录触发 Hive 分区字段合并。"""
    return pq.ParquetFile(path).read().to_pandas()


def _normalize_minute1_frame(frame: pd.DataFrame, code: str) -> pd.DataFrame:
    if frame.empty:
        return frame
    normalized = frame.copy()
    if "code" not in normalized:
        normalized.insert(0, "code", code)
    normalized["trade_date"] = pd.to_datetime(
        normalized["trade_date"], errors="raise"
    )
    for field in ("bar_start", "bar_end"):
        normalized[field] = pd.to_datetime(
            normalized[field], utc=True
        ).dt.tz_convert("Asia/Shanghai")
    return (
        normalized.drop_duplicates(subset=["bar_end"], keep="last")
        .sort_values("bar_end")
        .reset_index(drop=True)
    )


# AI_GENERATE_END-------- --------------------------------------------------------------
