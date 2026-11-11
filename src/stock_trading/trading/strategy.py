# AI_GENERATE_START --------
from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal, ROUND_FLOOR

from stock_trading.broker.models import FiveLevelQuote
from stock_trading.config import FIXED_TIME_URGENCY_STRATEGY
from stock_trading.market.domain import normalize_instrument

LOT_SIZE = 100


@dataclass(frozen=True)
class ExecutionPriceDecision:
    """固定时间紧迫度策略的一次实时定价结果。"""

    strategy: str
    stage: str
    price_level: str
    price: Decimal


def select_execution_price(
    quote: FiveLevelQuote,
    side: str,
    elapsed_seconds: float,
    window_seconds: float,
    passive_fraction: float,
    normal_fraction: float,
) -> ExecutionPriceDecision:
    """按执行窗口剩余时间，从被动报价逐步切换到主动报价。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError(f"不支持的交易方向: {side}")
    progress = min(1.0, max(0.0, elapsed_seconds / max(window_seconds, 1.0)))
    if progress < passive_fraction:
        stage = "PATIENT"
        level = "BID3" if side == "BUY" else "ASK3"
        price = quote.bids[2] if side == "BUY" else quote.asks[2]
    elif progress < normal_fraction:
        stage = "NORMAL"
        level = "BID1" if side == "BUY" else "ASK1"
        price = quote.bids[0] if side == "BUY" else quote.asks[0]
    else:
        stage = "URGENT"
        level = "ASK1" if side == "BUY" else "BID1"
        price = quote.asks[0] if side == "BUY" else quote.bids[0]
    return ExecutionPriceDecision(
        strategy=FIXED_TIME_URGENCY_STRATEGY,
        stage=stage,
        price_level=level,
        price=price,
    )


def affordable_quantity(
    cash: Decimal,
    price: Decimal,
    order_fee: Decimal,
) -> int:
    """按实时资金、固定订单费用和 100 股整手计算最大可买数量。"""
    if cash <= 0 or price <= 0:
        return 0
    lots = (cash / (price * LOT_SIZE)).to_integral_value(rounding=ROUND_FLOOR)
    quantity = int(lots) * LOT_SIZE
    while quantity > 0:
        if price * quantity + order_fee <= cash:
            return quantity
        quantity -= LOT_SIZE
    return 0
def permission_scope(instrument: str | None) -> str | None:
    """把券商、行情或 Qlib 股票代码统一映射为交易权限范围。"""
    try:
        normalized = normalize_instrument(instrument)
    except ValueError:
        return None
    if normalized.startswith("SH60"):
        return "SH_MAIN"
    if normalized.startswith("SH68"):
        return "SH_STAR"
    if normalized.startswith("SZ00"):
        return "SZ_MAIN"
    if normalized.startswith("SZ30"):
        return "SZ_GEM"
    return None

# AI_GENERATE_END --------
