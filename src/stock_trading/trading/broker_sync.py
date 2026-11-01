# AI_GENERATE_START ----
from __future__ import annotations

import logging
from collections import deque
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.models import BrokerOrder
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)
MONEY_QUANT = Decimal("0.0001")
RATE_QUANT = Decimal("0.00000001")


class BrokerOrderSyncService:
    """按月同步券商已成交订单，并从完整历史事实重建 FIFO 交易记录。"""

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
            business_key, sync_run_id = self._state_store.begin_history_order_sync(
                window_start, window_end
            )
            try:
                received = self._broker.get_history_orders(window_start, window_end)
                valid_orders = _valid_orders(received, window_start, window_end)
                summary = self._state_store.complete_history_order_sync(
                    business_key,
                    sync_run_id,
                    window_start,
                    window_end,
                    valid_orders,
                )
            except BaseException as error:
                self._state_store.fail_history_order_sync(business_key, error)
                raise
            summary["receivedOrders"] = len(received)
            summary["discardedOrders"] = len(received) - len(valid_orders)
            summaries.append(summary)
            LOGGER.info(
                "历史订单同步窗口完成：日期=%s 至 %s；接收=%d；有效=%d；删除=%d",
                window_start,
                window_end,
                len(received),
                len(valid_orders),
                summary["deletedOrders"],
            )

        trade_summary = self._rebuild_trade_records()
        return {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "windows": summaries,
            "receivedOrders": sum(int(row["receivedOrders"]) for row in summaries),
            "validOrders": sum(int(row["validOrders"]) for row in summaries),
            "discardedOrders": sum(int(row["discardedOrders"]) for row in summaries),
            "insertedOrders": sum(int(row["insertedOrders"]) for row in summaries),
            "updatedOrders": sum(int(row["updatedOrders"]) for row in summaries),
            "deletedOrders": sum(int(row["deletedOrders"]) for row in summaries),
            "tradeRecords": trade_summary,
        }

    def _rebuild_trade_records(self) -> dict[str, int]:
        orders = self._state_store.history_orders()
        open_lots: dict[str, deque[dict[str, object]]] = {}
        records: list[dict[str, object]] = []
        unmatched_sell_quantity = 0

        for order in orders:
            direction = str(order["direction"])
            quantity = int(order["filled_quantity"])
            price = Decimal(str(order["average_fill_price"]))
            instrument = str(order["instrument"])
            if direction == "BUY":
                record = self._new_buy_record(order, quantity, price)
                records.append(record)
                open_lots.setdefault(instrument, deque()).append(record)
                continue
            if direction != "SELL":
                continue
            lots = open_lots.setdefault(instrument, deque())
            unmatched_sell_quantity += self._match_sell(lots, order, quantity, price)

        count = self._state_store.replace_trade_records(records)
        LOGGER.info(
            "FIFO 交易记录重建完成：历史订单=%d；交易记录=%d；未匹配卖出数量=%d",
            len(orders),
            count,
            unmatched_sell_quantity,
        )
        return {
            "recordCount": count,
            "unmatchedSellQuantity": unmatched_sell_quantity,
        }

    def _new_buy_record(
        self, order: dict[str, object], quantity: int, price: Decimal
    ) -> dict[str, object]:
        buy_amount = _money(price * quantity)
        return {
            "buyTradeDate": str(order["trade_date"]),
            "buyOrderId": str(order["broker_order_id"]),
            "sellOrderIds": [],
            "instrument": str(order["instrument"]),
            "stockName": order["stock_name"],
            "market": order["market"],
            "status": "OPEN",
            "buyTime": str(order["order_time"]),
            "firstSellTime": None,
            "lastSellTime": None,
            "buyQuantity": quantity,
            "soldQuantity": 0,
            "remainingQuantity": quantity,
            "buyPrice": price,
            "averageSellPrice": None,
            "buyAmount": buy_amount,
            "soldCostAmount": Decimal("0"),
            "sellAmount": Decimal("0"),
            "buyFee": self._order_fee("BUY", buy_amount),
            "allocatedBuyFee": Decimal("0"),
            "sellFee": Decimal("0"),
            "grossProfit": Decimal("0"),
            "netProfit": Decimal("0"),
            "grossReturnRate": Decimal("0"),
            "returnRate": Decimal("0"),
            "holdingDays": None,
            "dailyReturnRate": Decimal("0"),
            "weightedQuantityDays": Decimal("0"),
        }

    def _match_sell(
        self,
        lots: deque[dict[str, object]],
        order: dict[str, object],
        quantity: int,
        price: Decimal,
    ) -> int:
        remaining = quantity
        sell_amount = _money(price * quantity)
        total_fee = self._order_fee("SELL", sell_amount)
        allocated_fee = Decimal("0")
        while remaining > 0 and lots:
            lot = lots[0]
            matched = min(remaining, int(lot["remainingQuantity"]))
            fee = _money(total_fee * Decimal(matched) / Decimal(quantity))
            remaining_after_match = remaining - matched
            if remaining_after_match == 0:
                fee = total_fee - allocated_fee
            allocated_fee += fee
            self._apply_sell(lot, order, matched, price, fee)
            remaining = remaining_after_match
            if int(lot["remainingQuantity"]) == 0:
                lots.popleft()
        return remaining

    def _apply_sell(
        self,
        record: dict[str, object],
        order: dict[str, object],
        quantity: int,
        price: Decimal,
        sell_fee: Decimal,
    ) -> None:
        sell_time = str(order["order_time"])
        buy_date = date.fromisoformat(str(record["buyTradeDate"]))
        sell_date = date.fromisoformat(str(order["trade_date"]))
        holding_days = max(0, (sell_date - buy_date).days)
        sold_quantity = int(record["soldQuantity"]) + quantity
        remaining_quantity = int(record["buyQuantity"]) - sold_quantity
        sell_order_ids = list(record["sellOrderIds"])
        sell_order_id = str(order["broker_order_id"])
        if sell_order_id not in sell_order_ids:
            sell_order_ids.append(sell_order_id)

        sold_cost = _money(
            Decimal(str(record["soldCostAmount"]))
            + Decimal(str(record["buyPrice"])) * quantity
        )
        total_sell_amount = _money(
            Decimal(str(record["sellAmount"])) + price * quantity
        )
        allocated_buy_fee = _money(
            Decimal(str(record["buyFee"]))
            * Decimal(sold_quantity)
            / Decimal(int(record["buyQuantity"]))
        )
        total_sell_fee = _money(Decimal(str(record["sellFee"])) + sell_fee)
        gross_profit = _money(total_sell_amount - sold_cost)
        net_profit = _money(gross_profit - allocated_buy_fee - total_sell_fee)
        gross_rate = _rate(gross_profit, sold_cost)
        return_rate = _rate(net_profit, sold_cost + allocated_buy_fee)
        weighted_days = Decimal(str(record["weightedQuantityDays"])) + Decimal(
            holding_days * quantity
        )
        average_days = weighted_days / Decimal(sold_quantity)

        record.update(
            {
                "sellOrderIds": sell_order_ids,
                "status": "CLOSED" if remaining_quantity == 0 else "PARTIAL",
                "firstSellTime": record["firstSellTime"] or sell_time,
                "lastSellTime": sell_time,
                "soldQuantity": sold_quantity,
                "remainingQuantity": remaining_quantity,
                "averageSellPrice": _money(total_sell_amount / sold_quantity),
                "soldCostAmount": sold_cost,
                "sellAmount": total_sell_amount,
                "allocatedBuyFee": allocated_buy_fee,
                "sellFee": total_sell_fee,
                "grossProfit": gross_profit,
                "netProfit": net_profit,
                "grossReturnRate": gross_rate,
                "returnRate": return_rate,
                "holdingDays": _money(average_days),
                "dailyReturnRate": _daily_rate(return_rate, average_days),
                "weightedQuantityDays": weighted_days,
            }
        )

    def _order_fee(self, direction: str, amount: Decimal) -> Decimal:
        commission = max(
            Decimal(str(self._config.trading_min_commission)),
            amount * Decimal(str(self._config.trading_commission_rate)),
        )
        fee = commission + amount * Decimal(
            str(self._config.trading_transfer_fee_rate)
        )
        if direction == "SELL":
            fee += amount * Decimal(str(self._config.trading_stamp_duty_rate))
        return _money(fee)


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


def _daily_rate(return_rate: Decimal, holding_days: Decimal) -> Decimal:
    effective_days = max(1.0, float(holding_days))
    compound_base = 1.0 + float(return_rate)
    if compound_base <= 0:
        return Decimal("-1").quantize(RATE_QUANT)
    return Decimal(str(compound_base ** (1.0 / effective_days) - 1.0)).quantize(
        RATE_QUANT, rounding=ROUND_HALF_UP
    )
# AI_GENERATE_END ----
