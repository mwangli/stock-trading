# AI_GENERATE_START ---------
from __future__ import annotations

import pandas as pd


def build_forward_labels(
    observation: pd.DataFrame,
    calendar: pd.DatetimeIndex,
    horizon: int,
) -> pd.Series:
    """生成次日下午买入、持有 N 个交易日后13:00–14:00 卖出的收益标签。"""
    if horizon <= 0:
        raise ValueError("标签交易日周期必须大于 0")
    labels = pd.Series(index=calendar, dtype="float32", name="label")
    if observation.empty:
        return labels
    required = {
        "buy_vwap", "sell_vwap", "morning_complete", "buy_complete", "sell_complete",
    }
    if not required.issubset(observation.columns):
        raise ValueError(f"T+{horizon} 标签缺少上午观察或下午执行窗口字段")
    buy_vwap = pd.to_numeric(observation["buy_vwap"], errors="coerce").reindex(calendar)
    sell_vwap = pd.to_numeric(observation["sell_vwap"], errors="coerce").reindex(calendar)
    morning_complete = observation["morning_complete"].reindex(calendar).fillna(False)
    entry_buy_complete = (
        observation["buy_complete"].reindex(calendar).shift(-1).fillna(False)
    )
    exit_sell_complete = (
        observation["sell_complete"]
        .reindex(calendar)
        .shift(-(horizon + 1))
        .fillna(False)
    )
    entry_buy_vwap = buy_vwap.shift(-1)
    exit_sell_vwap = sell_vwap.shift(-(horizon + 1))
    result = (exit_sell_vwap / entry_buy_vwap - 1.0).where(
        morning_complete.astype(bool)
        & entry_buy_complete.astype(bool)
        & exit_sell_complete.astype(bool)
        & entry_buy_vwap.gt(0)
        & exit_sell_vwap.gt(0)
    )
    return result.astype("float32").rename("label")


def build_t1_labels(observation: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
    """生成预测日 D、D+1 14:00–15:00 买入、D+2 13:00–14:00 卖出的 T+1 标签。"""
    return build_forward_labels(observation, calendar, 1)


# AI_GENERATE_END ---------
