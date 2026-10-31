# AI_GENERATE_START --
from __future__ import annotations

import logging
from datetime import date

from stock_trading.broker.models import BrokerOrder
from stock_trading.state.store import StateStore

LOGGER = logging.getLogger(__name__)


def permission_scope(instrument: str | None) -> str | None:
    """把券商、BaoStock 或 Qlib 股票代码统一映射为交易权限范围。"""
    normalized = str(instrument or "").upper().replace(".", "")
    if normalized.startswith(("SH60", "60")):
        return "SH_MAIN"
    if normalized.startswith(("SZ00", "00")):
        return "SZ_MAIN"
    return None


def refresh_permissions(
    store: StateStore,
    orders: tuple[BrokerOrder, ...] | list[BrokerOrder],
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
        if order.status in {"REJECTED", "UNKNOWN"}:
            continue
        scope = permission_scope(order.stock_code)
        if scope is None:
            continue
        observed = (
            order.order_time.isoformat()
            if order.order_time
            else observed_date.isoformat()
        )
        records[scope] = {
            "scope": scope,
            "status": "ALLOWED",
            "evidenceType": "HISTORICAL_ORDER",
            "evidenceDetail": f"历史委托号={order.order_id or 'UNKNOWN'}",
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
# AI_GENERATE_END --