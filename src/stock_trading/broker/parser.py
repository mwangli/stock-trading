# AI_GENERATE_START -----
from __future__ import annotations

from datetime import datetime
from decimal import Decimal, InvalidOperation
from typing import Iterable

from stock_trading.broker.models import (
    BrokerAccount,
    BrokerFill,
    BrokerOrder,
    BrokerPosition,
)


def split_row(row: str | None) -> list[str]:
    return [] if row is None else row.split("|")


def is_header_or_empty(row: str | None) -> bool:
    return (
        row is None
        or not row.strip()
        or "委托日期|" in row
        or row.startswith("日期|")
        or "证券代码|证券" in row
    )


def parse_decimal(value: object) -> Decimal | None:
    text = _text(value)
    if text is None:
        return None
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def parse_integer(value: object) -> int | None:
    number = parse_decimal(value)
    return int(number) if number is not None else None


def parse_account_rows(rows: Iterable[str]) -> BrokerAccount | None:
    values = list(rows)
    if len(values) < 2:
        return None
    fields = split_row(values[1])
    if len(fields) < 8:
        return None
    available_cash = parse_decimal(fields[3])
    total_assets = parse_decimal(fields[4])
    total_position = parse_decimal(fields[5])
    frozen_amount = parse_decimal(fields[7])
    if None in (available_cash, total_assets, total_position, frozen_amount):
        return None
    return BrokerAccount(
        available_cash=available_cash,
        total_assets=total_assets,
        total_position=total_position,
        frozen_amount=frozen_amount,
    )


def parse_position_rows(rows: Iterable[str]) -> tuple[BrokerPosition, ...]:
    positions: list[BrokerPosition] = []
    for row in list(rows)[1:]:
        fields = split_row(row)
        if len(fields) < 16:
            continue
        quantity = parse_integer(fields[1])
        available = parse_integer(fields[2])
        current_price = parse_decimal(fields[4])
        if quantity is None or available is None or current_price is None:
            continue
        positions.append(
            BrokerPosition(
                stock_code=_text(fields[9]) or "",
                stock_name=_text(fields[0]),
                quantity=quantity,
                available_quantity=available,
                frozen_quantity=max(0, quantity - available),
                average_cost=parse_decimal(fields[3]),
                current_price=current_price,
                market=_text(fields[15]),
            )
        )
    return tuple(positions)


def parse_order_rows(rows: Iterable[str]) -> tuple[BrokerOrder, ...]:
    orders: list[BrokerOrder] = []
    for row in rows:
        if is_header_or_empty(row):
            continue
        fields = split_row(row)
        if len(fields) < 14:
            continue
        order_price = parse_decimal(fields[7])
        order_quantity = parse_integer(fields[8])
        if order_price is None or order_quantity is None:
            continue
        orders.append(
            BrokerOrder(
                order_id=_text(fields[9]),
                stock_code=_text(fields[2]),
                stock_name=_text(fields[3]),
                direction=_direction(fields[5]),
                order_price=order_price,
                order_quantity=order_quantity,
                average_fill_price=parse_decimal(fields[10]),
                filled_quantity=parse_integer(fields[11]),
                raw_status=_text(fields[6]),
                status=_order_status(fields[6]),
                shareholder_account=_text(fields[12]),
                market=_text(fields[13]),
                order_time=_date_time(fields[0], fields[1]),
            )
        )
    return tuple(orders)


def parse_fill_rows(rows: Iterable[str], today_format: bool) -> tuple[BrokerFill, ...]:
    fills: list[BrokerFill] = []
    for row in rows:
        if is_header_or_empty(row):
            continue
        fields = split_row(row)
        if len(fields) < 12:
            continue
        values = (
            (fields[6], fields[5], fields[0], fields[1], fields[3], fields[2], fields[10], fields[11])
            if today_format
            else (fields[1], fields[4], fields[5], fields[6], fields[7], fields[8], fields[0], fields[11])
        )
        fill = _build_fill(*values)
        if fill is not None:
            fills.append(fill)
    return tuple(fills)


def _build_fill(
    order_id: str,
    stock_code: str,
    stock_name: str,
    direction: str,
    price_value: str,
    quantity_value: str,
    date_value: str,
    time_value: str,
) -> BrokerFill | None:
    price = parse_decimal(price_value)
    quantity = parse_integer(quantity_value)
    if price is None or quantity is None or quantity == 0:
        return None
    quantity = abs(quantity)
    return BrokerFill(
        order_id=_text(order_id),
        stock_code=_text(stock_code),
        stock_name=_text(stock_name),
        direction=_direction(direction),
        fill_price=price,
        fill_quantity=quantity,
        fill_amount=price * quantity,
        fill_time=_date_time(date_value, time_value),
    )


def _direction(value: object) -> str:
    text = _text(value)
    normalized = text.upper() if text is not None else None
    if text == "买入" or normalized in {"B", "BUY"}:
        return "BUY"
    if text == "卖出" or normalized in {"S", "SELL"}:
        return "SELL"
    return "UNKNOWN"


def _order_status(value: object) -> str:
    text = _text(value)
    return {
        "已成": "FILLED",
        "全部成交": "FILLED",
        "已报": "SUBMITTED",
        "已申报": "SUBMITTED",
        "部成": "PARTIAL",
        "部分成交": "PARTIAL",
        "已撤": "CANCELLED",
        "已报待撤": "CANCEL_SUBMITTING",
        "全部撤单": "CANCELLED",
        "废单": "REJECTED",
        "拒绝": "REJECTED",
    }.get(text, "UNKNOWN")


def _date_time(date_value: object, time_value: object) -> datetime | None:
    date_text = _text(date_value)
    time_text = _text(time_value)
    if date_text is None:
        return None
    for date_format in ("%Y-%m-%d", "%Y%m%d"):
        for time_format in ("%H:%M:%S", "%H%M%S") if time_text else (None,):
            try:
                if time_format is None:
                    return datetime.strptime(date_text, date_format)
                return datetime.strptime(f"{date_text} {time_text}", f"{date_format} {time_format}")
            except ValueError:
                continue
    return None


def _text(value: object) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None
# AI_GENERATE_END -----
