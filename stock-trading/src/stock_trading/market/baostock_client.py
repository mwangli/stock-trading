# AI_GENERATE_START --
from __future__ import annotations

import socket
from datetime import date
from types import TracebackType
from typing import Any
from urllib.parse import urlparse

import baostock as bs
import baostock.common.context as bs_context
import baostock.common.contants as bs_constants
import baostock.util.socketutil as bs_socket

from stock_trading.market.models import DailyBar, MinuteBar

DAILY_FIELDS = (
    "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,"
    "tradestatus,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
)
MINUTE_FIELDS = "date,time,code,open,high,low,close,volume,amount,adjustflag"


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
        if self._proxy_url:
            self._install_proxy_connector()
        try:
            result = bs.login()
        except BaseException:
            self._restore_connector()
            raise
        if result.error_code != "0":
            self._restore_connector()
            raise BaoStockError(f"BaoStock login failed: {result.error_code} {result.error_msg}")
        self._logged_in = True
        return self

    def __exit__(
        self,
        exc_type: type[BaseException] | None,
        exc_value: BaseException | None,
        traceback: TracebackType | None,
    ) -> None:
        try:
            if self._logged_in:
                bs.logout()
        finally:
            self._logged_in = False
            self._restore_connector()

    def fetch_daily_market(self, trade_date: date) -> list[DailyBar]:
        result = bs.query_daily_history_k_AStock(date=trade_date.isoformat())
        return [DailyBar.from_baostock(row) for row in self._rows(result)]

    def fetch_daily(self, code: str, trade_date: date) -> DailyBar | None:
        result = bs.query_history_k_data_plus(
            code,
            DAILY_FIELDS,
            start_date=trade_date.isoformat(),
            end_date=trade_date.isoformat(),
            frequency="d",
            adjustflag="3",
        )
        rows = self._rows(result)
        return DailyBar.from_baostock(rows[0]) if rows else None

    def fetch_minutes(self, code: str, start_date: date, end_date: date) -> list[MinuteBar]:
        result = bs.query_history_k_data_plus(
            code,
            MINUTE_FIELDS,
            start_date=start_date.isoformat(),
            end_date=end_date.isoformat(),
            frequency="5",
            adjustflag="3",
        )
        return [MinuteBar.from_baostock(row) for row in self._rows(result)]

    @staticmethod
    def _rows(result: Any) -> list[dict[str, str]]:
        if result is None:
            raise BaoStockError("BaoStock returned no result object")
        if result.error_code != "0":
            raise BaoStockError(f"BaoStock query failed: {result.error_code} {result.error_msg}")
        rows: list[dict[str, str]] = []
        while result.next():
            rows.append(dict(zip(result.fields, result.get_row_data(), strict=True)))
        return rows

    def _install_proxy_connector(self) -> None:
        proxy = urlparse(self._proxy_url or "")
        if proxy.scheme not in {"http", "https"} or not proxy.hostname or not proxy.port:
            raise ValueError("BAOSTOCK_PROXY must be an HTTP proxy URL with an explicit port")
        if proxy.username or proxy.password:
            raise ValueError("Authenticated BAOSTOCK_PROXY is not supported")

        self._original_connect = bs_socket.SocketUtil.connect
        timeout = self._socket_timeout_seconds

        def connect_through_proxy(socket_util: Any, api_key: str) -> None:
            target_host = (
                bs_constants.BAOSTOCK_VIP_SERVER_IP
                if api_key.startswith("bs-")
                else bs_constants.BAOSTOCK_SERVER_IP
            )
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

        bs_socket.SocketUtil.connect = connect_through_proxy

    def _restore_connector(self) -> None:
        if self._original_connect is not None:
            bs_socket.SocketUtil.connect = self._original_connect
            self._original_connect = None
# AI_GENERATE_END --
