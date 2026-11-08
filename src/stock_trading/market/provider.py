# AI_GENERATE_START -------
from __future__ import annotations

from datetime import date
from typing import Protocol

import pandas as pd

from stock_trading.market.storage import ParquetMarketStore


class MarketDataProvider(Protocol):
    """统一采集接口；数据源负责适配，存储和模型只接收统一字段。"""

    name: str

    def sync_stock_basics(
        self, store: ParquetMarketStore, snapshot_date: date
    ) -> dict[str, object]: ...

    def trading_dates(self, start_date: date, end_date: date) -> list[date]: ...

    def sync_daily(
        self,
        store: ParquetMarketStore,
        stocks: pd.DataFrame,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]: ...

    def sync_minute5(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]: ...

    def sync_minute(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]: ...
# AI_GENERATE_END -------
