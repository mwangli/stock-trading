# AI_GENERATE_START -
from __future__ import annotations

from datetime import time

import pandas as pd

BUY_START = time(13, 5)
BUY_END = time(14, 54)
SELL_START = time(9, 35)
SELL_END = time(11, 25)
EXPECTED_BUY_BARS = 21
EXPECTED_SELL_BARS = 22


def summarize_t1_windows(minutes: pd.DataFrame) -> pd.DataFrame:
    """把 5 分钟原始行情压缩为训练和回测需要的每日交易窗口。"""
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
    buy = _summarize_window(frame, BUY_START, BUY_END, EXPECTED_BUY_BARS, "buy")
    sell = _summarize_window(frame, SELL_START, SELL_END, EXPECTED_SELL_BARS, "sell")
    summary = buy.merge(sell, on="trade_date", how="outer").sort_values("trade_date")
    return summary.reset_index(drop=True)


def _summarize_window(
    frame: pd.DataFrame,
    start: time,
    end: time,
    expected_bars: int,
    prefix: str,
) -> pd.DataFrame:
    selected = frame[
        (frame["bar_start"].dt.time >= start)
        & (frame["bar_end"].dt.time <= end)
    ]
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
    columns = [
        "trade_date",
        f"{prefix}_vwap",
        f"{prefix}_low",
        f"{prefix}_high",
        f"{prefix}_bars",
        f"{prefix}_complete",
    ]
    return pd.DataFrame.from_records(rows, columns=columns)


def _empty_summary() -> pd.DataFrame:
    return pd.DataFrame(
        columns=[
            "trade_date",
            "buy_vwap",
            "buy_low",
            "buy_high",
            "buy_bars",
            "buy_complete",
            "sell_vwap",
            "sell_low",
            "sell_high",
            "sell_bars",
            "sell_complete",
        ]
    )
# AI_GENERATE_END -