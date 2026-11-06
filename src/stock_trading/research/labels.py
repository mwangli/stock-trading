# AI_GENERATE_START ------
from __future__ import annotations

import pandas as pd


def build_t1_labels(observation: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
    """生成上午观察、当日下午买入、下一交易日下午卖出的 T+1 收益。"""
    labels = pd.Series(index=calendar, dtype="float32", name="label")
    if observation.empty:
        return labels
    required = {
        "buy_vwap", "sell_vwap", "morning_complete", "buy_complete", "sell_complete",
    }
    if not required.issubset(observation.columns):
        raise ValueError("T+1 标签缺少上午观察或下午执行窗口字段")
    buy_vwap = pd.to_numeric(observation["buy_vwap"], errors="coerce").reindex(calendar)
    sell_vwap = pd.to_numeric(observation["sell_vwap"], errors="coerce").reindex(calendar)
    morning_complete = observation["morning_complete"].reindex(calendar).fillna(False)
    buy_complete = observation["buy_complete"].reindex(calendar).fillna(False)
    next_sell_complete = observation["sell_complete"].reindex(calendar).shift(-1).fillna(False)
    next_sell_vwap = sell_vwap.shift(-1)
    result = (next_sell_vwap / buy_vwap - 1.0).where(
        morning_complete.astype(bool)
        & buy_complete.astype(bool)
        & next_sell_complete.astype(bool)
        & buy_vwap.gt(0)
        & next_sell_vwap.gt(0)
    )
    return result.astype("float32").rename("label")
# AI_GENERATE_END ------
