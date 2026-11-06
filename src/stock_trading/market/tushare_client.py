# AI_GENERATE_START -----------
from __future__ import annotations

import json
import logging
import time
from datetime import date, datetime
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import ProxyHandler, Request, build_opener

from stock_trading.market.models import (
    CumulativeAdjustmentFactor,
    DailyBasic,
    DailyBar,
    StockBasic,
)

API_URL = "https://api.tushare.pro"
LOGGER = logging.getLogger(__name__)
RATE_LIMIT_WAIT_SECONDS = 61


class TushareError(RuntimeError):
    """TuShare HTTP 或业务接口调用失败。"""


class TushareClient:
    def __init__(self, token: str | None, timeout_seconds: int = 30) -> None:
        if not token:
            raise ValueError("缺少 TUSHARE_TOKEN；basic、daily 和 factor 采集无法执行")
        self._token = token
        self._timeout_seconds = timeout_seconds
        self._opener = build_opener(ProxyHandler({}))

    def fetch_stock_basics(self, snapshot_date: date) -> list[StockBasic]:
        rows = self._request(
            "stock_basic",
            {"exchange": "", "list_status": "L"},
            "ts_code,symbol,name,market,list_status,list_date,delist_date",
        )
        return [
            StockBasic.from_tushare(row, snapshot_date)
            for row in rows
            if str(row.get("ts_code") or "").upper().endswith((".SH", ".SZ"))
        ]

    def fetch_trade_dates(self, start_date: date, end_date: date) -> list[date]:
        rows = self._request(
            "trade_cal",
            {
                "exchange": "SSE",
                "start_date": start_date.strftime("%Y%m%d"),
                "end_date": end_date.strftime("%Y%m%d"),
                "is_open": "1",
            },
            "cal_date,is_open",
        )
        return sorted(
            datetime.strptime(str(row["cal_date"]), "%Y%m%d").date()
            for row in rows
            if str(row.get("is_open")) == "1"
        )

    def fetch_daily(
        self, trade_date: date, stock_names: dict[str, str]
    ) -> list[DailyBar]:
        rows = self._request(
            "daily",
            {"trade_date": trade_date.strftime("%Y%m%d")},
            "ts_code,trade_date,open,high,low,close,pre_close,vol,amount",
        )
        return [
            DailyBar.from_tushare(
                row,
                "ST" in stock_names.get(_normalized_code(row.get("ts_code")), "").upper(),
            )
            for row in rows
        ]

    def fetch_daily_basics(self, trade_date: date) -> list[DailyBasic]:
        """读取换手率、量比和市值等日级横截面指标。"""
        rows = self._request(
            "daily_basic",
            {"trade_date": trade_date.strftime("%Y%m%d")},
            "ts_code,trade_date,turnover_rate,volume_ratio,total_mv,circ_mv",
        )
        return [DailyBasic.from_tushare(row) for row in rows]

    def fetch_factors(self, trade_date: date) -> list[CumulativeAdjustmentFactor]:
        rows = self._request(
            "adj_factor",
            {"trade_date": trade_date.strftime("%Y%m%d")},
            "ts_code,trade_date,adj_factor",
        )
        return [CumulativeAdjustmentFactor.from_tushare(row) for row in rows]

    def _request(
        self, api_name: str, params: dict[str, str], fields: str
    ) -> list[dict[str, object]]:
        body = json.dumps(
            {
                "api_name": api_name,
                "token": self._token,
                "params": params,
                "fields": fields,
            }
        ).encode("utf-8")
        for attempt in range(1, 4):
            request = Request(
                API_URL,
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            try:
                with self._opener.open(
                    request, timeout=self._timeout_seconds
                ) as response:
                    payload: dict[str, Any] = json.loads(response.read().decode("utf-8"))
            except (HTTPError, URLError, TimeoutError, json.JSONDecodeError) as error:
                if attempt < 3:
                    LOGGER.warning(
                        "TuShare 请求失败，等待后重试：接口=%s；尝试=%d/3；原因=%s",
                        api_name,
                        attempt,
                        error,
                    )
                    time.sleep(attempt * 2)
                    continue
                raise TushareError(f"TuShare {api_name} 请求失败：{error}") from error
            if payload.get("code") == 0:
                data = payload.get("data") or {}
                names = data.get("fields") or []
                return [
                    dict(zip(names, values, strict=True))
                    for values in data.get("items") or []
                ]
            message = str(payload.get("msg") or "")
            if payload.get("code") == 40203 and "分钟" in message and attempt < 3:
                LOGGER.warning(
                    "TuShare 接口频率受限，等待后重试：接口=%s；尝试=%d/3",
                    api_name,
                    attempt,
                )
                time.sleep(RATE_LIMIT_WAIT_SECONDS)
                continue
            raise TushareError(
                f"TuShare {api_name} 返回错误：{payload.get('code')} "
                f"{message}"
            )
        raise RuntimeError("TuShare 请求重试意外结束")


def _normalized_code(value: object) -> str:
    symbol, exchange = str(value or "").split(".", maxsplit=1)
    return f"{exchange.lower()}.{symbol}"
# AI_GENERATE_END -----------
