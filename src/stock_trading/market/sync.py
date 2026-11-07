# AI_GENERATE_START- ------------------------------------------------------
from __future__ import annotations

import json
from datetime import date, datetime, time, timedelta
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.market.provider import MarketDataProvider
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.trade_calendar import MissingTradeCalendarError, trade_dates, latest_completed_trade_date


def ensure_trade_calendar(
    store: ParquetMarketStore, provider: MarketDataProvider, years: range | tuple[int, ...]
) -> None:
    """用既有数据源补齐完整年度日历；供应商未发布时明确失败，不猜工作日。"""
    directory = store.root / "calendar"
    for year in years:
        try:
            trade_dates(year, directory)
            continue
        except MissingTradeCalendarError:
            pass
        values = provider.trading_dates(date(year, 1, 1), date(year, 12, 31))
        if not values or values != sorted(set(values)) or any(value.year != year for value in values):
            raise ValueError(f"{provider.name} 未返回有效的 {year} 年交易日历")
        directory.mkdir(parents=True, exist_ok=True)
        target = directory / f"trade_dates_{year}.json"
        temporary = target.with_suffix(f".{uuid4().hex}.tmp")
        temporary.write_text(json.dumps({
            "year": year, "source": provider.name, "completeYear": True,
            "tradeDates": [value.isoformat() for value in values],
        }, ensure_ascii=False), encoding="utf-8")
        temporary.replace(target)


def resolve_completed_trade_date(
    store: ParquetMarketStore, provider: MarketDataProvider, now: datetime,
) -> date:
    """显式补齐所需日历，再查询默认的最近完整交易日。"""
    cutoff = now.date() if now.time() >= time(16, 0) else now.date() - timedelta(days=1)
    calendar_dir = store.root / "calendar"
    ensure_trade_calendar(store, provider, (cutoff.year,))
    if cutoff < trade_dates(cutoff.year, calendar_dir)[0]:
        ensure_trade_calendar(store, provider, (cutoff.year - 1,))
    return latest_completed_trade_date(now, calendar_dir=calendar_dir)


def research_codes(
    stocks: pd.DataFrame, start_date: date, end_date: date
) -> list[str]:
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        out_date = pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        if (ipo_date and ipo_date > end_date) or (out_date and out_date < start_date):
            continue
        codes.append(code)
    return sorted(set(codes))


def live_codes(stocks: pd.DataFrame, snapshot_date: date) -> list[str]:
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        out_date = pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        if (ipo_date and ipo_date > snapshot_date) or (out_date and out_date < snapshot_date):
            continue
        if str(row.status) not in {"", "1"} or str(row.trade_status) != "1":
            continue
        codes.append(code)
    return sorted(set(codes))


def trading_dates_between(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    start_date: date,
    end_date: date,
) -> list[date]:
    cache = store.root / "calendar" / "trade_dates.txt"
    cached = _read_trade_dates(cache)
    selected = [value for value in cached if start_date <= value <= end_date]
    if selected and selected[0] <= start_date + timedelta(days=15) and selected[-1] == end_date:
        return selected
    fetched = provider.trading_dates(start_date, end_date)
    merged = sorted(set(cached) | set(fetched))
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_text(
        "".join(f"{value.isoformat()}\n" for value in merged),
        encoding="utf-8",
    )
    return fetched


def _read_trade_dates(path: Path) -> list[date]:
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError:
        return []
    return sorted(date.fromisoformat(value) for value in lines if value.strip())


def sync_stock_basics(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    snapshot_date: date,
) -> dict[str, object]:
    return provider.sync_stock_basics(store, snapshot_date)


def sync_daily(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    stocks: pd.DataFrame,
    codes: list[str],
    dates: list[date],
) -> dict[str, object]:
    return provider.sync_daily(store, stocks, codes, dates)


def sync_factors(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    codes: list[str],
    dates: list[date],
) -> dict[str, object]:
    return provider.sync_factors(store, codes, dates)


def sync_minute5(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    return provider.sync_minute5(store, codes, start_date, end_date)


def sync_minute(
    store: ParquetMarketStore,
    provider: MarketDataProvider,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    return provider.sync_minute(store, codes, start_date, end_date)


# AI_GENERATE_END- ------------------------------------------------------
