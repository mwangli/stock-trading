# AI_GENERATE_START --------------------
from __future__ import annotations

import logging
import socket
from collections.abc import Callable
from contextlib import redirect_stderr, redirect_stdout
from datetime import date
from io import StringIO
from types import TracebackType
from typing import Any, TypeVar
from urllib.parse import urlparse

import baostock as bs
import baostock.common.context as bs_context
import baostock.common.contants as bs_constants
import baostock.util.socketutil as bs_socket

from stock_trading.market.models import (
    CumulativeAdjustmentFactor,
    DailyBar,
    MinuteBar,
    StockBasic,
)
from stock_trading.utils.logging_utils import set_log_context

DAILY_FIELDS = (
    "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,"
    "tradestatus,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
)
MINUTE_FIELDS = "date,time,code,open,high,low,close,volume,amount,adjustflag"
LOGGER = logging.getLogger(__name__)
ROW_PROGRESS_STEPS = 20
T = TypeVar("T")


class BaoStockError(RuntimeError):
    """BaoStock 协议或数据错误。"""


class BaoStockClient:
    """提供统一模型对象的 BaoStock 客户端。"""

    def __init__(
        self, proxy_url: str | None = None, socket_timeout_seconds: int = 60
    ) -> None:
        self._proxy_url = proxy_url
        self._socket_timeout_seconds = socket_timeout_seconds
        self._original_connect: Any = None
        self._logged_in = False

    def __enter__(self) -> "BaoStockClient":
        self._install_connector()
        try:
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
        try:
            if exc_type is None and self._logged_in:
                self._request(bs.logout)
        finally:
            self._logged_in = False
            self._close_connection()
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
        return sorted(
            date.fromisoformat(row["calendar_date"])
            for row in self._rows(result, "交易日历")
            if str(row.get("is_trading_day")) == "1"
        )

    def fetch_adjustment_factors(
        self, code: str, start_date: date, end_date: date
    ) -> list[CumulativeAdjustmentFactor]:
        result = self._request(
            lambda: bs.query_adjust_factor(
                code,
                start_date=start_date.isoformat(),
                end_date=end_date.isoformat(),
            )
        )
        return [
            CumulativeAdjustmentFactor.from_baostock(row)
            for row in self._rows(result, f"复权因子 {code}")
        ]

    def fetch_minutes(
        self, code: str, start_date: date, end_date: date
    ) -> list[MinuteBar]:
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
        rows: list[dict[str, str]] = []
        estimated_total = len(result.data)
        interval = max(1, estimated_total // ROW_PROGRESS_STEPS)
        while result.next():
            rows.append(dict(zip(result.fields, result.get_row_data(), strict=True)))
            page_remaining = max(0, len(result.data) - int(result.cur_row_num))
            estimated_total = max(estimated_total, len(rows) + page_remaining)
            interval = max(1, estimated_total // ROW_PROGRESS_STEPS)
            if len(rows) == 1 or len(rows) == estimated_total or len(rows) % interval == 0:
                set_log_context(
                    progress=f"{len(rows)}/{estimated_total}", target=operation
                )
                LOGGER.info("正在接收 BaoStock 数据")
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
                setattr(bs_context, "default_socket", connection)
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
            setattr(bs_context, "default_socket", connection)

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
# AI_GENERATE_END --------------------
