# AI_GENERATE_START ------
from __future__ import annotations

import logging
from datetime import date
from pathlib import Path

import pandas as pd

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.instruments import normalize_instrument, to_baostock_code
from stock_trading.market.storage import ParquetMarketStore, ResearchDataAudit
from stock_trading.market.sync import (
    live_codes,
    research_codes,
    sync_collection_data,
    sync_stock_basics,
)
from stock_trading.notifications import push_execution_result
from stock_trading.research.artifacts import model_root, resolve_current_model_dir
from stock_trading.research.model import predict_alpha158_lightgbm
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import refresh_qlib_from_parquet
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
    config: AppConfig,
    start_date: date,
    end_date: date,
    snapshot_date: date,
) -> dict[str, object]:
    """检查股票快照和指定日期范围，只补缺失项并确保 Qlib 有效。"""
    state = StateStore(config.state_db_path)
    business_key = state.start_task("COLLECT", end_date)
    store = ParquetMarketStore(config.data_dir)
    try:
        if store.stock_basics_valid(snapshot_date):
            LOGGER.info("当日股票基础信息快照已存在，跳过下载：日期=%s", snapshot_date)
            basics = {"skipped": True, "snapshotDate": snapshot_date.isoformat()}
        else:
            LOGGER.info("当日股票基础信息快照不存在，开始下载：日期=%s", snapshot_date)
            basics = sync_stock_basics(store, config.baostock_proxy, snapshot_date)

        stocks = store.read_stock_basics()
        daily_mode = start_date == end_date
        codes = (
            live_codes(stocks, end_date)
            if daily_mode
            else research_codes(stocks, start_date, end_date)
        )
        if not codes:
            raise ValueError("股票基础信息中没有满足当前研究窗口的股票")
        coverage_ranges = _stock_coverage_ranges(
            stocks, codes, start_date, end_date
        )
        LOGGER.info(
            "开始检查原始数据：股票数=%d；日期=%s 至 %s；模式=%s",
            len(codes),
            start_date,
            end_date,
            "当日" if daily_mode else "历史范围",
        )
        audit = store.audit_research_data(
            codes, start_date, end_date, coverage_ranges=coverage_ranges
        )
        _log_collection_audit("原始数据缺失清单生成完成", audit)
        if audit.complete:
            collection = {
                "completed": len(codes),
                "skipped": len(codes),
                "failed": {},
            }
            LOGGER.info("原始研究数据完整，跳过下载：股票数=%d", len(codes))
        else:
            collection = sync_collection_data(
                store,
                config.baostock_proxy,
                codes,
                start_date,
                end_date,
                skip_existing=True,
                plans=audit.pending,
            )
            audit = store.audit_research_data(
                codes, start_date, end_date, coverage_ranges=coverage_ranges
            )
            _log_collection_audit("下载后缺失清单复查完成", audit)
            if not audit.complete:
                raise ValueError("原始研究数据下载后仍存在缺失项")

        qlib_refresh = _refresh_collection_qlib(
            config,
            store,
            audit,
            start_date,
            end_date,
            daily_mode,
        )
    except BaseException as error:
        state.finish_task(business_key, False, error)
        raise
    state.finish_task(business_key, True)
    LOGGER.info(
        "股票数据采集完成：股票数=%d；完成=%s；跳过=%s；失败=%d",
        len(codes),
        collection["completed"],
        collection["skipped"],
        len(collection["failed"]),
    )
    return {
        "snapshot": basics,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(codes),
        "collection": collection,
        "qlibRefresh": qlib_refresh,
    }


def _stock_coverage_ranges(
    stocks: pd.DataFrame,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, tuple[date, date, bool]]:
    """按上市和退市日期确定每只股票需要覆盖的实际范围。"""
    frame = stocks.set_index("code", drop=False)
    ranges: dict[str, tuple[date, date, bool]] = {}
    for code in codes:
        row = frame.loc[code]
        ipo_date = row["ipo_date"].date() if pd.notna(row["ipo_date"]) else start_date
        out_date = row["out_date"].date() if pd.notna(row["out_date"]) else None
        code_start = max(start_date, ipo_date)
        code_end = min(end_date, out_date) if out_date else end_date
        ranges[code] = (code_start, code_end, out_date is None or out_date >= end_date)
    return ranges


def _refresh_collection_qlib(
    config: AppConfig,
    store: ParquetMarketStore,
    audit: ResearchDataAudit,
    start_date: date,
    end_date: date,
    daily_mode: bool,
) -> dict[str, object]:
    qlib = QlibBinaryStore(store, config.qlib_dir)
    qlib_start = start_date
    qlib_end = end_date
    qlib_audit: ResearchDataAudit | None = audit
    if daily_mode:
        existing_range = qlib.date_range()
        existing_instruments = qlib.instruments()
        if existing_range is None or existing_instruments is None:
            raise ValueError(
                "Qlib 尚未初始化；请先使用 stock-collect --date 开始日期:结束日期 "
                "准备历史数据"
            )
        daily_instruments = {
            normalize_instrument(code) for code in audit.model_codes
        }
        if (
            daily_instruments.issubset(existing_instruments)
            and qlib.is_date_ready(end_date)
        ):
            LOGGER.info("Qlib 已包含目标交易日，跳过重建：日期=%s", end_date)
            return {"skipped": True, "path": str(config.qlib_dir)}
        qlib_start, existing_end = existing_range
        qlib_end = max(existing_end, end_date)
        qlib_audit = None
        qlib_codes = sorted(
            to_baostock_code(code)
            for code in existing_instruments | daily_instruments
        )
    else:
        qlib_codes = list(qlib_audit.model_codes)
    if qlib.is_valid(qlib_codes, qlib_start, qlib_end):
        LOGGER.info("Qlib 数据检查通过，跳过重建")
        return {"skipped": True, "path": str(config.qlib_dir)}

    LOGGER.info(
        "Qlib 缺少目标交易日，开始刷新：日期=%s 至 %s；股票数=%d",
        qlib_start,
        qlib_end,
        len(qlib_codes),
    )
    if daily_mode:
        qlib_result = qlib.rebuild(qlib_codes, qlib_start, qlib_end)
        result: dict[str, object] = {
            "startDate": qlib_start.isoformat(),
            "endDate": qlib_end.isoformat(),
            "codes": qlib_codes,
            "qlib": qlib_result,
        }
    else:
        result = refresh_qlib_from_parquet(
            config,
            qlib_codes,
            qlib_start,
            qlib_end,
            audit=qlib_audit,
        )
    if not qlib.is_valid(qlib_codes, qlib_start, qlib_end):
        raise ValueError("Qlib 刷新后检查失败")
    return result


def _log_collection_audit(message: str, audit: ResearchDataAudit) -> None:
    LOGGER.info(
        "%s：缺失股票=%d；日线=%d；复权因子=%d；5分钟线=%d",
        message,
        len(audit.pending),
        sum(plan.needs_daily for plan in audit.pending),
        sum(plan.needs_adjustment for plan in audit.pending),
        sum(plan.needs_minute5 for plan in audit.pending),
    )


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


# AI_GENERATE_END ------
