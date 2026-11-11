# AI_GENERATE_START-- ----------------------------------
from __future__ import annotations

import logging
from dataclasses import dataclass
from datetime import UTC, date, datetime
from decimal import Decimal
from time import monotonic, sleep
from typing import Protocol
from zoneinfo import ZoneInfo

from stock_trading.broker.client import BrokerRejectedError
from stock_trading.broker.models import (
    BrokerAccount,
    BrokerOrder,
    BrokerPosition,
    FiveLevelQuote,
)
from stock_trading.config import (
    AppConfig,
    BUY_END,
    BUY_START,
    SELL_END,
    SELL_START,
    execution_window_seconds,
)
from stock_trading.market.domain import trade_dates
from stock_trading.state.store import ORDER_ACTIVE_STATUSES, StateStore
from stock_trading.trading.strategy import permission_scope, affordable_quantity, select_execution_price

SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")

LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True)
class AttemptOutcome:
    """封装单次委托尝试及其是否形成整个目标的终态。"""

    attempt: dict[str, object]
    terminal_status: str | None = None
    stop_reason: str | None = None


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

        LOGGER.info(
            "交易流程开始：业务日期=%s；股票=%s；方向=%s；模式=%s；最大尝试=%d；执行窗口=%d秒",
            business_date, instrument, side, mode,
            self._config.trading_max_attempts,
            execution_window_seconds(side),
        )
        self._reconcile_orders(reason="EXECUTION_START")
        existing = self._matching_intents(prefix)
        elapsed_before_resume = self._elapsed_before_resume(existing)
        remaining_window = self._remaining_window_seconds(business_date, side)
        deadline = started_at + max(
            0.0,
            min(
                execution_window_seconds(side) - elapsed_before_resume,
                remaining_window,
            ),
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
                LOGGER.info(
                    "交易恢复活动委托：clientOrderId=%s；brokerOrderId=%s；状态=%s",
                    active_client_id, active.get("broker_order_id"), active["status"],
                )
                outcome = self._resume_active_order(active_client_id, deadline)
                attempts.append(outcome.attempt)
                if outcome.terminal_status is not None:
                    return self._execution_result(
                        target,
                        outcome.terminal_status,
                        str(outcome.stop_reason),
                        attempts,
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

                # 实时报价/账户读取可能跨越窗口截止，不能在过时检查后继续提交。
                if monotonic() >= deadline or self._remaining_window_seconds(business_date, side) <= 0:
                    break

                client_order_id = f"{prefix}:A{attempt_number}"
                active_client_id = client_order_id
                live_order["client_order_id"] = client_order_id
                outcome = self._execute_attempt(
                    business_date,
                    target,
                    live_order,
                    attempt_number,
                    deadline,
                )
                attempts.append(outcome.attempt)
                if outcome.terminal_status is not None:
                    return self._execution_result(
                        target,
                        outcome.terminal_status,
                        str(outcome.stop_reason),
                        attempts,
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

    def _resume_active_order(
        self, client_order_id: str, deadline: float
    ) -> AttemptOutcome:
        """恢复一个活动委托，取得终态或确认撤单后允许外层继续重试。"""
        settled = self._wait_then_cancel(client_order_id, deadline)
        attempt = self._attempt_result(None, settled)
        if settled["status"] == "FILLED":
            return AttemptOutcome(attempt, "FILLED", "BROKER_FILLED")
        if settled["status"] != "CANCELLED":
            return AttemptOutcome(
                attempt, str(settled["status"]), "ACTIVE_ORDER_NOT_SETTLED"
            )
        return AttemptOutcome(attempt)

    def _execute_attempt(
        self,
        business_date: date,
        target: dict[str, object],
        live_order: dict[str, object],
        attempt_number: int,
        deadline: float,
    ) -> AttemptOutcome:
        """准备、提交、观察并在需要时撤销一次独立券商委托尝试。"""
        client_order_id = str(live_order["client_order_id"])
        LOGGER.info(
            "交易动作开始：动作=PREPARE_ORDER；业务日期=%s；尝试=%d/%d；clientOrderId=%s；股票=%s；方向=%s；价格=%s；数量=%s",
            business_date,
            attempt_number,
            self._config.trading_max_attempts,
            client_order_id,
            target["instrument"],
            target["side"],
            live_order["price"],
            live_order["quantity"],
        )
        self._state_store.prepare_real_order(
            client_order_id=client_order_id,
            business_date=business_date,
            instrument=str(target["instrument"]),
            side=str(target["side"]),
            quantity=int(live_order["quantity"]),
            price=Decimal(str(live_order["price"])),
            model_version_id=(
                int(target["modelVersionId"])
                if target.get("modelVersionId") is not None
                else None
            ),
            prediction_date=(
                str(target["predictionDate"])
                if target.get("predictionDate") is not None
                else None
            ),
        )
        self._state_store.start_order_submission(client_order_id)
        LOGGER.info(
            "交易动作完成：动作=PREPARE_ORDER；clientOrderId=%s；本地状态=SUBMITTING",
            client_order_id,
        )

        submitted = self._submit(live_order)
        if submitted["status"] == "UNKNOWN":
            submitted = self._wait_for_broker_state(
                client_order_id,
                self._config.trading_order_wait_seconds,
                deadline,
            )
        terminal = self._terminal_outcome(
            live_order, submitted, "SUBMISSION_NOT_CONFIRMED"
        )
        if terminal is not None:
            return terminal

        observed = self._wait_for_broker_state(
            client_order_id,
            self._config.trading_order_wait_seconds,
            deadline,
        )
        terminal = self._terminal_outcome(
            live_order, observed, "ORDER_STATE_UNCERTAIN"
        )
        if terminal is not None:
            return terminal

        settled = self._cancel_and_confirm(client_order_id)
        attempt = self._attempt_result(live_order, settled)
        if settled["status"] == "FILLED":
            return AttemptOutcome(attempt, "FILLED", "FILLED_DURING_CANCEL")
        if settled["status"] != "CANCELLED":
            return AttemptOutcome(
                attempt, str(settled["status"]), "CANCEL_NOT_CONFIRMED"
            )
        return AttemptOutcome(attempt)

    def _terminal_outcome(
        self,
        live_order: dict[str, object],
        intent: dict[str, object],
        uncertain_reason: str,
    ) -> AttemptOutcome | None:
        """把券商委托状态转换为单次尝试的终止结论。"""
        status = str(intent["status"])
        attempt = self._attempt_result(live_order, intent)
        if status == "FILLED":
            return AttemptOutcome(attempt, "FILLED", "BROKER_FILLED")
        if status == "REJECTED":
            return AttemptOutcome(attempt, "REJECTED", "BROKER_REJECTED")
        if status not in {"SUBMITTED", "PARTIAL"}:
            return AttemptOutcome(attempt, status, uncertain_reason)
        return None

    def _build_live_order(
        self, target: dict[str, object], elapsed_seconds: float
    ) -> tuple[dict[str, object] | None, str]:
        instrument = str(target["instrument"])
        stock_code = instrument[-6:]
        side = str(target["side"])
        quote = self._broker.get_five_level_quote(stock_code)
        decision = select_execution_price(
            quote=quote,
            side=side,
            elapsed_seconds=elapsed_seconds,
            window_seconds=execution_window_seconds(side),
            passive_fraction=self._config.trading_passive_fraction,
            normal_fraction=self._config.trading_normal_fraction,
        )
        if side == "SELL":
            positions = self._broker.get_positions()
            position = next(
                (item for item in positions if item.stock_code == stock_code), None
            )
            quantity = position.available_quantity if position else 0
            if quantity <= 0:
                return None, "NO_AVAILABLE_QUANTITY"
        else:
            reference_price = quote.price or quote.asks[0]
            if not Decimal(str(self._config.trading_min_price)) <= reference_price <= Decimal(
                str(self._config.trading_max_price)
            ):
                return None, "PRICE_OUT_OF_RANGE"
            account = self._broker.get_account()
            quantity = affordable_quantity(
                account.available_cash,
                decision.price,
                Decimal(str(self._config.trading_order_fee)),
            )
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
        instrument = str(order["instrument"])
        side = str(order["side"])
        price = Decimal(str(order["price"]))
        quantity = int(order["quantity"])
        LOGGER.info(
            "交易动作开始：动作=SUBMIT_ORDER；clientOrderId=%s；股票=%s；方向=%s；价格=%s；数量=%d",
            client_order_id, instrument, side, price, quantity,
        )
        try:
            broker_order_id = self._broker.submit_limit_order(
                direction=side, stock_code=instrument[-6:], price=price, quantity=quantity,
            )
        except BrokerRejectedError as error:
            self._state_store.finish_real_order(client_order_id, "REJECTED", error_message=str(error))
            scope = permission_scope(instrument)
            if scope and _is_permission_denial(str(error)):
                self._save_permission(scope, "DENIED", "EXPLICIT_BROKER_REJECTION", str(error))
            LOGGER.warning("交易动作完成：动作=SUBMIT_ORDER；clientOrderId=%s；结果=REJECTED；原因=%s", client_order_id, error)
            return self._required_intent(client_order_id)
        except Exception as error:
            self._state_store.finish_real_order(client_order_id, "UNKNOWN", error_message=str(error))
            LOGGER.error("交易动作完成：动作=SUBMIT_ORDER；clientOrderId=%s；结果=UNKNOWN；原因=%s", client_order_id, error, exc_info=True)
            return self._required_intent(client_order_id)
        self._state_store.finish_real_order(client_order_id, "SUBMITTED", broker_order_id=broker_order_id)
        scope = permission_scope(instrument)
        if scope:
            self._save_permission(scope, "ALLOWED", "ACCEPTED_ORDER", f"券商委托号={broker_order_id}")
        LOGGER.info("交易动作完成：动作=SUBMIT_ORDER；clientOrderId=%s；brokerOrderId=%s；结果=SUBMITTED", client_order_id, broker_order_id)
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
        initial = self._required_intent(client_order_id)
        LOGGER.info(
            "委托状态观察开始：clientOrderId=%s；brokerOrderId=%s；初始状态=%s；最长等待=%d秒；查询间隔=%d秒",
            client_order_id, initial.get("broker_order_id"), initial["status"], wait_seconds,
            self._config.trading_order_poll_seconds,
        )
        poll_number = 0
        while True:
            poll_number += 1
            self._reconcile_orders(client_order_id, f"ORDER_POLL_{poll_number}")
            intent = self._required_intent(client_order_id)
            if intent["status"] in {"FILLED", "CANCELLED", "REJECTED"}:
                LOGGER.info("委托状态观察结束：clientOrderId=%s；brokerOrderId=%s；查询次数=%d；终态=%s", client_order_id, intent.get("broker_order_id"), poll_number, intent["status"])
                return intent
            remaining = poll_deadline - monotonic()
            if remaining <= 0:
                LOGGER.warning("委托状态观察超时：clientOrderId=%s；brokerOrderId=%s；查询次数=%d；当前状态=%s", client_order_id, intent.get("broker_order_id"), poll_number, intent["status"])
                return intent
            sleep_seconds = min(self._config.trading_order_poll_seconds, remaining)
            LOGGER.debug("委托状态观察等待：clientOrderId=%s；当前状态=%s；下次查询等待=%.1f秒", client_order_id, intent["status"], sleep_seconds)
            sleep(sleep_seconds)

    def _cancel_and_confirm(self, client_order_id: str) -> dict[str, object]:
        intent = self._required_intent(client_order_id)
        broker_order_id = intent.get("broker_order_id")
        if broker_order_id is None:
            LOGGER.warning("交易动作跳过：动作=CANCEL_ORDER；clientOrderId=%s；原因=NO_BROKER_ORDER_ID", client_order_id)
            self._state_store.finish_real_order(client_order_id, "UNKNOWN", error_message="没有券商委托号，禁止撤单后重挂")
            return self._required_intent(client_order_id)
        LOGGER.info("交易动作开始：动作=CANCEL_ORDER；clientOrderId=%s；brokerOrderId=%s；当前状态=%s", client_order_id, broker_order_id, intent["status"])
        if intent["status"] != "CANCEL_SUBMITTING":
            self._state_store.start_order_cancel(client_order_id)
            LOGGER.info("撤单本地状态已记录：clientOrderId=%s；brokerOrderId=%s；本地状态=CANCEL_SUBMITTING", client_order_id, broker_order_id)
        try:
            accepted = self._broker.cancel_order(str(broker_order_id))
        except Exception as error:
            LOGGER.error("交易动作异常：动作=CANCEL_ORDER；clientOrderId=%s；brokerOrderId=%s；原因=%s", client_order_id, broker_order_id, error, exc_info=True)
            raise
        if not accepted:
            LOGGER.warning("交易动作完成：动作=CANCEL_ORDER；clientOrderId=%s；brokerOrderId=%s；结果=NOT_ACCEPTED", client_order_id, broker_order_id)
            self._reconcile_orders(client_order_id, "CANCEL_NOT_ACCEPTED")
            refreshed = self._required_intent(client_order_id)
            if refreshed["status"] in {"FILLED", "CANCELLED", "REJECTED"}:
                return refreshed
            self._state_store.finish_real_order(client_order_id, "UNKNOWN", broker_order_id=str(broker_order_id), error_message="券商未确认撤单请求受理")
            result = self._required_intent(client_order_id)
            LOGGER.warning("撤单流程结束：clientOrderId=%s；brokerOrderId=%s；终态=%s", client_order_id, broker_order_id, result["status"])
            return result
        LOGGER.info("交易动作完成：动作=CANCEL_ORDER；clientOrderId=%s；brokerOrderId=%s；结果=ACCEPTED；等待终态=%d秒", client_order_id, broker_order_id, self._config.trading_cancel_confirm_seconds)
        confirmed = self._wait_for_broker_state(client_order_id, self._config.trading_cancel_confirm_seconds, monotonic() + self._config.trading_cancel_confirm_seconds)
        if confirmed["status"] not in {"FILLED", "CANCELLED", "REJECTED"}:
            self._state_store.finish_real_order(client_order_id, "UNKNOWN", broker_order_id=str(broker_order_id), error_message="撤单已受理但未取得券商终态")
            confirmed = self._required_intent(client_order_id)
        LOGGER.info("撤单流程结束：clientOrderId=%s；brokerOrderId=%s；终态=%s", client_order_id, broker_order_id, confirmed["status"])
        return confirmed

    def _reconcile_orders(
        self, client_order_id: str | None = None, reason: str = "RECONCILE"
    ) -> tuple[BrokerOrder, ...]:
        LOGGER.info("交易动作开始：动作=QUERY_ORDERS；原因=%s；clientOrderId=%s", reason, client_order_id or "-")
        try:
            orders = self._broker.get_today_orders()
            self._state_store.reconcile_order_intents(orders)
        except Exception as error:
            LOGGER.error(
                "交易动作异常：动作=QUERY_ORDERS；原因=%s；clientOrderId=%s；错误=%s",
                reason,
                client_order_id or "-",
                error,
                exc_info=True,
            )
            raise
        if client_order_id is None:
            LOGGER.info("交易动作完成：动作=QUERY_ORDERS；原因=%s；券商委托数=%d", reason, len(orders))
            return orders
        intent = self._state_store.get_order_intent(client_order_id)
        if intent is None:
            LOGGER.warning("交易动作完成：动作=QUERY_ORDERS；原因=%s；clientOrderId=%s；结果=LOCAL_INTENT_NOT_FOUND；券商委托数=%d", reason, client_order_id, len(orders))
            return orders
        broker_order_id = intent.get("broker_order_id")
        broker_order = next((order for order in orders if broker_order_id is not None and order.order_id is not None and str(order.order_id) == str(broker_order_id)), None)
        LOGGER.info(
            "交易动作完成：动作=QUERY_ORDERS；原因=%s；clientOrderId=%s；brokerOrderId=%s；本地状态=%s；券商状态=%s；券商原始状态=%s；委托数量=%s；已成交数量=%s；券商委托数=%d",
            reason, client_order_id, broker_order_id, intent["status"],
            broker_order.status if broker_order else "NOT_MATCHED",
            broker_order.raw_status if broker_order else None,
            broker_order.order_quantity if broker_order else intent["quantity"],
            broker_order.filled_quantity if broker_order else None, len(orders),
        )
        return orders

    def _matching_intents(self, prefix: str) -> list[dict[str, object]]:
        return [
            row
            for row in self._state_store.list_order_intents()
            if str(row["client_order_id"]).startswith(f"{prefix}:A")
        ]

    def _remaining_window_seconds(self, business_date: date, side: str) -> float:
        now = datetime.now(SHANGHAI_ZONE)
        start_time, end_time = (
            (SELL_START, SELL_END) if side == "SELL" else (BUY_START, BUY_END)
        )
        if (
            side not in {"BUY", "SELL"}
            or now.date() != business_date
            or business_date not in trade_dates(business_date.year)
            or not start_time <= now.time() < end_time
        ):
            return 0.0
        window_end = datetime.combine(business_date, end_time, tzinfo=now.tzinfo)
        return max(0.0, (window_end - now).total_seconds())

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
                    "observedAt": datetime.now(SHANGHAI_ZONE).isoformat(),
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
        last_attempt = attempts[-1] if attempts else None
        LOGGER.info(
            "交易流程结束：股票=%s；方向=%s；状态=%s；停止原因=%s；尝试次数=%d；最后clientOrderId=%s；最后brokerOrderId=%s",
            target["instrument"], target["side"], status, stop_reason, len(attempts),
            last_attempt["clientOrderId"] if last_attempt else None,
            last_attempt["brokerOrderId"] if last_attempt else None,
        )
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
# AI_GENERATE_END-- ----------------------------------
