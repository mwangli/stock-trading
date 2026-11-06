# AI_GENERATE_START ----------------------------------------------------
from __future__ import annotations

from datetime import date, timedelta
from pathlib import Path

import pandas as pd

from stock_trading.market.provider import MarketDataProvider
from stock_trading.market.storage import ParquetMarketStore


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


# AI_GENERATE_END ----------------------------------------------------
