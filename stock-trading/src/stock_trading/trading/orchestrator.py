# AI_GENERATE_START ----
from __future__ import annotations

from datetime import date
from decimal import Decimal, ROUND_FLOOR
from typing import Protocol

from stock_trading.broker.models import (
    BrokerAccount,
    BrokerPosition,
    FiveLevelQuote,
)
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore

LOT_SIZE = 100


class BrokerReader(Protocol):
    def get_account(self) -> BrokerAccount: ...

    def get_positions(self) -> tuple[BrokerPosition, ...]: ...

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote: ...


class ShadowTradingOrchestrator:
    """Create idempotent morning-sell and afternoon-buy plans without broker writes."""

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

    def plan_morning_sell(self, business_date: date) -> dict[str, object]:
        orders: list[dict[str, object]] = []
        ignored: list[dict[str, object]] = []
        for position in self._broker.get_positions():
            if position.available_quantity <= 0:
                ignored.append(
                    {"instrument": position.stock_code, "reason": "NO_AVAILABLE_QUANTITY"}
                )
                continue
            instrument = _to_qlib_instrument(position.stock_code, position.market)
            quote = self._broker.get_five_level_quote(position.stock_code)
            orders.append(
                {
                    "clientOrderId": (
                        f"SHADOW:MORNING_SELL:{business_date.isoformat()}:{instrument}:STAGE1"
                    ),
                    "instrument": instrument,
                    "side": "SELL",
                    "quantity": position.available_quantity,
                    "price": quote.asks[2],
                    "candidateRank": None,
                }
            )
        state = self._state_store.save_shadow_plan(
            "SHADOW_MORNING_SELL", business_date.isoformat(), orders
        )
        return {
            "businessDate": business_date.isoformat(),
            "mode": "MORNING_SELL",
            "priceLevel": "ASK3",
            "orders": [_json_order(order) for order in orders],
            "ignored": ignored,
            "state": state,
        }

    def plan_afternoon_buy(self, business_date: date) -> dict[str, object]:
        positions = tuple(
            position for position in self._broker.get_positions() if position.quantity > 0
        )
        if positions:
            state = self._state_store.save_shadow_plan(
                "SHADOW_AFTERNOON_BUY", business_date.isoformat(), []
            )
            return {
                "businessDate": business_date.isoformat(),
                "mode": "AFTERNOON_BUY",
                "orders": [],
                "rejected": [{"reason": "POSITION_EXISTS"}],
                "state": state,
            }

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
        account = self._broker.get_account()

        rejected: list[dict[str, object]] = []
        orders: list[dict[str, object]] = []
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
            stock_code = _to_broker_code(instrument)
            quote = self._broker.get_five_level_quote(stock_code)
            reference_price = quote.price or quote.asks[0]
            if not Decimal(str(self._config.trading_min_price)) <= reference_price <= Decimal(
                str(self._config.trading_max_price)
            ):
                rejected.append(
                    {"instrument": instrument, "rank": rank, "reason": "PRICE_OUT_OF_RANGE"}
                )
                continue
            order_price = quote.bids[2]
            quantity = self._affordable_quantity(account.available_cash, order_price)
            if quantity <= 0:
                rejected.append(
                    {"instrument": instrument, "rank": rank, "reason": "INSUFFICIENT_CASH"}
                )
                continue
            orders.append(
                {
                    "clientOrderId": (
                        f"SHADOW:AFTERNOON_BUY:{business_date.isoformat()}:{instrument}:STAGE1"
                    ),
                    "instrument": instrument,
                    "side": "BUY",
                    "quantity": quantity,
                    "price": order_price,
                    "candidateRank": rank,
                }
            )
            break

        state = self._state_store.save_shadow_plan(
            "SHADOW_AFTERNOON_BUY", business_date.isoformat(), orders
        )
        return {
            "businessDate": business_date.isoformat(),
            "predictionDate": candidates[0]["prediction_date"],
            "modelStatus": candidates[0]["model_status"],
            "mode": "AFTERNOON_BUY",
            "priceLevel": "BID3",
            "orders": [_json_order(order) for order in orders],
            "rejected": rejected,
            "state": state,
        }

    def _affordable_quantity(self, cash: Decimal, price: Decimal) -> int:
        if cash <= 0 or price <= 0:
            return 0
        lots = (cash / (price * LOT_SIZE)).to_integral_value(rounding=ROUND_FLOOR)
        quantity = int(lots) * LOT_SIZE
        while quantity > 0:
            notional = price * quantity
            commission = max(
                Decimal(str(self._config.trading_min_commission)),
                notional * Decimal(str(self._config.trading_commission_rate)),
            )
            transfer_fee = notional * Decimal(
                str(self._config.trading_transfer_fee_rate)
            )
            if notional + commission + transfer_fee <= cash:
                return quantity
            quantity -= LOT_SIZE
        return 0


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


def _json_order(order: dict[str, object]) -> dict[str, object]:
    return {
        key: float(value) if isinstance(value, Decimal) else value
        for key, value in order.items()
    }
# AI_GENERATE_END ----
