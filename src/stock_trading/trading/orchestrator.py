# AI_GENERATE_START -----------
from __future__ import annotations

import logging
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.models import BrokerPosition, FiveLevelQuote
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.trading.permissions import permission_scope

LOGGER = logging.getLogger(__name__)


class BrokerReader(Protocol):
    def get_positions(self) -> tuple[BrokerPosition, ...]:
        """用途：读取当前持仓事实；输入：无；输出：券商持仓元组。"""
        ...

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote:
        """用途：读取证券五档行情；输入：六位证券代码；输出：五档行情。"""
        ...


class LiveTradingOrchestrator:
    """只选择交易目标，不冻结价格、数量或复用旧行情。"""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerReader,
    ) -> None:
        if config.trading_max_price < config.trading_min_price:
            raise ValueError("交易价格上限不能小于下限")
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def select_morning_sell_targets(self, business_date: date) -> dict[str, object]:
        """按券商当前可卖持仓选择卖出目标，实际数量和价格由每次委托前重算。"""
        targets: list[dict[str, object]] = []
        ignored: list[dict[str, object]] = []
        positions = self._broker.get_positions()
        for position in positions:
            instrument = _to_qlib_instrument(position.stock_code, position.market)
            if position.available_quantity <= 0:
                ignored.append(
                    {"instrument": instrument, "reason": "NO_AVAILABLE_QUANTITY"}
                )
                continue
            targets.append(
                {
                    "instrument": instrument,
                    "side": "SELL",
                    "candidateRank": None,
                    "modelVersionId": None,
                    "predictionDate": None,
                }
            )
        LOGGER.info(
            "上午卖出目标选择完成：目标数=%d；忽略数=%d",
            len(targets),
            len(ignored),
        )
        return {
            "businessDate": business_date.isoformat(),
            "mode": "MORNING_SELL",
            "targets": targets,
            "ignored": ignored,
        }

    def select_afternoon_buy_target(self, business_date: date) -> dict[str, object]:
        """选择一个模型候选作为买入目标，不保存或复用五档价格和委托数量。"""
        candidates = self._state_store.latest_prediction_candidates(
            business_date.isoformat(), 5
        )
        if not candidates:
            raise ValueError("目标交易日前没有可用 Top 5 预测")
        prediction_date = date.fromisoformat(str(candidates[0]["prediction_date"]))
        prediction_age = (business_date - prediction_date).days
        if not 1 <= prediction_age <= self._config.trading_max_prediction_age_days:
            raise ValueError(
                f"预测日期不满足新鲜度要求: predictionDate={prediction_date}, "
                f"businessDate={business_date}"
            )

        existing_target = self._existing_buy_target(business_date)
        if existing_target is not None:
            candidate = next(
                (
                    item
                    for item in candidates
                    if str(item["instrument"]).upper() == existing_target
                ),
                None,
            )
            LOGGER.info("恢复当日实时买入目标：股票=%s", existing_target)
            return self._buy_result(
                business_date,
                candidates,
                self._target(existing_target, candidate),
                [],
            )

        positions = tuple(
            position for position in self._broker.get_positions() if position.quantity > 0
        )
        if positions:
            LOGGER.info("下午买入目标选择停止：账户已有持仓")
            return self._buy_result(
                business_date,
                candidates,
                None,
                [{"reason": "POSITION_EXISTS"}],
            )

        permission_status = {
            str(item["scope"]): str(item["status"])
            for item in self._state_store.broker_permissions()
        }
        rejected: list[dict[str, object]] = []
        for candidate in candidates:
            instrument = str(candidate["instrument"]).upper()
            rank = int(candidate["rank"])
            if self._config.trading_allowed_prefixes and not instrument.startswith(
                self._config.trading_allowed_prefixes
            ):
                rejected.append(
                    {"instrument": instrument, "rank": rank, "reason": "NO_TRADING_PERMISSION"}
                )
                continue
            scope = permission_scope(instrument)
            if scope and permission_status.get(scope) == "DENIED":
                rejected.append(
                    {
                        "instrument": instrument,
                        "rank": rank,
                        "reason": "BROKER_PERMISSION_DENIED",
                    }
                )
                continue
            quote = self._broker.get_five_level_quote(_to_broker_code(instrument))
            reference_price = quote.price or quote.asks[0]
            if not Decimal(str(self._config.trading_min_price)) <= reference_price <= Decimal(
                str(self._config.trading_max_price)
            ):
                rejected.append(
                    {"instrument": instrument, "rank": rank, "reason": "PRICE_OUT_OF_RANGE"}
                )
                continue
            target = self._target(instrument, candidate)
            LOGGER.info("下午买入目标选择完成：股票=%s；排名=%d", instrument, rank)
            return self._buy_result(business_date, candidates, target, rejected)

        LOGGER.info("下午买入目标选择完成：没有可交易候选")
        return self._buy_result(business_date, candidates, None, rejected)

    def _existing_buy_target(self, business_date: date) -> str | None:
        prefix = f"LIVE:AFTERNOON_BUY:{business_date.isoformat()}:"
        matching = [
            row
            for row in self._state_store.list_order_intents()
            if str(row["client_order_id"]).startswith(prefix)
        ]
        if not matching or matching[-1]["status"] == "REJECTED":
            return None
        return str(matching[-1]["instrument"])

    @staticmethod
    def _target(
        instrument: str, candidate: dict[str, object] | None
    ) -> dict[str, object]:
        return {
            "instrument": instrument,
            "side": "BUY",
            "candidateRank": int(candidate["rank"]) if candidate else None,
            "modelVersionId": int(candidate["model_version_id"]) if candidate else None,
            "predictionDate": str(candidate["prediction_date"]) if candidate else None,
        }

    @staticmethod
    def _buy_result(
        business_date: date,
        candidates: list[dict[str, object]],
        target: dict[str, object] | None,
        rejected: list[dict[str, object]],
    ) -> dict[str, object]:
        return {
            "businessDate": business_date.isoformat(),
            "predictionDate": candidates[0]["prediction_date"],
            "modelStatus": candidates[0]["model_status"],
            "mode": "AFTERNOON_BUY",
            "targets": [target] if target else [],
            "rejected": rejected,
        }


def _to_broker_code(instrument: str) -> str:
    if len(instrument) != 8 or instrument[:2] not in {"SH", "SZ"}:
        raise ValueError(f"不支持的 Qlib 股票代码: {instrument}")
    return instrument[2:]


def _to_qlib_instrument(stock_code: str, market: str | None) -> str:
    code = stock_code.strip()
    if len(code) != 6 or not code.isdigit():
        raise ValueError(f"不支持的券商股票代码: {stock_code}")
    normalized_market = (market or "").upper()
    if normalized_market.startswith("SH"):
        exchange = "SH"
    elif normalized_market.startswith("SZ"):
        exchange = "SZ"
    else:
        exchange = "SH" if code.startswith(("6", "68")) else "SZ"
    return f"{exchange}{code}"
# AI_GENERATE_END -----------
