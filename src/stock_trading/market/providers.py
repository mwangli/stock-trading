# AI_GENERATE_START---------------------- --------
from __future__ import annotations

import logging
import time
from collections import defaultdict
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager
from datetime import date, time as datetime_time, timedelta
from decimal import Decimal
from pathlib import Path
from threading import Lock
from urllib.error import HTTPError, URLError

import pandas as pd

from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.models import BrokerMinuteSeries
from stock_trading.config import AppConfig
from stock_trading.market.baostock_client import (
    BaoStockClient,
    BaoStockConcurrentAccessError,
    BaoStockError,
)
from stock_trading.market.models import DailyBar, MinuteBar
from stock_trading.market.provider import MarketDataProvider
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.utils.instruments import to_broker_code
from stock_trading.utils.logging_utils import (
    log_context,
    set_log_context,
    should_log_progress,
)
from stock_trading.utils.time_utils import shanghai_today

LOGGER = logging.getLogger(__name__)
BAOSTOCK_SESSION_REQUEST_LIMIT = 500
BAOSTOCK_CODE_MAX_ATTEMPTS = 3
BAOSTOCK_MINUTE5_BATCH_DAYS = 365


def market_provider(config: AppConfig, name: str) -> MarketDataProvider:
    if name == "baostock":
        return BaoStockMarketProvider(
            config.baostock_proxy,
            config.state_db_path.parent / "baostock.lock",
        )
    if name == "zxbroker":
        return ZXBrokerMarketProvider(config)
    raise ValueError(f"不支持的行情数据源：{name}")


def l1_market_provider(config: AppConfig) -> MarketDataProvider:
    """L1 与 L2 统一使用 BaoStock，L3 使用券商。"""
    return market_provider(config, "baostock")


class BaoStockMarketProvider:
    name = "baostock"

    def __init__(self, proxy_url: str | None, process_lock_path: Path) -> None:
        self._proxy_url = proxy_url
        self._process_lock_path = process_lock_path

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
        def collect(client: BaoStockClient, code: str) -> int:
            values = client.fetch_daily_history(code, dates[0], dates[-1])
            if not values:
                raise ValueError("日线为空")
            store.write_daily_history(code, values)
            return len(values)

        return self._sync_serial_codes(codes, "日线", collect)

    def sync_minute5(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        batches: list[tuple[date, date]] = []
        cursor = start_date
        while cursor <= end_date:
            batch_end = min(
                end_date,
                cursor + timedelta(days=BAOSTOCK_MINUTE5_BATCH_DAYS - 1),
            )
            batches.append((cursor, batch_end))
            cursor = batch_end + timedelta(days=1)

        rows = 0
        skipped_code_batches = 0
        attempted_code_batches = 0
        failure_samples: dict[str, str] = {}
        batch_details: list[dict[str, object]] = []
        for batch_index, (batch_start, batch_end) in enumerate(batches, start=1):
            pending_codes = [
                code
                for code in codes
                if not store.minute_history_valid(code, batch_start, batch_end)
            ]
            skipped = len(codes) - len(pending_codes)
            skipped_code_batches += skipped
            attempted_code_batches += len(pending_codes)
            set_log_context(
                progress=f"{batch_index}/{len(batches)}",
                target=f"{batch_start}:{batch_end}",
            )
            LOGGER.info(
                "5分钟线时间批次开始：批次=%d/%d；日期=%s 至 %s；"
                "目标股票=%d；断点复用=%d；待采=%d",
                batch_index,
                len(batches),
                batch_start,
                batch_end,
                len(codes),
                skipped,
                len(pending_codes),
            )
            if not pending_codes:
                batch_details.append(
                    {
                        "startDate": batch_start.isoformat(),
                        "endDate": batch_end.isoformat(),
                        "requestedCodes": 0,
                        "completedCodes": 0,
                        "failedCodes": 0,
                    }
                )
                continue

            def collect(client: BaoStockClient, code: str) -> int:
                values = client.fetch_minutes(code, batch_start, batch_end)
                if not values:
                    raise ValueError("5分钟线为空")
                store.write_minute_history(
                    code,
                    values,
                    end_date,
                    required_start_date=batch_start,
                    required_end_date=batch_end,
                )
                return len(values)

            result = self._sync_serial_codes(
                pending_codes,
                "5分钟线",
                collect,
                date_range=(batch_start, batch_end),
            )
            rows += int(result["rows"])
            for code, reason in dict(result["failureSamples"]).items():
                failure_samples[
                    f"{code}@{batch_start.isoformat()}:{batch_end.isoformat()}"
                ] = str(reason)
            batch_details.append(
                {
                    "startDate": batch_start.isoformat(),
                    "endDate": batch_end.isoformat(),
                    "requestedCodes": len(pending_codes),
                    "completedCodes": int(result["codes"]),
                    "failedCodes": int(result["failedCodes"]),
                }
            )
            set_log_context(
                progress=f"{batch_index}/{len(batches)}",
                target=f"{batch_start}:{batch_end}",
            )
            LOGGER.info(
                "5分钟线时间批次完成：批次=%d/%d；日期=%s 至 %s；"
                "成功=%d；失败=%d",
                batch_index,
                len(batches),
                batch_start,
                batch_end,
                int(result["codes"]),
                int(result["failedCodes"]),
            )

        completed_codes = [
            code
            for code in codes
            if store.minute_collection_ready(code, start_date, end_date)
        ]
        return {
            "provider": self.name,
            "execution": "single-process-serial",
            "progressUnit": "date-range",
            "batchDays": BAOSTOCK_MINUTE5_BATCH_DAYS,
            "dateBatches": len(batches),
            "rows": rows,
            "codes": len(completed_codes),
            "completedCodes": completed_codes,
            "failedCodes": len(codes) - len(completed_codes),
            "attemptedCodeBatches": attempted_code_batches,
            "skippedCodeBatches": skipped_code_batches,
            "batchDetails": batch_details,
            "failureSamples": dict(list(failure_samples.items())[:20]),
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

    def _sync_serial_codes(
        self,
        codes: list[str],
        label: str,
        collect: Callable[[BaoStockClient, str], int],
        date_range: tuple[date, date] | None = None,
    ) -> dict[str, object]:
        completed = 0
        completed_codes: list[str] = []
        rows = 0
        failures: dict[str, str] = {}
        attempts: dict[str, int] = defaultdict(int)
        index = 0
        while index < len(codes):
            session_end = min(index + BAOSTOCK_SESSION_REQUEST_LIMIT, len(codes))
            reconnect = False
            with self._client() as client:
                while index < session_end:
                    code = codes[index]
                    current = index + 1
                    set_log_context(progress=f"{current}/{len(codes)}", target=code)
                    if should_log_progress(current, len(codes)):
                        if date_range is None:
                            _log_code_progress(label, current, len(codes), code)
                        else:
                            LOGGER.info(
                                "%s日期批次覆盖：日期=%s 至 %s；股票=%s；批次内=%d/%d",
                                label,
                                date_range[0],
                                date_range[1],
                                code,
                                current,
                                len(codes),
                            )
                    try:
                        written_rows = collect(client, code)
                    except BaoStockConcurrentAccessError:
                        raise
                    except BaoStockError as error:
                        if "10001011" in str(error):
                            raise BaoStockError(
                                f"BaoStock IP 已进入黑名单：{error}"
                            ) from error
                        attempts[code] += 1
                        if attempts[code] >= BAOSTOCK_CODE_MAX_ATTEMPTS:
                            failures[code] = str(error)
                            LOGGER.warning(
                                "%s采集失败：股票=%s；已重试=%d；原因=%s",
                                label,
                                code,
                                attempts[code],
                                error,
                            )
                            index += 1
                        else:
                            LOGGER.warning(
                                "%s连接失效，关闭后串行重连：股票=%s；尝试=%d/%d；原因=%s",
                                label,
                                code,
                                attempts[code],
                                BAOSTOCK_CODE_MAX_ATTEMPTS,
                                error,
                            )
                        reconnect = True
                        break
                    except Exception as error:
                        failures[code] = str(error)
                        LOGGER.warning("%s采集失败：股票=%s；原因=%s", label, code, error)
                        index += 1
                    else:
                        completed += 1
                        completed_codes.append(code)
                        rows += written_rows
                        index += 1
            if reconnect:
                continue
        return {
            "provider": self.name,
            "execution": "single-process-serial",
            "rows": rows,
            "codes": completed,
            "completedCodes": completed_codes,
            "failedCodes": len(failures),
            "failureSamples": dict(list(failures.items())[:20]),
        }

    @contextmanager
    def _client(self):
        active: BaoStockClient | None = None
        attempt = 0
        while active is None:
            attempt += 1
            client = BaoStockClient(
                self._proxy_url,
                process_lock_path=self._process_lock_path,
            )
            try:
                active = client.__enter__()
            except (BaoStockError, OSError, TimeoutError) as error:
                if isinstance(error, BaoStockConcurrentAccessError):
                    raise
                if "10001011" in str(error):
                    raise BaoStockError(f"BaoStock IP 已进入黑名单：{error}") from error
                wait_seconds = min(attempt * 3, 60)
                LOGGER.warning(
                    "BaoStock 连接失败，%d秒后继续串行重试：尝试=%d；原因=%s",
                    wait_seconds,
                    attempt,
                    error,
                )
                time.sleep(wait_seconds)
        try:
            yield active
        finally:
            active.__exit__(None, None, None)


class ZXBrokerMarketProvider:
    """将券商分时价点转换并保存为统一 1 分钟标准行情。"""

    name = "zxbroker"

    def __init__(self, config: AppConfig) -> None:
        self._client = ZXBrokerClient(
            config,
            captcha_solver=BaiduCaptchaSolver(config).solve,
        )
        self._workers = config.minute_market_workers
        self._request_interval_seconds = config.minute_market_request_interval_seconds
        self._last_request_started_at = 0.0
        self._rate_lock = Lock()

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
        codes_by_date = _minute_codes_by_trade_date(
            store, codes, start_date, end_date
        )
        complete_dates_by_code = {
            code: store.minute1_raw_complete_dates(code, start_date, end_date)
            for code in codes
        }
        target_code_dates = sum(len(values) for values in codes_by_date.values())
        ordered_dates = sorted(codes_by_date, reverse=True)
        with ThreadPoolExecutor(max_workers=self._workers) as executor:
            for date_index, trade_date in enumerate(ordered_dates, start=1):
                date_codes = codes_by_date[trade_date]
                missing_codes = [
                    code
                    for code in date_codes
                    if trade_date not in complete_dates_by_code[code]
                ]
                skipped_code_dates += len(date_codes) - len(missing_codes)
                with log_context(
                    progress=f"{date_index}/{len(ordered_dates)}",
                    target=trade_date.isoformat(),
                ):
                    LOGGER.info(
                        "1分钟线日期批次开始：日期=%s；目标股票=%d；已有完整=%d；"
                        "待采=%d；并发=%d；全局间隔=%.2f秒",
                        trade_date,
                        len(date_codes),
                        len(date_codes) - len(missing_codes),
                        len(missing_codes),
                        self._workers,
                        self._request_interval_seconds,
                    )
                    futures = {
                        executor.submit(
                            self._collect_minute_date,
                            store,
                            code,
                            trade_date,
                        ): code
                        for code in missing_codes
                    }
                    date_failures = 0
                    for completed, future in enumerate(
                        as_completed(futures), start=1
                    ):
                        code = futures[future]
                        failure_key = f"{code}@{trade_date.isoformat()}"
                        try:
                            written_rows, points = future.result()
                        except HTTPError as error:
                            if error.code in {403, 429}:
                                for pending in futures:
                                    pending.cancel()
                                raise RuntimeError(
                                    "券商分钟行情触发访问限制，已停止采集："
                                    f"HTTP {error.code}"
                                ) from error
                            failures[failure_key] = str(error)
                            date_failures += 1
                            LOGGER.warning(
                                "1分钟线采集失败：股票=%s；日期=%s；原因=%s",
                                code,
                                trade_date,
                                error,
                            )
                        except Exception as error:
                            failures[failure_key] = str(error)
                            date_failures += 1
                            LOGGER.warning(
                                "1分钟线采集失败：股票=%s；日期=%s；原因=%s",
                                code,
                                trade_date,
                                error,
                            )
                        else:
                            complete_dates_by_code[code].add(trade_date)
                            completed_code_dates += 1
                            rows += written_rows
                            minute_points += points
                        if should_log_progress(completed, len(futures)):
                            LOGGER.info(
                                "1分钟线日期批次覆盖：日期=%s；完成=%d/%d",
                                trade_date,
                                completed,
                                len(futures),
                            )
                    LOGGER.info(
                        "1分钟线日期批次完成：日期=%s；采集成功=%d；失败=%d",
                        trade_date,
                        len(missing_codes) - date_failures,
                        date_failures,
                    )
        return {
            "provider": self.name,
            "progressUnit": "trade-date",
            "rows": rows,
            "minutePoints": minute_points,
            "targetCodeDates": target_code_dates,
            "completedCodeDates": completed_code_dates,
            "skippedCodeDates": skipped_code_dates,
            "failedCodeDates": len(failures),
            "workers": self._workers,
            "requestIntervalSeconds": self._request_interval_seconds,
            "failureSamples": dict(list(failures.items())[:20]),
        }

    def _collect_minute_date(
        self,
        store: ParquetMarketStore,
        code: str,
        trade_date: date,
    ) -> tuple[int, int]:
        series = self._get_minute_series(code, trade_date)
        values = _to_minute_bars(code, series)
        if not values:
            raise ValueError("券商分钟行情没有可写入的 1 分钟数据")
        store.write_minute1_history(code, values)
        return len(values), len(series.points)

    def _get_minute_series(
        self, code: str, trade_date: date
    ) -> BrokerMinuteSeries:
        for attempt in range(2):
            with self._rate_lock:
                elapsed = time.monotonic() - self._last_request_started_at
                remaining = self._request_interval_seconds - elapsed
                if self._last_request_started_at and remaining > 0:
                    time.sleep(remaining)
                self._last_request_started_at = time.monotonic()
            try:
                return self._client.get_minute_series(
                    to_broker_code(code), trade_date
                )
            except HTTPError as error:
                if error.code in {403, 429} or attempt == 1:
                    raise
                LOGGER.warning(
                    "券商分钟行情网络请求失败，准备重试：股票=%s；日期=%s；原因=%s",
                    code,
                    trade_date,
                    error,
                )
                time.sleep(2)
            except (URLError, TimeoutError, ConnectionError, OSError) as error:
                if attempt == 1:
                    raise
                LOGGER.warning(
                    "券商分钟行情网络请求失败，准备重试：股票=%s；日期=%s；原因=%s",
                    code,
                    trade_date,
                    error,
                )
                time.sleep(2)
        raise RuntimeError("券商分钟行情重试流程异常结束")


def _log_code_progress(label: str, current: int, total: int, code: str) -> None:
    if should_log_progress(current, total):
        LOGGER.info(
            "%s采集进度：股票=%s；进度=%d/%d",
            label,
            code,
            current,
            total,
        )


def _minute_codes_by_trade_date(
    store: ParquetMarketStore,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[date, list[str]]:
    result: dict[date, list[str]] = defaultdict(list)
    for code in codes:
        try:
            daily = store.read_daily_history(code)
        except (OSError, ValueError, KeyError) as error:
            raise ValueError(
                f"股票 {code} 缺少日线，无法确定历史分钟交易日"
            ) from error
        dates = daily["trade_date"].dt.date
        selected = daily[(dates >= start_date) & (dates <= end_date)]
        if "tradestatus" in selected:
            selected = selected[selected["tradestatus"].astype(str).eq("1")]
        if "volume" in selected:
            selected = selected[
                pd.to_numeric(selected["volume"], errors="coerce")
                .fillna(0)
                .gt(0)
            ]
        for trade_date in sorted(set(selected["trade_date"].dt.date)):
            result[trade_date].append(code)
    today = shanghai_today()
    if start_date <= today <= end_date:
        existing = set(result[today])
        result[today].extend(code for code in codes if code not in existing)
    return dict(result)


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
# AI_GENERATE_END---------------------- --------
