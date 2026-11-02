# AI_GENERATE_START --
from __future__ import annotations

import logging
from datetime import date, timedelta
from pathlib import Path

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import live_codes, research_codes, sync_collection_data
from stock_trading.notifications import push_execution_result
from stock_trading.research.artifacts import model_root, resolve_current_model_dir
from stock_trading.research.model import predict_alpha158_lightgbm
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import (
    default_training_codes,
    refresh_qlib_from_parquet,
)
from stock_trading.state.store import StateStore
from stock_trading.trading.broker_sync import BrokerOrderSyncService
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.performance import (
    ModelEvaluationService,
    RealizedPerformanceService,
)
from stock_trading.trading.permissions import refresh_permissions

LOGGER = logging.getLogger(__name__)


def collect_workflow(
    config: AppConfig, codes: list[str], start_date: date, end_date: date
) -> dict[str, object]:
    """采集指定股票行情，刷新完整训练范围 Qlib，并记录每日任务状态。"""
    state = StateStore(config.state_db_path)
    business_key = state.start_task("COLLECT", end_date)
    LOGGER.info(
        "开始采集股票数据：股票数=%d；日期=%s 至 %s；连接=1",
        len(codes),
        start_date,
        end_date,
    )
    try:
        result = sync_collection_data(
            ParquetMarketStore(config.data_dir),
            config.baostock_proxy,
            codes,
            start_date,
            end_date,
        )
        research_start = end_date - timedelta(days=config.market_history_days)
        training_codes = default_training_codes(config, research_start, end_date)
        qlib_refresh = refresh_qlib_from_parquet(
            config, training_codes, research_start, end_date
        )
    except BaseException as error:
        state.finish_task(business_key, False, error)
        raise
    state.finish_task(business_key, True)
    LOGGER.info(
        "股票数据采集完成：股票数=%d；完成=%s；跳过=%s；失败=%d",
        len(codes),
        result["completed"],
        result["skipped"],
        len(result["failed"]),
    )
    return {"collection": result, "qlibRefresh": qlib_refresh}


def default_research_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    """从本地股票基础信息确定指定日期范围的默认采集股票池。"""
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到股票基础信息；collect 不会自动执行前置采集，"
            "请显式传入 --codes 或先运行 init"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def cleanup_workflow(config: AppConfig, reference_date: date) -> dict[str, object]:
    """按统一基准日期清理分钟行情和可淘汰状态，不删除长期业务事实。"""
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    market = ParquetMarketStore(config.data_dir).cleanup_minutes(reference_date)
    state = StateStore(config.state_db_path).cleanup(reference_date)
    return {
        "referenceDate": reference_date.isoformat(),
        "market": market,
        "state": state,
    }


def sync_broker_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    broker: ZXBrokerClient | None = None,
) -> dict[str, object]:
    """同步券商已成交事实、费用和权限，并完成可闭环的 FIFO 收益。"""
    LOGGER.info("开始同步券商历史订单：日期=%s 至 %s", start_date, end_date)
    store = StateStore(config.state_db_path)
    business_key = store.start_task("BROKER", end_date)
    try:
        result = BrokerOrderSyncService(
            config, store, broker or _build_broker(config)
        ).sync(start_date, end_date)
        result["permissions"] = refresh_permissions(
            store, store.broker_orders(), end_date
        )
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    LOGGER.info(
        "券商历史订单同步完成：日期=%s 至 %s；有效订单=%s；新增闭环=%s",
        start_date,
        end_date,
        result["validOrders"],
        result["settlement"]["realizedTrades"],
    )
    return result


def rank_workflow(config: AppConfig, prediction_date: date) -> dict[str, object]:
    """校验当日采集结果，生成完整横截面排名并导入 SQLite。"""
    LOGGER.info("开始生成完整横截面预测：预测日期=%s", prediction_date)
    state_store = StateStore(config.state_db_path)
    _assert_task_success(state_store, "COLLECT", prediction_date)
    store = ParquetMarketStore(config.data_dir)
    qlib = QlibBinaryStore(store, config.qlib_dir)
    if not qlib.is_date_ready(prediction_date):
        raise ValueError(
            f"collect 已成功但 Qlib 未覆盖目标日期：{prediction_date.isoformat()}"
        )
    model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    stocks = store.read_stock_basics()
    metadata_codes = live_codes(stocks, prediction_date)
    current_codes = store.tradable_codes_on(metadata_codes, prediction_date)
    prediction = predict_alpha158_lightgbm(
        config.qlib_dir,
        model_dir,
        prediction_date,
        eligible_instruments={normalize_instrument(code) for code in current_codes},
    )
    state = state_store.import_prediction_artifact(
        model_dir, Path(str(prediction["path"]))
    )
    LOGGER.info(
        "横截面预测完成：全市场评分数=%s；排名数=%s；文件=%s",
        prediction["universeRows"],
        prediction["rankingRows"],
        prediction["path"],
    )
    return {"prediction": prediction, "state": state}


def trading_workflow(
    config: AppConfig, business_date: date, side: str
) -> dict[str, object]:
    """选择实时交易目标并逐个执行，返回可供 CLI 输出的完整摘要。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError("交易方向必须是 BUY 或 SELL")
    mode = "AFTERNOON_BUY" if side == "BUY" else "MORNING_SELL"
    mode_name = "下午买入" if side == "BUY" else "上午卖出"
    LOGGER.info(
        "开始执行实时交易窗口：日期=%s；时段=%s；策略=%s；窗口=%d秒",
        business_date,
        mode_name,
        config.trading_execution_strategy,
        config.trading_execution_window_seconds,
    )
    store = StateStore(config.state_db_path)
    broker = _build_broker(config)
    execution = OrderExecutionOrchestrator(config, store, broker)
    selection = LiveTradingOrchestrator(config, store, broker).select_targets(
        business_date, side
    )
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
        "businessDate": business_date.isoformat(),
        "mode": mode,
        "strategy": config.trading_execution_strategy,
        "selection": selection,
        "executed": executed,
    }


def performance_workflow(
    config: AppConfig, business_date: date
) -> dict[str, object]:
    """在券商同步成功后汇总当日已实现收益。"""
    store = StateStore(config.state_db_path)
    _assert_task_success(store, "BROKER", business_date)
    business_key = store.start_task("PERFORMANCE", business_date)
    try:
        result = RealizedPerformanceService(store).update(business_date)
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    return result


def evaluate_workflow(
    config: AppConfig, business_date: date
) -> dict[str, object]:
    """在绩效汇总成功后评估模型，并按结论维护重训请求。"""
    store = StateStore(config.state_db_path)
    _assert_task_success(store, "PERFORMANCE", business_date)
    business_key = store.start_task("EVALUATE", business_date)
    try:
        result = ModelEvaluationService(config, store).evaluate(business_date)
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    return result


def _assert_task_success(
    store: StateStore, task_type: str, business_date: date
) -> None:
    task = store.task_status(task_type, business_date)
    if task is not None and task["status"] == "SUCCESS":
        return
    status = "MISSING" if task is None else str(task["status"])
    reason = "" if task is None else str(task.get("error_message") or "")
    detail = f"；原因={reason}" if reason else ""
    raise ValueError(
        f"前置任务未成功：{task_type}:{business_date.isoformat()}；"
        f"状态={status}{detail}"
    )


def _build_broker(config: AppConfig) -> ZXBrokerClient:
    return ZXBrokerClient(config, captcha_solver=BaiduCaptchaSolver(config).solve)


# AI_GENERATE_END --
