# AI_GENERATE_START -----
from __future__ import annotations

import logging
import time
from collections import defaultdict
from contextlib import contextmanager
from datetime import date
from decimal import Decimal

import pandas as pd

from stock_trading.config import AppConfig
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.models import CumulativeAdjustmentFactor, DailyBar
from stock_trading.market.provider import MarketDataProvider
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.tushare_client import TushareClient
from stock_trading.utils.logging_utils import log_context, set_log_context

LOGGER = logging.getLogger(__name__)
WRITE_BATCH_DAYS = 100


def market_provider(config: AppConfig, name: str) -> MarketDataProvider:
    if name == "tushare":
        return TushareMarketProvider(config.tushare_token)
    if name == "baostock":
        return BaoStockMarketProvider(config.baostock_proxy)
    raise ValueError(f"不支持的行情数据源：{name}")


class TushareMarketProvider:
    name = "tushare"

    def __init__(self, token: str | None) -> None:
        self._client = TushareClient(token)

    def sync_stock_basics(
        self, store: ParquetMarketStore, snapshot_date: date
    ) -> dict[str, object]:
        stocks = self._client.fetch_stock_basics(snapshot_date)
        if len(stocks) < 100:
            raise ValueError(f"TuShare 股票基础信息数量异常：{len(stocks)}")
        path = store.write_stock_basics(stocks)
        return {"provider": self.name, "rows": len(stocks), "path": str(path)}

    def trading_dates(self, start_date: date, end_date: date) -> list[date]:
        return self._client.fetch_trade_dates(start_date, end_date)

    def sync_daily(
        self,
        store: ParquetMarketStore,
        stocks: pd.DataFrame,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        selected = set(codes)
        names = {
            str(row.code): str(row.code_name) for row in stocks.itertuples(index=False)
        }
        rows = 0
        completed: set[str] = set()
        buffered: dict[str, list[DailyBar]] = defaultdict(list)
        for index, trade_date in enumerate(dates, start=1):
            with log_context(progress=f"{index}/{len(dates)}", target=str(trade_date)):
                values = self._client.fetch_daily(trade_date, names)
                if not values:
                    raise ValueError(f"TuShare 全市场日线为空：{trade_date}")
                selected_values = [value for value in values if value.code in selected]
                for code, grouped in _group_daily(selected_values).items():
                    buffered[code].extend(grouped)
                    completed.add(code)
                rows += len(selected_values)
                if index % WRITE_BATCH_DAYS == 0 or index == len(dates):
                    for code, grouped in buffered.items():
                        store.write_daily_history(code, grouped)
                    buffered.clear()
        return {
            "provider": self.name,
            "tradeDates": len(dates),
            "rows": rows,
            "codes": len(completed),
        }

    def sync_factors(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        selected = set(codes)
        buffered: dict[str, list[CumulativeAdjustmentFactor]] = defaultdict(list)
        rows = 0
        for index, trade_date in enumerate(dates, start=1):
            factors = [
                value
                for value in self._client.fetch_factors(trade_date)
                if value.code in selected
            ]
            for value in factors:
                buffered[value.code].append(value)
            rows += len(factors)
            if index % WRITE_BATCH_DAYS == 0 or index == len(dates):
                for code, grouped in buffered.items():
                    store.write_factors(code, grouped)
                buffered.clear()
        return {
            "provider": self.name,
            "tradeDates": len(dates),
            "rows": rows,
            "codes": len(codes),
        }

    def sync_minute5(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        del store, codes, start_date, end_date
        raise ValueError(
            "当前 TuShare 适配器未启用分钟行情权限；请设置 "
            "MINUTE5_MARKET_PROVIDER=baostock"
        )


class BaoStockMarketProvider:
    name = "baostock"

    def __init__(self, proxy_url: str | None) -> None:
        self._proxy_url = proxy_url

    def sync_stock_basics(
        self, store: ParquetMarketStore, snapshot_date: date
    ) -> dict[str, object]:
        with self._client() as client:
            stocks = client.fetch_stock_basics(snapshot_date)
        path = store.write_stock_basics(stocks)
        return {"provider": self.name, "rows": len(stocks), "path": str(path)}

    def trading_dates(self, start_date: date, end_date: date) -> list[date]:
        with self._client() as client:
            return client.fetch_trade_dates(start_date, end_date)

    def sync_daily(
        self,
        store: ParquetMarketStore,
        stocks: pd.DataFrame,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        del stocks
        if not dates:
            raise ValueError("指定日期范围内没有交易日")
        completed = 0
        rows = 0
        with self._client() as client:
            for index, code in enumerate(codes, start=1):
                set_log_context(progress=f"{index}/{len(codes)}", target=code)
                values = client.fetch_daily_history(code, dates[0], dates[-1])
                if values:
                    store.write_daily_history(code, values)
                    completed += 1
                    rows += len(values)
        return {"provider": self.name, "rows": rows, "codes": completed}

    def sync_factors(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        if not dates:
            raise ValueError("指定日期范围内没有交易日")
        completed = 0
        rows = 0
        with self._client() as client:
            for index, code in enumerate(codes, start=1):
                set_log_context(progress=f"{index}/{len(codes)}", target=code)
                factors = client.fetch_adjustment_factors(
                    code, date(1990, 1, 1), dates[-1]
                )
                if not factors:
                    factors = [
                        CumulativeAdjustmentFactor(
                            code=code,
                            operate_date=dates[0],
                            cumulative_factor=Decimal("1"),
                        )
                    ]
                store.write_factors(code, factors)
                completed += 1
                rows += len(factors)
        return {"provider": self.name, "rows": rows, "codes": completed}

    def sync_minute5(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        completed = 0
        rows = 0
        failures: dict[str, str] = {}
        with self._client() as client:
            for index, code in enumerate(codes, start=1):
                set_log_context(progress=f"{index}/{len(codes)}", target=code)
                try:
                    values = client.fetch_minutes(code, start_date, end_date)
                    if not values:
                        raise ValueError("5分钟线为空")
                    store.write_minute_history(code, values, end_date)
                    completed += 1
                    rows += len(values)
                except Exception as error:
                    failures[code] = str(error)
                    LOGGER.warning("5分钟线采集失败：股票=%s；原因=%s", code, error)
        return {
            "provider": self.name,
            "rows": rows,
            "codes": completed,
            "failedCodes": len(failures),
            "failureSamples": dict(list(failures.items())[:20]),
        }

    @contextmanager
    def _client(self):
        active: BaoStockClient | None = None
        for attempt in range(1, 4):
            client = BaoStockClient(self._proxy_url)
            try:
                active = client.__enter__()
            except (BaoStockError, OSError, TimeoutError) as error:
                if "10001011" in str(error):
                    raise BaoStockError(f"BaoStock IP 已进入黑名单：{error}") from error
                if attempt == 3:
                    raise
                LOGGER.warning(
                    "BaoStock 连接失败，稍后重试：尝试=%d/3；原因=%s",
                    attempt,
                    error,
                )
                time.sleep(attempt * 3)
            else:
                break
        if active is None:
            raise RuntimeError("BaoStock 连接重试意外结束")
        try:
            yield active
        finally:
            active.__exit__(None, None, None)


def _group_daily(values: list[DailyBar]) -> dict[str, list[DailyBar]]:
    grouped: dict[str, list[DailyBar]] = defaultdict(list)
    for value in values:
        grouped[value.code].append(value)
    return grouped
# AI_GENERATE_END -----
