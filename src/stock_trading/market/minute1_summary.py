# AI_GENERATE_START -
from __future__ import annotations

from datetime import time

import numpy as np
import pandas as pd


EXPECTED_MINUTE1_BARS = 241
MINUTE1_FEATURE_FIELDS = (
    "intraday_return",
    "intraday_range",
    "close_vwap",
    "realized_volatility",
    "downside_volatility",
    "max_drawdown",
    "morning_return_1m",
    "afternoon_return",
    "last30_return",
    "morning_volume_ratio",
    "afternoon_volume_ratio",
    "last30_volume_ratio",
    "volume_concentration",
    "amount_concentration",
    "zero_volume_ratio",
    "median_minute_amount",
    "volume_weighted_abs_return",
    "close_location",
    "trend_slope",
)


def summarize_minute1_days(minutes: pd.DataFrame) -> pd.DataFrame:
    """把完整1分钟行情压缩成无未来信息的日内量价特征。"""
    if minutes.empty:
        return _empty_summary()
    frame = minutes.copy()
    frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
    frame["bar_end"] = pd.to_datetime(frame["bar_end"], utc=True).dt.tz_convert(
        "Asia/Shanghai"
    )
    frame = frame[
        frame["bar_end"].dt.time.between(time(9, 30), time(11, 30))
        | frame["bar_end"].dt.time.between(time(13, 0), time(15, 0))
    ]
    rows = [
        _summarize_day(day, trade_date)
        for trade_date, day in frame.groupby("trade_date")
    ]
    return pd.DataFrame.from_records(rows, columns=_summary_columns()).sort_values(
        "trade_date"
    ).reset_index(drop=True)


def _summarize_day(day: pd.DataFrame, trade_date: pd.Timestamp) -> dict[str, object]:
    day = day.drop_duplicates(subset=["bar_end"], keep="last").sort_values("bar_end")
    close = pd.to_numeric(day["close"], errors="coerce")
    valid = close.gt(0) & close.notna()
    day = day.loc[valid].copy()
    close = close.loc[valid]
    volume = pd.to_numeric(day["volume"], errors="coerce").fillna(0.0)
    amount = pd.to_numeric(day["amount"], errors="coerce").fillna(0.0)
    if day.empty:
        return {column: None for column in _summary_columns()} | {
            "trade_date": pd.Timestamp(trade_date),
            "minute1_bars": 0,
            "minute1_complete": False,
        }

    total_volume = float(volume.sum())
    total_amount = float(amount.sum())
    vwap = total_amount / total_volume if total_volume > 0 else float("nan")
    returns = close.pct_change().replace([np.inf, -np.inf], np.nan).fillna(0.0)
    log_returns = np.log(close).diff().replace([np.inf, -np.inf], np.nan).dropna()
    drawdown = close / close.cummax() - 1.0
    morning = _window(day, time(9, 30), time(11, 30))
    afternoon = _window(day, time(13, 0), time(15, 0))
    last30 = _window(day, time(14, 30), time(15, 0))
    high = float(pd.to_numeric(day["high"], errors="coerce").max())
    low = float(pd.to_numeric(day["low"], errors="coerce").min())
    last_price = float(close.iloc[-1])
    first_price = float(close.iloc[0])
    x = np.linspace(0.0, 1.0, len(close))
    trend_slope = (
        float(np.polyfit(x, np.log(close.to_numpy()), 1)[0])
        if len(close) > 1
        else 0.0
    )
    weighted_abs_return = (
        float((returns.abs() * volume).sum() / total_volume)
        if total_volume > 0
        else float("nan")
    )
    return {
        "trade_date": pd.Timestamp(trade_date),
        "open": first_price,
        "high": high,
        "low": low,
        "close": last_price,
        "volume": total_volume,
        "amount": total_amount,
        "vwap": vwap,
        "intraday_return": last_price / first_price - 1.0,
        "intraday_range": high / low - 1.0 if low > 0 else float("nan"),
        "close_vwap": last_price / vwap - 1.0 if vwap > 0 else float("nan"),
        "realized_volatility": float(np.sqrt(np.square(log_returns).sum())),
        "downside_volatility": float(
            np.sqrt(np.square(log_returns[log_returns < 0]).sum())
        ),
        "max_drawdown": float(drawdown.min()),
        "morning_return_1m": _window_return(morning),
        "afternoon_return": _window_return(afternoon),
        "last30_return": _window_return(last30),
        "morning_volume_ratio": _volume_ratio(morning, total_volume),
        "afternoon_volume_ratio": _volume_ratio(afternoon, total_volume),
        "last30_volume_ratio": _volume_ratio(last30, total_volume),
        "volume_concentration": float(volume.max() / total_volume)
        if total_volume > 0
        else float("nan"),
        "amount_concentration": float(amount.max() / total_amount)
        if total_amount > 0
        else float("nan"),
        "zero_volume_ratio": float(volume.eq(0).mean()),
        "median_minute_amount": float(amount.median()),
        "volume_weighted_abs_return": weighted_abs_return,
        "close_location": (last_price - low) / (high - low)
        if high > low
        else 0.5,
        "trend_slope": trend_slope,
        "minute1_bars": len(day),
        "minute1_complete": len(day) == EXPECTED_MINUTE1_BARS and total_volume > 0,
    }


def _window(day: pd.DataFrame, start: time, end: time) -> pd.DataFrame:
    return day[
        (day["bar_end"].dt.time >= start) & (day["bar_end"].dt.time <= end)
    ]


def _window_return(window: pd.DataFrame) -> float:
    if window.empty:
        return float("nan")
    prices = pd.to_numeric(window["close"], errors="coerce").dropna()
    return float(prices.iloc[-1] / prices.iloc[0] - 1.0) if len(prices) > 1 else 0.0


def _volume_ratio(window: pd.DataFrame, total_volume: float) -> float:
    if total_volume <= 0 or window.empty:
        return float("nan")
    volume = pd.to_numeric(window["volume"], errors="coerce").fillna(0.0)
    return float(volume.sum() / total_volume)


def _summary_columns() -> list[str]:
    return [
        "trade_date",
        "open",
        "high",
        "low",
        "close",
        "volume",
        "amount",
        "vwap",
        *MINUTE1_FEATURE_FIELDS,
        "minute1_bars",
        "minute1_complete",
    ]


def _empty_summary() -> pd.DataFrame:
    return pd.DataFrame(columns=_summary_columns())


# AI_GENERATE_END -
