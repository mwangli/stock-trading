# AI_GENERATE_START --------------------
from __future__ import annotations

import base64
import binascii
import json
import logging
import threading
import time
from collections.abc import Callable
from datetime import date, datetime, timedelta
from decimal import Decimal
from typing import Any
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from stock_trading.broker.models import BrokerSyncSnapshot, FiveLevelQuote
from stock_trading.broker.parser import (
    parse_account_rows,
    parse_decimal,
    parse_fill_rows,
    is_header_or_empty,
    parse_order_rows,
    parse_position_rows,
)
from stock_trading.config import AppConfig

REQUEST_URL = "https://weixin.citicsinfo.com/reqxml"

ACTION_GET_PRICE = 33
ACTION_LOGIN = 100
ACTION_SUBMIT_ORDER = 110
ACTION_CANCEL_ORDER = 111
ACTION_QUERY_TODAY_ORDERS = 113
ACTION_QUERY_TODAY_FILLS = 114
ACTION_QUERY_HISTORY_FILLS = 115
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
        self._token = config.broker_token
        self._token_expire_at = (
            time.monotonic() + config.broker_token_expire_minutes * 60
            if config.broker_token
            else 0.0
        )
        self._login_lock = threading.Lock()
        self._login_blocked_until = 0.0
        self._shareholder_accounts: dict[str, str] | None = None

    def build_params(self, values: dict[str, object] | None = None) -> dict[str, object]:
        params = dict(values or {})
        params.update(
            {
                "cfrom": "H5",
                "tfrom": "PC",
                "newindex": "1",
                "MobileCode": self._config.broker_mobile_code,
                "intacttoserver": self._config.broker_intact_to_server,
                "reqno": int(time.time() * 1000),
            }
        )
        return params

    def login(self) -> str:
        self._require_enabled()
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
            LOGGER.info(
                "开始券商自动登录：最大验证码尝试=%d；Token缓存=%d分钟",
                self._config.broker_captcha_max_retries,
                self._config.broker_token_expire_minutes,
            )
            for attempt in range(1, self._config.broker_captcha_max_retries + 1):
                try:
                    LOGGER.info("券商登录中：验证码尝试=%d/%d", attempt, self._config.broker_captcha_max_retries)
                    token = self._login_once()
                    self._login_blocked_until = 0.0
                    LOGGER.info("券商登录成功：Token已缓存%d分钟", self._config.broker_token_expire_minutes)
                    return token
                except Exception as error:
                    last_error = f"{type(error).__name__}: {error}"
                    LOGGER.warning("券商登录尝试失败：进度=%d/%d；原因=%s", attempt, self._config.broker_captcha_max_retries, error)
            self._login_blocked_until = (
                time.monotonic()
                + self._config.broker_login_failure_cooldown_seconds
            )
            raise BrokerSafetyError(
                f"券商登录失败并进入冷却期，最后错误={last_error}"
            )

    def _login_once(self) -> str:
        LOGGER.info("券商登录阶段 1/3：获取验证码")
        captcha = self._request(self.build_params({"action": ACTION_CAPTCHA}))
        data_uri = captcha.get("MESSAGE")
        check_token = captcha.get("CHECKTOKEN")
        if not data_uri or not check_token:
            raise BrokerProtocolError("验证码接口未返回图片或 CheckToken")
        try:
            image = base64.b64decode(str(data_uri).split(",", 1)[-1], validate=True)
        except (ValueError, binascii.Error) as error:
            raise BrokerProtocolError("验证码图片无法解码") from error
        LOGGER.info("券商登录阶段 2/3：识别验证码；图片字节数=%d", len(image))
        check_code = self._captcha_solver(image)
        LOGGER.info("券商登录阶段 3/3：提交登录请求")
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

    def get_today_fills(self):
        LOGGER.info("正在读取券商当日成交")
        fills = parse_fill_rows(
            self._query_rows(
                ACTION_QUERY_TODAY_FILLS,
                {
                    "ReqlinkType": 1,
                    "StartPos": 0,
                    "MaxCount": self._config.broker_max_order_count,
                },
            ),
            today_format=True,
        )
        LOGGER.info("券商当日成交读取完成：成交数量=%d", len(fills))
        return fills

    def get_history_orders(self, start_date: date, end_date: date):
        LOGGER.info("正在读取券商历史委托：日期=%s 至 %s", start_date, end_date)
        orders = parse_order_rows(self._query_history_rows(ACTION_QUERY_HISTORY_ORDERS, start_date, end_date))
        LOGGER.info("券商历史委托读取完成：数量=%d", len(orders))
        return orders

    def get_history_fills(self, start_date: date, end_date: date):
        LOGGER.info("正在读取券商历史成交：日期=%s 至 %s", start_date, end_date)
        fills = parse_fill_rows(
            self._query_history_rows(ACTION_QUERY_HISTORY_FILLS, start_date, end_date),
            today_format=False,
        )
        LOGGER.info("券商历史成交读取完成：数量=%d", len(fills))
        return fills

    def get_realtime_price(self, stock_code: str) -> Decimal:
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
        price = parse_decimal(response.get("PRICE"))
        if price is None or price <= 0:
            raise BrokerProtocolError(f"未获得有效实时价格: {stock_code}")
        return price

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote:
        LOGGER.info("正在读取五档行情：股票=%s", stock_code)
        response = self._request(
            self.build_params(
                {
                    "stockcode": stock_code,
                    "action": ACTION_GET_PRICE,
                    "ReqlinkType": 1,
                    "Level": 5,
                    "UseBPrice": 1,
                }
            )
        )
        bids = tuple(_required_price(response, f"BUY{level}", f"BID{level}") for level in range(1, 6))
        asks = tuple(_required_price(response, f"SELL{level}", f"ASK{level}") for level in range(1, 6))
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

    def read_shadow_snapshot(self) -> BrokerSyncSnapshot:
        LOGGER.info("券商快照阶段 1/4：读取账户")
        account = self.get_account()
        LOGGER.info("券商快照阶段 2/4：读取持仓")
        positions = self.get_positions()
        LOGGER.info("券商快照阶段 3/4：读取委托")
        orders = self.get_today_orders()
        LOGGER.info("券商快照阶段 4/4：读取成交")
        fills = self.get_today_fills()
        LOGGER.info(
            "券商快照读取完成：持仓=%d；委托=%d；成交=%d",
            len(positions),
            len(orders),
            len(fills),
        )
        return BrokerSyncSnapshot(account=account, positions=positions, orders=orders, fills=fills)

    def read_history_snapshot(
        self, start_date: date, end_date: date
    ) -> BrokerSyncSnapshot:
        """读取账户、持仓以及指定区间的历史委托和成交快照。"""
        LOGGER.info("券商历史快照阶段 1/4：读取账户")
        account = self.get_account()
        LOGGER.info("券商历史快照阶段 2/4：读取持仓")
        positions = self.get_positions()
        LOGGER.info("券商历史快照阶段 3/4：读取历史委托")
        orders = self.get_history_orders(start_date, end_date)
        LOGGER.info("券商历史快照阶段 4/4：读取历史成交")
        fills = self.get_history_fills(start_date, end_date)
        return BrokerSyncSnapshot(
            account=account,
            positions=positions,
            orders=orders,
            fills=fills,
        )

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
            error_message = (
                response.get("ERRORMESSAGE")
                or response.get("ERRORINFO")
                or response.get("ERROR_INFO_ALL")
            )
            raise BrokerRejectedError(
                f"券商拒绝委托，错误码={response.get('ERRORNO')}，原因={error_message}"
            )
        LOGGER.info("券商已接受委托：股票=%s；方向=%s；券商委托号=%s", stock_code, direction.upper(), order_id)
        return str(order_id)

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
        window_end = date.today()
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
        while start_position < max_records:
            page_number += 1
            page = self._query_rows(
                action,
                {
                    "ReqlinkType": 1,
                    "StartPos": start_position,
                    "MaxCount": page_size,
                    "BeginDate": start_date.strftime("%Y%m%d"),
                    "EndDate": end_date.strftime("%Y%m%d"),
                },
            )
            data = [row for row in page if not is_header_or_empty(row)]
            rows.extend(data)
            LOGGER.info(
                "券商历史数据分页进度：action=%d；页=%d；本页=%d；累计=%d；上限=%d",
                action,
                page_number,
                len(data),
                len(rows),
                max_records,
            )
            if not data or len(data) < page_size:
                break
            start_position += len(data)
            time.sleep(self._config.broker_history_request_interval_seconds)
        return rows[:max_records]

    def _require_token(self) -> str:
        self._require_enabled()
        return self._token if self._valid_token() else self.login()

    def _require_enabled(self) -> None:
        if not self._config.broker_enabled:
            raise BrokerSafetyError("ZX证券接入未启用")

    def _request(self, params: dict[str, object]) -> dict[str, Any]:
        action = params.get("action")
        started_at = time.monotonic()
        LOGGER.info("正在调用券商接口：action=%s", action)
        response = self._transport(REQUEST_URL, params, self._config.broker_timeout_seconds)
        token = response.get("TOKEN")
        if token:
            self._token = str(token)
            self._token_expire_at = (
                time.monotonic() + self._config.broker_token_expire_minutes * 60
            )
        LOGGER.info(
            "券商接口调用完成：action=%s；错误码=%s；耗时=%.2f秒",
            action,
            response.get("ERRORNO"),
            time.monotonic() - started_at,
        )
        return response

    def _valid_token(self) -> bool:
        if self._token and time.monotonic() < self._token_expire_at:
            return True
        self._token = None
        self._token_expire_at = 0.0
        return False


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


def _required_price(response: dict[str, Any], primary: str, alternate: str) -> Decimal:
    value = parse_decimal(response.get(primary)) or parse_decimal(response.get(alternate))
    if value is None or value <= 0:
        raise BrokerProtocolError("券商未返回完整五档行情")
    return value
# AI_GENERATE_END --------------------
