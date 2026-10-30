# AI_GENERATE_START --
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal


@dataclass(frozen=True)
class BrokerAccount:
    available_cash: Decimal
    total_assets: Decimal
    total_position: Decimal
    frozen_amount: Decimal


@dataclass(frozen=True)
class BrokerPosition:
    stock_code: str
    stock_name: str | None
    quantity: int
    available_quantity: int
    frozen_quantity: int
    average_cost: Decimal | None
    current_price: Decimal
    market: str | None


@dataclass(frozen=True)
class BrokerOrder:
    order_id: str | None
    stock_code: str | None
    stock_name: str | None
    direction: str
    order_price: Decimal
    order_quantity: int
    average_fill_price: Decimal | None
    filled_quantity: int | None
    raw_status: str | None
    status: str
    market: str | None
    order_time: datetime | None


@dataclass(frozen=True)
class BrokerFill:
    order_id: str | None
    stock_code: str | None
    stock_name: str | None
    direction: str
    fill_price: Decimal
    fill_quantity: int
    fill_amount: Decimal
    fill_time: datetime | None


@dataclass(frozen=True)
class FiveLevelQuote:
    stock_code: str
    price: Decimal | None
    bids: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]
    asks: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]


@dataclass(frozen=True)
class BrokerSyncSnapshot:
    account: BrokerAccount
    positions: tuple[BrokerPosition, ...]
    orders: tuple[BrokerOrder, ...]
    fills: tuple[BrokerFill, ...]
# AI_GENERATE_END --
