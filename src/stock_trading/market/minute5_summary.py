# AI_GENERATE_START -------
from __future__ import annotations

from datetime import time

import pandas as pd

from stock_trading.contracts import BUY_END, BUY_START, SELL_END, SELL_START, WINDOW_CONTRACT

FULL_MORNING_START = time(9, 30)
FULL_MORNING_END = time(11, 30)
FULL_AFTERNOON_START = time(13, 0)
FULL_AFTERNOON_END = time(15, 0)
EXPECTED_FULL_BARS = 48
EXPECTED_SELL_BARS = 24
EXPECTED_BUY_BARS = 12
MINUTE5_FEATURE_FIELDS = ("full_return", "full_range", "full_close_vwap", "full_last30_return")


def summarize_trading_windows(minutes: pd.DataFrame) -> pd.DataFrame:
    """聚合完整交易日特征及固定上午卖出、下午买入窗口。"""
    if minutes.empty:
        return _empty_summary()
    frame = minutes.copy()
    frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
    frame["bar_start"] = pd.to_datetime(frame["bar_start"], utc=True).dt.tz_convert("Asia/Shanghai")
    frame["bar_end"] = pd.to_datetime(frame["bar_end"], utc=True).dt.tz_convert("Asia/Shanghai")
    frame = frame.drop_duplicates(subset=["trade_date", "bar_start"], keep="last")
    frame = frame.sort_values(["trade_date", "bar_start"])
    full = _summarize_full_day(frame)
    sell = _summarize_execution_window(frame, SELL_START, SELL_END, EXPECTED_SELL_BARS, "sell")
    buy = _summarize_execution_window(frame, BUY_START, BUY_END, EXPECTED_BUY_BARS, "buy")
    return full.merge(sell, on="trade_date", how="outer").merge(buy, on="trade_date", how="outer").sort_values("trade_date").reset_index(drop=True).assign(window_contract=WINDOW_CONTRACT)


def _summarize_full_day(frame: pd.DataFrame) -> pd.DataFrame:
    selected = pd.concat([_window(frame, FULL_MORNING_START, FULL_MORNING_END), _window(frame, FULL_AFTERNOON_START, FULL_AFTERNOON_END)], ignore_index=True).sort_values(["trade_date", "bar_start"])
    last30 = _window(frame, time(14, 30), FULL_AFTERNOON_END)
    rows: list[dict[str, object]] = []
    for trade_date, day in selected.groupby("trade_date"):
        volume = float(pd.to_numeric(day["volume"], errors="coerce").sum())
        amount = float(pd.to_numeric(day["amount"], errors="coerce").sum())
        open_price = float(day.iloc[0]["open"])
        close_price = float(day.iloc[-1]["close"])
        high = float(pd.to_numeric(day["high"], errors="coerce").max())
        low = float(pd.to_numeric(day["low"], errors="coerce").min())
        vwap = amount / volume if volume > 0 else None
        tail = last30[last30["trade_date"] == trade_date]
        tail_open = float(tail.iloc[0]["open"]) if not tail.empty else None
        rows.append({"trade_date": pd.Timestamp(trade_date), "full_open": open_price, "full_high": high, "full_low": low, "full_close": close_price, "full_volume": volume, "full_amount": amount, "full_vwap": vwap, "full_return": close_price / open_price - 1.0 if open_price > 0 else None, "full_range": high / low - 1.0 if low > 0 else None, "full_close_vwap": close_price / vwap - 1.0 if vwap and vwap > 0 else None, "full_last30_return": close_price / tail_open - 1.0 if tail_open and tail_open > 0 else None, "full_bars": len(day), "full_complete": _complete_full_day(day) and volume > 0})
    return pd.DataFrame.from_records(rows, columns=_full_columns())


def _summarize_execution_window(frame: pd.DataFrame, start: time, end: time, expected_bars: int, prefix: str) -> pd.DataFrame:
    selected = _window(frame, start, end)
    rows: list[dict[str, object]] = []
    for trade_date, day in selected.groupby("trade_date"):
        volume = float(pd.to_numeric(day["volume"], errors="coerce").sum())
        amount = float(pd.to_numeric(day["amount"], errors="coerce").sum())
        rows.append({"trade_date": pd.Timestamp(trade_date), f"{prefix}_vwap": amount / volume if volume > 0 else None, f"{prefix}_low": float(pd.to_numeric(day["low"], errors="coerce").min()), f"{prefix}_high": float(pd.to_numeric(day["high"], errors="coerce").max()), f"{prefix}_bars": len(day), f"{prefix}_complete": _complete_window(day, start, end, expected_bars) and volume > 0})
    return pd.DataFrame.from_records(rows, columns=["trade_date", f"{prefix}_vwap", f"{prefix}_low", f"{prefix}_high", f"{prefix}_bars", f"{prefix}_complete"])


def require_window_contract(frame: pd.DataFrame) -> None:
    if not frame.empty and ("window_contract" not in frame or not frame["window_contract"].eq(WINDOW_CONTRACT).all()):
        raise ValueError("5分钟摘要合同已过期；请从原始数据全量重建摘要")


def _complete_full_day(day: pd.DataFrame) -> bool:
    if len(day) != EXPECTED_FULL_BARS:
        return False
    trade_date = pd.Timestamp(day.iloc[0]["trade_date"]).date()
    morning = pd.date_range(pd.Timestamp.combine(trade_date, FULL_MORNING_START).tz_localize("Asia/Shanghai"), pd.Timestamp.combine(trade_date, FULL_MORNING_END).tz_localize("Asia/Shanghai"), freq="5min", inclusive="left")
    afternoon = pd.date_range(pd.Timestamp.combine(trade_date, FULL_AFTERNOON_START).tz_localize("Asia/Shanghai"), pd.Timestamp.combine(trade_date, FULL_AFTERNOON_END).tz_localize("Asia/Shanghai"), freq="5min", inclusive="left")
    return pd.DatetimeIndex(day["bar_start"]).equals(morning.append(afternoon))


def _complete_window(day: pd.DataFrame, start: time, end: time, expected_bars: int) -> bool:
    if len(day) != expected_bars:
        return False
    trade_date = pd.Timestamp(day.iloc[0]["trade_date"]).date()
    starts = pd.date_range(pd.Timestamp.combine(trade_date, start).tz_localize("Asia/Shanghai"), pd.Timestamp.combine(trade_date, end).tz_localize("Asia/Shanghai"), freq="5min", inclusive="left")
    return pd.DatetimeIndex(day["bar_start"]).equals(starts)


def _window(frame: pd.DataFrame, start: time, end: time) -> pd.DataFrame:
    return frame[(frame["bar_start"].dt.time >= start) & (frame["bar_end"].dt.time <= end)]


def _full_columns() -> list[str]:
    return ["trade_date", "full_open", "full_high", "full_low", "full_close", "full_volume", "full_amount", "full_vwap", *MINUTE5_FEATURE_FIELDS, "full_bars", "full_complete"]


def _empty_summary() -> pd.DataFrame:
    columns = _full_columns() + ["window_contract"]
    for prefix in ("sell", "buy"):
        columns.extend([f"{prefix}_vwap", f"{prefix}_low", f"{prefix}_high", f"{prefix}_bars", f"{prefix}_complete"])
    return pd.DataFrame(columns=columns)


# AI_GENERATE_END -------
