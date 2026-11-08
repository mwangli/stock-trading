# AI_GENERATE_START-------------------------- -----------------------------------------------------------------
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
    EXPECTED_MINUTE1_BARS,
    summarize_minute1_days,
)
from stock_trading.market.models import (
    BASIC_CONTRACT_BAOSTOCK,
    DAILY_CONTRACT_BAOSTOCK,
    FACTOR_CONTRACT_BAOSTOCK,
    STOCK_CONTRACT_BAOSTOCK,
    CumulativeAdjustmentFactor,
    DailyBasic,
    DailyBar,
    MinuteBar,
    StockBasic,
)
from stock_trading.utils.logging_utils import set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)
MINUTE_HISTORY_RETENTION_DAYS = 366 * 5


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
    """管理股票清单、分级行情和模型基础指标。"""

    def __init__(self, root: Path) -> None:
        self._root = root / "raw"
        self._derived_root = root / "derived"

    @property
    def root(self) -> Path:
        return self._root

    def write_stock_basics(self, stocks: list[StockBasic]) -> Path:
        """原子写入当前股票基础信息快照。"""
        target = self._root / "stock" / "stocks.parquet"
        return self._write(target, [stock.as_record() for stock in stocks])

    def read_stock_basics(self) -> pd.DataFrame:
        """读取股票基础信息快照。"""
        target = self._root / "stock" / "stocks.parquet"
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
            "source_contract",
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
            and frame["source_contract"].eq(STOCK_CONTRACT_BAOSTOCK).all()
        )

    def write_daily_history(self, code: str, bars: list[DailyBar]) -> Path:
        target = self._root / "daily" / f"code={code}" / "bars.parquet"
        records = [_without(bar.as_record(), "code") for bar in bars]
        if target.is_file():
            existing = _read_parquet_file(target)
            existing_contracts = (
                set(existing["source_contract"].dropna().astype(str))
                if "source_contract" in existing
                else set()
            )
            if existing_contracts != {DAILY_CONTRACT_BAOSTOCK}:
                return self._write(target, records)
        return self._merge_history(target, records, "trade_date")

    def write_basic_history(
        self, code: str, values: list[DailyBasic]
    ) -> Path:
        target = (
            self._derived_root / "basic" / f"code={code}" / "indicators.parquet"
        )
        records = [_without(value.as_record(), "code") for value in values]
        if target.is_file():
            existing = _read_parquet_file(target)
            contracts = set(existing.get("source_contract", pd.Series(dtype=str)))
            if contracts != {BASIC_CONTRACT_BAOSTOCK}:
                return self._write(target, records)
        return self._merge_history(target, records, "trade_date")

    def read_basic_history(self, code: str) -> pd.DataFrame:
        target = (
            self._derived_root / "basic" / f"code={code}" / "indicators.parquet"
        )
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_basic_panel(
        self,
        start_date: date,
        end_date: date,
        codes: set[str] | None = None,
    ) -> pd.DataFrame:
        """一次读取日期范围内的每日基础指标，供模型准备复用。"""
        directory = self._derived_root / "basic"
        selected_codes = codes or {
            path.parent.name.removeprefix("code=")
            for path in directory.glob("code=*/indicators.parquet")
        }
        paths = [
            directory / f"code={code}" / "indicators.parquet"
            for code in sorted(selected_codes)
            if (directory / f"code={code}" / "indicators.parquet").is_file()
        ]
        if not paths:
            return pd.DataFrame()
        frames: list[pd.DataFrame] = []
        for path in paths:
            values = _read_parquet_file(path)
            if "code" not in values:
                values.insert(0, "code", path.parent.name.removeprefix("code="))
            frames.append(values)
        frame = pd.concat(frames, ignore_index=True)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        if "source_contract" not in frame:
            return frame.iloc[0:0].copy()
        frame = frame[frame["source_contract"].eq(BASIC_CONTRACT_BAOSTOCK)]
        dates = frame["trade_date"].dt.date
        frame = frame[dates.between(start_date, end_date)]
        return (
            frame.drop_duplicates(subset=["trade_date", "code"], keep="last")
            .sort_values(["code", "trade_date"])
            .reset_index(drop=True)
        )

    def valid_basic_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回换手率、量比和流通市值覆盖生命周期范围的股票。"""
        try:
            frame = self.read_basic_panel(start_date, end_date, set(codes))
            turnover = pd.to_numeric(frame["turnover_rate"], errors="coerce")
            volume_ratio = pd.to_numeric(frame["volume_ratio"], errors="coerce")
            circulating = pd.to_numeric(
                frame["circulating_market_value"], errors="coerce"
            )
        except (OSError, ValueError, KeyError, AttributeError):
            return []
        usable = frame[
            turnover.notna() & volume_ratio.gt(0) & circulating.gt(0)
        ]
        groups = {
            str(code): values
            for code, values in usable.groupby("code", sort=False)
        }
        result: list[str] = []
        for code in codes:
            values = groups.get(code)
            if values is None or len(values) < minimum_days:
                continue
            dates = pd.to_datetime(
                values["trade_date"], errors="coerce"
            ).dt.date
            code_start = (start_dates or {}).get(code, start_date)
            code_end = (end_dates or {}).get(code, end_date)
            if (
                dates.notna().all()
                and dates.min() <= code_start + timedelta(days=15)
                and dates.max() >= code_end - timedelta(days=15)
            ):
                result.append(code)
        return result

    def write_factors(
        self,
        code: str,
        factors: list[CumulativeAdjustmentFactor],
    ) -> Path:
        """保存单一来源合同的累计复权因子。"""
        target = (
            self._derived_root / "factor" / f"code={code}" / "factors.parquet"
        )
        records = [_without(factor.as_record(), "code") for factor in factors]
        contracts = {str(record["factor_contract"]) for record in records}
        if contracts != {FACTOR_CONTRACT_BAOSTOCK}:
            raise ValueError(f"复权因子来源合同无效：{code}")
        if target.is_file():
            existing = _read_parquet_file(target)
            existing_contracts = (
                set(existing["factor_contract"].dropna().astype(str))
                if "factor_contract" in existing
                else set()
            )
            if existing_contracts != contracts:
                return self._write(target, records)
        return self._merge_history(target, records, "operate_date")

    def write_minute_history(
        self, code: str, bars: list[MinuteBar], reference_date: date
    ) -> dict[str, object]:
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = pd.DataFrame.from_records([bar.as_record() for bar in bars])
        existing = self.read_minutes(code)
        frames = [value for value in (existing, incoming) if not value.empty]
        return self._store_minute_raw(
            code, pd.concat(frames, ignore_index=True), reference_date
        )

    def write_minute1_history(self, code: str, bars: list[MinuteBar]) -> Path:
        """按股票和月份只保存券商 1 分钟原始行情。"""
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = _normalize_minute1_frame(
            pd.DataFrame.from_records([bar.as_record() for bar in bars]), code
        )
        written: list[Path] = []
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
        return written[-1]

    def read_daily_history(self, code: str) -> pd.DataFrame:
        target = self._root / "daily" / f"code={code}" / "bars.parquet"
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_factors(self, code: str) -> pd.DataFrame:
        target = (
            self._derived_root / "factor" / f"code={code}" / "factors.parquet"
        )
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["operate_date"] = pd.to_datetime(frame["operate_date"])
        return frame.sort_values("operate_date").reset_index(drop=True)

    def read_minutes(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
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
        dates = frame["trade_date"].dt.date
        if start_date is not None:
            frame = frame[dates >= start_date]
            dates = frame["trade_date"].dt.date
        if end_date is not None:
            frame = frame[dates <= end_date]
        return frame.sort_values("bar_end").reset_index(drop=True)

    def read_minute1_history(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        directory = self._root / "minute1" / f"code={code}"
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

    def read_minute_summaries(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        """只读取指定范围内符合当前窗口合同的分钟摘要。"""
        frame = self._read_minute_summary_frame(code, start_date, end_date)
        if frame.empty:
            return frame
        if "window_contract" not in frame:
            return frame.iloc[0:0].copy()
        return (
            frame[frame["window_contract"].eq(WINDOW_CONTRACT)]
            .sort_values("trade_date")
            .reset_index(drop=True)
        )

    def rebuild_minute_summaries(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        """从本地原始5分钟数据显式重建指定范围的窗口摘要。"""
        rebuilt_codes = 0
        rebuilt_rows = 0
        missing_codes: list[str] = []
        for code in codes:
            raw = self.read_minutes(code, start_date, end_date)
            if raw.empty:
                missing_codes.append(code)
                continue
            generated = summarize_trading_windows(raw)
            if generated.empty:
                missing_codes.append(code)
                continue
            target = self._minute_summary_path(code)
            existing = self._read_minute_summary_frame(code)
            generated_dates = set(generated["trade_date"])
            if not existing.empty:
                existing = existing[~existing["trade_date"].isin(generated_dates)]
            merged = pd.concat(
                [frame for frame in (existing, generated) if not frame.empty],
                ignore_index=True,
            )
            merged = (
                merged.drop_duplicates(subset=["trade_date"], keep="last")
                .sort_values("trade_date")
                .reset_index(drop=True)
            )
            self._write_frame(target, merged)
            rebuilt_codes += 1
            rebuilt_rows += len(generated)
        return {
            "requestedCodes": len(codes),
            "rebuiltCodes": rebuilt_codes,
            "rebuiltRows": rebuilt_rows,
            "missingRawCodes": len(missing_codes),
            "missingRawSamples": missing_codes[:20],
        }

    def rebuild_minute1_summaries(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        """从本地原始1分钟数据显式重建指定范围的日级特征。"""
        rebuilt_codes = 0
        rebuilt_rows = 0
        missing_codes: list[str] = []
        for code in codes:
            raw = self.read_minute1_history(code, start_date, end_date)
            if raw.empty:
                missing_codes.append(code)
                continue
            generated = summarize_minute1_days(raw)
            if generated.empty:
                missing_codes.append(code)
                continue
            self._write_minute1_summaries(code, generated)
            rebuilt_codes += 1
            rebuilt_rows += len(generated)
        return {
            "requestedCodes": len(codes),
            "rebuiltCodes": rebuilt_codes,
            "rebuiltRows": rebuilt_rows,
            "missingRawCodes": len(missing_codes),
            "missingRawSamples": missing_codes[:20],
        }

    def _read_minute_summary_frame(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        target = self._minute_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        dates = frame["trade_date"].dt.date
        if start_date is not None:
            frame = frame[dates >= start_date]
            dates = frame["trade_date"].dt.date
        if end_date is not None:
            frame = frame[dates <= end_date]
        return frame.sort_values("trade_date").reset_index(drop=True)

    def daily_history_valid(self, code: str, start_date: date, end_date: date) -> bool:
        """检查日线文件可读、字段完整且覆盖研究窗口。"""
        try:
            frame = self.read_daily_history(code)
            pd.to_datetime(frame["trade_date"], errors="raise")
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {
            "code", "trade_date", "open", "high", "low", "close", "preclose",
            "volume", "amount", "turnover_rate", "pe_ttm", "pb_mrq",
            "ps_ttm", "pcf_ncf_ttm", "tradestatus", "is_st",
            "source_contract",
        }
        dates = pd.to_datetime(frame["trade_date"], errors="coerce").dt.date
        return (
            required.issubset(frame.columns)
            and not frame.empty
            and dates.notna().all()
            and dates.min() <= start_date + timedelta(days=15)
            and dates.le(end_date).any()
            and dates.max() >= end_date - timedelta(days=15)
            and frame["source_contract"].eq(DAILY_CONTRACT_BAOSTOCK).all()
        )

    def factors_valid(
        self,
        code: str,
        end_date: date,
        start_date: date | None = None,
    ) -> bool:
        """检查复权因子文件可读、字段完整且日期不超过目标日。"""
        try:
            frame = self.read_factors(code)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        return self._factor_frame_valid(
            frame, earliest_date=start_date, end_date=end_date
        )

    def minute_summaries_valid(
        self,
        code: str,
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> bool:
        try:
            summaries = self.read_minute_summaries(code, start_date, end_date)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {
            "trade_date",
            "full_complete",
            "sell_complete",
            "buy_complete",
            "full_open",
            "full_high",
            "full_low",
            "full_close",
            "full_volume",
            "full_amount",
            "full_vwap",
            "sell_vwap",
            "buy_vwap",
        }
        if not required.issubset(summaries.columns) or summaries.empty:
            return False
        try:
            daily = self._eligible_daily_rows(self.read_daily_history(code))
            daily_dates = pd.to_datetime(daily["trade_date"], errors="raise").dt.date
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        expected_dates = set(daily_dates[daily_dates.between(start_date, end_date)])
        if not expected_dates:
            return True
        effective_end = max(expected_dates)
        dates = summaries["trade_date"].dt.date
        window = summaries[dates.between(start_date, effective_end)]
        return self._minute_window_usable(
            window,
            effective_end,
            expected_dates - {effective_end},
            require_full_end,
        )

    def minute_history_valid(
        self,
        code: str,
        start_date: date,
        end_date: date,
    ) -> bool:
        """检查原始5分钟行情覆盖研究窗口内全部可交易日。"""
        try:
            minutes = self.read_minutes(code, start_date, end_date)
            daily = self._eligible_daily_rows(self.read_daily_history(code))
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        required = {
            "code",
            "trade_date",
            "bar_start",
            "bar_end",
            "open",
            "high",
            "low",
            "close",
            "volume",
            "amount",
        }
        if minutes.empty or not required.issubset(minutes.columns):
            return False
        expected_dates = set(
            pd.to_datetime(daily["trade_date"], errors="raise")
            .dt.date.loc[
                lambda values: values.between(start_date, end_date)
            ]
        )
        if not expected_dates:
            return True
        summaries = summarize_trading_windows(minutes)
        if summaries.empty:
            return False
        complete = summaries[
            summaries["full_complete"].fillna(False).astype(bool)
            & summaries["buy_complete"].fillna(False).astype(bool)
            & summaries["sell_complete"].fillna(False).astype(bool)
        ]
        complete_dates = set(complete["trade_date"].dt.date)
        return expected_dates.issubset(complete_dates)

    def minute_data_valid(
        self,
        code: str,
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> bool:
        """原始5分钟行情与派生窗口摘要必须同时完整。"""
        return self.minute_history_valid(
            code, start_date, end_date
        ) and self.minute_summaries_valid(
            code, start_date, end_date, require_full_end
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
        if current.empty or not current["full_complete"].fillna(False).astype(bool).all():
            return False
        if require_full_end and not bool(
            current["buy_complete"].fillna(False).astype(bool).all()
            and current["sell_complete"].fillna(False).astype(bool).all()
        ):
            return False
        complete_history = history[
            history["full_complete"].fillna(False).astype(bool)
            & history["buy_complete"].fillna(False).astype(bool)
            & history["sell_complete"].fillna(False).astype(bool)
        ]
        complete_dates = set(complete_history["trade_date"].dt.date)
        return bool((expected_history or set()).issubset(complete_dates))

    @staticmethod
    def _factor_frame_valid(
        frame: pd.DataFrame,
        earliest_date: date | None = None,
        end_date: date | None = None,
    ) -> bool:
        required = {
            "code",
            "operate_date",
            "cumulative_factor",
            "factor_contract",
        }
        if frame.empty or not required.issubset(frame.columns):
            return False
        dates = pd.to_datetime(frame["operate_date"], errors="coerce").dt.date
        factors = pd.to_numeric(frame["cumulative_factor"], errors="coerce")
        contracts = set(frame["factor_contract"].dropna().astype(str))
        return bool(
            dates.notna().all()
            and factors.notna().all()
            and factors.gt(0).all()
            and contracts == {FACTOR_CONTRACT_BAOSTOCK}
            and (
                end_date is None
                or (
                    dates.le(end_date).any()
                    and dates.max() >= end_date - timedelta(days=15)
                )
            )
            and (earliest_date is None or dates.min() <= earliest_date)
        )

    def valid_daily_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回日线和复权数据均通过窗口校验的股票编号。"""
        return [
            code for code in codes
            if self.daily_history_valid(
                code,
                (start_dates or {}).get(code, start_date),
                (end_dates or {}).get(code, end_date),
            )
            and self.factors_valid(
                code,
                (end_dates or {}).get(code, end_date),
                (start_dates or {}).get(code, start_date),
            )
        ]

    def valid_research_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回日线、复权因子和分钟窗口均覆盖研究范围的股票编号。"""
        daily = set(
            self.valid_daily_codes(
                codes,
                start_date,
                end_date,
                start_dates=start_dates,
                end_dates=end_dates,
            )
        )
        researchable = set(self.model_research_codes(codes, start_date, end_date))
        return [
            code
            for code in codes
            if code in daily
            and code in researchable
            and self.minute_data_valid(
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
        legacy_minute_codes: list[str] = []
        legacy_minute_rows = 0
        current_minute_codes = 0
        missing_minute_codes: list[str] = []
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
                factor_valid = self._factor_frame_valid(
                    factors,
                    earliest_date=(
                        raw_window_dates.min()
                        if not raw_window_dates.empty
                        else None
                    ),
                    end_date=code_end,
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
                summary_frame = self._read_minute_summary_frame(
                    code, code_start, code_end
                )
                current = (
                    summary_frame["window_contract"].eq(WINDOW_CONTRACT)
                    if "window_contract" in summary_frame
                    else pd.Series(False, index=summary_frame.index)
                )
                legacy_rows = int((~current).sum())
                if legacy_rows:
                    legacy_minute_codes.append(code)
                    legacy_minute_rows += legacy_rows
                summaries = summary_frame[current].reset_index(drop=True)
                if not summaries.empty:
                    current_minute_codes += 1
                expected_dates = set(
                    eligible_dates[
                        eligible_dates.between(code_start, code_end)
                    ]
                )
                minute_valid = not expected_dates or self._minute_window_usable(
                    summaries,
                    max(expected_dates),
                    expected_dates - {max(expected_dates)},
                    require_full_end=code_end < end_date,
                )
            except (OSError, ValueError, KeyError, AttributeError):
                minute_valid = False
            if not minute_valid:
                missing_minute_codes.append(code)
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

        LOGGER.info(
            "分钟摘要审计汇总：预期股票=%d；当前合同有数据=%d；缺失或不完整=%d；"
            "旧合同股票=%d；旧合同记录=%d；缺失样例=%s",
            total,
            current_minute_codes,
            len(missing_minute_codes),
            len(legacy_minute_codes),
            legacy_minute_rows,
            ",".join(missing_minute_codes[:10]) or "无",
        )
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
                rows = self.read_minute_summaries(code, trade_date, trade_date)
                if (
                    not rows.empty
                    and rows["full_complete"].fillna(False).astype(bool).all()
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

    def minute1_raw_complete_dates(
        self, code: str, start_date: date, end_date: date
    ) -> set[date]:
        """仅依据原始1分钟数据判断完整交易日，不依赖派生摘要。"""
        raw = self.read_minute1_history(code, start_date, end_date)
        if raw.empty:
            return set()
        result: set[date] = set()
        for trade_date, rows in raw.groupby(raw["trade_date"].dt.date):
            unique_bars = rows.drop_duplicates(subset=["bar_end"], keep="last")
            volume = pd.to_numeric(unique_bars["volume"], errors="coerce").fillna(0)
            if len(unique_bars) == EXPECTED_MINUTE1_BARS and volume.sum() > 0:
                result.add(trade_date)
        return result

    def valid_minute1_raw_codes(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int = 1,
    ) -> list[str]:
        return [
            code
            for code in codes
            if len(self.minute1_raw_complete_dates(code, start_date, end_date))
            >= minimum_days
        ]

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

    def valid_model_codes(
        self,
        codes: list[str],
        frequency: str,
        start_date: date,
        end_date: date,
        minimum_days: int,
    ) -> list[str]:
        """按模型频率筛选只依赖同频原始数据的可训练股票。"""
        result: list[str] = []
        for code in codes:
            try:
                if frequency == "daily":
                    frame = self._eligible_daily_rows(self.read_daily_history(code))
                    dates = pd.to_datetime(frame["trade_date"], errors="raise").dt.date
                    usable = frame[dates.between(start_date, end_date)]
                elif frequency == "minute5":
                    frame = self.read_minute_summaries(code, start_date, end_date)
                    usable = frame[
                        frame["full_complete"].fillna(False).astype(bool)
                        & frame["buy_complete"].fillna(False).astype(bool)
                        & frame["sell_complete"].fillna(False).astype(bool)
                    ]
                elif frequency == "minute1":
                    frame = self.read_minute1_summaries(code)
                    dates = pd.to_datetime(frame["trade_date"], errors="raise").dt.date
                    frame = frame[dates.between(start_date, end_date)]
                    usable = frame[
                        frame["minute1_complete"].fillna(False).astype(bool)
                        & frame["buy_complete"].fillna(False).astype(bool)
                        & frame["sell_complete"].fillna(False).astype(bool)
                    ]
                else:
                    raise ValueError(f"不支持的模型数据频率：{frequency}")
                if len(usable) >= minimum_days:
                    result.append(code)
            except (OSError, ValueError, KeyError, AttributeError):
                continue
        return result

    def cleanup_minutes(self, reference_date: date) -> dict[str, int]:
        root = self._root / "minute5"
        directories = sorted(root.glob("code=*")) if root.is_dir() else []
        before_raw_rows = 0
        after_raw_rows = 0
        for directory in directories:
            code = directory.name.removeprefix("code=")
            minutes = self.read_minutes(code)
            if minutes.empty:
                continue
            result = self._store_minute_raw(code, minutes, reference_date)
            before_raw_rows += int(result["beforeRawRows"])
            after_raw_rows += int(result["rawRows"])
        return {
            "codes": len(directories),
            "beforeRawRows": before_raw_rows,
            "rawRows": after_raw_rows,
            "deletedRawRows": max(0, before_raw_rows - after_raw_rows),
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

    def _store_minute_raw(
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
        before_raw_rows = len(frame)
        retention_date = max(reference_date, frame["trade_date"].dt.date.max())
        raw_cutoff = retention_date - timedelta(days=MINUTE_HISTORY_RETENTION_DAYS)
        raw = frame[frame["trade_date"].dt.date >= raw_cutoff]
        raw_path = self._root / "minute5" / f"code={code}" / "bars.parquet"
        if raw.empty:
            raw_path.unlink(missing_ok=True)
        else:
            self._write(raw_path, raw.to_dict(orient="records"))
        return {
            "rawPath": str(raw_path),
            "rawRows": len(raw),
            "beforeRawRows": before_raw_rows,
        }

    def _minute_summary_path(self, code: str) -> Path:
        return self._derived_root / "minute5_summary" / f"code={code}" / "windows.parquet"

    def _minute1_partition_path(self, code: str, month: str) -> Path:
        return (
            self._root
            / "minute1"
            / f"code={code}"
            / f"month={month}"
            / "bars.parquet"
        )

    def _minute1_summary_path(self, code: str) -> Path:
        return self._derived_root / "minute1_summary" / f"code={code}" / "features.parquet"

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


# AI_GENERATE_END--------------------------- -----------------------------------------------------------------
