# AI_GENERATE_START ------------
from __future__ import annotations

from datetime import date, datetime, time, timedelta

import numpy as np
import pandas as pd

from stock_trading.contracts import BUY_END, BUY_START, SELL_END, SELL_START, WINDOW_CONTRACT

FULL_MORNING_START = time(9, 30)
FULL_MORNING_END = time(11, 30)
FULL_AFTERNOON_START = time(13, 0)
FULL_AFTERNOON_END = time(15, 0)
EXPECTED_FULL_BARS = 48
EXPECTED_SELL_BARS = 24
EXPECTED_BUY_BARS = 12
MIN_FULL_BARS = 44
MIN_SELL_BARS = 22
MIN_BUY_BARS = 11
MINUTE5_FEATURE_FIELDS = (
    "full_last30_return",
    "morning_return",
    "afternoon_return",
    "lunch_gap_return",
    "morning_volume_share",
    "last30_volume_share",
    "intraday_volatility",
    "trend_efficiency",
)


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
    sell = _summarize_execution_window(
        frame, SELL_START, SELL_END, EXPECTED_SELL_BARS, MIN_SELL_BARS, "sell"
    )
    buy = _summarize_execution_window(
        frame, BUY_START, BUY_END, EXPECTED_BUY_BARS, MIN_BUY_BARS, "buy"
    )
    return full.merge(sell, on="trade_date", how="outer").merge(buy, on="trade_date", how="outer").sort_values("trade_date").reset_index(drop=True).assign(window_contract=WINDOW_CONTRACT)


def _summarize_full_day(frame: pd.DataFrame) -> pd.DataFrame:
    morning = _window(frame, FULL_MORNING_START, FULL_MORNING_END)
    afternoon = _window(frame, FULL_AFTERNOON_START, FULL_AFTERNOON_END)
    selected = pd.concat(
        [morning, afternoon], ignore_index=True
    ).sort_values(["trade_date", "bar_start"])
    if selected.empty:
        return pd.DataFrame(columns=_full_columns())
    last30 = _window(frame, time(14, 30), FULL_AFTERNOON_END)
    full = _aggregate(selected, "full")
    morning_values = _aggregate(morning, "morning")
    afternoon_values = _aggregate(afternoon, "afternoon")
    tail_values = _aggregate(last30, "last30")
    result = (
        full.join(morning_values, how="left")
        .join(afternoon_values, how="left")
        .join(tail_values, how="left")
    )

    log_close = np.log(selected["close"].where(selected["close"].gt(0)))
    previous = log_close.groupby(selected["trade_date"], sort=False).shift(1)
    log_open = np.log(selected["open"].where(selected["open"].gt(0)))
    path_return = log_close - previous.fillna(log_open)
    path_group = path_return.groupby(selected["trade_date"], sort=False)
    result["intraday_volatility"] = path_group.std(ddof=0)
    path_length = path_return.abs().groupby(
        selected["trade_date"], sort=False
    ).sum(min_count=1)
    net_move = np.log(
        result["full_close"].where(result["full_close"].gt(0))
        / result["full_open"].where(result["full_open"].gt(0))
    ).abs()
    result["trend_efficiency"] = (net_move / path_length).where(
        path_length.gt(0), 0.0
    )
    valid_path = path_group.count().eq(result["full_bars"])
    result.loc[~valid_path, ["intraday_volatility", "trend_efficiency"]] = np.nan

    result["full_vwap"] = result["full_amount"] / result["full_volume"].where(
        result["full_volume"].gt(0)
    )
    result["full_last30_return"] = _series_return(
        result["full_close"], result["last30_open"]
    )
    result["morning_return"] = _series_return(
        result["morning_close"], result["morning_open"]
    )
    result["afternoon_return"] = _series_return(
        result["afternoon_close"], result["afternoon_open"]
    )
    result["lunch_gap_return"] = _series_return(
        result["afternoon_open"], result["morning_close"]
    )
    result["morning_volume_share"] = (
        result["morning_volume"] / result["full_volume"]
    ).where(result["full_volume"].gt(0))
    result["last30_volume_share"] = (
        result["last30_volume"] / result["full_volume"]
    ).where(result["full_volume"].gt(0))
    result["full_complete"] = (
        result["full_bars"].between(MIN_FULL_BARS, EXPECTED_FULL_BARS)
        & result["morning_first"].dt.time.eq(FULL_MORNING_START)
        & result["morning_last"].dt.time.eq(_last_bar_start(FULL_MORNING_END))
        & result["afternoon_first"].dt.time.eq(FULL_AFTERNOON_START)
        & result["afternoon_last"].dt.time.eq(_last_bar_start(FULL_AFTERNOON_END))
        & result["full_volume"].gt(0)
    )
    result.index.name = "trade_date"
    return result.reset_index().reindex(columns=_full_columns())


def _summarize_execution_window(
    frame: pd.DataFrame,
    start: time,
    end: time,
    expected_bars: int,
    minimum_bars: int,
    prefix: str,
) -> pd.DataFrame:
    selected = _window(frame, start, end)
    columns = ["trade_date", f"{prefix}_vwap", f"{prefix}_low", f"{prefix}_high", f"{prefix}_bars", f"{prefix}_complete"]
    if selected.empty:
        return pd.DataFrame(columns=columns)
    values = _aggregate(selected, prefix)
    values[f"{prefix}_vwap"] = values[f"{prefix}_amount"] / values[
        f"{prefix}_volume"
    ].where(values[f"{prefix}_volume"].gt(0))
    values[f"{prefix}_complete"] = (
        values[f"{prefix}_bars"].between(minimum_bars, expected_bars)
        & values[f"{prefix}_first"].dt.time.eq(start)
        & values[f"{prefix}_last"].dt.time.eq(_last_bar_start(end))
        & values[f"{prefix}_volume"].gt(0)
    )
    values.index.name = "trade_date"
    return values.reset_index().reindex(columns=columns)


def _aggregate(frame: pd.DataFrame, prefix: str) -> pd.DataFrame:
    return frame.groupby("trade_date", sort=True).agg(
        **{
            f"{prefix}_open": ("open", "first"),
            f"{prefix}_high": ("high", "max"),
            f"{prefix}_low": ("low", "min"),
            f"{prefix}_close": ("close", "last"),
            f"{prefix}_volume": ("volume", "sum"),
            f"{prefix}_amount": ("amount", "sum"),
            f"{prefix}_bars": ("bar_start", "size"),
            f"{prefix}_first": ("bar_start", "first"),
            f"{prefix}_last": ("bar_start", "last"),
        }
    )


def _series_return(end_price: pd.Series, start_price: pd.Series) -> pd.Series:
    return (end_price / start_price - 1.0).where(start_price.gt(0))


def _last_bar_start(end: time) -> time:
    return (datetime.combine(date.min, end) - timedelta(minutes=5)).time()


def require_window_contract(frame: pd.DataFrame) -> None:
    if not frame.empty and ("window_contract" not in frame or not frame["window_contract"].eq(WINDOW_CONTRACT).all()):
        raise ValueError("5分钟摘要合同已过期；请从原始数据全量重建摘要")


def _window(frame: pd.DataFrame, start: time, end: time) -> pd.DataFrame:
    return frame[(frame["bar_start"].dt.time >= start) & (frame["bar_end"].dt.time <= end)]


def _full_columns() -> list[str]:
    return ["trade_date", "full_open", "full_high", "full_low", "full_close", "full_volume", "full_amount", "full_vwap", *MINUTE5_FEATURE_FIELDS, "full_bars", "full_complete"]


def _empty_summary() -> pd.DataFrame:
    columns = _full_columns() + ["window_contract"]
    for prefix in ("sell", "buy"):
        columns.extend([f"{prefix}_vwap", f"{prefix}_low", f"{prefix}_high", f"{prefix}_bars", f"{prefix}_complete"])
    return pd.DataFrame(columns=columns)


# AI_GENERATE_END ------------
