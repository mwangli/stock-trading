# AI_GENERATE_START ------
from __future__ import annotations

from dataclasses import dataclass, field
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
    shareholder_account: str | None = field(repr=False)
    market: str | None
    order_time: datetime | None


@dataclass(frozen=True)
class FiveLevelQuote:
    stock_code: str
    price: Decimal | None
    bids: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]
    asks: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]
# AI_GENERATE_END ------
