# AI_GENERATE_START----- ----------
from __future__ import annotations

import json
import logging
import time
from collections import deque
from dataclasses import replace
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

from stock_trading.broker.client import (
    BrokerProtocolError,
    BrokerSafetyError,
    ZXBrokerClient,
)
from stock_trading.broker.models import BrokerFeeBreakdown, BrokerOrder
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.trading.strategy import permission_scope
from stock_trading.market.domain import normalize_instrument
from stock_trading.utils.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)
MONEY_QUANT = Decimal("0.0001")
RATE_QUANT = Decimal("0.00000001")
NON_TRADING_HOURS = "NON_TRADING_HOURS"


class BrokerOrderSyncService:
    """按月增量同步券商成交事实，并结算尚未闭环的 FIFO 数量。"""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: ZXBrokerClient,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def sync(self, start_date: date, end_date: date) -> dict[str, object]:
        if end_date < start_date:
            raise ValueError("结束日期不能早于开始日期")
        windows = list(_month_windows(start_date, end_date))
        summaries: list[dict[str, object]] = []
        for index, (window_start, window_end) in enumerate(windows, start=1):
            set_log_context(
                progress=f"{index}/{len(windows)}",
                target=f"{window_start}:{window_end}",
            )
            LOGGER.info("历史订单同步窗口开始")
            received = self._broker.get_history_orders(window_start, window_end)
            orders_with_fees = tuple(
                replace(
                    order,
                    fee=order.fee or BrokerFeeBreakdown.business_zero(
                        Decimal(str(self._config.trading_order_fee))
                    ),
                )
                for order in received
            )
            valid_orders = _valid_orders(
                orders_with_fees, window_start, window_end
            )
            self._state_store.reconcile_order_intents(valid_orders)
            summary = self._state_store.upsert_broker_orders(valid_orders)
            summary["receivedOrders"] = len(received)
            summary["discardedOrders"] = len(received) - len(valid_orders)
            summaries.append(summary)
            LOGGER.info(
                "历史订单同步窗口完成：日期=%s 至 %s；接收=%d；有效=%d",
                window_start,
                window_end,
                len(received),
                len(valid_orders),
            )

        set_log_context(step="3/4 成交")
        settlement = IncrementalSettlementService(
            self._config, self._state_store
        ).settle()
        set_log_context(step="4/4 收益闭环")
        return {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "windows": summaries,
            "receivedOrders": sum(int(row["receivedOrders"]) for row in summaries),
            "validOrders": sum(int(row["validOrders"]) for row in summaries),
            "discardedOrders": sum(int(row["discardedOrders"]) for row in summaries),
            "insertedOrders": sum(int(row["insertedOrders"]) for row in summaries),
            "updatedOrders": sum(int(row["updatedOrders"]) for row in summaries),
            "settlement": settlement,
        }


class IncrementalSettlementService:
    """仅对券商订单尚未结算的新增成交数量执行 FIFO 闭环。"""

    def __init__(self, config: AppConfig, state_store: StateStore) -> None:
        self._config = config
        self._state_store = state_store

    def settle(self) -> dict[str, int]:
        orders = self._state_store.unsettled_broker_orders()
        open_lots: dict[str, deque[dict[str, object]]] = {}
        trades: list[dict[str, object]] = []
        settled_increments: dict[tuple[str, str], int] = {}
        unmatched_sell_quantity = 0

        for order in orders:
            direction = str(order["direction"])
            quantity = int(order["filled_quantity"]) - int(order["matched_quantity"])
            instrument = str(order["instrument"])
            if direction == "BUY":
                open_lots.setdefault(instrument, deque()).append(
                    {"order": order, "remaining": quantity}
                )
                continue
            if direction != "SELL":
                continue
            lots = open_lots.setdefault(instrument, deque())
            unmatched_sell_quantity += self._settle_sell(
                lots, order, quantity, trades, settled_increments
            )

        trade_count = self._state_store.apply_realized_trades(trades)
        LOGGER.info(
            "FIFO 增量结算完成：待结算订单=%d；新增闭环=%d；未匹配卖出数量=%d",
            len(orders),
            trade_count,
            unmatched_sell_quantity,
        )
        return {
            "realizedTrades": trade_count,
            "unmatchedSellQuantity": unmatched_sell_quantity,
        }

    def _settle_sell(
        self,
        lots: deque[dict[str, object]],
        order: dict[str, object],
        quantity: int,
        trades: list[dict[str, object]],
        settled_increments: dict[tuple[str, str], int],
    ) -> int:
        remaining = quantity
        while remaining > 0 and lots:
            lot = lots[0]
            buy_order = dict(lot["order"])
            if str(buy_order["trade_date"]) >= str(order["trade_date"]):
                break
            matched = min(remaining, int(lot["remaining"]))
            buy_before = int(buy_order["matched_quantity"]) + settled_increments.get(
                self._order_key(buy_order), 0
            )
            sell_before = int(order["matched_quantity"]) + settled_increments.get(
                self._order_key(order), 0
            )
            trades.append(
                self._realized_trade(
                    buy_order, order, matched, buy_before, sell_before
                )
            )
            self._increment(settled_increments, buy_order, matched)
            self._increment(settled_increments, order, matched)
            lot["remaining"] = int(lot["remaining"]) - matched
            remaining -= matched
            if int(lot["remaining"]) == 0:
                lots.popleft()
        return remaining

    def _realized_trade(
        self,
        buy_order: dict[str, object],
        sell_order: dict[str, object],
        quantity: int,
        buy_before: int,
        sell_before: int,
    ) -> dict[str, object]:
        buy_price = Decimal(str(buy_order["average_fill_price"]))
        sell_price = Decimal(str(sell_order["average_fill_price"]))
        cost_basis = _money(buy_price * quantity)
        sell_amount = _money(sell_price * quantity)
        buy_fee = self._allocated_fee(buy_order, buy_before, quantity)
        sell_fee = self._allocated_fee(sell_order, sell_before, quantity)
        gross_profit = _money(sell_amount - cost_basis)
        net_profit = _money(gross_profit - buy_fee - sell_fee)
        buy_order_id = str(buy_order["broker_order_id"])
        sell_order_id = str(sell_order["broker_order_id"])
        return {
            "tradeKey": (
                f"{buy_order['trade_date']}:{buy_order_id}:"
                f"{sell_order['trade_date']}:{sell_order_id}"
            ),
            "modelVersionId": buy_order["model_version_id"],
            "instrument": str(buy_order["instrument"]),
            "buyOrderId": buy_order_id,
            "sellOrderId": sell_order_id,
            "buyDate": str(buy_order["trade_date"]),
            "sellDate": str(sell_order["trade_date"]),
            "quantity": quantity,
            "buyPrice": buy_price,
            "sellPrice": sell_price,
            "buyFee": buy_fee,
            "sellFee": sell_fee,
            "costBasis": cost_basis,
            "sellAmount": sell_amount,
            "grossProfit": gross_profit,
            "netProfit": net_profit,
            "grossReturn": _rate(gross_profit, cost_basis),
            "netReturn": _rate(net_profit, cost_basis + buy_fee),
            "buyTime": str(buy_order["order_time"]),
            "sellTime": str(sell_order["order_time"]),
        }

    @staticmethod
    def _increment(
        increments: dict[tuple[str, str], int],
        order: dict[str, object],
        quantity: int,
    ) -> None:
        key = IncrementalSettlementService._order_key(order)
        increments[key] = increments.get(key, 0) + quantity

    @staticmethod
    def _order_key(order: dict[str, object]) -> tuple[str, str]:
        return str(order["trade_date"]), str(order["broker_order_id"])

    def _allocated_fee(
        self,
        order: dict[str, object],
        settled_before: int,
        quantity: int,
    ) -> Decimal:
        filled_quantity = int(order["filled_quantity"])
        total_fee = self._order_fee(order)
        before = _money(total_fee * Decimal(settled_before) / filled_quantity)
        after = _money(
            total_fee * Decimal(settled_before + quantity) / filled_quantity
        )
        return after - before

    def _order_fee(self, order: dict[str, object]) -> Decimal:
        value = order.get("total_fee")
        if value is not None:
            return _money(Decimal(str(value)))
        return _money(Decimal(str(self._config.trading_order_fee)))


def _month_windows(start_date: date, end_date: date):
    cursor = start_date
    while cursor <= end_date:
        next_month = (
            date(cursor.year + 1, 1, 1)
            if cursor.month == 12
            else date(cursor.year, cursor.month + 1, 1)
        )
        window_end = min(end_date, next_month - timedelta(days=1))
        yield cursor, window_end
        cursor = window_end + timedelta(days=1)


def _valid_orders(
    orders: tuple[BrokerOrder, ...], start_date: date, end_date: date
) -> tuple[BrokerOrder, ...]:
    unique: dict[tuple[date, str], BrokerOrder] = {}
    for order in orders:
        if not _is_valid_order(order):
            continue
        trade_date = order.order_time.date()
        if not start_date <= trade_date <= end_date:
            continue
        unique[(trade_date, str(order.order_id))] = order
    return tuple(unique.values())


def _is_valid_order(order: BrokerOrder) -> bool:
    return (
        order.status == "FILLED"
        and bool(str(order.order_id or "").strip())
        and bool(str(order.stock_code or "").strip())
        and order.direction in {"BUY", "SELL"}
        and order.order_time is not None
        and order.filled_quantity is not None
        and order.filled_quantity > 0
        and order.average_fill_price is not None
        and order.average_fill_price.is_finite()
        and order.average_fill_price > 0
        and order.average_fill_price * order.filled_quantity > 0
        and order.order_price.is_finite()
    )


def _money(value: Decimal) -> Decimal:
    return value.quantize(MONEY_QUANT, rounding=ROUND_HALF_UP)


def _rate(numerator: Decimal, denominator: Decimal) -> Decimal:
    if denominator <= 0:
        return Decimal("0").quantize(RATE_QUANT)
    return (numerator / denominator).quantize(RATE_QUANT, rounding=ROUND_HALF_UP)


class RealizedProfitService:
    """汇总指定卖出日已经闭环的真实收益。"""

    def __init__(self, state_store: StateStore) -> None:
        self._state_store = state_store

    def summarize(
        self, model_id: str, business_date: date
    ) -> dict[str, object]:
        set_log_context(progress="-/-", target=str(business_date))
        profit = self._state_store.realized_performance(model_id, business_date)
        LOGGER.info(
            "已实现收益汇总完成：闭环=%d；数量=%d；成本=%s；卖出额=%s；净收益=%s",
            profit["trade_count"],
            profit["quantity"],
            profit["cost_basis"],
            profit["sell_amount"],
            profit["net_pnl"],
        )
        return profit


class ModelReviewService:
    """根据已实现月度毛收益判断模型是否继续使用或重新训练。"""

    def __init__(self, config: AppConfig, state_store: StateStore) -> None:
        self._config = config
        self._state_store = state_store

    def review(self, model_id: str, business_date: date) -> dict[str, object]:
        set_log_context(progress="-/-", target=str(business_date))
        model_version_id = self._state_store.latest_model_version_id(model_id)
        window_observations = self._config.model_evaluation_observations
        rows = self._state_store.recent_monthly_realized_performance(
            model_id, business_date, window_observations
        )
        observations = len(rows)
        positive_period_ratio: float | None = None
        total_gross_return: float | None = None
        total_net_return: float | None = None
        max_drawdown: float | None = None
        decision = "WARMUP"
        reason = f"有效已实现收益月份 {observations}/{window_observations}"
        if rows:
            gross_returns = [float(row["gross_return"]) for row in rows]
            net_returns = [float(row["net_return"]) for row in rows]
            positive_period_ratio = sum(value > 0 for value in gross_returns) / observations
            total_gross_return = _compound_return(gross_returns)
            total_net_return = _compound_return(net_returns)
            max_drawdown = _max_drawdown(gross_returns)
        if observations >= window_observations:
            if (
                total_gross_return is not None
                and total_gross_return > self._config.model_min_total_gross_return
                and positive_period_ratio is not None
                and positive_period_ratio
                >= self._config.model_min_positive_month_ratio
                and max_drawdown is not None
                and max_drawdown <= self._config.model_max_drawdown
            ):
                decision = "KEEP"
                reason = "月度已实现毛收益、盈利月份比例和毛回撤达标"
            else:
                decision = "RETRAIN"
                reason = "月度毛收益、盈利月份比例或毛回撤未达标"
        saved = self._state_store.save_model_evaluation(
            model_id=model_id,
            model_version_id=model_version_id,
            business_date=business_date.isoformat(),
            window_observations=window_observations,
            observations=observations,
            positive_period_ratio=positive_period_ratio,
            total_gross_return=total_gross_return,
            total_net_return=total_net_return,
            max_drawdown=max_drawdown,
            decision=decision,
            reason=reason,
        )
        LOGGER.info(
            "模型复盘完成：有效已实现月份=%d/%d；判断=%s",
            observations,
            window_observations,
            saved["decision"],
        )
        return {"monthlyProfit": rows, "decision": saved}


def _compound_return(returns: list[float]) -> float:
    value = 1.0
    for item in returns:
        value *= 1.0 + item
    return value - 1.0


def _max_drawdown(returns: list[float]) -> float:
    equity = 1.0
    peak = 1.0
    drawdown = 0.0
    for item in returns:
        equity *= 1.0 + item
        peak = max(peak, equity)
        drawdown = max(drawdown, 1.0 - equity / peak)
    return drawdown


class BrokerPermissionSyncService:
    """串行执行高价合法数量买入探测并逐股保存权限事实。"""

    def __init__(
        self,
        config: AppConfig,
        store: StateStore,
        broker: ZXBrokerClient,
    ) -> None:
        self._config = config
        self._store = store
        self._broker = broker

    def sync(
        self,
        instruments: list[str],
        observed_at: str,
        force: bool = False,
    ) -> dict[str, object]:
        normalized = list(dict.fromkeys(normalize_instrument(item) for item in instruments))
        normalized_set = set(normalized)
        observation_date = observed_at[:10]
        completed = {} if force else {
            str(item["instrument"]): str(item["status"])
            for item in self._store.broker_stock_permissions()
            if str(item["observed_at"]).startswith(observation_date)
            and str(item["instrument"]) in normalized_set
        }
        pending = [item for item in normalized if item not in completed]
        allowed = sum(status == "ALLOWED" for status in completed.values())
        denied = sum(status == "DENIED" for status in completed.values())
        failures: list[str] = []
        total = len(normalized)
        skipped = total - len(pending)
        if pending:
            self._broker.prepare_permission_probe()
        workers = 1
        LOGGER.info("券商 Token 按响应串行轮换，权限探测固定使用单 worker")
        remaining = list(pending)
        checked = 0
        while remaining:
            batch = remaining[:100]
            remaining = remaining[100:]
            LOGGER.info(
                "逐股权限同步批次开始：worker=%d；批次数量=%d；剩余=%d",
                workers,
                len(batch),
                len(remaining),
            )
            outcomes = self._sync_batch(batch, observed_at, workers)
            retry_instruments: list[str] = []
            auth_failure = False
            for outcome in outcomes:
                instrument = str(outcome["instrument"])
                status = str(outcome.get("status") or "")
                if not status:
                    if outcome.get("failureType") == NON_TRADING_HOURS:
                        raise RuntimeError(
                            f"权限检查终止：券商返回非交易时间，首个股票={instrument}"
                        )
                    retry_instruments.append(instrument)
                    auth_failure = auth_failure or outcome.get("failureType") == "AUTH"
                    continue
                checked += 1
                allowed += status == "ALLOWED"
                denied += status == "DENIED"
                index = skipped + checked
                set_log_context(
                    progress=f"{index}/{total}",
                    target=instrument,
                )
                if status == "DENIED" or index == total or index % 100 == 0:
                    LOGGER.info(
                        "逐股权限同步进度：%d/%d；股票=%s；状态=%s；允许=%d；拒绝=%d",
                        index,
                        total,
                        instrument,
                        status,
                        allowed,
                        denied,
                    )
            if retry_instruments and workers > 1:
                if auth_failure:
                    self._broker.invalidate_token()
                LOGGER.warning(
                    "并发权限探测批次不稳定，降为单线程补测：原worker=%d；待补=%d；认证异常=%s",
                    workers,
                    len(retry_instruments),
                    auth_failure,
                )
                workers = 1
                remaining = retry_instruments + remaining
                continue
            if retry_instruments:
                failures.extend(retry_instruments)
        return {
            "total": total,
            "checked": checked,
            "skipped": skipped,
            "allowed": allowed,
            "denied": denied,
            "failures": failures,
            "observedAt": observed_at,
        }

    def _sync_batch(
        self,
        instruments: list[str],
        observed_at: str,
        workers: int,
    ) -> list[dict[str, object]]:
        if workers != 1:
            raise ValueError("券商权限探测只允许单 worker")
        outcomes: list[dict[str, object]] = []
        for instrument in instruments:
            outcome = self._probe_one(instrument, observed_at)
            outcomes.append(outcome)
            if outcome.get("failureType") == NON_TRADING_HOURS:
                break
        return outcomes

    def _probe_one(
        self,
        instrument: str,
        observed_at: str,
    ) -> dict[str, object]:
        set_log_context(target=instrument)
        scope = permission_scope(instrument)
        if scope is None:
            return self._failure(instrument, "PROTOCOL", "不支持该股票范围")
        result: dict[str, object] | None = None
        for attempt in range(1, 4):
            try:
                result = self._broker.probe_buy_permission(instrument[-6:])
                break
            except (OSError, TimeoutError, ConnectionError) as error:
                if attempt < 3:
                    LOGGER.warning(
                        "逐股权限探测网络异常，等待重试：股票=%s；尝试=%d/3；原因=%s",
                        instrument,
                        attempt,
                        error,
                    )
                    time.sleep(attempt)
                    continue
                return self._failure(instrument, "NETWORK", str(error))
            except (BrokerProtocolError, BrokerSafetyError) as error:
                return self._failure(instrument, "PROTOCOL", str(error))
        if result is None:
            return self._failure(instrument, "PROTOCOL", "券商响应为空")
        message = str(result.get("errorMessage") or "")
        if _is_non_trading_hours(message):
            return self._failure(instrument, NON_TRADING_HOURS, message)
        if _is_auth_failure(message):
            return self._failure(instrument, "AUTH", message)
        if _is_permission_denial(str(result.get("errorNo") or ""), message):
            status = "DENIED"
        else:
            # 权限拒绝优先级最高；其他券商业务拒绝（例如资金不足）
            # 说明请求已进入业务校验链路，按“非权限拒绝即有权限”落库。
            status = "ALLOWED"
        detail = (
            f"code={instrument[-6:]};errorNo={result.get('errorNo') or ''};"
            f"message={message}"
        )[:300]
        self._store.save_broker_stock_permission(
            {
                "instrument": instrument,
                "stockCode": instrument[-6:],
                "scope": scope,
                "status": status,
                "evidenceType": "HIGH_PRICE_PERMISSION_PROBE",
                "errorNo": str(result.get("errorNo") or ""),
                "evidenceDetail": detail,
                "responseJson": json.dumps(
                    result.get("rawResponse") or {},
                    ensure_ascii=False,
                    separators=(",", ":"),
                    default=str,
                ),
                "responseMsg": str(result.get("responseMsg") or message or ""),
                "observedAt": observed_at,
            }
        )
        return {"instrument": instrument, "status": status}

    @staticmethod
    def _failure(
        instrument: str,
        failure_type: str,
        detail: str,
    ) -> dict[str, object]:
        LOGGER.error(
            "逐股权限探测未形成权限结论：股票=%s；类型=%s；原因=%s",
            instrument,
            failure_type,
            detail,
        )
        return {
            "instrument": instrument,
            "failureType": failure_type,
        }


def _is_permission_denial(error_no: str, message: str) -> bool:
    if error_no.strip() not in {"-410011", "410011"}:
        return False
    normalized = message.replace(" ", "")
    return any(
        keyword in normalized
        for keyword in (
            "无权限",
            "没有权限",
            "无相关的交易权限",
            "没有交易权限",
            "未开通",
            "禁止交易",
            "不允许交易",
        )
    )


def _is_non_trading_hours(message: str) -> bool:
    normalized = message.replace(" ", "")
    return any(
        keyword in normalized
        for keyword in (
            "非交易时间",
            "不在交易时间",
            "当前时间不允许交易",
            "闭市",
            "休市",
            "未开市",
        )
    )


def _is_auth_failure(message: str) -> bool:
    normalized = message.replace(" ", "").lower()
    return any(
        keyword in normalized
        for keyword in (
            "token",
            "未登录",
            "请登录",
            "重新登录",
            "登录超时",
            "会话失效",
            "会话过期",
            "身份认证",
        )
    )


def _is_permission_allowed_evidence(message: str) -> bool:
    normalized = message.replace(" ", "")
    return any(
        keyword in normalized
        for keyword in (
            "委托价格",
            "价格超出",
            "价格超过",
            "价格高于",
            "涨停价",
            "涨跌停",
        )
    )

# AI_GENERATE_END----- ----------
