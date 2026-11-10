# AI_GENERATE_START----- ----------------------------------
from __future__ import annotations

import base64
import binascii
import hashlib
import json
import logging
import os
import threading
import time
from collections.abc import Callable
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any
from urllib.error import URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen
from uuid import uuid4

from stock_trading.broker.models import BrokerMinuteSeries, FiveLevelQuote
from stock_trading.broker.parser import (
    parse_minute_series,
    parse_account_rows,
    parse_decimal,
    is_header_or_empty,
    parse_order_rows,
    parse_position_rows,
)
from stock_trading.config import AppConfig
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.utils.time_utils import shanghai_today

ACTION_GET_PRICE = 33
ACTION_GET_MINUTES = 20109
ACTION_LOGIN = 100
ACTION_SUBMIT_ORDER = 110
ACTION_CANCEL_ORDER = 111
ACTION_QUERY_TODAY_ORDERS = 113
ACTION_GET_ACCOUNT = 116
ACTION_GET_POSITIONS = 117
ACTION_QUERY_HISTORY_ORDERS = 5018
ACTION_CAPTCHA = 41092

Transport = Callable[[str, dict[str, object], float], dict[str, Any]]
CaptchaSolver = Callable[[bytes], int]
LOGGER = logging.getLogger(__name__)


class BrokerProtocolError(RuntimeError):
    """The broker returned an incomplete or invalid protocol response."""


class BrokerRejectedError(BrokerProtocolError):
    """The broker explicitly rejected an order request."""


class BrokerSafetyError(RuntimeError):
    """A real broker write was rejected by a local safety boundary."""


class ZXBrokerClient:
    """Minimal client for the broker fields already verified in the Java adapter."""

    def __init__(
        self,
        config: AppConfig,
        transport: Transport | None = None,
        captcha_solver: CaptchaSolver | None = None,
    ) -> None:
        self._config = config
        self._transport = transport or _post_form
        self._captcha_solver = captcha_solver
        self._token_lock = threading.RLock()
        self._reqno_lock = threading.Lock()
        self._last_reqno = 0
        self._token = config.broker_token
        self._token_expire_at = (
            time.monotonic() + config.broker_token_expire_minutes * 60
            if config.broker_token
            else 0.0
        )
        self._token_updated_at = time.time() if config.broker_token else 0.0
        self._token_cache_path = config.state_db_path.parent / "broker-token.json"
        self._token_cache_scope = hashlib.sha256(
            f"{config.broker_base_url}\0{config.broker_account}".encode("utf-8")
        ).hexdigest()
        if not self._token:
            self._load_cached_token()
        self._login_lock = threading.Lock()
        self._login_blocked_until = 0.0
        self._shareholder_accounts: dict[str, str] | None = None

    def build_params(self, values: dict[str, object] | None = None) -> dict[str, object]:
        params = dict(values or {})
        with self._reqno_lock:
            reqno = max(int(time.time() * 1000), self._last_reqno + 1)
            self._last_reqno = reqno
        params.update(
            {
                "cfrom": "H5",
                "tfrom": "PC",
                "newindex": "1",
                "MobileCode": self._config.broker_mobile_code,
                "intacttoserver": self._config.broker_intact_to_server,
                "reqno": reqno,
            }
        )
        return params

    def login(self) -> str:
        self._load_cached_token()
        if self._valid_token():
            LOGGER.info("券商登录跳过：正在复用有效 Token")
            return self._token
        if self._captcha_solver is None:
            raise BrokerSafetyError("未配置经过验收的验证码识别器，禁止自动登录")
        if not self._config.broker_account or not self._config.broker_encoded_password:
            raise BrokerSafetyError("未配置券商资金账号或加密密码")
        with self._login_lock:
            if self._valid_token():
                LOGGER.info("券商登录跳过：等待锁期间已有线程完成登录")
                return self._token
            remaining = self._login_blocked_until - time.monotonic()
            if remaining > 0:
                raise BrokerSafetyError(
                    f"券商登录处于失败冷却期，请在 {max(1, int(remaining) + 1)} 秒后重试"
                )
            last_error = "未知错误"
            set_log_context(
                progress=f"0/{self._config.broker_captcha_max_retries}",
                target="券商登录",
            )
            LOGGER.info(
                "开始自动登录：最大验证码尝试=%d；Token缓存=%d分钟",
                self._config.broker_captcha_max_retries,
                self._config.broker_token_expire_minutes,
            )
            for attempt in range(1, self._config.broker_captcha_max_retries + 1):
                try:
                    set_log_context(
                        progress=f"{attempt}/{self._config.broker_captcha_max_retries}",
                        target="券商登录",
                    )
                    LOGGER.info("开始验证码登录尝试")
                    token = self._login_once()
                    self._login_blocked_until = 0.0
                    LOGGER.info("券商登录成功：Token已缓存%d分钟", self._config.broker_token_expire_minutes)
                    return token
                except (URLError, TimeoutError, ConnectionError) as error:
                    last_error = f"{type(error).__name__}: {error}"
                    set_log_context(
                        progress=f"{attempt}/{self._config.broker_captcha_max_retries}",
                        target="券商登录",
                    )
                    LOGGER.warning(
                        "券商登录依赖服务网络异常，未进入冷却期：原因=%s",
                        error,
                    )
                    raise BrokerSafetyError(
                        "券商登录依赖服务网络异常，未进入冷却期，请稍后重试；"
                        f"最后错误={last_error}"
                    ) from error
                except Exception as error:
                    last_error = f"{type(error).__name__}: {error}"
                    set_log_context(
                        progress=f"{attempt}/{self._config.broker_captcha_max_retries}",
                        target="券商登录",
                    )
                    LOGGER.warning("登录尝试失败：原因=%s", error)
            self._login_blocked_until = (
                time.monotonic()
                + self._config.broker_login_failure_cooldown_seconds
            )
            raise BrokerSafetyError(
                f"券商登录失败并进入冷却期，最后错误={last_error}"
            )

    def _login_once(self) -> str:
        set_log_context(step="1/3", progress="-/-", target="券商验证码")
        LOGGER.info("获取验证码")
        captcha = self._request(self.build_params({"action": ACTION_CAPTCHA}))
        data_uri = captcha.get("MESSAGE")
        check_token = captcha.get("CHECKTOKEN")
        if not data_uri or not check_token:
            raise BrokerProtocolError("验证码接口未返回图片或 CheckToken")
        try:
            image = base64.b64decode(str(data_uri).split(",", 1)[-1], validate=True)
        except (ValueError, binascii.Error) as error:
            raise BrokerProtocolError("验证码图片无法解码") from error
        set_log_context(step="2/3", progress="-/-", target="券商验证码")
        LOGGER.info("识别验证码：图片字节数=%d", len(image))
        check_code = self._captcha_solver(image)
        set_log_context(step="3/3", progress="-/-", target="券商登录")
        LOGGER.info("提交登录请求")
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_LOGIN,
                    "modulus_id": 2,
                    "MobileType": 3,
                    "accounttype": "ZJACCOUNT",
                    "account": self._config.broker_account,
                    "password": self._config.broker_encoded_password,
                    "signkey": self._config.broker_sign_key,
                    "CheckCode": check_code,
                    "CheckToken": check_token,
                    "code": "",
                    "maxcount": 100,
                    "CHANNEL": "",
                }
            )
        )
        if not self._token:
            raise BrokerProtocolError(f"券商登录失败，错误码={response.get('ERRORNO')}")
        return self._token

    def get_account(self):
        LOGGER.info("正在读取券商账户资金")
        account = parse_account_rows(self._query_rows(ACTION_GET_ACCOUNT, {"ReqlinkType": 1}))
        if account is None:
            raise BrokerProtocolError("账户资金响应为空或字段不足")
        LOGGER.info(
            "券商账户资金读取完成：可用资金=%s；总资产=%s；持仓市值=%s",
            account.available_cash,
            account.total_assets,
            account.total_position,
        )
        return account

    def get_positions(self):
        LOGGER.info("正在读取券商持仓")
        positions = parse_position_rows(self._query_rows(ACTION_GET_POSITIONS, {"ReqlinkType": 1}))
        LOGGER.info("券商持仓读取完成：持仓数量=%d", len(positions))
        return positions

    def get_today_orders(self):
        LOGGER.info("正在读取券商当日委托")
        orders = parse_order_rows(
            self._query_rows(
                ACTION_QUERY_TODAY_ORDERS,
                {
                    "ReqlinkType": 1,
                    "StartPos": 0,
                    "MaxCount": self._config.broker_max_order_count,
                },
            )
        )
        LOGGER.info("券商当日委托读取完成：委托数量=%d", len(orders))
        return orders

    def get_history_orders(self, start_date: date, end_date: date):
        LOGGER.info("正在读取券商历史委托：日期=%s 至 %s", start_date, end_date)
        orders = parse_order_rows(self._query_history_rows(ACTION_QUERY_HISTORY_ORDERS, start_date, end_date))
        LOGGER.info("券商历史委托读取完成：数量=%d", len(orders))
        return orders

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote:
        LOGGER.info("正在读取五档行情：股票=%s", stock_code)
        response = self._request(
            self.build_params(
                {
                    "stockcode": stock_code,
                    "action": ACTION_GET_PRICE,
                }
            )
        )
        bids, asks = _parse_five_level_prices(response)
        quote = FiveLevelQuote(
            stock_code=stock_code,
            price=parse_decimal(response.get("PRICE")),
            bids=bids,
            asks=asks,
        )
        LOGGER.info(
            "五档行情读取完成：股票=%s；最新价=%s；买三=%s；卖三=%s",
            stock_code,
            quote.price,
            quote.bids[2],
            quote.asks[2],
        )
        return quote

    def get_minute_series(
        self, stock_code: str, trade_date: date | None = None
    ) -> BrokerMinuteSeries:
        """读取单只证券单日分时价点；历史日期由 BeginDate 精确指定。"""
        LOGGER.info(
            "正在读取券商分钟行情：股票=%s；日期=%s",
            stock_code,
            trade_date.isoformat() if trade_date else "当日",
        )
        values: dict[str, object] = {
            "action": ACTION_GET_MINUTES,
            "stockcode": stock_code,
        }
        if trade_date is not None:
            values["BeginDate"] = trade_date.strftime("%Y%m%d")
        response = self._request(self.build_params(values))
        series = parse_minute_series(response, stock_code)
        if trade_date is not None and series.trade_date != trade_date:
            raise BrokerProtocolError(
                f"券商分钟行情日期不匹配: {series.trade_date} != {trade_date}"
            )
        LOGGER.info(
            "券商分钟行情读取完成：股票=%s；日期=%s；分钟点=%d",
            stock_code,
            series.trade_date,
            len(series.points),
        )
        return series

    def submit_limit_order(
        self,
        direction: str,
        stock_code: str,
        price: Decimal,
        quantity: int,
    ) -> str:
        direction_code = {"BUY": "B", "SELL": "S"}.get(direction.upper())
        if direction_code is None:
            raise ValueError("不支持的买卖方向")
        if price <= 0:
            raise ValueError("委托价格必须大于零")
        if quantity <= 0:
            raise ValueError("委托数量必须大于零")
        if direction_code == "B" and quantity % 100 != 0:
            raise ValueError("A股买入数量必须是 100 股整数倍")
        LOGGER.info(
            "准备提交券商限价委托：股票=%s；方向=%s；价格=%s；数量=%d",
            stock_code,
            direction.upper(),
            price,
            quantity,
        )
        shareholder_account, shareholder_account_type = self._resolve_order_account(
            stock_code
        )
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_SUBMIT_ORDER,
                    "isformat": 1,
                    "PriceType": 0,
                    "Direction": direction_code,
                    "StockCode": stock_code,
                    "Price": price,
                    "Volume": quantity,
                    "WTAccount": shareholder_account,
                    "WTAccountType": shareholder_account_type,
                    "ProPrice": "",
                    "token": self._require_token(),
                }
            )
        )
        order_id = response.get("ANSWERNO")
        if not order_id:
            error_message = _order_error_message(response)
            raise BrokerRejectedError(
                f"券商拒绝委托，错误码={response.get('ERRORNO')}，原因={error_message}"
            )
        LOGGER.info("券商已接受委托：股票=%s；方向=%s；券商委托号=%s", stock_code, direction.upper(), order_id)
        return str(order_id)

    def probe_buy_permission(self, stock_code: str) -> dict[str, object]:
        """以不可成交高价和合法数量调用买入接口，采集权限校验事实。"""
        code = stock_code.strip()
        if len(code) != 6 or not code.isdigit():
            raise ValueError(f"股票代码必须是六位数字: {stock_code}")
        shareholder_account, account_type = self._permission_probe_account(code)
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_SUBMIT_ORDER,
                    "isformat": 1,
                    "PriceType": 0,
                    "Direction": "B",
                    "StockCode": code,
                    "Price": 9999,
                    "Volume": 10000,
                    "WTAccount": shareholder_account,
                    "WTAccountType": account_type,
                    "ProPrice": "",
                    "token": self._require_token(),
                }
            )
        )
        order_id = str(response.get("ANSWERNO") or "")
        cancel_accepted = False
        if order_id:
            cancel_accepted = self.cancel_order(order_id)
            if not cancel_accepted:
                raise BrokerSafetyError("权限探测委托被受理但立即撤单失败")
        return {
            "stockCode": code,
            "accepted": bool(order_id),
            "cancelAccepted": cancel_accepted,
            "errorNo": str(response.get("ERRORNO") or ""),
            "errorMessage": _order_error_message(response),
            "responseMsg": _order_response_message(response),
            "rawResponse": response,
        }

    def prepare_permission_probe(self) -> None:
        """在进入并发探测前串行加载沪深股东账号。"""
        self._load_shareholder_accounts("上海")
        self._load_shareholder_accounts("深圳")

    def invalidate_token(self) -> None:
        """认证异常后清理内存及本地 Token，下一次请求重新登录。"""
        with self._token_lock:
            self._token = None
            self._token_expire_at = 0.0
            self._token_updated_at = 0.0
            self._remove_cached_token()

    def _permission_probe_account(self, stock_code: str) -> tuple[str, str]:
        if stock_code.startswith("6"):
            market, account_type = "上海", "SHACCOUNT"
        elif stock_code.startswith("0"):
            market, account_type = "深圳", "SZACCOUNT"
        else:
            raise ValueError(f"权限探测不支持该股票代码: {stock_code}")
        account = self._load_shareholder_accounts(market).get(market)
        if not account:
            raise BrokerProtocolError(f"历史委托中没有{market}股东账号")
        return account, account_type

    def _resolve_order_account(self, stock_code: str) -> tuple[str, str]:
        response = self._request(
            self.build_params(
                {
                    "stockcode": stock_code,
                    "action": ACTION_GET_PRICE,
                    "ReqlinkType": 1,
                    "Level": 1,
                    "UseBPrice": 1,
                }
            )
        )
        account_type = str(response.get("WTACCOUNTTYPE") or "").strip().upper()
        market = {
            "SHACCOUNT": "上海",
            "SZACCOUNT": "深圳",
        }.get(account_type)
        if market is None:
            raise BrokerProtocolError(f"券商未返回可识别的股东账号类型: {stock_code}")
        accounts = self._load_shareholder_accounts(market)
        account = accounts.get(market)
        if not account:
            raise BrokerProtocolError(f"历史委托中没有{market}股东账号")
        return account, account_type

    def _load_shareholder_accounts(self, required_market: str) -> dict[str, str]:
        accounts = dict(self._shareholder_accounts or {})
        if required_market in accounts:
            return accounts
        window_end = shanghai_today()
        earliest = date(2000, 1, 1)
        while window_end >= earliest and required_market not in accounts:
            window_start = max(earliest, window_end - timedelta(days=729))
            latest: dict[str, tuple[datetime, str]] = {}
            for order in self.get_history_orders(window_start, window_end):
                market = (order.market or "").strip()
                account = (order.shareholder_account or "").strip()
                if market not in {"上海", "深圳"} or not account:
                    continue
                order_time = order.order_time or datetime.min
                current = latest.get(market)
                if current is None or order_time > current[0]:
                    latest[market] = (order_time, account)
            for market, (_, account) in latest.items():
                accounts.setdefault(market, account)
            window_end = window_start - timedelta(days=1)
        self._shareholder_accounts = accounts
        return accounts

    def cancel_order(self, broker_order_id: str) -> bool:
        LOGGER.info("准备提交券商撤单请求：券商委托号=%s", broker_order_id)
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_CANCEL_ORDER,
                    "ContactID": broker_order_id,
                    "token": self._require_token(),
                }
            )
        )
        accepted = str(response.get("ERRORNO")) == "0"
        log = LOGGER.info if accepted else LOGGER.warning
        log(
            "券商撤单请求完成：券商委托号=%s；是否受理=%s；错误码=%s；原因=%s",
            broker_order_id,
            accepted,
            response.get("ERRORNO"),
            response.get("ERRORMESSAGE") or response.get("ERRORINFO"),
        )
        return accepted

    def _query_rows(self, action: int, values: dict[str, object]) -> list[str]:
        token = self._require_token()
        response = self._request(
            self.build_params(values | {"action": action, "token": token, "Token": token})
        )
        rows = response.get("GRID0")
        if rows is None:
            return []
        if not isinstance(rows, list):
            raise BrokerProtocolError("GRID0 不是数组")
        return [str(row) for row in rows]

    def _query_history_rows(self, action: int, start_date: date, end_date: date) -> list[str]:
        if end_date < start_date:
            raise ValueError("结束日期不能早于开始日期")
        page_size = self._config.broker_history_page_size
        max_records = max(page_size, self._config.broker_history_max_records)
        start_position = 0
        rows: list[str] = []
        page_number = 0
        while len(rows) < max_records:
            page_number += 1
            request_count = min(page_size, max_records - len(rows))
            page = self._query_rows(
                action,
                {
                    "ReqlinkType": 1,
                    "StartPos": start_position,
                    "MaxCount": request_count,
                    "BeginDate": start_date.strftime("%Y%m%d"),
                    "EndDate": end_date.strftime("%Y%m%d"),
                },
            )
            data = [row for row in page if not is_header_or_empty(row)]
            rows.extend(data)
            set_log_context(
                progress=f"{len(rows)}/{max_records}",
                target=f"action={action}",
            )
            LOGGER.debug(
                "历史数据分页完成：页=%d；本页=%d；累计=%d；上限=%d",
                page_number,
                len(data),
                len(rows),
                max_records,
            )
            if not data or len(data) < request_count:
                break
            start_position += len(data)
            if len(rows) >= max_records:
                probe = self._query_rows(
                    action,
                    {
                        "ReqlinkType": 1,
                        "StartPos": start_position,
                        "MaxCount": 1,
                        "BeginDate": start_date.strftime("%Y%m%d"),
                        "EndDate": end_date.strftime("%Y%m%d"),
                    },
                )
                if any(not is_header_or_empty(row) for row in probe):
                    raise BrokerProtocolError(
                        f"券商历史数据超过配置上限 {max_records} 条，拒绝写入不完整窗口"
                    )
                break
            time.sleep(self._config.broker_history_request_interval_seconds)
        return rows

    def _require_token(self) -> str:
        with self._token_lock:
            self._load_cached_token()
            if self._valid_token():
                return str(self._token)
        return self.login()

    def _request(self, params: dict[str, object]) -> dict[str, Any]:
        action = params.get("action")
        started_at = time.monotonic()
        LOGGER.debug("正在调用券商接口：action=%s", action)
        response = self._transport(
            self._config.broker_base_url,
            params,
            self._config.broker_timeout_seconds,
        )
        token = response.get("TOKEN")
        if token:
            refreshed_token = str(token)
            token_changed = self._cache_token(refreshed_token)
            if token_changed:
                LOGGER.info("券商响应 Token 已同步：action=%s", action)
        LOGGER.debug(
            "券商接口调用完成：action=%s；错误码=%s；耗时=%.2f秒",
            action,
            response.get("ERRORNO"),
            time.monotonic() - started_at,
        )
        return response

    def _valid_token(self) -> bool:
        with self._token_lock:
            if self._token and time.monotonic() < self._token_expire_at:
                return True
            self._token = None
            self._token_expire_at = 0.0
            return False

    def _load_cached_token(self) -> None:
        with self._token_lock:
            if not self._token_cache_path.exists():
                return
            try:
                cached = json.loads(self._token_cache_path.read_text(encoding="utf-8"))
                token = cached["token"]
                expires_at = float(cached["expiresAt"])
                scope = str(cached["scope"])
                updated_at = float(
                    cached.get(
                        "updatedAt",
                        expires_at - self._config.broker_token_expire_minutes * 60,
                    )
                )
            except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError):
                LOGGER.warning("券商 Token 本地缓存无效，已清理")
                self._remove_cached_token()
                return
            remaining = expires_at - time.time()
            if scope != self._token_cache_scope:
                return
            if not isinstance(token, str) or not token or remaining <= 0:
                LOGGER.info("券商 Token 本地缓存已过期，已清理")
                self._remove_cached_token()
                return
            if self._token and updated_at <= self._token_updated_at:
                return
            token_changed = token != self._token
            self._token = token
            self._token_expire_at = time.monotonic() + remaining
            self._token_updated_at = updated_at
            if token_changed:
                LOGGER.info(
                    "券商 Token 本地缓存已同步：剩余有效期约%d分钟",
                    max(1, int(remaining / 60)),
                )

    def _cache_token(self, token: str) -> bool:
        with self._token_lock:
            token_changed = token != self._token
            updated_at = time.time()
            expires_at = updated_at + self._config.broker_token_expire_minutes * 60
            self._token = token
            self._token_expire_at = (
                time.monotonic() + self._config.broker_token_expire_minutes * 60
            )
            self._token_updated_at = updated_at
            self._token_cache_path.parent.mkdir(parents=True, exist_ok=True)
            temporary_path = self._token_cache_path.with_name(
                f".{self._token_cache_path.name}.{os.getpid()}.{uuid4().hex}.tmp"
            )
            temporary_path.write_text(
                json.dumps(
                    {
                        "scope": self._token_cache_scope,
                        "token": token,
                        "expiresAt": expires_at,
                        "updatedAt": updated_at,
                    },
                    ensure_ascii=True,
                    separators=(",", ":"),
                ),
                encoding="utf-8",
            )
            if os.name != "nt":
                temporary_path.chmod(0o600)
            temporary_path.replace(self._token_cache_path)
            return token_changed

    def _remove_cached_token(self) -> None:
        with self._token_lock:
            try:
                self._token_cache_path.unlink(missing_ok=True)
            except OSError as error:
                LOGGER.warning("券商 Token 本地缓存清理失败：%s", error)


def _post_form(url: str, params: dict[str, object], timeout: float) -> dict[str, Any]:
    encoded = urlencode({key: str(value) for key, value in params.items()}).encode("utf-8")
    request = Request(url, data=encoded, method="POST")
    request.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urlopen(request, timeout=timeout) as response:
        body = response.read().decode("utf-8")
    result = json.loads(body)
    if not isinstance(result, dict):
        raise BrokerProtocolError("券商响应不是 JSON 对象")
    return result


def _order_error_message(response: dict[str, Any]) -> str:
    return _order_response_message(response)


def _order_response_message(response: dict[str, Any]) -> str:
    """保留券商原始中文消息字段，兼容不同接口大小写命名。"""
    for key in (
        "MSG",
        "msg",
        "ERRORMSG",
        "ERRORMESSAGE",
        "ERRORINFO",
        "ERROR_INFO_ALL",
        "errorMessage",
    ):
        value = response.get(key)
        if value not in (None, ""):
            return str(value).strip()
    return ""


def _parse_five_level_prices(
    response: dict[str, Any],
) -> tuple[
    tuple[Decimal, Decimal, Decimal, Decimal, Decimal],
    tuple[Decimal, Decimal, Decimal, Decimal, Decimal],
]:
    fields = str(response.get("BUYSELL") or "").split("|")
    bid_indexes = (1, 8, 10, 12, 14)
    ask_indexes = (3, 16, 18, 20, 22)
    if len(fields) <= max(*bid_indexes, *ask_indexes):
        raise BrokerProtocolError("券商未返回完整五档行情")
    bids = tuple(parse_decimal(fields[index]) for index in bid_indexes)
    asks = tuple(parse_decimal(fields[index]) for index in ask_indexes)
    if any(price is None or price <= 0 for price in bids + asks):
        raise BrokerProtocolError("券商未返回完整五档行情")
    return bids, asks
# AI_GENERATE_END----- ----------------------------------
