# AI_GENERATE_START -
from __future__ import annotations

from datetime import date
from decimal import Decimal, ROUND_HALF_UP

import pandas as pd


def recent_limit_down_count(
    history: pd.DataFrame,
    code: str,
    end_date: date,
    lookback_days: int,
) -> int:
    """统计截至指定日期最近若干交易日的收盘跌停次数。"""
    if "trade_date" in history.columns:
        dates = pd.to_datetime(history["trade_date"])
    else:
        dates = pd.to_datetime(history.index)
    available = history.loc[dates <= pd.Timestamp(end_date)].tail(lookback_days)
    return sum(
        float(row["close"])
        <= limit_price(
            float(row["preclose"]),
            limit_rate(code, row),
            -1,
        )
        + 0.001
        for _, row in available.iterrows()
    )


def limit_rate(code: str, daily: pd.Series) -> float:
    if str(daily["is_st"]) == "1":
        return 0.05
    if code.startswith("sz.300") or code.startswith("sh.688"):
        return 0.20
    return 0.10


def limit_price(preclose: float, rate: float, direction: int) -> float:
    value = Decimal(str(preclose)) * (
        Decimal("1") + Decimal(direction) * Decimal(str(rate))
    )
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
# AI_GENERATE_END -
