# AI_GENERATE_START -------
from __future__ import annotations

import base64
import binascii
import json
import time
from collections.abc import Callable
from datetime import date
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
WriteGuard = Callable[[str], None]


class BrokerProtocolError(RuntimeError):
    """The broker returned an incomplete or invalid protocol response."""


class BrokerSafetyError(RuntimeError):
    """A real broker write was rejected by a local safety boundary."""


class ZXBrokerClient:
    """Minimal client for the broker fields already verified in the Java adapter."""

    def __init__(
        self,
        config: AppConfig,
        transport: Transport | None = None,
        captcha_solver: CaptchaSolver | None = None,
        write_guard: WriteGuard | None = None,
        cancel_guard: WriteGuard | None = None,
    ) -> None:
        self._config = config
        self._transport = transport or _post_form
        self._captcha_solver = captcha_solver
        self._write_guard = write_guard or _reject_all_writes
        self._cancel_guard = cancel_guard or _reject_all_writes
        self._token = config.broker_token
        self._token_expire_at = (
            time.monotonic() + config.broker_token_expire_minutes * 60
            if config.broker_token
            else 0.0
        )

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
            return self._token
        if self._captcha_solver is None:
            raise BrokerSafetyError("未配置经过验收的验证码识别器，禁止自动登录")
        if not self._config.broker_account or not self._config.broker_encoded_password:
            raise BrokerSafetyError("未配置券商资金账号或加密密码")
        captcha = self._request(self.build_params({"action": ACTION_CAPTCHA}))
        data_uri = captcha.get("MESSAGE")
        check_token = captcha.get("CHECKTOKEN")
        if not data_uri or not check_token:
            raise BrokerProtocolError("验证码接口未返回图片或 CheckToken")
        try:
            image = base64.b64decode(str(data_uri).split(",", 1)[-1], validate=True)
        except (ValueError, binascii.Error) as error:
            raise BrokerProtocolError("验证码图片无法解码") from error
        check_code = self._captcha_solver(image)
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
        account = parse_account_rows(self._query_rows(ACTION_GET_ACCOUNT, {"ReqlinkType": 1}))
        if account is None:
            raise BrokerProtocolError("账户资金响应为空或字段不足")
        return account

    def get_positions(self):
        return parse_position_rows(self._query_rows(ACTION_GET_POSITIONS, {"ReqlinkType": 1}))

    def get_today_orders(self):
        return parse_order_rows(
            self._query_rows(
                ACTION_QUERY_TODAY_ORDERS,
                {
                    "ReqlinkType": 1,
                    "StartPos": 0,
                    "MaxCount": self._config.broker_max_order_count,
                },
            )
        )

    def get_today_fills(self):
        return parse_fill_rows(
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

    def get_history_orders(self, start_date: date, end_date: date):
        return parse_order_rows(self._query_history_rows(ACTION_QUERY_HISTORY_ORDERS, start_date, end_date))

    def get_history_fills(self, start_date: date, end_date: date):
        return parse_fill_rows(
            self._query_history_rows(ACTION_QUERY_HISTORY_FILLS, start_date, end_date),
            today_format=False,
        )

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
        return FiveLevelQuote(
            stock_code=stock_code,
            price=parse_decimal(response.get("PRICE")),
            bids=bids,
            asks=asks,
        )

    def read_shadow_snapshot(self) -> BrokerSyncSnapshot:
        return BrokerSyncSnapshot(
            account=self.get_account(),
            positions=self.get_positions(),
            orders=self.get_today_orders(),
            fills=self.get_today_fills(),
        )

    def submit_limit_order(
        self,
        client_order_id: str,
        direction: str,
        stock_code: str,
        price: Decimal,
        quantity: int,
    ) -> str:
        self._write_guard(client_order_id)
        direction_code = {"BUY": "B", "SELL": "S"}.get(direction.upper())
        if direction_code is None:
            raise ValueError("不支持的买卖方向")
        if price <= 0:
            raise ValueError("委托价格必须大于零")
        if quantity <= 0:
            raise ValueError("委托数量必须大于零")
        if direction_code == "B" and quantity % 100 != 0:
            raise ValueError("A股买入数量必须是 100 股整数倍")
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_SUBMIT_ORDER,
                    "PriceType": 0,
                    "Direction": direction_code,
                    "StockCode": stock_code,
                    "Price": price,
                    "Volume": quantity,
                    "token": self._require_token(),
                }
            )
        )
        order_id = response.get("ANSWERNO")
        if not order_id:
            raise BrokerProtocolError(f"券商拒绝委托，错误码={response.get('ERRORNO')}")
        return str(order_id)

    def cancel_order(self, client_order_id: str, broker_order_id: str) -> bool:
        self._cancel_guard(client_order_id)
        response = self._request(
            self.build_params(
                {
                    "action": ACTION_CANCEL_ORDER,
                    "ContactID": broker_order_id,
                    "token": self._require_token(),
                }
            )
        )
        return str(response.get("ERRORNO")) == "0"

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
        while start_position < max_records:
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
        response = self._transport(REQUEST_URL, params, self._config.broker_timeout_seconds)
        token = response.get("TOKEN")
        if token:
            self._token = str(token)
            self._token_expire_at = (
                time.monotonic() + self._config.broker_token_expire_minutes * 60
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


def _reject_all_writes(client_order_id: str) -> None:
    raise BrokerSafetyError(f"真实券商写入默认关闭: {client_order_id}")
# AI_GENERATE_END -------
