# AI_GENERATE_START --
from __future__ import annotations

import time
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.client import BrokerRejectedError, BrokerSafetyError
from stock_trading.broker.models import BrokerOrder, BrokerSyncSnapshot
from stock_trading.config import AppConfig
from stock_trading.state.store import ORDER_TERMINAL_STATUSES, StateStore


class BrokerExecutionClient(Protocol):
    """Broker contract whose write methods must invoke the configured SQLite guards."""

    def submit_limit_order(
        self,
        client_order_id: str,
        direction: str,
        stock_code: str,
        price: Decimal,
        quantity: int,
    ) -> str: ...

    def cancel_order(self, client_order_id: str, broker_order_id: str) -> bool: ...

    def get_today_orders(self) -> tuple[BrokerOrder, ...]: ...

    def read_shadow_snapshot(self) -> BrokerSyncSnapshot: ...


class OrderExecutionError(RuntimeError):
    """Order execution stopped because broker facts are not safely terminal."""


class OrderExecutionOrchestrator:
    """Submit one planned price stage and stop unless the broker confirms its terminal state."""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerExecutionClient,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def execute_stage(self, client_order_id: str) -> dict[str, object]:
        shadow_order = self._state_store.get_shadow_order(client_order_id)
        if shadow_order is None:
            raise ValueError(f"影子订单不存在: {client_order_id}")
        intent = self._state_store.prepare_real_order(
            client_order_id=client_order_id,
            instrument=str(shadow_order["instrument"]),
            side=str(shadow_order["side"]),
            quantity=int(shadow_order["quantity"]),
            price=Decimal(str(shadow_order["price"])),
        )
        if intent["status"] in ORDER_TERMINAL_STATUSES:
            return self._result(intent)
        if intent["status"] in {"SUBMITTING", "CANCEL_SUBMITTING", "UNKNOWN"}:
            self.recover_pending_orders(
                date.fromisoformat(str(shadow_order["business_date"]))
            )
            intent = self._required_intent(client_order_id)
        if intent["status"] == "PREPARED":
            intent = self._submit(shadow_order)
        if intent["status"] in ORDER_TERMINAL_STATUSES:
            return self._result(intent)
        if intent["status"] == "UNKNOWN":
            raise OrderExecutionError(f"订单状态未知，停止新增风险: {client_order_id}")

        intent = self._await_terminal(client_order_id)
        self.recover_pending_orders(
            date.fromisoformat(str(shadow_order["business_date"]))
        )
        return self._result(intent)

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
                client_order_id=client_order_id,
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
        except BrokerSafetyError as error:
            current = self._required_intent(client_order_id)
            if current["status"] != "PREPARED":
                self._state_store.finish_real_order(
                    client_order_id, "UNKNOWN", error_message=str(error)
                )
            raise
        except BaseException as error:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message=str(error)
            )
            raise OrderExecutionError(
                f"委托结果不确定，停止新增风险: {client_order_id}"
            ) from error
        self._state_store.finish_real_order(
            client_order_id, "SUBMITTED", broker_order_id=broker_order_id
        )
        return self._required_intent(client_order_id)

    def _await_terminal(self, client_order_id: str) -> dict[str, object]:
        intent = self._poll(client_order_id)
        if intent["status"] in ORDER_TERMINAL_STATUSES:
            return intent
        if intent["status"] == "UNKNOWN":
            raise OrderExecutionError(f"券商返回未知状态: {client_order_id}")
        if intent["status"] == "CANCEL_SUBMITTING":
            return self._poll_cancel_terminal(client_order_id)
        broker_order_id = intent.get("broker_order_id")
        if not broker_order_id:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message="活动订单缺少券商订单号"
            )
            raise OrderExecutionError(f"活动订单缺少券商订单号: {client_order_id}")
        try:
            accepted = self._broker.cancel_order(client_order_id, str(broker_order_id))
        except BaseException as error:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message=str(error)
            )
            raise OrderExecutionError(
                f"撤单结果不确定，停止新增风险: {client_order_id}"
            ) from error
        if not accepted:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message="券商未接受撤单请求"
            )
            raise OrderExecutionError(f"券商未接受撤单请求: {client_order_id}")
        return self._poll_cancel_terminal(client_order_id)

    def _poll(self, client_order_id: str) -> dict[str, object]:
        for attempt in range(self._config.trading_order_poll_attempts):
            self._state_store.reconcile_order_intents(self._broker.get_today_orders())
            intent = self._required_intent(client_order_id)
            if (
                intent["status"] in ORDER_TERMINAL_STATUSES
                or intent["status"] == "UNKNOWN"
            ):
                return intent
            if attempt + 1 < self._config.trading_order_poll_attempts:
                time.sleep(self._config.trading_order_poll_interval_seconds)
        return self._required_intent(client_order_id)

    def _poll_cancel_terminal(self, client_order_id: str) -> dict[str, object]:
        intent = self._poll(client_order_id)
        if intent["status"] in ORDER_TERMINAL_STATUSES:
            return intent
        self._state_store.finish_real_order(
            client_order_id,
            "UNKNOWN",
            error_message="撤单后未确认券商终态",
        )
        raise OrderExecutionError(f"撤单后未确认券商终态: {client_order_id}")

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
# AI_GENERATE_END --
