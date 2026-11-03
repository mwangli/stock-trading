# AI_GENERATE_START
from __future__ import annotations

import logging
from datetime import date

import pandas as pd

from stock_trading.market.storage import ParquetMarketStore, ResearchDataAudit

LOGGER = logging.getLogger(__name__)


def audit_research_quality(
    store: ParquetMarketStore,
    stocks: pd.DataFrame,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> ResearchDataAudit:
    """使用统一股票生命周期范围审计研究数据。"""
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
    return store.audit_research_data(
        codes,
        start_date,
        end_date,
        coverage_ranges=coverage_ranges,
    )


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
    missing_adjustment = 0
    missing_minute5 = 0
    if audit is not None:
        missing_daily = sum(plan.needs_daily for plan in audit.pending)
        missing_adjustment = sum(
            plan.needs_adjustment for plan in audit.pending
        )
        missing_minute5 = sum(plan.needs_minute5 for plan in audit.pending)
        details.update(
            {
                "missingDaily": missing_daily,
                "missingAdjustment": missing_adjustment,
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
        values += (missing_daily, missing_adjustment, missing_minute5)
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


# AI_GENERATE_END
