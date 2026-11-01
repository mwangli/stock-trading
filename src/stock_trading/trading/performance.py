# AI_GENERATE_START --------
from __future__ import annotations

import logging
from datetime import date, datetime
from decimal import Decimal
from typing import Protocol
from zoneinfo import ZoneInfo

from stock_trading.broker.models import BrokerAccount
from stock_trading.config import AppConfig
from stock_trading.state.store import StateStore
from stock_trading.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)


class PerformanceBrokerClient(Protocol):
    def get_account(self) -> BrokerAccount: ...


class LivePerformanceService:
    """Build daily live performance and deterministic model evaluation from broker facts."""

    def __init__(
        self,
        config: AppConfig,
        state_store: StateStore,
        broker: PerformanceBrokerClient,
    ) -> None:
        """用途：绑定绩效配置和状态库；输入：配置和状态库；输出：无。"""
        self._config = config
        self._state_store = state_store
        self._broker = broker

    def update(
        self,
        business_date: date,
    ) -> dict[str, object]:
        """读取实时账户和已同步订单，生成当日绩效与模型评估。"""
        external_cash_flow = Decimal("0")
        shanghai_today = datetime.now(ZoneInfo("Asia/Shanghai")).date()
        if business_date != shanghai_today:
            raise ValueError(
                f"evaluate 只支持上海当天 {shanghai_today.isoformat()}，不生成历史权益"
            )
        set_log_context(progress="1/4", target="账户和历史订单")
        LOGGER.info("读取券商实时权益和当日历史订单")
        account = self._broker.get_account()
        orders = self._state_store.history_orders_for_date(business_date)
        LOGGER.info(
            "绩效事实读取完成：权益=%s；可用资金=%s；持仓市值=%s；成交订单=%d",
            account.total_assets,
            account.available_cash,
            account.total_position,
            len(orders),
        )
        set_log_context(progress="2/4", target="成交成本")
        LOGGER.info("计算当日成交额和估算费用")
        turnover, estimated_fees = self._estimate_trading_costs(orders)
        previous = self._state_store.latest_live_performance_before(
            business_date.isoformat()
        )
        model_version_id = self._state_store.performance_model_id(
            business_date.isoformat()
        )
        equity = account.total_assets
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
        set_log_context(progress="3/4", target=str(business_date))
        LOGGER.info("保存当日绩效")
        performance = self._state_store.save_live_performance(
            {
                "businessDate": business_date.isoformat(),
                "modelVersionId": model_version_id,
                "cash": float(account.available_cash),
                "marketValue": float(account.total_position),
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
        set_log_context(progress="4/4", target=f"模型={model_version_id}")
        LOGGER.info("执行滚动模型评估")
        evaluation = self._evaluate(business_date, model_version_id)
        LOGGER.info(
            "滚动模型评估完成：决策=%s；原因=%s",
            evaluation["evaluation"]["decision"],
            evaluation["evaluation"]["reason"],
        )
        return {
            "businessDate": business_date.isoformat(),
            "performance": performance,
            "evaluation": evaluation,
        }

    def _estimate_trading_costs(
        self, orders: list[dict[str, object]]
    ) -> tuple[Decimal, Decimal]:
        """按当日已成交订单估算成交额和交易费用。"""
        amounts = [
            (
                str(order["direction"]),
                Decimal(str(order["average_fill_price"]))
                * Decimal(str(order["filled_quantity"])),
            )
            for order in orders
        ]
        turnover = sum((amount for _, amount in amounts), Decimal("0"))
        estimated_fees = Decimal("0")
        commission_rate = Decimal(str(self._config.trading_commission_rate))
        min_commission = Decimal(str(self._config.trading_min_commission))
        transfer_rate = Decimal(str(self._config.trading_transfer_fee_rate))
        stamp_duty_rate = Decimal(str(self._config.trading_stamp_duty_rate))
        for direction, amount in amounts:
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
            model_version_id=model_version_id,
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
# AI_GENERATE_END --------
