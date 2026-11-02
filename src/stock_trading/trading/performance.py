# AI_GENERATE_START -----------
from __future__ import annotations

import logging
from datetime import date

from stock_trading.config import AppConfig
from stock_trading.logging_utils import set_log_context
from stock_trading.state.store import StateStore

LOGGER = logging.getLogger(__name__)


class RealizedPerformanceService:
    """直接汇总当日已实现交易，不保存重复绩效快照。"""

    def __init__(self, state_store: StateStore) -> None:
        self._state_store = state_store

    def update(self, business_date: date) -> dict[str, object]:
        set_log_context(step="1/1", progress="-/-", target=str(business_date))
        performance = self._state_store.realized_performance(business_date)
        LOGGER.info(
            "已实现绩效计算完成：闭环=%d；数量=%d；成本=%s；卖出额=%s；净收益=%s",
            performance["trade_count"],
            performance["quantity"],
            performance["cost_basis"],
            performance["sell_amount"],
            performance["net_pnl"],
        )
        return {
            "businessDate": business_date.isoformat(),
            "performance": performance,
        }


class ModelEvaluationService:
    """根据已实现绩效序列形成模型保留或重训决策。"""

    def __init__(self, config: AppConfig, state_store: StateStore) -> None:
        self._config = config
        self._state_store = state_store

    def evaluate(self, business_date: date) -> dict[str, object]:
        model_version_id = self._state_store.performance_model_id(
            business_date.isoformat()
        )
        window_observations = self._config.model_evaluation_observations
        rows = self._state_store.recent_realized_performance(
            model_version_id, business_date, window_observations
        )
        observations = len(rows)
        positive_period_ratio: float | None = None
        total_gross_return: float | None = None
        total_net_return: float | None = None
        max_drawdown: float | None = None
        decision = "WARMUP"
        reason = f"有效已实现收益样本 {observations}/{window_observations}"
        if rows:
            gross_returns = [float(row["gross_return"]) for row in rows]
            net_returns = [float(row["net_return"]) for row in rows]
            positive_period_ratio = sum(value > 0 for value in gross_returns) / observations
            total_gross_return = _compound_return(gross_returns)
            total_net_return = _compound_return(net_returns)
            max_drawdown = _max_drawdown(net_returns)
        if observations >= window_observations:
            if (
                total_gross_return is not None
                and total_gross_return > self._config.model_min_total_gross_return
                and max_drawdown is not None
                and max_drawdown <= self._config.model_max_drawdown
            ):
                decision = "KEEP"
                reason = "滚动已实现毛收益达标且回撤未越界"
            else:
                decision = "RETRAIN"
                reason = "滚动已实现收益不达标或回撤越界"
        evaluation = self._state_store.save_model_evaluation(
            model_version_id=model_version_id,
            business_date=business_date.isoformat(),
            window_observations=window_observations,
            observations=observations,
            positive_period_ratio=positive_period_ratio,
            total_gross_return=total_gross_return,
            total_net_return=total_net_return,
            max_drawdown=max_drawdown,
            decision=decision,
            reason=reason,
        )
        LOGGER.info(
            "模型评估完成：有效已实现样本=%d/%d；决策=%s",
            observations,
            window_observations,
            evaluation["decision"],
        )
        return {"businessDate": business_date.isoformat(), "evaluation": evaluation}


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
# AI_GENERATE_END -----------
