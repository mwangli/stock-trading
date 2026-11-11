# AI_GENERATE_START -------
from __future__ import annotations

import base64
import binascii
import struct
from datetime import date, datetime, time, timedelta
from decimal import Decimal, InvalidOperation
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from stock_trading.broker.models import (
    BrokerAccount,
    BrokerMinutePoint,
    BrokerMinuteSeries,
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


SHANGHAI = ZoneInfo("Asia/Shanghai")


def parse_minute_series(
    response: dict[str, Any], stock_code: str
) -> BrokerMinuteSeries:
    """解析 action=20109 的 Base64 分时响应。"""
    grid_value = response.get("GRID0")
    if not isinstance(grid_value, list) or not grid_value:
        raise ValueError("券商分钟行情未返回 GRID0")
    grid = _decode(str(grid_value[0]), "GRID0")
    amounts = _decode(str(response.get("BUYSELL") or ""), "BUYSELL")
    if not grid or len(grid) % 12 != 0:
        raise ValueError("券商分钟行情 GRID0 长度无效")
    count = len(grid) // 12
    if len(amounts) != count * 4:
        raise ValueError("券商分钟行情 BUYSELL 与 GRID0 条数不一致")

    rows = tuple(struct.iter_unpack("<III", grid))
    amount_values = struct.unpack(f"<{count}I", amounts)
    lead_values = _lead_values(response.get("LEAD"), count)
    trade_date = date.fromisoformat(_required_date(response))
    price_unit = Decimal(str(response.get("PRICEUNIT") or "0"))
    if price_unit <= 0:
        raise ValueError("券商分钟行情 PRICEUNIT 无效")

    pre_close_raw, open_raw = _snapshot_prices(response)
    start_position = int(str(response.get("STARTPOS") or "0"))
    try:
        base_raw = _price_base(response)
    except ValueError as error:
        if "价格基准缺失" not in str(error):
            raise
        base_raw = 0
    if (
        base_raw <= 0
        and pre_close_raw <= 0
        and open_raw <= 0
        and all(
            price_offset == 0
            and average_offset == 0
            and volume_lots == 0
            for price_offset, average_offset, volume_lots in rows
        )
    ):
        return BrokerMinuteSeries(
            stock_code=stock_code,
            trade_date=trade_date,
            pre_close=Decimal("0"),
            open_price=Decimal("0"),
            points=(),
        )
    if base_raw <= 0 and start_position == 0 and open_raw > 0 and rows:
        inferred_base = open_raw - rows[0][0]
        if inferred_base <= 0:
            raise ValueError("券商分钟行情价格基准校验失败")
        base_raw = inferred_base
    if base_raw <= 0:
        raise ValueError("券商分钟行情价格基准缺失且无法推导")
    slots = _minute_slots(str(response.get("BEGINDATE") or ""), trade_date)
    selected_slots = slots[start_position : start_position + count]
    if len(selected_slots) != count:
        raise ValueError("券商分钟行情时间槽数量不足")

    points = tuple(
        BrokerMinutePoint(
            timestamp=timestamp,
            price=Decimal(base_raw + price_offset) / price_unit,
            average_price=Decimal(base_raw + average_offset) / price_unit,
            volume_lots=volume_lots,
            amount=Decimal(amount_values[index]),
            lead=lead_values[index] if lead_values is not None else None,
        )
        for index, (
            timestamp,
            (price_offset, average_offset, volume_lots),
        ) in enumerate(zip(selected_slots, rows, strict=True))
    )
    return BrokerMinuteSeries(
        stock_code=stock_code,
        trade_date=trade_date,
        pre_close=Decimal(pre_close_raw) / price_unit,
        open_price=Decimal(open_raw) / price_unit,
        points=points,
    )


def _decode(value: str, field: str) -> bytes:
    try:
        return base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as error:
        raise ValueError(f"券商分钟行情 {field} 无法解码") from error


def _lead_values(value: object, count: int) -> tuple[int, ...] | None:
    if not value:
        return None
    decoded = _decode(str(value), "LEAD")
    if len(decoded) != count * 4:
        raise ValueError("券商分钟行情 LEAD 与 GRID0 条数不一致")
    return struct.unpack(f"<{count}i", decoded)


def _required_date(response: dict[str, Any]) -> str:
    value = str(response.get("ENDDATE") or response.get("HQDATE") or "")
    if len(value) != 8 or not value.isdigit():
        raise ValueError("券商分钟行情交易日期无效")
    return f"{value[:4]}-{value[4:6]}-{value[6:]}"


def _snapshot_prices(response: dict[str, Any]) -> tuple[int, int]:
    parts = str(response.get("BINDATA") or "").split("|")
    if len(parts) < 2:
        raise ValueError("券商分钟行情 BINDATA 无效")
    snapshot = _decode(parts[1], "BINDATA")
    if len(snapshot) < 8:
        raise ValueError("券商分钟行情 BINDATA 长度不足")
    return struct.unpack("<II", snapshot[:8])


def _price_base(response: dict[str, Any]) -> int:
    head = _decode(str(response.get("HEADADD") or ""), "HEADADD")
    if len(head) < 4:
        raise ValueError("券商分钟行情 HEADADD 长度不足")
    head_base = struct.unpack("<I", head[:4])[0]
    low_price = int(str(response.get("LOWPRICE") or "0"))
    if low_price > 0:
        return low_price
    if head_base > 0:
        return head_base
    raise ValueError("券商分钟行情价格基准缺失")


def _minute_slots(value: str, trade_date: date) -> tuple[datetime, ...]:
    parts = [part for part in value.split("|") if part]
    if len(parts) < 4 or len(parts) % 2 != 0:
        raise ValueError("券商分钟行情交易时段无效")
    slots: list[datetime] = []
    for index in range(0, len(parts), 2):
        start = datetime.combine(trade_date, time.fromisoformat(parts[index]), SHANGHAI)
        end = datetime.combine(trade_date, time.fromisoformat(parts[index + 1]), SHANGHAI)
        current = start if index == 0 else start + timedelta(minutes=1)
        while current <= end:
            slots.append(current)
            current += timedelta(minutes=1)
    return tuple(slots)
# AI_GENERATE_END -------
