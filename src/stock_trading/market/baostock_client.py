# AI_GENERATE_START --------------------------
from __future__ import annotations

import logging
import os
import socket
from threading import RLock
from collections.abc import Callable
from contextlib import redirect_stderr, redirect_stdout
from datetime import date, timedelta
from io import StringIO
from pathlib import Path
from types import TracebackType
from typing import Any, TypeVar
from urllib.parse import urlparse

import baostock as bs
import baostock.common.context as bs_context
import baostock.common.contants as bs_constants
import baostock.util.socketutil as bs_socket

from stock_trading.market.models import DailyBar, MinuteBar, StockBasic
DAILY_FIELDS = (
    "date,code,open,high,low,close,preclose,volume,amount,adjustflag,turn,"
    "tradestatus,pctChg,peTTM,pbMRQ,psTTM,pcfNcfTTM,isST"
)
MINUTE_FIELDS = "date,time,code,open,high,low,close,volume,amount,adjustflag"
BAOSTOCK_PAGE_SIZE = 10_000
LOGGER = logging.getLogger(__name__)
T = TypeVar("T")


class BaoStockError(RuntimeError):
    """BaoStock 协议或数据错误。"""


class BaoStockConcurrentAccessError(BaoStockError):
    """另一个进程已经占用 BaoStock 连接。"""


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


class BaoStockProcessLock:
    """禁止多个进程同时建立 BaoStock 连接。"""

    _registry_lock = RLock()
    _held_locks: dict[Path, tuple[Any, int]] = {}

    def __init__(self, path: Path) -> None:
        self._path = path
        self._handle: Any = None

    def acquire(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        path = self._path.resolve()
        with self._registry_lock:
            held = self._held_locks.get(path)
            if held is not None:
                handle, count = held
                self._held_locks[path] = (handle, count + 1)
                self._handle = handle
                return

            handle = path.open("a+b")
            handle.seek(0, 2)
            if handle.tell() == 0:
                handle.write(b"0")
                handle.flush()
            handle.seek(0)
            try:
                if os.name == "nt":
                    import msvcrt

                    msvcrt.locking(handle.fileno(), msvcrt.LK_NBLCK, 1)
                else:
                    import fcntl

                    fcntl.flock(handle.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as error:
                handle.close()
                raise BaoStockConcurrentAccessError(
                    "已有 BaoStock 采集进程运行；禁止并发连接，请等待当前任务完成"
                ) from error
            self._held_locks[path] = (handle, 1)
            self._handle = handle

    def release(self) -> None:
        handle = self._handle
        if handle is None:
            return
        path = self._path.resolve()
        with self._registry_lock:
            held = self._held_locks.get(path)
            if held is None or held[0] is not handle:
                self._handle = None
                return
            _, count = held
            if count > 1:
                self._held_locks[path] = (handle, count - 1)
                self._handle = None
                return
            try:
                handle.seek(0)
                if os.name == "nt":
                    import msvcrt

                    msvcrt.locking(handle.fileno(), msvcrt.LK_UNLCK, 1)
                else:
                    import fcntl

                    fcntl.flock(handle.fileno(), fcntl.LOCK_UN)
            finally:
                self._held_locks.pop(path, None)
                handle.close()
                self._handle = None


class BaoStockClient:
    """提供统一模型对象的 BaoStock 客户端。"""

    def __init__(
        self,
        proxy_url: str | None = None,
        socket_timeout_seconds: int = 60,
        process_lock_path: Path | None = None,
    ) -> None:
        self._proxy_url = proxy_url
        self._socket_timeout_seconds = socket_timeout_seconds
        self._process_lock = (
            BaoStockProcessLock(process_lock_path)
            if process_lock_path is not None
            else None
        )
        self._original_connect: Any = None
        self._logged_in = False

    def __enter__(self) -> "BaoStockClient":
        if self._process_lock is not None:
            self._process_lock.acquire()
        try:
            self._install_connector()
            result = self._request(bs.login)
        except BaseException:
            self._close_connection()
            self._restore_connector()
            if self._process_lock is not None:
                self._process_lock.release()
            raise
        if result.error_code != "0":
            self._close_connection()
            self._restore_connector()
            if self._process_lock is not None:
                self._process_lock.release()
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
            if self._process_lock is not None:
                self._process_lock.release()

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
# AI_GENERATE_END ---------------------------
