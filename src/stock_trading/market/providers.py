# AI_GENERATE_START---- --------
from __future__ import annotations

import logging
import time
from collections import defaultdict
from contextlib import contextmanager
from datetime import date, time as datetime_time
from decimal import Decimal
from urllib.error import HTTPError, URLError

import pandas as pd

from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.models import BrokerMinuteSeries
from stock_trading.config import AppConfig
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.models import CumulativeAdjustmentFactor, DailyBar, MinuteBar
from stock_trading.market.provider import MarketDataProvider
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.tushare_client import TushareClient
from stock_trading.utils.instruments import to_broker_code
from stock_trading.utils.logging_utils import log_context, set_log_context
from stock_trading.utils.time_utils import shanghai_today

LOGGER = logging.getLogger(__name__)
WRITE_BATCH_DAYS = 100


def market_provider(config: AppConfig, name: str) -> MarketDataProvider:
    if name == "tushare":
        return TushareMarketProvider(config.tushare_token)
    if name == "baostock":
        return BaoStockMarketProvider(config.baostock_proxy)
    if name == "zxbroker":
        return ZXBrokerMarketProvider(config)
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

    def sync_minute(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        del store, codes, start_date, end_date
        raise ValueError("TuShare 适配器不提供本项目使用的 1 分钟行情")


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

    def sync_minute(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        del store, codes, start_date, end_date
        raise ValueError("BaoStock 适配器只提供项目使用的 5 分钟行情")

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


class ZXBrokerMarketProvider:
    """将券商分时价点转换并保存为统一 1 分钟标准行情。"""

    name = "zxbroker"

    def __init__(self, config: AppConfig) -> None:
        self._client = ZXBrokerClient(config)
        self._request_interval_seconds = config.minute_market_request_interval_seconds
        self._last_request_started_at = 0.0

    def sync_stock_basics(
        self, store: ParquetMarketStore, snapshot_date: date
    ) -> dict[str, object]:
        del store, snapshot_date
        raise ValueError("券商行情适配器不提供股票基础信息")

    def trading_dates(self, start_date: date, end_date: date) -> list[date]:
        del start_date, end_date
        raise ValueError("券商行情适配器不提供交易日历")

    def sync_daily(
        self,
        store: ParquetMarketStore,
        stocks: pd.DataFrame,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        del store, stocks, codes, dates
        raise ValueError("券商行情适配器不提供日线")

    def sync_factors(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        dates: list[date],
    ) -> dict[str, object]:
        del store, codes, dates
        raise ValueError("券商行情适配器不提供复权因子")

    def sync_minute5(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        del store, codes, start_date, end_date
        raise ValueError("券商适配器只提供 1 分钟行情")

    def sync_minute(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        completed_code_dates = 0
        skipped_code_dates = 0
        rows = 0
        minute_points = 0
        failures: dict[str, str] = {}
        target_code_dates = 0
        for index, code in enumerate(codes, start=1):
            set_log_context(progress=f"{index}/{len(codes)}", target=code)
            trade_dates = _minute_trade_dates_for_code(
                store, code, start_date, end_date
            )
            target_code_dates += len(trade_dates)
            complete_dates = store.minute1_complete_dates(
                code, start_date, end_date
            )
            missing_dates = [
                trade_date
                for trade_date in trade_dates
                if trade_date not in complete_dates
            ]
            skipped_code_dates += len(trade_dates) - len(missing_dates)
            if not missing_dates:
                continue
            LOGGER.info(
                "券商分钟行情缺口检查：股票=%s；目标日期=%d；已有完整日期=%d；待采日期=%d",
                code,
                len(trade_dates),
                len(trade_dates) - len(missing_dates),
                len(missing_dates),
            )
            for trade_date in missing_dates:
                failure_key = f"{code}@{trade_date.isoformat()}"
                try:
                    series = self._get_minute_series(code, trade_date)
                    minute_points += len(series.points)
                    values = _to_minute_bars(code, series)
                    if not values:
                        raise ValueError("券商分钟行情没有可写入的 1 分钟数据")
                    store.write_minute1_history(code, values)
                    completed_code_dates += 1
                    rows += len(values)
                except HTTPError as error:
                    if error.code in {403, 429}:
                        raise RuntimeError(
                            "券商分钟行情触发访问限制，已停止采集："
                            f"HTTP {error.code}"
                        ) from error
                    failures[failure_key] = str(error)
                    LOGGER.warning(
                        "1分钟线采集失败：股票=%s；日期=%s；原因=%s",
                        code,
                        trade_date,
                        error,
                    )
                except Exception as error:
                    failures[failure_key] = str(error)
                    LOGGER.warning(
                        "1分钟线采集失败：股票=%s；日期=%s；原因=%s",
                        code,
                        trade_date,
                        error,
                    )
        return {
            "provider": self.name,
            "rows": rows,
            "minutePoints": minute_points,
            "targetCodeDates": target_code_dates,
            "completedCodeDates": completed_code_dates,
            "skippedCodeDates": skipped_code_dates,
            "failedCodeDates": len(failures),
            "requestIntervalSeconds": self._request_interval_seconds,
            "failureSamples": dict(list(failures.items())[:20]),
        }

    def _get_minute_series(
        self, code: str, trade_date: date
    ) -> BrokerMinuteSeries:
        for attempt in range(2):
            elapsed = time.monotonic() - self._last_request_started_at
            remaining = self._request_interval_seconds - elapsed
            if self._last_request_started_at and remaining > 0:
                time.sleep(remaining)
            self._last_request_started_at = time.monotonic()
            try:
                return self._client.get_minute_series(
                    to_broker_code(code), trade_date
                )
            except (HTTPError, URLError, TimeoutError) as error:
                if attempt == 1:
                    raise
                LOGGER.warning(
                    "券商分钟行情网络请求失败，准备重试：股票=%s；日期=%s；原因=%s",
                    code,
                    trade_date,
                    error,
                )
                time.sleep(10)
        raise RuntimeError("券商分钟行情重试流程异常结束")


def _group_daily(values: list[DailyBar]) -> dict[str, list[DailyBar]]:
    grouped: dict[str, list[DailyBar]] = defaultdict(list)
    for value in values:
        grouped[value.code].append(value)
    return grouped


def _minute_trade_dates_for_code(
    store: ParquetMarketStore,
    code: str,
    start_date: date,
    end_date: date,
) -> list[date]:
    try:
        daily = store.read_daily_history(code)
    except (OSError, ValueError, KeyError) as error:
        if start_date == end_date:
            return [start_date]
        raise ValueError(
            f"股票 {code} 缺少日线，无法确定历史分钟交易日"
        ) from error
    dates = daily["trade_date"].dt.date
    selected = daily[(dates >= start_date) & (dates <= end_date)]
    if "tradestatus" in selected:
        selected = selected[selected["tradestatus"].astype(str).eq("1")]
    if "volume" in selected:
        selected = selected[
            pd.to_numeric(selected["volume"], errors="coerce").fillna(0).gt(0)
        ]
    trade_dates = sorted(set(selected["trade_date"].dt.date))
    today = shanghai_today()
    if start_date <= today <= end_date and today not in trade_dates:
        trade_dates.append(today)
    return sorted(trade_dates)


def _to_minute_bars(
    code: str, series: BrokerMinuteSeries
) -> list[MinuteBar]:
    regular = [
        point
        for point in series.points
        if (
            datetime_time(9, 30) <= point.timestamp.time() <= datetime_time(11, 30)
            or datetime_time(13, 0) <= point.timestamp.time() <= datetime_time(15, 0)
        )
        and point.price > 0
    ]
    return [
        MinuteBar(
            trade_date=series.trade_date,
            bar_end=point.timestamp,
            code=code,
            open=point.price,
            high=point.price,
            low=point.price,
            close=point.price,
            volume=Decimal(point.volume_lots * 100),
            amount=point.amount,
            interval_minutes=1,
        )
        for point in regular
    ]
# AI_GENERATE_END---- --------
