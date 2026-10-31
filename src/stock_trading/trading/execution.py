# AI_GENERATE_START ----------------
from __future__ import annotations

import logging
from datetime import UTC, date, datetime
from decimal import Decimal, ROUND_FLOOR
from time import monotonic, sleep
from typing import Protocol

from stock_trading.broker.client import BrokerRejectedError
from stock_trading.broker.models import (
    BrokerAccount,
    BrokerOrder,
    BrokerPosition,
    BrokerSyncSnapshot,
    FiveLevelQuote,
)
from stock_trading.config import AppConfig
from stock_trading.state.store import ORDER_ACTIVE_STATUSES, StateStore
from stock_trading.trading.permissions import permission_scope
from stock_trading.trading.strategy import select_execution_price

LOT_SIZE = 100
LOGGER = logging.getLogger(__name__)


class BrokerExecutionClient(Protocol):
    def get_account(self) -> BrokerAccount: ...

    def get_positions(self) -> tuple[BrokerPosition, ...]: ...

    def get_five_level_quote(self, stock_code: str) -> FiveLevelQuote: ...

    def get_today_orders(self) -> tuple[BrokerOrder, ...]: ...

    def submit_limit_order(
        self,
        direction: str,
        stock_code: str,
        price: Decimal,
        quantity: int,
    ) -> str: ...

    def cancel_order(self, broker_order_id: str) -> bool: ...

    def read_shadow_snapshot(self) -> BrokerSyncSnapshot: ...


class OrderExecutionOrchestrator:
    """基于券商实时事实执行限价委托、撤单确认和重新报价。"""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerExecutionClient,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def execute_target(
        self, business_date: date, target: dict[str, object]
    ) -> dict[str, object]:
        """在固定执行窗口内按时间紧迫度策略处理一个买入或卖出目标。"""
        instrument = str(target["instrument"])
        side = str(target["side"])
        mode = "AFTERNOON_BUY" if side == "BUY" else "MORNING_SELL"
        prefix = f"LIVE:{mode}:{business_date.isoformat()}:{instrument}"
        started_at = monotonic()
        attempts: list[dict[str, object]] = []
        active_client_id: str | None = None

        self._reconcile_orders()
        existing = self._matching_intents(prefix)
        elapsed_before_resume = self._elapsed_before_resume(existing)
        deadline = started_at + max(
            0.0,
            self._config.trading_execution_window_seconds - elapsed_before_resume,
        )
        filled = next((row for row in reversed(existing) if row["status"] == "FILLED"), None)
        if filled is not None:
            return self._execution_result(target, "FILLED", "ALREADY_FILLED", attempts)

        attempt_number = self._next_attempt_number(existing)
        active = next(
            (row for row in reversed(existing) if row["status"] in ORDER_ACTIVE_STATUSES),
            None,
        )
        try:
            if active is not None:
                active_client_id = str(active["client_order_id"])
                settled = self._wait_then_cancel(active_client_id, deadline)
                attempts.append(self._attempt_result(None, settled))
                if settled["status"] == "FILLED":
                    return self._execution_result(target, "FILLED", "BROKER_FILLED", attempts)
                if settled["status"] != "CANCELLED":
                    return self._execution_result(
                        target, str(settled["status"]), "ACTIVE_ORDER_NOT_SETTLED", attempts
                    )
                active_client_id = None

            while attempt_number <= self._config.trading_max_attempts:
                if monotonic() >= deadline:
                    break
                live_order, stop_reason = self._build_live_order(
                    target, elapsed_before_resume + monotonic() - started_at
                )
                if live_order is None:
                    status = "COMPLETED" if attempts else "SKIPPED"
                    return self._execution_result(target, status, stop_reason, attempts)

                client_order_id = f"{prefix}:A{attempt_number}"
                active_client_id = client_order_id
                live_order["client_order_id"] = client_order_id
                self._state_store.prepare_real_order(
                    client_order_id=client_order_id,
                    instrument=instrument,
                    side=side,
                    quantity=int(live_order["quantity"]),
                    price=Decimal(str(live_order["price"])),
                )
                self._state_store.start_order_submission(client_order_id)
                submitted = self._submit(live_order)
                if submitted["status"] == "UNKNOWN":
                    submitted = self._wait_for_broker_state(
                        client_order_id,
                        self._config.trading_order_wait_seconds,
                        deadline,
                    )
                if submitted["status"] == "FILLED":
                    attempts.append(self._attempt_result(live_order, submitted))
                    return self._execution_result(target, "FILLED", "BROKER_FILLED", attempts)
                if submitted["status"] == "REJECTED":
                    attempts.append(self._attempt_result(live_order, submitted))
                    return self._execution_result(target, "REJECTED", "BROKER_REJECTED", attempts)
                if submitted["status"] not in {"SUBMITTED", "PARTIAL"}:
                    attempts.append(self._attempt_result(live_order, submitted))
                    return self._execution_result(
                        target,
                        str(submitted["status"]),
                        "SUBMISSION_NOT_CONFIRMED",
                        attempts,
                    )

                observed = self._wait_for_broker_state(
                    client_order_id,
                    self._config.trading_order_wait_seconds,
                    deadline,
                )
                if observed["status"] == "FILLED":
                    attempts.append(self._attempt_result(live_order, observed))
                    return self._execution_result(target, "FILLED", "BROKER_FILLED", attempts)
                if observed["status"] == "REJECTED":
                    attempts.append(self._attempt_result(live_order, observed))
                    return self._execution_result(target, "REJECTED", "BROKER_REJECTED", attempts)
                if observed["status"] not in {"SUBMITTED", "PARTIAL"}:
                    attempts.append(self._attempt_result(live_order, observed))
                    return self._execution_result(
                        target, str(observed["status"]), "ORDER_STATE_UNCERTAIN", attempts
                    )

                settled = self._cancel_and_confirm(client_order_id)
                attempts.append(self._attempt_result(live_order, settled))
                if settled["status"] == "FILLED":
                    return self._execution_result(target, "FILLED", "FILLED_DURING_CANCEL", attempts)
                if settled["status"] != "CANCELLED":
                    return self._execution_result(
                        target, str(settled["status"]), "CANCEL_NOT_CONFIRMED", attempts
                    )
                active_client_id = None
                attempt_number += 1

            reason = (
                "EXECUTION_WINDOW_EXPIRED"
                if monotonic() >= deadline
                else "MAX_ATTEMPTS_REACHED"
            )
            return self._execution_result(target, "STOPPED", reason, attempts)
        except KeyboardInterrupt:
            LOGGER.warning("实时交易收到 Ctrl+C，开始处理活动委托：clientOrderId=%s", active_client_id)
            if active_client_id is not None:
                intent = self._state_store.get_order_intent(active_client_id)
                if intent is not None and intent["status"] in {"SUBMITTED", "PARTIAL"}:
                    try:
                        self._cancel_and_confirm(active_client_id)
                    except Exception as error:
                        LOGGER.error("Ctrl+C 后撤单处理失败，保留恢复状态：%s", error)
            raise

    def recover_pending_orders(self, business_date: date) -> dict[str, object]:
        """用券商事实恢复本地未终态委托。"""
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

    def _build_live_order(
        self, target: dict[str, object], elapsed_seconds: float
    ) -> tuple[dict[str, object] | None, str]:
        instrument = str(target["instrument"])
        stock_code = instrument[-6:]
        side = str(target["side"])
        positions = self._broker.get_positions()
        quote = self._broker.get_five_level_quote(stock_code)
        decision = select_execution_price(
            quote=quote,
            side=side,
            elapsed_seconds=elapsed_seconds,
            window_seconds=self._config.trading_execution_window_seconds,
            passive_fraction=self._config.trading_passive_fraction,
            normal_fraction=self._config.trading_normal_fraction,
        )
        if side == "SELL":
            position = next(
                (item for item in positions if item.stock_code == stock_code), None
            )
            quantity = position.available_quantity if position else 0
            if quantity <= 0:
                return None, "NO_AVAILABLE_QUANTITY"
        else:
            other_positions = [
                item
                for item in positions
                if item.quantity > 0 and item.stock_code != stock_code
            ]
            if other_positions:
                return None, "OTHER_POSITION_EXISTS"
            reference_price = quote.price or quote.asks[0]
            if not Decimal(str(self._config.trading_min_price)) <= reference_price <= Decimal(
                str(self._config.trading_max_price)
            ):
                return None, "PRICE_OUT_OF_RANGE"
            account = self._broker.get_account()
            quantity = self._affordable_quantity(account.available_cash, decision.price)
            if quantity <= 0:
                return None, "INSUFFICIENT_CASH"
        LOGGER.info(
            "实时委托参数：股票=%s；方向=%s；阶段=%s；档位=%s；价格=%s；数量=%d",
            instrument,
            side,
            decision.stage,
            decision.price_level,
            decision.price,
            quantity,
        )
        return (
            {
                "instrument": instrument,
                "side": side,
                "quantity": quantity,
                "price": decision.price,
                "strategy": decision.strategy,
                "urgencyStage": decision.stage,
                "priceLevel": decision.price_level,
            },
            "READY",
        )

    def _submit(self, order: dict[str, object]) -> dict[str, object]:
        client_order_id = str(order["client_order_id"])
        try:
            broker_order_id = self._broker.submit_limit_order(
                direction=str(order["side"]),
                stock_code=str(order["instrument"])[-6:],
                price=Decimal(str(order["price"])),
                quantity=int(order["quantity"]),
            )
        except BrokerRejectedError as error:
            self._state_store.finish_real_order(
                client_order_id, "REJECTED", error_message=str(error)
            )
            scope = permission_scope(str(order["instrument"]))
            if scope and _is_permission_denial(str(error)):
                self._save_permission(scope, "DENIED", "EXPLICIT_BROKER_REJECTION", str(error))
            return self._required_intent(client_order_id)
        except Exception as error:
            self._state_store.finish_real_order(
                client_order_id, "UNKNOWN", error_message=str(error)
            )
            return self._required_intent(client_order_id)
        self._state_store.finish_real_order(
            client_order_id, "SUBMITTED", broker_order_id=broker_order_id
        )
        scope = permission_scope(str(order["instrument"]))
        if scope:
            self._save_permission(scope, "ALLOWED", "ACCEPTED_ORDER", f"券商委托号={broker_order_id}")
        return self._required_intent(client_order_id)

    def _wait_then_cancel(
        self, client_order_id: str, deadline: float
    ) -> dict[str, object]:
        current = self._required_intent(client_order_id)
        wait_seconds = (
            self._config.trading_cancel_confirm_seconds
            if current["status"] == "CANCEL_SUBMITTING"
            else self._config.trading_order_wait_seconds
        )
        observed = self._wait_for_broker_state(
            client_order_id,
            wait_seconds,
            max(deadline, monotonic() + wait_seconds),
        )
        if observed["status"] in {"SUBMITTED", "PARTIAL"}:
            return self._cancel_and_confirm(client_order_id)
        if observed["status"] == "CANCEL_SUBMITTING":
            self._state_store.finish_real_order(
                client_order_id,
                "UNKNOWN",
                broker_order_id=str(observed["broker_order_id"]),
                error_message="恢复后仍未取得撤单终态",
            )
            return self._required_intent(client_order_id)
        return observed

    def _wait_for_broker_state(
        self, client_order_id: str, wait_seconds: int, deadline: float
    ) -> dict[str, object]:
        poll_deadline = min(deadline, monotonic() + wait_seconds)
        while True:
            self._reconcile_orders()
            intent = self._required_intent(client_order_id)
            if intent["status"] in {"FILLED", "CANCELLED", "REJECTED"}:
                return intent
            remaining = poll_deadline - monotonic()
            if remaining <= 0:
                return intent
            sleep(min(self._config.trading_order_poll_seconds, remaining))

    def _cancel_and_confirm(self, client_order_id: str) -> dict[str, object]:
        intent = self._required_intent(client_order_id)
        broker_order_id = intent.get("broker_order_id")
        if broker_order_id is None:
            self._state_store.finish_real_order(
                client_order_id,
                "UNKNOWN",
                error_message="没有券商委托号，禁止撤单后重挂",
            )
            return self._required_intent(client_order_id)
        if intent["status"] != "CANCEL_SUBMITTING":
            self._state_store.start_order_cancel(client_order_id)
        accepted = self._broker.cancel_order(str(broker_order_id))
        if not accepted:
            self._reconcile_orders()
            refreshed = self._required_intent(client_order_id)
            if refreshed["status"] in {"FILLED", "CANCELLED", "REJECTED"}:
                return refreshed
            self._state_store.finish_real_order(
                client_order_id,
                "UNKNOWN",
                broker_order_id=str(broker_order_id),
                error_message="券商未确认撤单请求受理",
            )
            return self._required_intent(client_order_id)
        confirm_deadline = monotonic() + self._config.trading_cancel_confirm_seconds
        confirmed = self._wait_for_broker_state(
            client_order_id,
            self._config.trading_cancel_confirm_seconds,
            confirm_deadline,
        )
        if confirmed["status"] not in {"FILLED", "CANCELLED", "REJECTED"}:
            self._state_store.finish_real_order(
                client_order_id,
                "UNKNOWN",
                broker_order_id=str(broker_order_id),
                error_message="撤单已受理但未取得券商终态",
            )
            return self._required_intent(client_order_id)
        return confirmed

    def _reconcile_orders(self) -> tuple[BrokerOrder, ...]:
        orders = self._broker.get_today_orders()
        self._state_store.reconcile_order_intents(orders)
        return orders

    def _matching_intents(self, prefix: str) -> list[dict[str, object]]:
        return [
            row
            for row in self._state_store.list_order_intents()
            if str(row["client_order_id"]).startswith(f"{prefix}:A")
        ]

    @staticmethod
    def _next_attempt_number(intents: list[dict[str, object]]) -> int:
        numbers: list[int] = []
        for intent in intents:
            suffix = str(intent["client_order_id"]).rsplit(":A", 1)[-1]
            if suffix.isdigit():
                numbers.append(int(suffix))
        return max(numbers, default=0) + 1

    @staticmethod
    def _elapsed_before_resume(intents: list[dict[str, object]]) -> float:
        if not intents:
            return 0.0
        created_at = datetime.fromisoformat(str(intents[0]["created_at"]))
        if created_at.tzinfo is None:
            created_at = created_at.replace(tzinfo=UTC)
        return max(0.0, (datetime.now(UTC) - created_at.astimezone(UTC)).total_seconds())

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

    def _save_permission(
        self, scope: str, status: str, evidence_type: str, evidence_detail: str
    ) -> None:
        self._state_store.save_broker_permissions(
            [
                {
                    "scope": scope,
                    "status": status,
                    "evidenceType": evidence_type,
                    "evidenceDetail": evidence_detail[:300],
                    "observedAt": datetime.now().astimezone().isoformat(),
                }
            ]
        )

    def _required_intent(self, client_order_id: str) -> dict[str, object]:
        intent = self._state_store.get_order_intent(client_order_id)
        if intent is None:
            raise ValueError(f"订单意图不存在: {client_order_id}")
        return intent

    @staticmethod
    def _attempt_result(
        order: dict[str, object] | None, intent: dict[str, object]
    ) -> dict[str, object]:
        result = {
            "clientOrderId": intent["client_order_id"],
            "brokerOrderId": intent["broker_order_id"],
            "status": intent["status"],
            "errorMessage": intent["error_message"],
            "instrument": intent["instrument"],
            "side": intent["side"],
            "quantity": int(intent["quantity"]),
            "price": float(intent["price"]),
        }
        if order is not None:
            result.update(
                {
                    "strategy": order["strategy"],
                    "urgencyStage": order["urgencyStage"],
                    "priceLevel": order["priceLevel"],
                }
            )
        return result

    @staticmethod
    def _execution_result(
        target: dict[str, object],
        status: str,
        stop_reason: str,
        attempts: list[dict[str, object]],
    ) -> dict[str, object]:
        return {
            "target": target,
            "status": status,
            "stopReason": stop_reason,
            "attempts": attempts,
        }


def _is_permission_denial(message: str) -> bool:
    normalized = message.replace(" ", "")
    return any(
        keyword in normalized
        for keyword in ("无权限", "没有权限", "未开通", "禁止交易", "不允许交易")
    )
# AI_GENERATE_END ----------------
