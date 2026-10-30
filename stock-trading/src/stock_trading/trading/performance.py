# AI_GENERATE_START --
from __future__ import annotations

from collections import defaultdict
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.models import BrokerFill, BrokerSyncSnapshot
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore


class BrokerPerformanceClient(Protocol):
    def read_shadow_snapshot(self) -> BrokerSyncSnapshot: ...


class LivePerformanceService:
    """Build daily live performance and deterministic model evaluation from broker facts."""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerPerformanceClient,
    ) -> None:
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def update(
        self,
        business_date: date,
        external_cash_flow: Decimal = Decimal("0"),
    ) -> dict[str, object]:
        snapshot = self._broker.read_shadow_snapshot()
        sync = self._state_store.sync_broker_snapshot(
            snapshot, business_date.isoformat()
        )
        turnover, estimated_fees = self._estimate_trading_costs(
            snapshot.fills, business_date
        )
        previous = self._state_store.latest_live_performance_before(
            business_date.isoformat()
        )
        model_version_id = self._state_store.active_model_id()
        equity = snapshot.account.total_assets
        net_pnl: Decimal | None = None
        gross_pnl: Decimal | None = None
        net_return: Decimal | None = None
        gross_return: Decimal | None = None
        if previous is not None:
            previous_equity = Decimal(str(previous["equity"]))
            if previous_equity <= 0:
                raise ValueError("上一交易日权益必须大于零")
            net_pnl = equity - previous_equity - external_cash_flow
            gross_pnl = net_pnl + estimated_fees
            net_return = net_pnl / previous_equity
            gross_return = gross_pnl / previous_equity
        performance = self._state_store.save_live_performance(
            {
                "businessDate": business_date.isoformat(),
                "modelVersionId": model_version_id,
                "cash": float(snapshot.account.available_cash),
                "marketValue": float(snapshot.account.total_position),
                "equity": float(equity),
                "externalCashFlow": float(external_cash_flow),
                "turnover": float(turnover),
                "estimatedFees": float(estimated_fees),
                "grossPnl": _optional_float(gross_pnl),
                "netPnl": _optional_float(net_pnl),
                "grossReturn": _optional_float(gross_return),
                "netReturn": _optional_float(net_return),
            }
        )
        evaluation = self._evaluate(business_date, model_version_id)
        return {
            "businessDate": business_date.isoformat(),
            "brokerSync": sync,
            "performance": performance,
            "evaluation": evaluation,
        }

    def _estimate_trading_costs(
        self, fills: tuple[BrokerFill, ...], business_date: date
    ) -> tuple[Decimal, Decimal]:
        grouped_amounts: dict[tuple[str, str], Decimal] = defaultdict(Decimal)
        for index, fill in enumerate(fills):
            if fill.fill_time is not None and fill.fill_time.date() != business_date:
                continue
            key = (fill.order_id or f"UNBOUND:{index}", fill.direction)
            grouped_amounts[key] += fill.fill_amount
        turnover = sum(grouped_amounts.values(), Decimal("0"))
        estimated_fees = Decimal("0")
        commission_rate = Decimal(str(self._config.trading_commission_rate))
        min_commission = Decimal(str(self._config.trading_min_commission))
        transfer_rate = Decimal(str(self._config.trading_transfer_fee_rate))
        stamp_duty_rate = Decimal(str(self._config.trading_stamp_duty_rate))
        for (_, direction), amount in grouped_amounts.items():
            estimated_fees += max(min_commission, amount * commission_rate)
            estimated_fees += amount * transfer_rate
            if direction == "SELL":
                estimated_fees += amount * stamp_duty_rate
        return turnover, estimated_fees

    def _evaluate(
        self, business_date: date, model_version_id: int | None
    ) -> dict[str, object]:
        window_days = self._config.model_evaluation_window_days
        rows = self._state_store.recent_live_performance(
            model_version_id, window_days
        )
        observations = len(rows)
        gross_positive_ratio: float | None = None
        total_gross_return: float | None = None
        total_net_return: float | None = None
        max_drawdown: float | None = None
        decision = "WARMUP"
        reason = f"有效收益样本 {observations}/{window_days}"
        if rows:
            gross_returns = [float(row["gross_return"]) for row in rows]
            net_returns = [float(row["net_return"]) for row in rows]
            gross_positive_ratio = sum(value > 0 for value in gross_returns) / observations
            total_gross_return = _compound_return(gross_returns)
            total_net_return = _compound_return(net_returns)
            max_drawdown = _max_drawdown(net_returns)
        if observations >= window_days:
            if (
                total_gross_return is not None
                and total_gross_return
                > self._config.model_min_total_gross_return
                and max_drawdown is not None
                and max_drawdown <= self._config.model_max_drawdown
            ):
                decision = "KEEP"
                reason = "滚动毛收益为正且净值回撤未越界"
            else:
                decision = "RETRAIN"
                reason = "滚动毛收益不达标或净值回撤越界"
        return self._state_store.save_model_evaluation(
            business_date=business_date.isoformat(),
            window_days=window_days,
            observations=observations,
            gross_positive_ratio=gross_positive_ratio,
            total_gross_return=total_gross_return,
            total_net_return=total_net_return,
            max_drawdown=max_drawdown,
            decision=decision,
            reason=reason,
        )


def _compound_return(returns: list[float]) -> float:
    value = 1.0
    for item in returns:
        value *= 1.0 + item
    return value - 1.0


def _max_drawdown(returns: list[float]) -> float:
    equity = 1.0
    peak = 1.0
    drawdown = 0.0
    for item in returns:
        equity *= 1.0 + item
        peak = max(peak, equity)
        drawdown = max(drawdown, 1.0 - equity / peak)
    return drawdown


def _optional_float(value: Decimal | None) -> float | None:
    return float(value) if value is not None else None
# AI_GENERATE_END --
