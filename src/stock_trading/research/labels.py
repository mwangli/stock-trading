# AI_GENERATE_START -------------
from __future__ import annotations

import pandas as pd

from stock_trading.research.model_config import ModelSpec


def build_labels(observation: pd.DataFrame, calendar: pd.DatetimeIndex, spec: ModelSpec) -> pd.Series:
    """严格按模型绑定的唯一策略生成 T+1 标签。"""
    if observation.empty:
        return pd.Series(index=calendar, dtype="float32", name="label")
    if spec.data_level == "L1":
        return _daily_vwap_labels(observation, calendar)
    if spec.data_level == "L2":
        return _minute5_window_labels(observation, calendar)
    raise ValueError(f"当前阶段不支持的数据等级：{spec.data_level}")


def _daily_vwap_labels(observation: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
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


def _minute5_window_labels(observation: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
    required = {"full_complete", "buy_vwap", "sell_vwap", "buy_complete", "sell_complete"}
    if not required.issubset(observation.columns):
        raise ValueError("L2 标签缺少完整日或买卖窗口字段")
    full_complete = observation["full_complete"].reindex(calendar).fillna(False)
    buy_complete = observation["buy_complete"].reindex(calendar).shift(-1).fillna(False)
    sell_complete = observation["sell_complete"].reindex(calendar).shift(-2).fillna(False)
    buy_vwap = pd.to_numeric(observation["buy_vwap"], errors="coerce").reindex(calendar).shift(-1)
    sell_vwap = pd.to_numeric(observation["sell_vwap"], errors="coerce").reindex(calendar).shift(-2)
    labels = (sell_vwap / buy_vwap - 1.0).where(
        full_complete.astype(bool) & buy_complete.astype(bool) & sell_complete.astype(bool)
        & buy_vwap.gt(0) & sell_vwap.gt(0)
    )
    return labels.astype("float32").rename("label")


# AI_GENERATE_END -------------
