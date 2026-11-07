# AI_GENERATE_START ---------
from __future__ import annotations

import logging
from collections import deque
from dataclasses import replace
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.models import BrokerFeeBreakdown, BrokerOrder
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.trading.strategy import permission_scope
from stock_trading.utils.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)
MONEY_QUANT = Decimal("0.0001")
RATE_QUANT = Decimal("0.00000001")


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

        settlement = IncrementalSettlementService(
            self._config, self._state_store
        ).settle()
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
        set_log_context(step="1/2", progress="-/-", target=str(business_date))
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
        set_log_context(step="2/2", progress="-/-", target=str(business_date))
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


def refresh_permissions(
    store: StateStore,
    orders: list[dict[str, object]],
    observed_date: date,
) -> list[dict[str, object]]:
    """根据历史已受理委托刷新主板交易权限证据。"""
    existing = {
        str(item["scope"]): {
            "scope": str(item["scope"]),
            "status": str(item["status"]),
            "evidenceType": str(item["evidence_type"]),
            "evidenceDetail": str(item["evidence_detail"] or ""),
            "observedAt": str(item["observed_at"]),
        }
        for item in store.broker_permissions()
    }
    records: dict[str, dict[str, str]] = {
        scope: existing.get(scope, _unknown_permission(scope, observed_date))
        for scope in ("SH_MAIN", "SZ_MAIN")
    }
    for order in orders:
        if str(order["status"]) in {"REJECTED", "UNKNOWN"}:
            continue
        scope = permission_scope(order["instrument"])
        if scope is None:
            continue
        observed = (
            str(order["order_time"] or observed_date.isoformat())
        )
        records[scope] = {
            "scope": scope,
            "status": "ALLOWED",
            "evidenceType": "HISTORICAL_ORDER",
            "evidenceDetail": f"历史委托号={order['broker_order_id'] or 'UNKNOWN'}",
            "observedAt": observed,
        }
    saved = store.save_broker_permissions(list(records.values()))
    LOGGER.info(
        "券商权限刷新完成：%s",
        ",".join(f"{row['scope']}={row['status']}" for row in saved),
    )
    return saved


def _unknown_permission(scope: str, observed_date: date) -> dict[str, str]:
    return {
        "scope": scope,
        "status": "UNKNOWN",
        "evidenceType": "NO_CONFIRMED_ORDER",
        "evidenceDetail": "没有找到可确认权限的历史成功委托",
        "observedAt": observed_date.isoformat(),
    }

# AI_GENERATE_END ---------
