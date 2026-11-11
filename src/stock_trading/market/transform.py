# AI_GENERATE_START
from __future__ import annotations

from collections.abc import Mapping
from datetime import date, datetime, time, timedelta
from decimal import Decimal
import logging
from typing import TYPE_CHECKING

import numpy as np
import pandas as pd

from stock_trading.contracts import BUY_END, BUY_START, SELL_END, SELL_START, WINDOW_CONTRACT
from stock_trading.market.models import BASIC_CONTRACT_BAOSTOCK, FACTOR_CONTRACT_BAOSTOCK, CumulativeAdjustmentFactor, DailyBasic
from stock_trading.utils.logging_utils import set_log_context, should_log_progress

if TYPE_CHECKING:
    from stock_trading.market.storage import ParquetMarketStore

LOGGER = logging.getLogger(__name__)

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
    """把完整 1 分钟行情压缩成无未来信息的日内量价特征。"""
    if minutes.empty:
        return _empty_minute1_summary()
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
        _summarize_minute1_day(day, trade_date)
        for trade_date, day in frame.groupby("trade_date")
    ]
    return pd.DataFrame.from_records(
        rows, columns=_minute1_summary_columns()
    ).sort_values("trade_date").reset_index(drop=True)


def _summarize_minute1_day(
    day: pd.DataFrame, trade_date: pd.Timestamp
) -> dict[str, object]:
    day = day.drop_duplicates(subset=["bar_end"], keep="last").sort_values(
        "bar_end"
    )
    close = pd.to_numeric(day["close"], errors="coerce")
    valid = close.gt(0) & close.notna()
    day = day.loc[valid].copy()
    close = close.loc[valid]
    volume = pd.to_numeric(day["volume"], errors="coerce").fillna(0.0)
    amount = pd.to_numeric(day["amount"], errors="coerce").fillna(0.0)
    if day.empty:
        return {column: None for column in _minute1_summary_columns()} | {
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
    morning = _minute1_window(day, time(9, 30), time(11, 30))
    afternoon = _minute1_window(day, time(13, 0), time(15, 0))
    last30 = _minute1_window(day, time(14, 30), time(15, 0))
    buy_window = _minute1_window(day, BUY_START, BUY_END)
    sell_window = _minute1_window(day, SELL_START, SELL_END)
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
    minute1_complete = len(day) == EXPECTED_MINUTE1_BARS and total_volume > 0
    buy_volume = float(pd.to_numeric(buy_window["volume"], errors="coerce").sum())
    sell_volume = float(
        pd.to_numeric(sell_window["volume"], errors="coerce").sum()
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
        "morning_return_1m": _minute1_window_return(morning),
        "afternoon_return": _minute1_window_return(afternoon),
        "last30_return": _minute1_window_return(last30),
        "morning_volume_ratio": _minute1_volume_ratio(morning, total_volume),
        "afternoon_volume_ratio": _minute1_volume_ratio(afternoon, total_volume),
        "last30_volume_ratio": _minute1_volume_ratio(last30, total_volume),
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
        "buy_vwap": _minute1_window_vwap(buy_window),
        "buy_low": _minute1_window_price(buy_window, "low", "min"),
        "buy_high": _minute1_window_price(buy_window, "high", "max"),
        "buy_complete": minute1_complete and len(buy_window) >= 60 and buy_volume > 0,
        "sell_vwap": _minute1_window_vwap(sell_window),
        "sell_low": _minute1_window_price(sell_window, "low", "min"),
        "sell_high": _minute1_window_price(sell_window, "high", "max"),
        "sell_complete": minute1_complete
        and len(sell_window) >= 120
        and sell_volume > 0,
        "minute1_bars": len(day),
        "minute1_complete": minute1_complete,
    }


def _minute1_window(day: pd.DataFrame, start: time, end: time) -> pd.DataFrame:
    return day[
        (day["bar_end"].dt.time >= start) & (day["bar_end"].dt.time <= end)
    ]


def _minute1_window_return(window: pd.DataFrame) -> float:
    if window.empty:
        return float("nan")
    prices = pd.to_numeric(window["close"], errors="coerce").dropna()
    return float(prices.iloc[-1] / prices.iloc[0] - 1.0) if len(prices) > 1 else 0.0


def _minute1_volume_ratio(window: pd.DataFrame, total_volume: float) -> float:
    if total_volume <= 0 or window.empty:
        return float("nan")
    volume = pd.to_numeric(window["volume"], errors="coerce").fillna(0.0)
    return float(volume.sum() / total_volume)


def _minute1_window_vwap(window: pd.DataFrame) -> float:
    if window.empty:
        return float("nan")
    volume = float(pd.to_numeric(window["volume"], errors="coerce").sum())
    amount = float(pd.to_numeric(window["amount"], errors="coerce").sum())
    return amount / volume if volume > 0 else float("nan")


def _minute1_window_price(
    window: pd.DataFrame, field: str, operation: str
) -> float:
    if window.empty:
        return float("nan")
    values = pd.to_numeric(window[field], errors="coerce")
    return float(values.min() if operation == "min" else values.max())


def _minute1_summary_columns() -> list[str]:
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
        "buy_vwap",
        "buy_low",
        "buy_high",
        "buy_complete",
        "sell_vwap",
        "sell_low",
        "sell_high",
        "sell_complete",
        "minute1_bars",
        "minute1_complete",
    ]


def _empty_minute1_summary() -> pd.DataFrame:
    return pd.DataFrame(columns=_minute1_summary_columns())


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

def prepare_daily_derivatives(
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
    start_dates: Mapping[str, date] | None = None,
    end_dates: Mapping[str, date] | None = None,
) -> dict[str, object]:
    """从本地日线一次生成 basic 与 factor，不访问外部数据源。"""
    import stock_trading.market.quality as quality
    basic_ready = set(
        quality.valid_basic_codes(store,
            codes,
            start_date,
            end_date,
            63,
            start_dates=start_dates,
            end_dates=end_dates,
        )
    )
    factor_ready = {
        code
        for code in codes
        if quality.factors_valid(store,
            code,
            (end_dates or {}).get(code, end_date),
            (start_dates or {}).get(code, start_date),
        )
    }
    pending = [
        code
        for code in codes
        if code not in basic_ready or code not in factor_ready
    ]
    basic_codes = 0
    basic_rows = 0
    factor_codes = 0
    factor_rows = 0
    failures: dict[str, str] = {}
    for index, code in enumerate(pending, start=1):
        set_log_context(progress=f"{index}/{len(pending)}", target=code)
        if should_log_progress(index, len(pending)):
            LOGGER.info(
                "日线派生进度：股票=%s；进度=%d/%d",
                code,
                index,
                len(pending),
            )
        try:
            daily = store.read_daily_history(code)
            code_start = (start_dates or {}).get(code, start_date)
            code_end = (end_dates or {}).get(code, end_date)
            if code not in basic_ready:
                basics = _derive_basics(code, daily, code_start, code_end)
                if basics:
                    store.write_basic_history(code, basics)
                    basic_codes += 1
                    basic_rows += len(basics)
            if code not in factor_ready:
                factors = _derive_factors(code, daily)
                if factors:
                    store.write_factors(code, factors)
                    factor_codes += 1
                    factor_rows += len(factors)
        except Exception as error:
            failures[code] = str(error)
            LOGGER.warning("日线派生失败：股票=%s；原因=%s", code, error)
    return {
        "requestedCodes": len(codes),
        "pendingCodes": len(pending),
        "basicCodes": basic_codes,
        "basicRows": basic_rows,
        "factorCodes": factor_codes,
        "factorRows": factor_rows,
        "failedCodes": len(failures),
        "failureSamples": dict(list(failures.items())[:20]),
    }


def _derive_factors(
    code: str, daily: pd.DataFrame
) -> list[CumulativeAdjustmentFactor]:
    if daily.empty:
        return []
    ordered = daily.sort_values("trade_date")
    cumulative = Decimal("1")
    previous_close: Decimal | None = None
    factors: list[CumulativeAdjustmentFactor] = []
    for row in ordered.itertuples(index=False):
        close = Decimal(str(row.close))
        preclose = Decimal(str(row.preclose))
        if previous_close is not None and preclose > 0:
            cumulative *= previous_close / preclose
        factors.append(
            CumulativeAdjustmentFactor(
                code=code,
                operate_date=pd.Timestamp(row.trade_date).date(),
                cumulative_factor=cumulative,
                factor_contract=FACTOR_CONTRACT_BAOSTOCK,
            )
        )
        previous_close = close
    return factors


def _derive_basics(
    code: str,
    daily: pd.DataFrame,
    start_date: date,
    end_date: date,
) -> list[DailyBasic]:
    if daily.empty:
        return []
    ordered = daily.sort_values("trade_date").copy()
    ordered["trade_date"] = pd.to_datetime(
        ordered["trade_date"], errors="raise"
    )
    volume = pd.to_numeric(ordered["volume"], errors="coerce")
    previous_average = volume.shift(1).rolling(5, min_periods=5).mean()
    ordered["volume_ratio"] = volume / previous_average.where(
        previous_average.ne(0)
    )
    dates = ordered["trade_date"].dt.date
    selected = ordered[dates.between(start_date, end_date)]
    values: list[DailyBasic] = []
    for row in selected.itertuples(index=False):
        turnover = _decimal_or_none(row.turnover_rate)
        close = _decimal_or_none(row.close)
        row_volume = _decimal_or_none(row.volume)
        circulating = (
            close * row_volume * Decimal("100") / turnover
            if close is not None
            and row_volume is not None
            and turnover is not None
            and turnover > 0
            else None
        )
        values.append(
            DailyBasic(
                trade_date=pd.Timestamp(row.trade_date).date(),
                code=code,
                turnover_rate=turnover,
                volume_ratio=_decimal_or_none(row.volume_ratio),
                circulating_market_value=circulating,
                pe_ttm=_decimal_or_none(row.pe_ttm),
                pb_mrq=_decimal_or_none(row.pb_mrq),
                ps_ttm=_decimal_or_none(row.ps_ttm),
                pcf_ncf_ttm=_decimal_or_none(row.pcf_ncf_ttm),
                source_contract=BASIC_CONTRACT_BAOSTOCK,
            )
        )
    return values


def _decimal_or_none(value: object) -> Decimal | None:
    if value is None or pd.isna(value):
        return None
    return Decimal(str(value))
# AI_GENERATE_END
