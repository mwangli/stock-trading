# AI_GENERATE_START ------------------------------
from __future__ import annotations

import json
import logging
from datetime import date, time
from pathlib import Path

import pandas as pd

from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.config import AppConfig
from stock_trading.utils.instruments import normalize_instrument
from stock_trading.market.storage import ParquetMarketStore, ResearchDataAudit
from stock_trading.market.quality import (
    audit_research_quality,
    require_data_quality,
)
from stock_trading.market.providers import market_provider
from stock_trading.market.sync import (
    live_codes,
    research_codes,
    sync_daily,
    sync_factors,
    sync_minute5,
    sync_stock_basics,
    trading_dates_between,
)
from stock_trading.utils.notifications import push_execution_result
from stock_trading.research.artifacts import (
    model_root,
    prediction_artifact_path,
    prediction_artifact_valid,
    resolve_current_model_dir,
)
from stock_trading.research.model import predict_alpha158_lightgbm
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import activate_trained_model
from stock_trading.state.store import StateStore
from stock_trading.trading.broker_sync import BrokerOrderSyncService
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.review import (
    ModelReviewService,
    RealizedProfitService,
)
from stock_trading.trading.permissions import refresh_permissions
from stock_trading.utils.time_utils import shanghai_now

LOGGER = logging.getLogger(__name__)


def collect_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    snapshot_date: date,
    requested_codes: list[str] | None = None,
    mode: str = "daily",
) -> dict[str, object]:
    """按模式采集日线研究数据或5分钟行情。"""
    if mode not in {"daily", "minute5"}:
        raise ValueError("采集模式只能是 daily 或 minute5")
    state = StateStore(config.state_db_path)
    task_type = "COLLECT_DAILY" if mode == "daily" else "COLLECT_MINUTE5"
    business_key = state.start_task(task_type, end_date)
    store = ParquetMarketStore(config.data_dir)
    try:
        result = (
            _collect_daily_data(
                config,
                store,
                start_date,
                end_date,
                snapshot_date,
                requested_codes,
            )
            if mode == "daily"
            else _collect_minute5_data(
                config, store, start_date, end_date, requested_codes
            )
        )
    except BaseException as error:
        state.finish_task(business_key, False, error)
        raise
    state.finish_task(business_key, True)
    LOGGER.info(
        "股票数据采集完成：模式=%s；股票数=%d",
        mode,
        result["researchCodes"],
    )
    return result


def _collect_daily_data(
    config: AppConfig,
    store: ParquetMarketStore,
    start_date: date,
    end_date: date,
    snapshot_date: date,
    requested_codes: list[str] | None,
) -> dict[str, object]:
    errors: dict[str, str] = {}
    collection: dict[str, object] = {}
    provider = market_provider(config, config.daily_market_provider)
    if store.stock_basics_valid(snapshot_date):
        collection["basic"] = {
            "skipped": True,
            "snapshotDate": snapshot_date.isoformat(),
        }
    else:
        try:
            collection["basic"] = sync_stock_basics(
                store, provider, snapshot_date
            )
        except Exception as error:
            _record_collection_error(errors, "basic", error)
            collection["basic"] = {"failed": True, "error": str(error)}

    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        _record_collection_error(errors, "basic", error)
        raise ValueError(_collection_error_message(errors)) from error
    codes = _collection_codes(stocks, start_date, end_date, requested_codes)
    dates = trading_dates_between(
        store, provider, start_date, end_date
    )
    if not dates:
        raise ValueError("指定日期范围内没有交易日")

    audit = audit_research_quality(store, stocks, codes, start_date, end_date)
    missing_daily = sum(plan.needs_daily for plan in audit.pending)
    daily_coverage = (len(codes) - missing_daily) / len(codes)
    if missing_daily:
        try:
            collection["daily"] = sync_daily(
                store, provider, stocks, codes, dates
            )
        except Exception as error:
            _record_collection_error(errors, "daily", error)
            collection["daily"] = {"failed": True, "error": str(error)}
    else:
        collection["daily"] = {
            "skipped": True,
            "codes": len(codes) - missing_daily,
            "missingCodes": missing_daily,
            "coverageRate": daily_coverage,
        }

    audit = audit_research_quality(store, stocks, codes, start_date, end_date)
    if any(plan.needs_factor for plan in audit.pending):
        try:
            collection["factor"] = sync_factors(
                store, provider, codes, dates
            )
        except Exception as error:
            _record_collection_error(errors, "factor", error)
            collection["factor"] = {"failed": True, "error": str(error)}
    else:
        collection["factor"] = {"skipped": True, "codes": len(codes)}

    audit = audit_research_quality(store, stocks, codes, start_date, end_date)
    _log_collection_audit("日线和复权因子独立检查完成", audit)
    quality = {
        "basic": _basic_quality(store, snapshot_date),
        "daily": _component_quality(
            "collect-daily",
            codes,
            audit,
            "needs_daily",
            config.data_quality_min_ratio,
            errors,
        ),
        "factor": _component_quality(
            "collect-factor",
            codes,
            audit,
            "needs_factor",
            config.data_quality_min_ratio,
            errors,
        ),
    }
    if not quality["basic"]["valid"]:
        errors.setdefault("basic", "股票基础信息快照检查失败")
    if errors:
        raise ValueError(_collection_error_message(errors))
    return {
        "mode": "daily",
        "provider": provider.name,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(codes),
        "collection": collection,
        "dataQuality": quality,
    }


def _collect_minute5_data(
    config: AppConfig,
    store: ParquetMarketStore,
    start_date: date,
    end_date: date,
    requested_codes: list[str] | None,
) -> dict[str, object]:
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError("未找到股票基础信息；请先执行 stock-collect --daily") from error
    codes = _collection_codes(stocks, start_date, end_date, requested_codes)
    provider = market_provider(config, config.minute5_market_provider)
    result = sync_minute5(store, provider, codes, start_date, end_date)
    require_full_end = _requires_complete_end_window(end_date)
    usable = sum(
        store.minute_summaries_valid(
            code, start_date, end_date, require_full_end
        )
        for code in codes
    )
    quality = require_data_quality(
        "collect-minute5",
        len(codes),
        usable,
        config.data_quality_min_ratio,
    )
    return {
        "mode": "minute5",
        "provider": provider.name,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(codes),
        "collection": {"minute5": result},
        "dataQuality": {"minute5": quality},
    }


def _collection_codes(
    stocks: pd.DataFrame,
    start_date: date,
    end_date: date,
    requested_codes: list[str] | None,
) -> list[str]:
    available_codes = (
        live_codes(stocks, end_date)
        if start_date == end_date
        else research_codes(stocks, start_date, end_date)
    )
    if requested_codes:
        unknown = sorted(set(requested_codes) - set(available_codes))
        if unknown:
            raise ValueError(f"股票不在当前采集范围：{','.join(unknown)}")
        return requested_codes
    if not available_codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的股票")
    return available_codes


def _basic_quality(
    store: ParquetMarketStore, snapshot_date: date
) -> dict[str, object]:
    return {
        "stage": "collect-basic",
        "valid": bool(store.stock_basics_valid(snapshot_date)),
        "snapshotDate": snapshot_date.isoformat(),
    }


def _component_quality(
    stage: str,
    codes: list[str],
    audit: ResearchDataAudit,
    missing_field: str,
    threshold: float,
    errors: dict[str, str],
) -> dict[str, object]:
    missing = sum(bool(getattr(plan, missing_field)) for plan in audit.pending)
    try:
        return require_data_quality(stage, len(codes), len(codes) - missing, threshold)
    except ValueError as error:
        errors[stage] = str(error)
        return {
            "stage": stage,
            "expectedCodes": len(codes),
            "usableCodes": len(codes) - missing,
            "missingCodes": missing,
            "coverageRate": (len(codes) - missing) / len(codes),
            "threshold": threshold,
        }


def _record_collection_error(
    errors: dict[str, str], component: str, error: Exception
) -> None:
    errors[component] = str(error)
    LOGGER.error("采集阶段失败：类型=%s；原因=%s", component, error)


def _collection_error_message(errors: dict[str, str]) -> str:
    details = "；".join(
        f"{component}={message}" for component, message in errors.items()
    )
    return f"daily 采集未完整成功，已保留成功写入的数据：{details}"


def prepare_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    force: bool = False,
) -> dict[str, object]:
    """只读取原始 Parquet，幂等准备指定范围的 Qlib。"""
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError("未找到股票基础信息；请先执行 stock-collect --daily") from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    LOGGER.info(
        "开始检查 Qlib 原始数据：股票数=%d；日期=%s 至 %s",
        len(codes),
        start_date,
        end_date,
    )
    audit = audit_research_quality(
        store, stocks, codes, start_date, end_date
    )
    _log_collection_audit("Qlib 原始数据检查完成", audit)
    ready_codes = store.valid_research_codes(
        codes,
        start_date,
        end_date,
        require_full_end=_requires_complete_end_window(end_date),
    )
    quality = require_data_quality(
        "prepare",
        len(codes),
        len(ready_codes),
        config.data_quality_min_ratio,
        audit,
    )
    prepared = QlibBinaryStore(store, config.qlib_dir).prepare(
        ready_codes, start_date, end_date, force=force
    )
    LOGGER.info(
        "Qlib 准备完成：模式=%s；股票数=%d；日期=%s 至 %s",
        prepared["mode"],
        prepared["codes"],
        prepared["startDate"],
        prepared["endDate"],
    )
    return {
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(ready_codes),
        "dataQuality": quality,
        "qlib": prepared,
    }


def _log_collection_audit(message: str, audit: ResearchDataAudit) -> None:
    LOGGER.info(
        "%s：缺失股票=%d；日线=%d；复权因子=%d；5分钟线=%d",
        message,
        len(audit.pending),
        sum(plan.needs_daily for plan in audit.pending),
        sum(plan.needs_factor for plan in audit.pending),
        sum(plan.needs_minute5 for plan in audit.pending),
    )


def _requires_complete_end_window(end_date: date) -> bool:
    now = shanghai_now()
    return end_date < now.date() or (
        end_date == now.date() and now.time() >= time(15, 0)
    )


def cleanup_workflow(config: AppConfig, reference_date: date) -> dict[str, object]:
    """按统一基准日期清理可淘汰状态，不删除长期业务事实。"""
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    market = ParquetMarketStore(config.data_dir).cleanup_minutes(reference_date)
    state = StateStore(config.state_db_path).cleanup(reference_date)
    return {
        "referenceDate": reference_date.isoformat(),
        "market": market,
        "state": state,
    }


def sync_order_workflow(
    config: AppConfig,
    start_date: date,
    end_date: date,
    broker: ZXBrokerClient | None = None,
) -> dict[str, object]:
    """同步券商已成交事实、费用和权限，并完成可闭环的 FIFO 收益。"""
    LOGGER.info("开始同步券商历史订单：日期=%s 至 %s", start_date, end_date)
    store = StateStore(config.state_db_path)
    business_key = store.start_task("ORDER", end_date)
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
    _assert_task_success(state_store, "COLLECT_MINUTE5", prediction_date)
    store = ParquetMarketStore(config.data_dir)
    qlib = QlibBinaryStore(store, config.qlib_dir)
    stocks = store.read_stock_basics()
    metadata_codes = live_codes(stocks, prediction_date)
    current_codes = store.minute5_codes_on(metadata_codes, prediction_date)
    expected_instruments = {
        normalize_instrument(code) for code in current_codes
    }
    available_instruments = qlib.available_instruments(prediction_date)
    if available_instruments is None:
        raise ValueError(
            f"Qlib 未覆盖目标日期：{prediction_date.isoformat()}；"
            "请先执行 stock-prepare"
        )
    ready_instruments = expected_instruments & available_instruments
    quality = require_data_quality(
        "rank",
        len(expected_instruments),
        len(ready_instruments),
        config.data_quality_min_ratio,
    )
    model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    activation = activate_trained_model(config, model_dir)
    prediction_path = prediction_artifact_path(model_dir, prediction_date)
    existing = _existing_prediction_summary(
        model_dir,
        prediction_path,
        prediction_date,
        ready_instruments,
    )
    if existing is None:
        prediction = predict_alpha158_lightgbm(
            config.qlib_dir,
            model_dir,
            prediction_date,
            eligible_instruments=ready_instruments,
        )
        state = state_store.import_prediction_artifact(
            model_dir, Path(str(prediction["path"]))
        )
    else:
        prediction = existing
        if state_store.prediction_registered(
            model_dir / "model.pkl", prediction_date.isoformat()
        ):
            state = {
                "status": "SUCCESS",
                "predictionDate": prediction_date.isoformat(),
                "predictions": prediction["rankingRows"],
                "reused": True,
            } | state_store.summary()
        else:
            state = state_store.import_prediction_artifact(
                model_dir, prediction_path
            )
        LOGGER.info(
            "现有横截面排名检查通过，跳过重复计算：日期=%s；股票数=%d",
            prediction_date,
            prediction["rankingRows"],
        )
    LOGGER.info(
        "横截面预测完成：全市场评分数=%s；排名数=%s；文件=%s",
        prediction["universeRows"],
        prediction["rankingRows"],
        prediction["path"],
    )
    return {
        "prediction": prediction,
        "activation": activation,
        "state": state,
        "dataQuality": quality,
    }


def _existing_prediction_summary(
    model_dir: Path,
    path: Path,
    prediction_date: date,
    expected_instruments: set[str],
) -> dict[str, object] | None:
    if not prediction_artifact_valid(model_dir, prediction_date):
        return None
    frame = pd.read_parquet(path)
    actual_instruments = {
        normalize_instrument(value) for value in frame["instrument"]
    }
    if actual_instruments != expected_instruments:
        return None
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    return {
        "model": metrics["model"],
        "featureSet": metrics["featureSet"],
        "predictionDate": prediction_date.isoformat(),
        "universeRows": len(frame),
        "rankingRows": len(frame),
        "path": str(path),
        "topCandidates": [
            {
                "instrument": str(row.instrument),
                "rank": int(row.rank),
                "score": float(row.score),
            }
            for row in frame.head(5).itertuples(index=False)
        ],
    }


def trading_workflow(
    config: AppConfig, business_date: date, side: str
) -> dict[str, object]:
    """选择实时交易目标并逐个执行，返回可供 CLI 输出的完整摘要。"""
    if side not in {"BUY", "SELL"}:
        raise ValueError("交易方向必须是 BUY 或 SELL")
    now = shanghai_now()
    start_time, end_time = (
        (time(14, 0), time(15, 0))
        if side == "BUY"
        else (time(13, 0), time(14, 0))
    )
    if now.date() != business_date or not start_time <= now.time() < end_time:
        action = "买入" if side == "BUY" else "卖出"
        raise ValueError(
            f"{action}只能在 {start_time.strftime('%H:%M')}-"
            f"{end_time.strftime('%H:%M')} 执行"
        )
    mode = "AFTERNOON_BUY" if side == "BUY" else "AFTERNOON_SELL"
    mode_name = "下午买入" if side == "BUY" else "下午卖出"
    LOGGER.info(
        "开始执行实时交易窗口：日期=%s；时段=%s；策略=%s；窗口=%d秒",
        business_date,
        mode_name,
        config.trading_execution_strategy,
        config.trading_execution_window_seconds,
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
        "accountSnapshot": account_snapshot,
        "selection": selection,
        "executed": executed,
    }


def review_workflow(
    config: AppConfig, business_date: date
) -> dict[str, object]:
    """汇总已实现收益并判断模型是否需要重训。"""
    store = StateStore(config.state_db_path)
    _assert_task_success(store, "ORDER", business_date)
    business_key = store.start_task("REVIEW", business_date)
    try:
        profit = RealizedProfitService(store).summarize(business_date)
        review = ModelReviewService(config, store).review(business_date)
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    return {
        "businessDate": business_date.isoformat(),
        "profit": profit,
        "monthlyProfit": review["monthlyProfit"],
        "decision": review["decision"],
    }


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


# AI_GENERATE_END ------------------------------
