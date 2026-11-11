# AI_GENERATE_START -
from __future__ import annotations

import logging
from datetime import date, time

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.market.domain import live_codes, normalize_instrument, trade_dates
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.state.store import StateStore
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.post_trade import BrokerOrderSyncService, BrokerPermissionSyncService
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.utils.notifications import push_execution_result
from stock_trading.utils.time_utils import shanghai_now, shanghai_today
from stock_trading.state.workflows import _record_task

LOGGER = logging.getLogger(__name__)



def sync_order_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    broker: ZXBrokerClient | None = None,
) -> dict[str, object]:
    """同步券商已成交事实和费用，并完成可闭环的 FIFO 收益。"""
    LOGGER.info("开始同步券商历史订单：日期=%s 至 %s", start_date, end_date)
    store = StateStore(config.state_db_path)
    active_broker = broker or _build_broker(config)
    with _record_task(store, "ORDER", end_date):
        account_snapshot = store.save_account_snapshot(
            active_broker.get_account()
        )
        LOGGER.info(
            "订单同步前账户快照已更新：采集时间=%s；可用资金=%s；"
            "总资产=%s；持仓市值=%s；冻结金额=%s",
            account_snapshot["captured_at"],
            account_snapshot["available_cash"],
            account_snapshot["total_assets"],
            account_snapshot["total_position"],
            account_snapshot["frozen_amount"],
        )
        result = BrokerOrderSyncService(config, store, active_broker).sync(
            start_date, end_date
        )
        result["accountSnapshot"] = account_snapshot
    LOGGER.info(
        "券商历史订单同步完成：日期=%s 至 %s；有效订单=%s；新增闭环=%s",
        start_date,
        end_date,
        result["validOrders"],
        result["settlement"]["realizedTrades"],
    )
    return result



def sync_permission_workflow(
    config: AppConfig,
    broker: ZXBrokerClient | None = None,
    resume: bool = False,
    force: bool = False,
) -> dict[str, object]:
    """对当前全部有效主板股票执行高价合法数量权限探测并逐股落库。"""
    business_date = shanghai_today()
    market_store = ParquetMarketStore(config.market_dir)
    instruments = [
        normalize_instrument(code)
        for code in live_codes(
            market_store.read_stock_basics(),
            business_date,
            include_special_boards=True,
        )
    ]
    if not instruments:
        raise ValueError("股票基础信息中没有可执行权限探测的有效股票")
    instruments = sorted(
        instruments,
        key=lambda item: (0 if item.startswith("SH68") else 1, item),
    )
    store = StateStore(config.state_db_path)
    previous_task = store.task_status("PERMISSION", business_date)
    if resume and previous_task and previous_task.get("status") == "FAILED":
        started_at = str(previous_task.get("started_at") or "")
        completed_in_run = {
            str(item["instrument"])
            for item in store.broker_stock_permissions()
            if started_at and str(item.get("updated_at") or "") >= started_at
        }
        instruments = [
            instrument for instrument in instruments if instrument not in completed_in_run
        ]
        LOGGER.info(
            "续跑上次未完成权限探测：已完成=%d；待检查=%d",
            len(completed_in_run),
            len(instruments),
        )
        if not instruments:
            return {
                "total": 0,
                "checked": 0,
                "skipped": len(completed_in_run),
                "allowed": 0,
                "denied": 0,
                "failures": [],
                "observedAt": shanghai_now().isoformat(timespec="seconds"),
            }
    active_broker = broker or _build_broker(config)
    observed_at = shanghai_now().isoformat(timespec="seconds")
    with _record_task(store, "PERMISSION", business_date):
        result = BrokerPermissionSyncService(
            config, store, active_broker
        ).sync(instruments, observed_at, force=force)
        if result["failures"]:
            raise RuntimeError(
                f"仍有 {len(result['failures'])} 只股票权限探测网络失败，请续跑补齐"
            )
    LOGGER.info(
        "逐股券商权限同步完成：总数=%d；允许=%d；拒绝=%d",
        result["total"],
        result["allowed"],
        result["denied"],
    )
    return result



def trading_workflow(
    config: AppConfig, spec: ModelSpec, business_date: date, side: str
) -> dict[str, object]:
    """选择实时交易目标并逐个执行，返回可供 CLI 输出的完整摘要。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError("交易方向必须是 BUY 或 SELL")
    now = shanghai_now()
    window = spec.strategy.buy_window if side == "BUY" else spec.strategy.sell_window
    start_time, end_time = (time.fromisoformat(value) for value in window)
    if now.date() != business_date or not start_time <= now.time() < end_time:
        action = "买入" if side == "BUY" else "卖出"
        raise ValueError(
            f"{action}只能在 {start_time.strftime('%H:%M')}-"
            f"{end_time.strftime('%H:%M')} 执行"
        )
    dates = trade_dates(business_date.year)
    if business_date not in dates:
        raise ValueError(f"不是交易日: {business_date}")
    if side == "BUY" and business_date == dates[0]:
    mode = f"{spec.data_level}_{side}"
    mode_name = "买入" if side == "BUY" else "卖出"
    LOGGER.info(
        "开始执行实时交易窗口：日期=%s；时段=%s；策略=%s；窗口=%d秒",
        business_date,
        mode_name,
        config.trading_execution_strategy,
        ((end_time.hour * 60 + end_time.minute) - (start_time.hour * 60 + start_time.minute)) * 60,
    )
    store = StateStore(config.state_db_path)
    broker = _build_broker(config)
    account_snapshot = store.save_account_snapshot(broker.get_account())
    LOGGER.info(
        "交易前账户快照已更新：采集时间=%s；可用资金=%s；总资产=%s；持仓市值=%s；冻结金额=%s",
        account_snapshot["captured_at"],
        account_snapshot["available_cash"],
        account_snapshot["total_assets"],
        account_snapshot["total_position"],
        account_snapshot["frozen_amount"],
    )
    execution = OrderExecutionOrchestrator(config, store, broker)
    model = create_research_model(config, spec)
    selection = LiveTradingOrchestrator(
        config, store, broker, spec.model_id, model.qlib_dir
    ).select_targets(business_date, side)
    executed: list[dict[str, object]] = []
    for target in selection["targets"]:
        result = execution.execute_target(business_date, target)
        executed.append(result)
        push_execution_result(config, business_date, result)
    LOGGER.info(
        "实时交易窗口完成：目标数=%d；执行结果数=%d",
        len(selection["targets"]),
        len(executed),
    )
    return {
        "modelId": spec.model_id,
        "businessDate": business_date.isoformat(),
        "mode": mode,
        "strategy": config.trading_execution_strategy,
        "accountSnapshot": account_snapshot,
        "selection": selection,
        "executed": executed,
    }



def _build_broker(config: AppConfig) -> ZXBrokerClient:
    return ZXBrokerClient(config, captcha_solver=BaiduCaptchaSolver(config).solve)


# AI_GENERATE_END----------------------------------------------- -------------------------------------
# AI_GENERATE_END -
