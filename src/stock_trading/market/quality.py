# AI_GENERATE_START -----
from __future__ import annotations

import logging
from collections.abc import Mapping
from dataclasses import dataclass
from datetime import date, timedelta

import pandas as pd

from stock_trading.config import WINDOW_CONTRACT
from stock_trading.market.models import (
    DAILY_CONTRACT_BAOSTOCK,
    FACTOR_CONTRACT_BAOSTOCK,
    STOCK_CONTRACT_BAOSTOCK,
)
from stock_trading.market.transform import EXPECTED_MINUTE1_BARS, MINUTE5_FEATURE_FIELDS, summarize_trading_windows
import pyarrow.parquet as pq

MINUTE_HISTORY_MIN_COMPLETE_RATIO = 0.99
from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from stock_trading.market.storage import ParquetMarketStore
from stock_trading.utils.logging_utils import set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)


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


def audit_research_quality(
    store: ParquetMarketStore,
    stocks: pd.DataFrame,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> ResearchDataAudit:
    """使用统一股票生命周期范围审计研究数据。"""
    coverage_ranges = research_coverage_ranges(
        stocks, codes, start_date, end_date
    )
    return audit_research_data(
        store,
        codes,
        start_date,
        end_date,
        coverage_ranges=coverage_ranges,
    )


def audit_research_data(
    store: ParquetMarketStore,
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
        "code",
        "trade_date",
        "open",
        "high",
        "low",
        "close",
        "volume",
        "tradestatus",
        "is_st",
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
            daily = store.read_daily_history(code)
            daily_dates = pd.to_datetime(
                daily["trade_date"], errors="raise"
            ).dt.date
        except (OSError, ValueError, KeyError, AttributeError):
            daily = pd.DataFrame()
            daily_dates = pd.Series(dtype="object")
        raw_window_dates = daily_dates[daily_dates.between(code_start, code_end)]
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
            factors = store.read_factors(code)
            factor_valid = _factor_frame_valid(
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
            _eligible_daily_rows(daily)
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
            summary_frame = store._read_minute_summary_frame(
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
                eligible_dates[eligible_dates.between(code_start, code_end)]
            )
            minute_valid = not expected_dates or _minute_window_usable(
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
        "分钟摘要审计汇总：预期股票=%d；当前合同有数据=%d；"
        "缺失或不完整=%d；旧合同股票=%d；旧合同记录=%d；缺失样例=%s",
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


def research_coverage_ranges(
    stocks: pd.DataFrame,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, tuple[date, date, bool]]:
    """返回每只股票在研究区间内应有数据的生命周期范围。"""
    frame = stocks.set_index("code", drop=False)
    coverage_ranges: dict[str, tuple[date, date, bool]] = {}
    for code in codes:
        row = frame.loc[code]
        ipo_date = row["ipo_date"].date() if pd.notna(row["ipo_date"]) else start_date
        out_date = row["out_date"].date() if pd.notna(row["out_date"]) else None
        code_start = max(start_date, ipo_date)
        code_end = min(end_date, out_date) if out_date else end_date
        coverage_ranges[code] = (
            code_start,
            code_end,
            out_date is None or out_date >= end_date,
        )
    return coverage_ranges


def require_data_quality(
    stage: str,
    expected_count: int,
    usable_count: int,
    threshold: float,
    audit: ResearchDataAudit | None = None,
) -> dict[str, object]:
    """按统一覆盖率输出质量结果，低于配置阈值时终止。"""
    if expected_count <= 0:
        raise ValueError(f"{stage} 没有可评估的数据")
    coverage_rate = usable_count / expected_count
    missing_count = max(0, expected_count - usable_count)
    details = {
        "stage": stage,
        "expectedCodes": expected_count,
        "usableCodes": usable_count,
        "missingCodes": missing_count,
        "coverageRate": coverage_rate,
        "threshold": threshold,
    }
    missing_daily = 0
    missing_factor = 0
    missing_minute5 = 0
    if audit is not None:
        missing_daily = sum(plan.needs_daily for plan in audit.pending)
        missing_factor = sum(plan.needs_factor for plan in audit.pending)
        missing_minute5 = sum(plan.needs_minute5 for plan in audit.pending)
        details.update(
            {
                "missingDaily": missing_daily,
                "missingFactor": missing_factor,
                "missingMinute5": missing_minute5,
            }
        )
    message = (
        "%s 数据质量：覆盖率=%.2f%%；可用=%d；预期=%d；缺失=%d；阈值=%.2f%%"
    )
    values = (
        stage,
        coverage_rate * 100,
        usable_count,
        expected_count,
        missing_count,
        threshold * 100,
    )
    if audit is not None:
        message += "；日线缺失=%d；复权缺失=%d；5分钟线缺失=%d"
        values += (missing_daily, missing_factor, missing_minute5)
    if coverage_rate < threshold:
        raise ValueError(
            f"{stage} 数据覆盖率不足：覆盖率={coverage_rate:.2%}；"
            f"阈值={threshold:.2%}；可用={usable_count}；预期={expected_count}"
        )
    if missing_count:
        LOGGER.warning(message, *values)
    else:
        LOGGER.info(message, *values)
    return details




def stock_basics_valid(store, snapshot_date: date) -> bool:
    frame = store.read_stock_basics()
    required = {
        "code", "code_name", "ipo_date", "out_date", "security_type",
        "market_category", "status", "trade_status", "snapshot_date",
        "source_contract",
    }
    if not required.issubset(frame.columns) or len(frame) < 100:
        return False
    if not frame["code"].astype(str).str.match(r"^(sh|sz)\.\d{6}$").all():
        return False
    return (
        frame["snapshot_date"].notna().all()
        and frame["snapshot_date"].dt.date.eq(snapshot_date).all()
        and frame["source_contract"].eq(STOCK_CONTRACT_BAOSTOCK).all()
    )

def daily_trade_dates(store, code: str, start_date: date, end_date: date) -> set[date]:
    daily = _eligible_daily_rows(store.read_daily_history(code))
    dates = pd.to_datetime(daily["trade_date"], errors="raise").dt.date
    return set(dates[dates.between(start_date, end_date)])

def valid_basic_codes(
        store,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回换手率、量比和流通市值覆盖生命周期范围的股票。"""
        try:
            frame = store.read_basic_panel(start_date, end_date, set(codes))
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

def daily_history_valid(store, code: str, start_date: date, end_date: date) -> bool:
        """检查日线文件可读、字段完整且覆盖研究窗口。"""
        try:
            frame = store.read_daily_history(code)
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
        store,
        code: str,
        end_date: date,
        start_date: date | None = None,
    ) -> bool:
        """检查复权因子文件可读、字段完整且日期不超过目标日。"""
        try:
            frame = store.read_factors(code)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        return _factor_frame_valid(
            frame, earliest_date=start_date, end_date=end_date
        )

def minute1_summaries_valid(
        store, code: str, start_date: date, end_date: date
    ) -> bool:
        """检查 1 分钟摘要是否覆盖原始数据中的完整交易日。"""
        try:
            raw = store.read_minute1_history(code, start_date, end_date)
            summary = store.read_minute1_summaries(code)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        if raw.empty or summary.empty:
            return False
        expected_dates = set(raw["trade_date"].dt.date)
        summary_dates = set(summary["trade_date"].dt.date)
        complete = summary[
            summary["minute1_complete"].fillna(False).astype(bool)
            & summary["buy_complete"].fillna(False).astype(bool)
            & summary["sell_complete"].fillna(False).astype(bool)
        ]
        return expected_dates.issubset(summary_dates) and expected_dates.issubset(
            set(complete["trade_date"].dt.date)
        )

def minute_summaries_valid(
        store,
        code: str,
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> bool:
        try:
            summaries = store.read_minute_summaries(code, start_date, end_date)
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
            *MINUTE5_FEATURE_FIELDS,
        }
        if not required.issubset(summaries.columns) or summaries.empty:
            return False
        try:
            daily = _eligible_daily_rows(store.read_daily_history(code))
            daily_dates = pd.to_datetime(daily["trade_date"], errors="raise").dt.date
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        expected_dates = set(daily_dates[daily_dates.between(start_date, end_date)])
        if not expected_dates:
            return True
        effective_end = max(expected_dates)
        dates = summaries["trade_date"].dt.date
        window = summaries[dates.between(start_date, effective_end)]
        return _minute_window_usable(
            window,
            effective_end,
            expected_dates - {effective_end},
            require_full_end,
        )

def minute_collection_ready(
    store,
    code: str,
    start_date: date,
    end_date: date,
    expected_dates: set[date] | None = None,
) -> bool:
    """Return whether minute5 raw data covers the requested trade dates."""
    raw_path = store._root / "minute5" / f"code={code}" / "bars.parquet"
    if not raw_path.is_file():
        return False
    raw_dates = pd.to_datetime(
        pq.ParquetFile(raw_path).read(columns=["trade_date"])["trade_date"].to_pandas(),
        errors="raise",
    ).dt.date
    if expected_dates is None:
        daily = _eligible_daily_rows(store.read_daily_history(code))
        expected_dates = set(
            pd.to_datetime(daily["trade_date"], errors="raise").dt.date.loc[
                lambda values: values.between(start_date, end_date)
            ]
        )
    else:
        expected_dates = {value for value in expected_dates if start_date <= value <= end_date}
    if not expected_dates:
        return True
    coverage = len(set(raw_dates) & expected_dates) / len(expected_dates)
    return coverage >= MINUTE_HISTORY_MIN_COMPLETE_RATIO and minute_history_valid(
        store, code, start_date, end_date, expected_dates=expected_dates
    )

def minute_history_valid(
        store,
        code: str,
        start_date: date,
        end_date: date,
        expected_dates: set[date] | None = None,
    ) -> bool:
        """检查原始5分钟行情，不要求读取日线即可判断覆盖范围。"""
        try:
            minutes = store.read_minutes(code, start_date, end_date)
            if expected_dates is None:
                daily = _eligible_daily_rows(store.read_daily_history(code))
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
        if expected_dates is None:
            expected_dates = set(
                pd.to_datetime(daily["trade_date"], errors="raise")
                .dt.date.loc[
                    lambda values: values.between(start_date, end_date)
                ]
            )
        else:
            expected_dates = {
                value for value in expected_dates
                if start_date <= value <= end_date
            }
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
        return (
            len(complete_dates & expected_dates) / len(expected_dates)
            >= MINUTE_HISTORY_MIN_COMPLETE_RATIO
        )

def minute_data_valid(
        store,
        code: str,
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
    ) -> bool:
        """原始5分钟行情与派生窗口摘要必须同时完整。"""
        return minute_history_valid(store,
            code, start_date, end_date
        ) and minute_summaries_valid(store,
            code, start_date, end_date, require_full_end
        )

def valid_daily_codes(
        store,
        codes: list[str],
        start_date: date,
        end_date: date,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回日线和复权数据均通过窗口校验的股票编号。"""
        return [
            code for code in codes
            if daily_history_valid(store,
                code,
                (start_dates or {}).get(code, start_date),
                (end_dates or {}).get(code, end_date),
            )
            and factors_valid(store,
                code,
                (end_dates or {}).get(code, end_date),
                (start_dates or {}).get(code, start_date),
            )
        ]

def valid_research_codes(
        store,
        codes: list[str],
        start_date: date,
        end_date: date,
        require_full_end: bool = False,
        start_dates: Mapping[str, date] | None = None,
        end_dates: Mapping[str, date] | None = None,
    ) -> list[str]:
        """返回日线、复权因子和分钟窗口均覆盖研究范围的股票编号。"""
        daily = set(
            valid_daily_codes(store,
                codes,
                start_date,
                end_date,
                start_dates=start_dates,
                end_dates=end_dates,
            )
        )
        researchable = set(model_research_codes(store, codes, start_date, end_date))
        return [
            code
            for code in codes
            if code in daily
            and code in researchable
            and minute_data_valid(store,
                code, start_date, end_date, require_full_end
            )
        ]

def model_research_codes(
        store, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回研究窗口内至少存在一个非 ST 可交易日的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = _eligible_daily_rows(store.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.between(start_date, end_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

def tradable_codes_on(store, codes: list[str], trade_date: date) -> list[str]:
        """返回指定交易日具备有效非 ST 日线事实的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = _eligible_daily_rows(store.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.eq(trade_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

def minute5_codes_on(store, codes: list[str], trade_date: date) -> list[str]:
        result: list[str] = []
        for code in codes:
            try:
                rows = store.read_minute_summaries(code, trade_date, trade_date)
                if (
                    not rows.empty
                    and rows["full_complete"].fillna(False).astype(bool).all()
                ):
                    result.append(code)
            except (OSError, ValueError, KeyError, AttributeError):
                continue
        return result

def minute1_codes_on(store, codes: list[str], trade_date: date) -> list[str]:
        """返回指定交易日存在有效成交事实的 1 分钟行情股票。"""
        return minute1_codes_between(store, codes, trade_date, trade_date)

def minute1_codes_between(
        store, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回指定日期范围内存在有效成交事实的 1 分钟行情股票。"""
        result: list[str] = []
        for code in codes:
            try:
                summaries = store.read_minute1_summaries(code)
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
        store, code: str, start_date: date, end_date: date
    ) -> set[date]:
        """返回具备完整241根标准分钟线的交易日。"""
        summaries = store.read_minute1_summaries(code)
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
        store, code: str, start_date: date, end_date: date
    ) -> set[date]:
        """仅依据原始1分钟数据判断完整交易日，不依赖派生摘要。"""
        result: set[date] = set()
        for path in store._minute1_history_paths(code, start_date, end_date):
            raw = pq.ParquetFile(path).read(
                columns=["trade_date", "bar_end", "volume"]
            ).to_pandas()
            raw["trade_date"] = pd.to_datetime(
                raw["trade_date"], errors="raise"
            )
            raw["bar_end"] = pd.to_datetime(raw["bar_end"], utc=True)
            dates = raw["trade_date"].dt.date
            raw = raw[dates.between(start_date, end_date)]
            for trade_date, rows in raw.groupby(raw["trade_date"].dt.date):
                unique_bars = rows.drop_duplicates(
                    subset=["bar_end"], keep="last"
                )
                volume = pd.to_numeric(
                    unique_bars["volume"], errors="coerce"
                ).fillna(0)
                if (
                    len(unique_bars) == EXPECTED_MINUTE1_BARS
                    and volume.sum() > 0
                ):
                    result.add(trade_date)
        return result

def valid_minute1_raw_codes(
        store,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int = 1,
    ) -> list[str]:
        return [
            code
            for code in codes
            if len(minute1_raw_complete_dates(store, code, start_date, end_date))
            >= minimum_days
        ]

def valid_minute1_codes(
        store,
        codes: list[str],
        start_date: date,
        end_date: date,
        minimum_days: int,
    ) -> list[str]:
        """返回达到分钟训练历史门槛的股票。"""
        return [
            code
            for code in codes
            if len(minute1_complete_dates(store, code, start_date, end_date))
            >= minimum_days
        ]

def valid_model_codes(
        store,
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
                    frame = _eligible_daily_rows(store.read_daily_history(code))
                    dates = pd.to_datetime(frame["trade_date"], errors="raise").dt.date
                    usable = frame[dates.between(start_date, end_date)]
                elif frequency == "minute5":
                    frame = store.read_minute_summaries(code, start_date, end_date)
                    usable = frame[
                        frame["full_complete"].fillna(False).astype(bool)
                        & frame["buy_complete"].fillna(False).astype(bool)
                        & frame["sell_complete"].fillna(False).astype(bool)
                    ]
                elif frequency == "minute1":
                    frame = store.read_minute1_summaries(code)
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
        expected = expected_history or set()
        if not expected:
            return True
        complete_dates = set(complete_history["trade_date"].dt.date)
        return (
            len(complete_dates & expected) / len(expected)
            >= MINUTE_HISTORY_MIN_COMPLETE_RATIO
        )

def _eligible_daily_rows(frame: pd.DataFrame) -> pd.DataFrame:
        return frame[
            (frame["tradestatus"].astype(str) == "1")
            & (frame["is_st"].astype(str) != "1")
            & (frame["close"].astype(float) > 0)
            & (frame["volume"].astype(float) > 0)
        ]

# AI_GENERATE_END -----
