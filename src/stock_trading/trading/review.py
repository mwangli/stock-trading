# AI_GENERATE_START -
from __future__ import annotations

import logging
from datetime import date

from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.utils.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)


class RealizedProfitService:
    """汇总指定卖出日已经闭环的真实收益。"""

    def __init__(self, state_store: StateStore) -> None:
        self._state_store = state_store

    def summarize(self, business_date: date) -> dict[str, object]:
        set_log_context(step="1/2", progress="-/-", target=str(business_date))
        profit = self._state_store.realized_performance(business_date)
        LOGGER.info(
            "已实现收益汇总完成：闭环=%d；数量=%d；成本=%s；卖出额=%s；净收益=%s",
            profit["trade_count"],
            profit["quantity"],
            profit["cost_basis"],
            profit["sell_amount"],
            profit["net_pnl"],
        )
        return profit


class ModelReviewService:
    """根据已实现月度毛收益判断模型是否继续使用或重新训练。"""

    def __init__(self, config: AppConfig, state_store: StateStore) -> None:
        self._config = config
        self._state_store = state_store

    def review(self, business_date: date) -> dict[str, object]:
        set_log_context(step="2/2", progress="-/-", target=str(business_date))
        model_version_id = self._state_store.performance_model_id(
            business_date.isoformat()
        )
        window_observations = self._config.model_evaluation_observations
        rows = self._state_store.recent_monthly_realized_performance(
            model_version_id, business_date, window_observations
        )
        observations = len(rows)
        positive_period_ratio: float | None = None
        total_gross_return: float | None = None
        total_net_return: float | None = None
        max_drawdown: float | None = None
        decision = "WARMUP"
        reason = f"有效已实现收益月份 {observations}/{window_observations}"
        if rows:
            gross_returns = [float(row["gross_return"]) for row in rows]
            net_returns = [float(row["net_return"]) for row in rows]
            positive_period_ratio = sum(value > 0 for value in gross_returns) / observations
            total_gross_return = _compound_return(gross_returns)
            total_net_return = _compound_return(net_returns)
            max_drawdown = _max_drawdown(gross_returns)
        if observations >= window_observations:
            if (
                total_gross_return is not None
                and total_gross_return > self._config.model_min_total_gross_return
                and positive_period_ratio is not None
                and positive_period_ratio
                >= self._config.model_min_positive_month_ratio
                and max_drawdown is not None
                and max_drawdown <= self._config.model_max_drawdown
            ):
                decision = "KEEP"
                reason = "月度已实现毛收益、盈利月份比例和毛回撤达标"
            else:
                decision = "RETRAIN"
                reason = "月度毛收益、盈利月份比例或毛回撤未达标"
        saved = self._state_store.save_model_evaluation(
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
            "模型复盘完成：有效已实现月份=%d/%d；判断=%s",
            observations,
            window_observations,
            saved["decision"],
        )
        return {"monthlyProfit": rows, "decision": saved}


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
# AI_GENERATE_END -
