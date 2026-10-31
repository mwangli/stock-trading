# AI_GENERATE_START ------
from __future__ import annotations

import logging
import socket
import threading
from collections.abc import Callable
from datetime import date
from types import TracebackType
from typing import Any, TypeVar, cast
from urllib.parse import urlparse

import baostock as bs
import baostock.common.context as bs_context
import baostock.common.contants as bs_constants
import baostock.util.socketutil as bs_socket

from stock_trading.market.models import AdjustmentFactor, DailyBar, MinuteBar, StockBasic

DAILY_FIELDS = (
    "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,"
    "tradestatus,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
)
MINUTE_FIELDS = "date,time,code,open,high,low,close,volume,amount,adjustflag"
LOGGER = logging.getLogger(__name__)
REQUEST_POLL_SECONDS = 0.2
T = TypeVar("T")


class BaoStockError(RuntimeError):
    """Raised when BaoStock returns a protocol or data error."""


class BaoStockClient:
    """Small BaoStock client limited to the market data required by the T+1 flow."""

    def __init__(self, proxy_url: str | None = None, socket_timeout_seconds: int = 60) -> None:
        self._proxy_url = proxy_url
        self._socket_timeout_seconds = socket_timeout_seconds
        self._original_connect: Any = None
        self._logged_in = False

    def __enter__(self) -> "BaoStockClient":
        LOGGER.info("正在连接 BaoStock：代理=%s", "已配置" if self._proxy_url else "未配置")
        self._install_connector()
        try:
            result = self._request_interruptibly("BaoStock 登录", bs.login)
        except BaseException:
            self._restore_connector()
            raise
        if result.error_code != "0":
            self._restore_connector()
            raise BaoStockError(f"BaoStock login failed: {result.error_code} {result.error_msg}")
        self._logged_in = True
        LOGGER.info("BaoStock 登录成功")
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        try:
            if exc_type is KeyboardInterrupt:
                self.interrupt()
            elif self._logged_in:
                self._request_interruptibly("BaoStock 登出", bs.logout)
        finally:
            self._logged_in = False
            self._restore_connector()

    def fetch_daily_market(self, trade_date: date) -> list[DailyBar]:
        LOGGER.info("正在请求全市场日线：交易日=%s", trade_date)
        result = self._request_interruptibly(
            f"全市场日线 {trade_date}",
            lambda: bs.query_daily_history_k_AStock(date=trade_date.isoformat()),
        )
        return [
            DailyBar.from_baostock(row)
            for row in self._rows(result, f"全市场日线 {trade_date}")
        ]

    def fetch_stock_basics(self, snapshot_date: date) -> list[StockBasic]:
        """读取全部 A 股基础资料，并合并指定日期的交易状态。"""
        LOGGER.info("正在请求全部股票基础信息：快照日期=%s", snapshot_date)
        market_rows = self._rows(
            self._request_interruptibly(
                f"证券列表 {snapshot_date}",
                lambda: bs.query_all_stock(day=snapshot_date.isoformat()),
            ),
            f"证券列表 {snapshot_date}",
        )
        basic_rows = self._rows(
            self._request_interruptibly("股票基础资料", bs.query_stock_basic),
            "股票基础资料",
        )
        market_by_code = {
            str(row.get("code") or "").lower(): row
            for row in market_rows
            if str(row.get("code") or "").lower().startswith(("sh.", "sz."))
        }
        basic_by_code = {
            str(row.get("code") or "").lower(): row
            for row in basic_rows
            if str(row.get("code") or "").lower().startswith(("sh.", "sz."))
        }
        codes = sorted(set(market_by_code) | set(basic_by_code))
        result = [
            StockBasic.from_baostock(
                basic_by_code.get(code, {}),
                market_by_code.get(code, {}),
                snapshot_date,
            )
            for code in codes
        ]
        LOGGER.info(
            "股票基础信息接收完成：证券列表=%d；基础资料=%d；合并结果=%d",
            len(market_rows),
            len(basic_rows),
            len(result),
        )
        return result

    def fetch_daily(self, code: str, trade_date: date) -> DailyBar | None:
        result = self._request_interruptibly(
            f"单股日线 {code} {trade_date}",
            lambda: bs.query_history_k_data_plus(
                code,
                DAILY_FIELDS,
                start_date=trade_date.isoformat(),
                end_date=trade_date.isoformat(),
                frequency="d",
                adjustflag="3",
            ),
        )
        rows = self._rows(result, f"单股日线 {code} {trade_date}")
        return DailyBar.from_baostock(rows[0]) if rows else None

    def fetch_daily_history(self, code: str, start_date: date, end_date: date) -> list[DailyBar]:
        result = self._request_interruptibly(
            f"日线历史 {code}",
            lambda: bs.query_history_k_data_plus(
                code,
                DAILY_FIELDS,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
                frequency="d",
                adjustflag="3",
            ),
        )
        return [
            DailyBar.from_baostock(row)
            for row in self._rows(result, f"日线历史 {code}")
        ]

    def fetch_adjustment_factors(
        self, code: str, start_date: date, end_date: date
    ) -> list[AdjustmentFactor]:
        result = self._request_interruptibly(
            f"复权因子 {code}",
            lambda: bs.query_adjust_factor(
                code,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
            ),
        )
        return [
            AdjustmentFactor.from_baostock(row)
            for row in self._rows(result, f"复权因子 {code}")
        ]

    def fetch_minutes(self, code: str, start_date: date, end_date: date) -> list[MinuteBar]:
        result = self._request_interruptibly(
            f"5分钟K线 {code}",
            lambda: bs.query_history_k_data_plus(
                code,
                MINUTE_FIELDS,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
                frequency="5",
                adjustflag="3",
            ),
        )
        return [
            MinuteBar.from_baostock(row)
            for row in self._rows(result, f"5分钟K线 {code}")
        ]

    @staticmethod
    def _rows(result: Any, operation: str) -> list[dict[str, str]]:
        if result is None:
            raise BaoStockError("BaoStock returned no result object")
        if result.error_code != "0":
            raise BaoStockError(f"BaoStock query failed: {result.error_code} {result.error_msg}")
        rows: list[dict[str, str]] = []
        while result.next():
            rows.append(dict(zip(result.fields, result.get_row_data(), strict=True)))
            if len(rows) % 500 == 0:
                LOGGER.info("数据采集中：任务=%s；已接收=%d 条", operation, len(rows))
        LOGGER.info("数据接收完成：任务=%s；总数=%d 条", operation, len(rows))
        return rows

    def _install_connector(self) -> None:
        self._original_connect = bs_socket.SocketUtil.connect
        timeout = self._socket_timeout_seconds
        proxy = urlparse(self._proxy_url or "")
        if self._proxy_url:
            if proxy.scheme not in {"http", "https"} or not proxy.hostname or not proxy.port:
                raise ValueError("BAOSTOCK_PROXY must be an HTTP proxy URL with an explicit port")
            if proxy.username or proxy.password:
                raise ValueError("Authenticated BAOSTOCK_PROXY is not supported")

        def connect_with_timeout(socket_util: Any, api_key: str) -> None:
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
                setattr(bs_context, "default_socket", connection)
                return
            connection = socket.create_connection((proxy.hostname, proxy.port), timeout=timeout)
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
                    raise BaoStockError("Proxy closed before establishing the BaoStock tunnel")
                response += chunk
            status_line = response.split(b"\r\n", 1)[0]
            if b" 200 " not in status_line:
                connection.close()
                status = status_line.decode("ascii", errors="replace")
                raise BaoStockError(f"BaoStock proxy tunnel failed: {status}")
            connection.settimeout(timeout)
            setattr(bs_context, "default_socket", connection)

        bs_socket.SocketUtil.connect = connect_with_timeout

    def interrupt(self) -> None:
        """关闭当前 BaoStock socket，使正在阻塞的网络读取尽快退出。"""
        connection = getattr(bs_context, "default_socket", None)
        if connection is None:
            return
        try:
            connection.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        finally:
            connection.close()
            setattr(bs_context, "default_socket", None)
            self._logged_in = False

    def _request_interruptibly(self, operation: str, request: Callable[[], T]) -> T:
        completed = threading.Event()
        outcome: list[tuple[bool, object]] = []

        def run() -> None:
            try:
                outcome.append((True, request()))
            except BaseException as error:
                outcome.append((False, error))
            finally:
                completed.set()

        worker = threading.Thread(
            target=run,
            name=f"baostock-{operation}",
            daemon=True,
        )
        worker.start()
        try:
            while not completed.wait(REQUEST_POLL_SECONDS):
                pass
        except KeyboardInterrupt:
            LOGGER.warning("收到 Ctrl+C，正在终止 BaoStock 请求：任务=%s", operation)
            self.interrupt()
            completed.wait(1)
            raise

        if not outcome:
            raise BaoStockError(f"BaoStock request ended without a result: {operation}")
        succeeded, value = outcome[0]
        if not succeeded:
            raise cast(BaseException, value)
        return cast(T, value)

    def _restore_connector(self) -> None:
        if self._original_connect is not None:
            bs_socket.SocketUtil.connect = self._original_connect
            self._original_connect = None
# AI_GENERATE_END ------
