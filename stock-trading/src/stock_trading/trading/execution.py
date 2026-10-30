# AI_GENERATE_START -------
from __future__ import annotations

from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.client import BrokerRejectedError
from stock_trading.broker.models import (
    BrokerSyncSnapshot,
)
from stock_trading.state.store import ORDER_TERMINAL_STATUSES, StateStore


class BrokerExecutionClient(Protocol):
    """Broker contract used to submit orders and read broker facts."""

    def submit_limit_order(
        self,
        direction: str,
        stock_code: str,
        price: Decimal,
        quantity: int,
    ) -> str: ...

    def read_shadow_snapshot(self) -> BrokerSyncSnapshot: ...


class OrderExecutionOrchestrator:
    """Submit one idempotent planned order and persist the broker result."""

    def __init__(
        self,
        state_store: StateStore,
        broker: BrokerExecutionClient,
    ) -> None:
        self._state_store = state_store
        self._broker = broker

    def execute_stage(self, client_order_id: str) -> dict[str, object]:
        shadow_order = self._state_store.get_shadow_order(client_order_id)
        if shadow_order is None:
            raise ValueError(f"影子订单不存在: {client_order_id}")
        existing = self._state_store.get_order_intent(client_order_id)
        if existing is not None and existing["status"] in ORDER_TERMINAL_STATUSES:
            return self._result(existing)
        intent = self._state_store.prepare_real_order(
            client_order_id=client_order_id,
            instrument=str(shadow_order["instrument"]),
            side=str(shadow_order["side"]),
            quantity=int(shadow_order["quantity"]),
            price=Decimal(str(shadow_order["price"])),
        )
        if intent["status"] in ORDER_TERMINAL_STATUSES:
            return self._result(intent)
        if intent["status"] != "PREPARED":
            return self._result(intent)
        self._state_store.start_order_submission(client_order_id)
        return self._result(self._submit(shadow_order))

    def recover_pending_orders(self, business_date: date) -> dict[str, object]:
        snapshot = self._broker.read_shadow_snapshot()
        sync = self._state_store.sync_broker_snapshot(
            snapshot, business_date.isoformat()
        )
        intents = self._state_store.reconcile_order_intents(
            snapshot.orders, mark_unmatched_unknown=True
        )
        return {
            "businessDate": business_date.isoformat(),
            "sync": sync,
            "intents": intents,
        }

    def _submit(self, shadow_order: dict[str, object]) -> dict[str, object]:
        client_order_id = str(shadow_order["client_order_id"])
        try:
            broker_order_id = self._broker.submit_limit_order(
                direction=str(shadow_order["side"]),
                stock_code=str(shadow_order["instrument"])[-6:],
                price=Decimal(str(shadow_order["price"])),
                quantity=int(shadow_order["quantity"]),
            )
        except BrokerRejectedError as error:
            self._state_store.finish_real_order(
                client_order_id, "REJECTED", error_message=str(error)
            )
            return self._required_intent(client_order_id)
        except Exception as error:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message=str(error)
            )
            return self._required_intent(client_order_id)
        self._state_store.finish_real_order(
            client_order_id, "SUBMITTED", broker_order_id=broker_order_id
        )
        return self._required_intent(client_order_id)

    def _required_intent(self, client_order_id: str) -> dict[str, object]:
        intent = self._state_store.get_order_intent(client_order_id)
        if intent is None:
            raise ValueError(f"订单意图不存在: {client_order_id}")
        return intent

    @staticmethod
    def _result(intent: dict[str, object]) -> dict[str, object]:
        return {
            "clientOrderId": intent["client_order_id"],
            "brokerOrderId": intent["broker_order_id"],
            "status": intent["status"],
            "errorMessage": intent["error_message"],
        }
# AI_GENERATE_END -------
