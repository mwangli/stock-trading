# AI_GENERATE_START --
from __future__ import annotations

from datetime import time

import pandas as pd

BUY_START = time(13, 5)
BUY_END = time(14, 54)
SELL_START = time(9, 35)
SELL_END = time(11, 25)
EXPECTED_BUY_BARS = 21
EXPECTED_SELL_BARS = 22


def build_t1_labels(minutes: pd.DataFrame, calendar: pd.DatetimeIndex) -> pd.Series:
    """Build prediction-day labels from next-day buy and following-day sell VWAPs."""
    if minutes.empty:
        return pd.Series(dtype="float32")

    frame = minutes.copy()
    frame["bar_start_time"] = frame["bar_start"].dt.time
    frame["bar_end_time"] = frame["bar_end"].dt.time
    buy = frame[
        (frame["bar_start_time"] >= BUY_START) & (frame["bar_end_time"] <= BUY_END)
    ]
    sell = frame[
        (frame["bar_start_time"] >= SELL_START) & (frame["bar_end_time"] <= SELL_END)
    ]
    buy_vwap = _complete_window_vwap(buy, EXPECTED_BUY_BARS)
    sell_vwap = _complete_window_vwap(sell, EXPECTED_SELL_BARS)

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


def _complete_window_vwap(
    frame: pd.DataFrame, expected_bars: int
) -> dict[pd.Timestamp, float]:
    values: dict[pd.Timestamp, float] = {}
    for trade_date, day in frame.groupby("trade_date"):
        if len(day) != expected_bars or day["volume"].sum() <= 0:
            continue
        values[pd.Timestamp(trade_date)] = float(day["amount"].sum() / day["volume"].sum())
    return values
# AI_GENERATE_END --
