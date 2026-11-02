# AI_GENERATE_START -------
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from decimal import Decimal


@dataclass(frozen=True)
class BrokerFeeBreakdown:
    """表示一笔券商成交委托的可审计费用事实。"""

    base_fee: Decimal
    commission: Decimal = Decimal("0")
    stamp_tax: Decimal = Decimal("0")
    other_broker_fee: Decimal = Decimal("0")
    aggregate_total: Decimal | None = None
    source: str = "BUSINESS_ZERO"

    @property
    def total_fee(self) -> Decimal:
        """聚合费用存在时以聚合值为准，否则汇总固定费用和独立明细。"""
        if self.aggregate_total is not None:
            return self.aggregate_total
        return self.base_fee + self.commission + self.stamp_tax + self.other_broker_fee

    @classmethod
    def business_zero(cls, base_fee: Decimal) -> "BrokerFeeBreakdown":
        """在券商未提供费用事实时使用固定费用和零额外费用。"""
        return cls(base_fee=base_fee, source="BUSINESS_ZERO")


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
    fee: BrokerFeeBreakdown | None = None


@dataclass(frozen=True)
class FiveLevelQuote:
    stock_code: str
    price: Decimal | None
    bids: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]
    asks: tuple[Decimal, Decimal, Decimal, Decimal, Decimal]
# AI_GENERATE_END -------
