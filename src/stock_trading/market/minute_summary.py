# AI_GENERATE_START --
from __future__ import annotations

from datetime import time

import pandas as pd

MORNING_START = time(9, 30)
MORNING_END = time(11, 30)
SELL_START = time(9, 35)
SELL_END = time(11, 25)
BUY_START = time(13, 5)
BUY_END = time(14, 54)
EXPECTED_MORNING_BARS = 24
EXPECTED_SELL_BARS = 22
EXPECTED_BUY_BARS = 21


def summarize_trading_windows(minutes: pd.DataFrame) -> pd.DataFrame:
    """聚合上午观察、次日上午卖出和下午买入三个固定执行窗口。"""
    if minutes.empty:
        return _empty_summary()
    frame = minutes.copy()
    frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
    frame["bar_start"] = pd.to_datetime(frame["bar_start"], utc=True).dt.tz_convert(
        "Asia/Shanghai"
    )
    frame["bar_end"] = pd.to_datetime(frame["bar_end"], utc=True).dt.tz_convert(
        "Asia/Shanghai"
    )
    morning = _summarize_morning(frame)
    sell = _summarize_execution_window(
        frame, SELL_START, SELL_END, EXPECTED_SELL_BARS, "sell"
    )
    buy = _summarize_execution_window(
        frame, BUY_START, BUY_END, EXPECTED_BUY_BARS, "buy"
    )
    return (
        morning.merge(sell, on="trade_date", how="outer")
        .merge(buy, on="trade_date", how="outer")
        .sort_values("trade_date")
        .reset_index(drop=True)
    )


def _summarize_morning(frame: pd.DataFrame) -> pd.DataFrame:
    selected = _window(frame, MORNING_START, MORNING_END)
    last30 = _window(frame, time(11, 0), MORNING_END)
    rows: list[dict[str, object]] = []
    for trade_date, day in selected.groupby("trade_date"):
        volume = float(day["volume"].sum())
        amount = float(day["amount"].sum())
        open_price = float(day.iloc[0]["open"])
        close_price = float(day.iloc[-1]["close"])
        vwap = amount / volume if volume > 0 else None
        tail = last30[last30["trade_date"] == trade_date]
        tail_open = float(tail.iloc[0]["open"]) if not tail.empty else None
        rows.append(
            {
                "trade_date": pd.Timestamp(trade_date),
                "morning_open": open_price,
                "morning_high": float(day["high"].max()),
                "morning_low": float(day["low"].min()),
                "morning_close": close_price,
                "morning_volume": volume,
                "morning_amount": amount,
                "morning_vwap": vwap,
                "morning_return": close_price / open_price - 1.0
                if open_price > 0
                else None,
                "morning_range": float(day["high"].max())
                / float(day["low"].min())
                - 1.0
                if float(day["low"].min()) > 0
                else None,
                "morning_close_vwap": close_price / vwap - 1.0
                if vwap and vwap > 0
                else None,
                "morning_last30_return": close_price / tail_open - 1.0
                if tail_open and tail_open > 0
                else None,
                "morning_bars": len(day),
                "morning_complete": len(day) == EXPECTED_MORNING_BARS and volume > 0,
            }
        )
    return pd.DataFrame.from_records(rows, columns=_morning_columns())


def _summarize_execution_window(
    frame: pd.DataFrame,
    start: time,
    end: time,
    expected_bars: int,
    prefix: str,
) -> pd.DataFrame:
    selected = _window(frame, start, end)
    rows: list[dict[str, object]] = []
    for trade_date, day in selected.groupby("trade_date"):
        volume = float(day["volume"].sum())
        rows.append(
            {
                "trade_date": pd.Timestamp(trade_date),
                f"{prefix}_vwap": float(day["amount"].sum() / volume)
                if volume > 0
                else None,
                f"{prefix}_low": float(day["low"].min()),
                f"{prefix}_high": float(day["high"].max()),
                f"{prefix}_bars": len(day),
                f"{prefix}_complete": len(day) == expected_bars and volume > 0,
            }
        )
    return pd.DataFrame.from_records(
        rows,
        columns=[
            "trade_date",
            f"{prefix}_vwap",
            f"{prefix}_low",
            f"{prefix}_high",
            f"{prefix}_bars",
            f"{prefix}_complete",
        ],
    )


def _window(frame: pd.DataFrame, start: time, end: time) -> pd.DataFrame:
    return frame[
        (frame["bar_start"].dt.time >= start)
        & (frame["bar_end"].dt.time <= end)
    ]


def _morning_columns() -> list[str]:
    return [
        "trade_date",
        "morning_open",
        "morning_high",
        "morning_low",
        "morning_close",
        "morning_volume",
        "morning_amount",
        "morning_vwap",
        "morning_return",
        "morning_range",
        "morning_close_vwap",
        "morning_last30_return",
        "morning_bars",
        "morning_complete",
    ]


def _empty_summary() -> pd.DataFrame:
    columns = _morning_columns()
    for prefix in ("sell", "buy"):
        columns.extend(
            [
                f"{prefix}_vwap",
                f"{prefix}_low",
                f"{prefix}_high",
                f"{prefix}_bars",
                f"{prefix}_complete",
            ]
        )
    return pd.DataFrame(columns=columns)
# AI_GENERATE_END --
