# AI_GENERATE_START --
from __future__ import annotations


def normalize_instrument(value: object, market: str | None = None) -> str:
    """将 BaoStock、券商或 Qlib 代码统一为 SH600000 格式。"""
    raw = str(value or "").strip().upper()
    if "." in raw:
        exchange, symbol = raw.split(".", maxsplit=1)
    elif len(raw) == 8 and raw[:2] in {"SH", "SZ"}:
        exchange, symbol = raw[:2], raw[2:]
    elif len(raw) == 6 and raw.isdigit():
        normalized_market = (market or "").strip().upper()
        if normalized_market.startswith("SH"):
            exchange = "SH"
        elif normalized_market.startswith("SZ"):
            exchange = "SZ"
        else:
            exchange = "SH" if raw.startswith("6") else "SZ"
        symbol = raw
    else:
        raise ValueError(f"不支持的证券代码: {value}")

    if exchange not in {"SH", "SZ"} or len(symbol) != 6 or not symbol.isdigit():
        raise ValueError(f"不支持的证券代码: {value}")
    return f"{exchange}{symbol}"


def to_baostock_code(instrument: object) -> str:
    """将内部标准证券代码转换为 BaoStock 代码。"""
    normalized = normalize_instrument(instrument)
    return f"{normalized[:2].lower()}.{normalized[2:]}"


def to_broker_code(instrument: object) -> str:
    """将内部标准证券代码转换为券商六位证券代码。"""
    return normalize_instrument(instrument)[2:]
# AI_GENERATE_END --
