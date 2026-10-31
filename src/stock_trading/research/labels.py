# AI_GENERATE_START ---
from __future__ import annotations

import pandas as pd


def build_t1_labels(summaries: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
    """使用长期分钟聚合结果生成次日下午买入、隔日上午卖出的 T+1 标签。"""
    if summaries.empty:
        return pd.Series(dtype="float32")
    frame = summaries.copy()
    frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
    frame = frame.set_index("trade_date")
    buy_vwap = frame.loc[frame["buy_complete"].astype(bool), "buy_vwap"].to_dict()
    sell_vwap = frame.loc[frame["sell_complete"].astype(bool), "sell_vwap"].to_dict()

    labels: dict[pd.Timestamp, float] = {}
    for prediction_index in range(len(calendar) - 2):
        prediction_date = calendar[prediction_index]
        buy_date = calendar[prediction_index + 1]
        sell_date = calendar[prediction_index + 2]
        buy_price = buy_vwap.get(buy_date)
        sell_price = sell_vwap.get(sell_date)
        if buy_price is None or sell_price is None or buy_price <= 0:
            continue
        labels[prediction_date] = sell_price / buy_price - 1.0
    return pd.Series(labels, dtype="float32", name="label")
# AI_GENERATE_END ---