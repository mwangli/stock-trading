# AI_GENERATE_START ---
from __future__ import annotations

import base64
import binascii
import struct
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from typing import Any
from zoneinfo import ZoneInfo

from stock_trading.broker.models import BrokerMinutePoint, BrokerMinuteSeries

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
    base_raw = _price_base(response)
    start_position = int(str(response.get("STARTPOS") or "0"))
    if start_position == 0 and open_raw > 0 and rows:
        inferred_base = open_raw - rows[0][0]
        if inferred_base <= 0:
            raise ValueError("券商分钟行情价格基准校验失败")
        base_raw = inferred_base

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
# AI_GENERATE_END ---
