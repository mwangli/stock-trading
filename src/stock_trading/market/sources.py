# AI_GENERATE_START
from __future__ import annotations

import logging
import socket
import time
from collections import defaultdict
from collections.abc import Callable
from concurrent.futures import ThreadPoolExecutor, as_completed
from contextlib import contextmanager, redirect_stderr, redirect_stdout
from contextvars import copy_context
from decimal import Decimal
from datetime import date, timedelta, time as datetime_time
from io import StringIO
from threading import Lock
from types import TracebackType
from typing import Any, Protocol, TypeVar
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse

import baostock as bs
import baostock.common.context as bs_context
import baostock.common.contants as bs_constants
import baostock.util.socketutil as bs_socket
import pandas as pd

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.models import BrokerMinuteSeries
from stock_trading.config import AppConfig
from stock_trading.market.domain import trading_dates_between
from stock_trading.market.models import DailyBar, MinuteBar, StockBasic
from stock_trading.market.storage import ParquetMarketStore
import stock_trading.market.quality as quality
from stock_trading.market.domain import to_broker_code
from stock_trading.utils.logging_utils import log_context, set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)
T = TypeVar("T")

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
        expected_dates: set[date] | None = None,
    ) -> dict[str, object]: ...

    def sync_minute(
        self,
        store: ParquetMarketStore,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]: ...

DAILY_FIELDS = (
    "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,"
    "tradestatus,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
)
MINUTE_FIELDS = "date,time,code,open,high,low,close,volume,amount,adjustflag"
BAOSTOCK_PAGE_SIZE = 10_000


class BaoStockError(RuntimeError):
    """BaoStock 协议或数据错误。"""


class _ClosedAwareSocket:
    """把服务端正常关闭转换成 BaoStock 能处理的网络异常。"""

    def __init__(self, connection: socket.socket) -> None:
        self._connection = connection

    def recv(self, buffer_size: int, flags: int = 0) -> bytes:
        chunk = self._connection.recv(buffer_size, flags)
        if not chunk:
            raise ConnectionError("BaoStock 服务端已关闭连接")
        return chunk

    def __getattr__(self, name: str) -> Any:
        return getattr(self._connection, name)


class BaoStockClient:
    """提供统一模型对象的 BaoStock 客户端。"""

    def __init__(
        self,
        proxy_url: str | None = None,
        socket_timeout_seconds: int = 60,
    ) -> None:
        self._proxy_url = proxy_url
        self._socket_timeout_seconds = socket_timeout_seconds
        self._original_connect: Any = None
        self._logged_in = False

    def __enter__(self) -> "BaoStockClient":
        try:
            self._install_connector()
            result = self._request(bs.login)
        except BaseException:
            self._close_connection()
            self._restore_connector()
            raise
        if result.error_code != "0":
            self._close_connection()
            self._restore_connector()
            raise BaoStockError(
                f"BaoStock 登录失败：{result.error_code} {result.error_msg}"
            )
        self._logged_in = True
        LOGGER.info("BaoStock 登录成功")
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        del exc_type, exc_value, traceback
        try:
            # BaoStock 0.9.4 在服务端先关闭连接时，logout 会无限读取空字节。
            self._close_connection()
        finally:
            self._logged_in = False
            self._restore_connector()

    def fetch_stock_basics(self, snapshot_date: date) -> list[StockBasic]:
        market_rows = self._rows(
            self._request(
                lambda: bs.query_all_stock(day=snapshot_date.isoformat())
            ),
            f"证券列表 {snapshot_date}",
        )
        market_by_code = {
            str(row.get("code") or "").lower(): row
            for row in market_rows
            if str(row.get("code") or "").lower().startswith(("sh.", "sz."))
        }
        basic_rows = self._rows(
            self._request(bs.query_stock_basic), "股票基础资料"
        )
        basic_by_code = {
            str(row.get("code") or "").lower(): row
            for row in basic_rows
            if str(row.get("code") or "").lower().startswith(("sh.", "sz."))
        }
        codes = sorted(set(market_by_code) | set(basic_by_code))
        if len(codes) < 100:
            raise BaoStockError(f"BaoStock 股票基础信息数量异常：{len(codes)}")
        return [
            StockBasic.from_baostock(
                basic_by_code.get(code, {}),
                market_by_code.get(code, {}),
                snapshot_date,
            )
            for code in codes
        ]

    def fetch_daily_history(
        self, code: str, start_date: date, end_date: date
    ) -> list[DailyBar]:
        result = self._request(
            lambda: bs.query_history_k_data_plus(
                code,
                DAILY_FIELDS,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
                frequency="d",
                adjustflag="3",
            )
        )
        return [
            DailyBar.from_baostock(row)
            for row in self._rows(result, f"日线历史 {code}")
        ]

    def fetch_trade_dates(self, start_date: date, end_date: date) -> list[date]:
        result = self._request(
            lambda: bs.query_trade_dates(
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
            )
        )
        rows = self._rows(result, "交易日历")
        expected = {start_date + timedelta(days=offset) for offset in range((end_date - start_date).days + 1)}
        actual = [date.fromisoformat(row["calendar_date"]) for row in rows]
        if len(actual) != len(expected) or set(actual) != expected or any(
            str(row.get("is_trading_day")) not in {"0", "1"} for row in rows
        ):
            raise BaoStockError("交易日历尚未完整发布或响应缺日，不能推测交易日")
        return sorted(
            date.fromisoformat(row["calendar_date"])
            for row in rows
            if str(row.get("is_trading_day")) == "1"
        )

    def fetch_minutes(
        self, code: str, start_date: date, end_date: date
    ) -> list[MinuteBar]:
        bs_constants.BAOSTOCK_PER_PAGE_COUNT = BAOSTOCK_PAGE_SIZE
        result = self._request(
            lambda: bs.query_history_k_data_plus(
                code,
                MINUTE_FIELDS,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
                frequency="5",
                adjustflag="3",
            )
        )
        return [
            MinuteBar.from_baostock(row)
            for row in self._rows(result, f"5分钟线 {code}")
        ]

    @staticmethod
    def _rows(result: Any, operation: str) -> list[dict[str, str]]:
        if result is None:
            raise BaoStockError("BaoStock 未返回结果对象")
        if result.error_code != "0":
            raise BaoStockError(
                f"BaoStock 查询失败：{result.error_code} {result.error_msg}"
            )
        server_page_size = int(result.per_page_count or 0)
        if server_page_size > 0:
            bs_constants.BAOSTOCK_PER_PAGE_COUNT = server_page_size
        rows: list[dict[str, str]] = []
        while result.next():
            rows.append(dict(zip(result.fields, result.get_row_data(), strict=True)))
        return rows

    def _install_connector(self) -> None:
        self._original_connect = bs_socket.SocketUtil.connect
        timeout = self._socket_timeout_seconds
        proxy = urlparse(self._proxy_url or "")
        if self._proxy_url:
            if proxy.scheme not in {"http", "https"} or not proxy.hostname or not proxy.port:
                raise ValueError("BAOSTOCK_PROXY 必须是包含端口的 HTTP 代理地址")
            if proxy.username or proxy.password:
                raise ValueError("BAOSTOCK_PROXY 不支持代理认证")

        def connect_with_timeout(socket_util: Any, api_key: str) -> None:
            del socket_util
            target_host = (
                bs_constants.BAOSTOCK_VIP_SERVER_IP
                if api_key.startswith("bs-")
                else bs_constants.BAOSTOCK_SERVER_IP
            )
            if not self._proxy_url:
                connection = socket.create_connection(
                    (target_host, bs_constants.BAOSTOCK_SERVER_PORT), timeout=timeout
                )
                connection.settimeout(timeout)
                setattr(
                    bs_context,
                    "default_socket",
                    _ClosedAwareSocket(connection),
                )
                return
            connection = socket.create_connection(
                (proxy.hostname, proxy.port), timeout=timeout
            )
            request = (
                f"CONNECT {target_host}:{bs_constants.BAOSTOCK_SERVER_PORT} HTTP/1.1\r\n"
                f"Host: {target_host}:{bs_constants.BAOSTOCK_SERVER_PORT}\r\n"
                "Proxy-Connection: Keep-Alive\r\n\r\n"
            )
            connection.sendall(request.encode("ascii"))
            response = b""
            while b"\r\n\r\n" not in response:
                chunk = connection.recv(4096)
                if not chunk:
                    connection.close()
                    raise BaoStockError("BaoStock 代理连接提前关闭")
                response += chunk
            if b" 200 " not in response.split(b"\r\n", 1)[0]:
                connection.close()
                raise BaoStockError("BaoStock 代理隧道建立失败")
            connection.settimeout(timeout)
            setattr(
                bs_context,
                "default_socket",
                _ClosedAwareSocket(connection),
            )

        bs_socket.SocketUtil.connect = connect_with_timeout

    @staticmethod
    def _close_connection() -> None:
        connection = getattr(bs_context, "default_socket", None)
        if connection is None:
            return
        try:
            connection.close()
        except OSError:
            pass
        finally:
            setattr(bs_context, "default_socket", None)

    @staticmethod
    def _request(request: Callable[[], T]) -> T:
        console_noise = StringIO()
        with redirect_stdout(console_noise), redirect_stderr(console_noise):
            return request()

    def _restore_connector(self) -> None:
        if self._original_connect is not None:
            bs_socket.SocketUtil.connect = self._original_connect
            self._original_connect = None

BAOSTOCK_SESSION_REQUEST_LIMIT = 500
BAOSTOCK_CODE_MAX_ATTEMPTS = 3
BAOSTOCK_MINUTE5_BATCH_DAYS = 365


def market_provider(config: AppConfig, name: str) -> MarketDataProvider:
    if name == "baostock":
        return BaoStockMarketProvider(config.baostock_proxy)
    if name == "zxbroker":
        return ZXBrokerMarketProvider(config)
    raise ValueError(f"不支持的行情数据源：{name}")


def l1_market_provider(config: AppConfig) -> MarketDataProvider:
    """L1 与 L2 统一使用 BaoStock，L3 使用券商。"""
    return market_provider(config, "baostock")


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
        expected_dates: set[date] | None = None,
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
        expected_dates_by_code: dict[str, set[date]] = {}
        for code in codes:
            code_dates = quality.daily_trade_dates(store, code, start_date, end_date)
            expected_dates_by_code[code] = (
                code_dates & expected_dates
                if expected_dates is not None
                else code_dates
            )
        for batch_index, (batch_start, batch_end) in enumerate(batches, start=1):
            check_step = "2/2 5分钟线检查"
            set_log_context(step=check_step, progress=f"0/{len(codes)}", target="-")
            LOGGER.info(
                "5分钟线断点检查开始：日期=%s 至 %s；目标股票=%d",
                batch_start,
                batch_end,
                len(codes),
            )
            pending_codes: list[str] = []
            reused_codes = 0
            for checked, code in enumerate(codes, start=1):
                code_expected_dates = {
                    value
                    for value in expected_dates_by_code[code]
                    if batch_start <= value <= batch_end
                }
                ready = not code_expected_dates or quality.minute_history_valid(store,
                    code,
                    batch_start,
                    batch_end,
                    expected_dates=code_expected_dates,
                )
                if ready:
                    reused_codes += 1
                else:
                    pending_codes.append(code)
                set_log_context(
                    step=check_step,
                    progress=f"{checked}/{len(codes)}",
                    target=code,
                )
                if should_log_progress(checked, len(codes)):
                    LOGGER.info(
                        "5分钟线断点检查进度：日期=%s 至 %s；已检查=%d/%d；断点复用=%d；待采=%d",
                        batch_start,
                        batch_end,
                        checked,
                        len(codes),
                        reused_codes,
                        len(pending_codes),
                    )
            LOGGER.info(
                "5分钟线断点检查完成：日期=%s 至 %s；已检查=%d；断点复用=%d；待采=%d",
                batch_start,
                batch_end,
                len(codes),
                reused_codes,
                len(pending_codes),
            )
            skipped = len(codes) - len(pending_codes)
            skipped_code_batches += skipped
            attempted_code_batches += len(pending_codes)
            set_log_context(
                step="2/2 5分钟线",
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
                step="2/2 5分钟线",
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

        final_check_step = "2/2 5分钟线检查"
        set_log_context(step=final_check_step, progress=f"0/{len(codes)}", target="-")
        LOGGER.info(
            "5分钟线采集结果检查开始：日期=%s 至 %s；目标股票=%d",
            start_date,
            end_date,
            len(codes),
        )
        completed_codes: list[str] = []
        for checked, code in enumerate(codes, start=1):
            ready = quality.minute_collection_ready(
                store, code,
                start_date,
                end_date,
                expected_dates=expected_dates_by_code[code],
            )
            if ready:
                completed_codes.append(code)
            set_log_context(
                step=final_check_step,
                progress=f"{checked}/{len(codes)}",
                target=code,
            )
            if should_log_progress(checked, len(codes)):
                LOGGER.info(
                    "5分钟线采集结果检查进度：已检查=%d/%d；完成=%d；失败=%d",
                    checked,
                    len(codes),
                    len(completed_codes),
                    checked - len(completed_codes),
                )
        set_log_context(
            step=final_check_step,
            progress=f"{len(codes)}/{len(codes)}",
            target="-",
        )
        LOGGER.info(
            "5分钟线采集结果检查完成：已检查=%d；完成=%d；失败=%d",
            len(codes),
            len(completed_codes),
            len(codes) - len(completed_codes),
        )
        return {
            "provider": self.name,
            "execution": "serial",
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
            "execution": "serial",
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
            client = BaoStockClient(self._proxy_url)
            try:
                active = client.__enter__()
            except (BaoStockError, OSError, TimeoutError) as error:
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
        expected_dates: set[date] | None = None,
    ) -> dict[str, object]:
        del store, codes, start_date, end_date, expected_dates
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
        empty_code_dates = 0
        rows = 0
        minute_points = 0
        failures: dict[str, str] = {}
        codes_by_date = _minute_codes_by_trade_date(
            store, codes, start_date, end_date
        )
        complete_dates_by_code: dict[str, set[date]] = {}
        set_log_context(
            step="2/2 1分钟线检查",
            progress=f"0/{len(codes)}",
            target="-",
        )
        LOGGER.info(
            "1分钟线断点检查开始：日期=%s 至 %s；目标股票=%d",
            start_date,
            end_date,
            len(codes),
        )
        for checked, code in enumerate(codes, start=1):
            complete_dates_by_code[code] = quality.minute1_raw_complete_dates(store,
                code, start_date, end_date
            )
            set_log_context(
                step="2/2 1分钟线检查",
                progress=f"{checked}/{len(codes)}",
                target=code,
            )
            if should_log_progress(checked, len(codes)):
                LOGGER.info(
                    "1分钟线断点检查进度：已检查=%d/%d；完整股票日期=%d",
                    checked,
                    len(codes),
                    sum(len(values) for values in complete_dates_by_code.values()),
                )
        LOGGER.info(
            "1分钟线断点检查完成：已检查=%d；完整股票日期=%d",
            len(codes),
            sum(len(values) for values in complete_dates_by_code.values()),
        )
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
                            copy_context().run,
                            self._collect_minute_date,
                            store,
                            code,
                            trade_date,
                        ): code
                        for code in missing_codes
                    }
                    date_failures = 0
                    date_empty = 0
                    for completed, future in enumerate(
                        as_completed(futures), start=1
                    ):
                        code = futures[future]
                        failure_key = f"{code}@{trade_date.isoformat()}"
                        try:
                            result = future.result()
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
                            if result is None:
                                empty_code_dates += 1
                                date_empty += 1
                                skipped_code_dates += 1
                                continue
                            written_rows, points = result
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
                        "1分钟线日期批次完成：日期=%s；采集成功=%d；无成交=%d；失败=%d",
                        trade_date,
                        len(missing_codes) - date_failures - date_empty,
                        date_empty,
                        date_failures,
                    )
        return {
            "provider": self.name,
            "execution": "thread-pool",
            "progressUnit": "trade-date",
            "rows": rows,
            "minutePoints": minute_points,
            "targetCodeDates": target_code_dates,
            "completedCodeDates": completed_code_dates,
            "skippedCodeDates": skipped_code_dates,
            "emptyCodeDates": empty_code_dates,
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
    ) -> tuple[int, int] | None:
        series = self._get_minute_series(code, trade_date)
        values = _to_minute_bars(code, series)
        if not values:
            return None
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
    calendar_dates = trading_dates_between(start_date, end_date)
    stocks = store.read_stock_basics()
    stocks["code"] = stocks["code"].astype(str).str.lower()
    result: dict[date, list[str]] = defaultdict(list)
    for code in codes:
        matched = stocks[stocks["code"].eq(code)]
        if matched.empty:
            raise ValueError(f"股票基础信息中不存在股票 {code}")
        stock = matched.iloc[0]
        ipo_date = (
            pd.Timestamp(stock["ipo_date"]).date()
            if pd.notna(stock["ipo_date"])
            else None
        )
        out_date = (
            pd.Timestamp(stock["out_date"]).date()
            if pd.notna(stock["out_date"])
            else None
        )
        for trade_date in calendar_dates:
            if (ipo_date and trade_date < ipo_date) or (
                out_date and trade_date > out_date
            ):
                continue
            result[trade_date].append(code)
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
# AI_GENERATE_END
