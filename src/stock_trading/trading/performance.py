# AI_GENERATE_START ----
from __future__ import annotations

import logging
from collections import defaultdict
from datetime import date
from decimal import Decimal
from typing import Protocol

from stock_trading.broker.models import BrokerFill, BrokerSyncSnapshot
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore

LOGGER = logging.getLogger(__name__)


class BrokerPerformanceClient(Protocol):
    def read_shadow_snapshot(self) -> BrokerSyncSnapshot:
        """用途：读取绩效计算所需券商事实；输入：无；输出：券商同步快照。"""
        ...


class LivePerformanceService:
    """Build daily live performance and deterministic model evaluation from broker facts."""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: BrokerPerformanceClient,
    ) -> None:
        """用途：绑定绩效配置、状态库和券商读取器；输入：配置、状态库和券商客户端；输出：无。"""
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def update(
        self,
        business_date: date,
        external_cash_flow: Decimal = Decimal("0"),
    ) -> dict[str, object]:
        """用途：同步券商事实并生成当日绩效和模型评估；输入：业务日期与外部资金流；输出：同步、绩效和评估结果。"""
        # 数据流：券商快照 -> 事实表 -> 日绩效 -> 滚动指标 -> 保持或重训决策。
        LOGGER.info("绩效更新阶段 1/5：读取券商事实；业务日期=%s", business_date)
        snapshot = self._broker.read_shadow_snapshot()
        LOGGER.info(
            "券商事实读取完成：权益=%s；可用资金=%s；持仓市值=%s；成交数=%d",
            snapshot.account.total_assets,
            snapshot.account.available_cash,
            snapshot.account.total_position,
            len(snapshot.fills),
        )
        LOGGER.info("绩效更新阶段 2/5：保存券商事实")
        sync = self._state_store.sync_broker_snapshot(
            snapshot, business_date.isoformat()
        )
        LOGGER.info("绩效更新阶段 3/5：计算当日成交额和估算费用")
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
            LOGGER.info(
                "当日收益计算完成：前日权益=%s；当日权益=%s；净损益=%s；毛损益=%s；净收益率=%s；毛收益率=%s",
                previous_equity,
                equity,
                net_pnl,
                gross_pnl,
                net_return,
                gross_return,
            )
        else:
            LOGGER.info("当日收益暂不可计算：没有上一交易日权益快照")
        LOGGER.info(
            "成交成本汇总：成交额=%s；估算费用=%s；外部资金流=%s",
            turnover,
            estimated_fees,
            external_cash_flow,
        )
        LOGGER.info("绩效更新阶段 4/5：保存当日绩效")
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
        LOGGER.info("绩效更新阶段 5/5：执行滚动模型评估；模型版本=%s", model_version_id)
        evaluation = self._evaluate(business_date, model_version_id)
        LOGGER.info(
            "滚动模型评估完成：决策=%s；原因=%s",
            evaluation["evaluation"]["decision"],
            evaluation["evaluation"]["reason"],
        )
        return {
            "businessDate": business_date.isoformat(),
            "brokerSync": sync,
            "performance": performance,
            "evaluation": evaluation,
        }

    def _estimate_trading_costs(
        self, fills: tuple[BrokerFill, ...], business_date: date
    ) -> tuple[Decimal, Decimal]:
        """用途：按订单聚合当日成交并估算交易费用；输入：成交列表和日期；输出：成交额与估算费用。"""
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
        """用途：计算滚动收益和回撤并形成模型决策；输入：业务日期和模型主键；输出：持久化评估结果。"""
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
        LOGGER.info("滚动评估样本准备完成：有效样本=%d/%d", observations, window_days)
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
        LOGGER.info(
            "滚动评估指标：毛收益正值比例=%s；累计毛收益=%s；累计净收益=%s；最大回撤=%s；决策=%s",
            gross_positive_ratio,
            total_gross_return,
            total_net_return,
            max_drawdown,
            decision,
        )
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
    """用途：计算收益序列的复合收益；输入：单期收益率列表；输出：累计复合收益率。"""
    value = 1.0
    for item in returns:
        value *= 1.0 + item
    return value - 1.0


def _max_drawdown(returns: list[float]) -> float:
    """用途：计算收益序列对应净值的最大回撤；输入：单期收益率列表；输出：最大回撤比例。"""
    equity = 1.0
    peak = 1.0
    drawdown = 0.0
    for item in returns:
        equity *= 1.0 + item
        peak = max(peak, equity)
        drawdown = max(drawdown, 1.0 - equity / peak)
    return drawdown


def _optional_float(value: Decimal | None) -> float | None:
    """用途：转换可空 Decimal；输入：Decimal 或空值；输出：float 或空值。"""
    return float(value) if value is not None else None
# AI_GENERATE_END ----
