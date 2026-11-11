# AI_GENERATE_START --
from __future__ import annotations

import json
import logging
import shutil
from datetime import date, time, timedelta
from pathlib import Path

import pandas as pd

from stock_trading.config import AppConfig
from stock_trading.market.domain import (
    latest_completed_trade_date,
    live_codes,
    normalize_instrument,
    research_codes,
    shanghai_now,
    shift_trade_date,
    tradable_days,
    utc_now,
)
from stock_trading.market.quality import (
    audit_research_quality,
    factors_valid,
    minute5_codes_on,
    require_data_quality,
    research_coverage_ranges,
    valid_basic_codes,
    valid_daily_codes,
    valid_model_codes,
)
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.artifacts import (
    load_valid_prediction_artifact,
    model_artifacts_valid,
    model_directory,
    prediction_artifact_path,
    replace_model_directory,
    staging_model_directory,
    validate_model_artifacts,
)
from stock_trading.research.backtest import (
    backtest_artifact_valid,
    run_portfolio_backtest,
)
from stock_trading.research.config import MIN_QLIB_TRADING_DAYS, ModelSpec
from stock_trading.research.model import automatic_model_segments, create_research_model
from stock_trading.state.store import StateStore
from stock_trading.state.workflows import _record_task
from stock_trading.trading.post_trade import ModelReviewService, RealizedProfitService
from stock_trading.utils.logging_utils import set_log_context

LOGGER = logging.getLogger(__name__)


def prepare_workflow(
    config: AppConfig,
    spec: ModelSpec,
    start_date: date,
    end_date: date,
    force: bool = False,
) -> dict[str, object]:
    """读取 derived，补齐可信年度日历后幂等准备指定范围的 Qlib。"""
    store = ParquetMarketStore(config.market_dir, source="derived")
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到派生股票基础信息；请先执行 stock data-generate"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    LOGGER.info(
        "开始准备研究数据：股票数=%d；日期=%s 至 %s",
        len(codes),
        start_date,
        end_date,
    )
    ready_codes = ready_research_codes(
        store,
        spec,
        stocks,
        codes,
        start_date,
        end_date,
    )
    quality = require_data_quality(
        "prepare",
        len(codes),
        len(ready_codes),
        config.data_quality_min_ratio,
    )
    prepared = create_research_model(config, spec).prepare(
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
        "modelId": spec.model_id,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "researchCodes": len(ready_codes),
        "dataQuality": quality,
        "qlib": prepared,
    }


def rank_workflow(
    config: AppConfig, spec: ModelSpec, prediction_date: date | None
) -> dict[str, object]:
    """收盘且数据就绪后生成 D 信号，明确用于下一交易日买入。"""
    now = shanghai_now()
    store = ParquetMarketStore(config.market_dir)
    if prediction_date is None:
        prediction_date = latest_completed_trade_date(now)
    if prediction_date > now.date() or (
        prediction_date == now.date() and now.time() < time(15, 0)
    ):
        raise ValueError("排名只能使用已经收盘的交易日数据")
    execution_date = shift_trade_date(prediction_date, 1)
    LOGGER.info("开始生成完整横截面预测：预测日期=%s", prediction_date)
    state_store = StateStore(config.state_db_path)
    _assert_task_success(state_store, "COLLECT_DAILY", prediction_date)
    if spec.feature_frequency == "minute5":
        _assert_task_success(state_store, "COLLECT_MINUTE5", prediction_date)
    model = create_research_model(config, spec)
    stocks = store.read_stock_basics()
    metadata_codes = live_codes(stocks, prediction_date)
    if spec.feature_frequency == "minute5":
        current_codes = minute5_codes_on(store, metadata_codes, prediction_date)
    else:
        current_codes = metadata_codes
    expected_instruments = {
        normalize_instrument(code) for code in current_codes
    }
    available_instruments = model.available_instruments(prediction_date)
    if available_instruments is None:
        raise ValueError(
            f"Qlib 未覆盖目标日期：{prediction_date.isoformat()}；"
            "请先执行 stock model-prepare"
        )
    ready_instruments = expected_instruments & available_instruments
    quality = require_data_quality(
        "rank",
        len(expected_instruments),
        len(ready_instruments),
        config.data_quality_min_ratio,
    )
    tradable_instruments = (
        _tradable_pool(store, current_codes, prediction_date, config)
        & ready_instruments
    )
    if not tradable_instruments:
        raise ValueError(f"目标日期没有可交易股票：{prediction_date}")
    model_dir = model_directory(config.model_dir, spec)
    if not model_artifacts_valid(model_dir, spec):
        raise ValueError(
            f"模型制品不存在或不完整：{spec.model_id}；请先执行 stock model-train"
        )
    prediction_path = prediction_artifact_path(model_dir, prediction_date)
    existing = _existing_prediction_summary(
        model_dir,
        prediction_path,
        prediction_date,
        tradable_instruments,
    )
    if existing is None:
        prediction = model.rank(
            prediction_date, eligible_instruments=tradable_instruments
        )
        state = state_store.import_prediction_artifact(
            model_dir, Path(str(prediction["path"]))
        )
    else:
        prediction = existing
        if state_store.prediction_registered(
            model_dir / "model.pkl", prediction_date.isoformat(), prediction_path
        ):
            state = {
                "status": "SUCCESS",
                "predictionDate": prediction_date.isoformat(),
                "predictions": prediction["rankingRows"],
                "reused": True,
            }
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
        "modelId": spec.model_id,
        "featureDate": prediction_date.isoformat(),
        "executionDate": execution_date.isoformat(),
        "prediction": prediction,
        "state": state,
        "dataQuality": quality,
    }


def review_workflow(
    config: AppConfig, spec: ModelSpec, business_date: date
) -> dict[str, object]:
    """汇总已实现收益并判断模型是否需要重训。"""
    store = StateStore(config.state_db_path)
    _assert_task_success(store, "ORDER", business_date)
    with _record_task(store, "REVIEW", business_date):
        profit = RealizedProfitService(store).summarize(
            spec.model_id, business_date
        )
        review = ModelReviewService(config, store).review(
            spec.model_id, business_date
        )
    return {
        "modelId": spec.model_id,
        "businessDate": business_date.isoformat(),
        "profit": profit,
        "monthlyProfit": review["monthlyProfit"],
        "decision": review["decision"],
    }


def run_training_pipeline(
    config: AppConfig,
    spec: ModelSpec,
    force: bool = False,
) -> dict[str, object]:
    """训练、回测和评估一个模型 ID，成功后再原子替换当前制品。"""
    model = create_research_model(config, spec)
    start_date, end_date = model.date_range() or default_training_range(config)
    codes = default_training_codes(config, start_date, end_date)
    preparation = assert_training_data_ready(
        config, spec, codes, start_date, end_date
    )
    preparation_summary = _preparation_summary(preparation)
    target = model.artifact_dir
    store = StateStore(config.state_db_path)
    if not force and complete_model_artifacts_valid(config, spec, target):
        state = (
            None
            if store.model_artifacts_registered(target)
            else store.import_model_artifacts(target)
        )
        return {
            "modelId": spec.model_id,
            "preparation": preparation_summary,
            "training": {
                "skipped": True,
                "artifactPath": str(target),
            },
            "state": state,
        }

    calendar_path = model.qlib_dir / "calendars" / "day.txt"
    if not calendar_path.is_file():
        raise ValueError("模型 Qlib 数据不存在，无法训练")
    segments = automatic_model_segments(
        calendar_path,
        None,
        spec.label_horizon,
    )
    staging = staging_model_directory(target)
    shutil.rmtree(staging, ignore_errors=True)
    try:
        training = model.train(staging, segments)
        validate_model_artifacts(staging, spec)
        backtest = run_backtest(config, spec, model.qlib_dir, staging)
        evaluation = evaluate_candidate(config, spec, staging, backtest)
        if not complete_model_artifacts_valid(config, spec, staging):
            raise ValueError("模型训练、回测或评估制品不完整")
        target.parent.mkdir(parents=True, exist_ok=True)
        replace_model_directory(staging, target)
    finally:
        shutil.rmtree(staging, ignore_errors=True)

    state = store.import_model_artifacts(target)
    completed_requests = store.complete_training_requests(
        spec.model_id, int(state["modelVersionId"])
    )
    return {
        "modelId": spec.model_id,
        "preparation": preparation_summary,
        "training": training
        | {"skipped": False, "artifactPath": str(target)},
        "evaluation": evaluation,
        "state": state,
        "completedTrainingRequests": completed_requests,
    }


def assert_training_data_ready(
    config: AppConfig,
    spec: ModelSpec,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    store = ParquetMarketStore(config.market_dir)
    stocks = store.read_stock_basics()
    coverage_ranges = research_coverage_ranges(
        stocks, codes, start_date, end_date
    )
    end_dates = {
        code: coverage_ranges[code][1]
        for code in codes
    }
    start_dates = {
        code: coverage_ranges[code][0]
        for code in codes
    }
    audit = (
        audit_research_quality(store, stocks, codes, start_date, end_date)
        if spec.feature_frequency == "minute5"
        else None
    )
    model_codes = ready_research_codes(
        store, spec, stocks, codes, start_date, end_date
    )
    raw_quality = require_data_quality(
        "train-raw",
        len(codes),
        len(model_codes),
        config.data_quality_min_ratio,
        audit,
    )
    expected = {normalize_instrument(code) for code in model_codes}
    model = create_research_model(config, spec)
    qlib_range = model.date_range()
    available = model.available_instruments(end_date)
    if (
        qlib_range is None
        or qlib_range[0] > start_date + timedelta(days=15)
        or qlib_range[1] < end_date
        or available is None
    ):
        raise ValueError(
            f"模型 {spec.model_id} 的 Qlib 数据未覆盖训练范围；请先执行 stock model-prepare"
        )
    ready = expected & available
    qlib_quality = require_data_quality(
        "train-qlib",
        len(expected),
        len(ready),
        config.data_quality_min_ratio,
    )
    return {
        "modelId": spec.model_id,
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "codes": sorted(ready),
        "marketCodes": model_codes,
        "dataQuality": {"raw": raw_quality, "qlib": qlib_quality},
    }


def default_training_range(config: AppConfig) -> tuple[date, date]:
    end_date = latest_completed_trade_date(shanghai_now())
    return end_date - timedelta(days=config.market_history_days), end_date


def default_training_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    store = ParquetMarketStore(config.market_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到股票基础信息；请先执行 stock data-collect"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def ready_research_codes(
    store: ParquetMarketStore,
    spec: ModelSpec,
    stocks,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> list[str]:
    """按模型字段要求统一筛选可用于准备和训练的股票。"""
    coverage_ranges = research_coverage_ranges(
        stocks, codes, start_date, end_date
    )
    start_dates = {code: values[0] for code, values in coverage_ranges.items()}
    end_dates = {code: values[1] for code, values in coverage_ranges.items()}
    daily_ready = valid_daily_codes(
        store,
        codes,
        start_date,
        end_date,
        start_dates=start_dates,
        end_dates=end_dates,
    )
    if "basic" in spec.required_fields:
        basic_ready = set(
            valid_basic_codes(
                store,
                daily_ready,
                start_date,
                end_date,
                MIN_QLIB_TRADING_DAYS,
                start_dates=start_dates,
                end_dates=end_dates,
            )
        )
        daily_ready = [code for code in daily_ready if code in basic_ready]
    if "factor" in spec.required_fields:
        factor_ready = {
            code
            for code in daily_ready
            if factors_valid(store, code, end_dates[code], start_dates[code])
        }
        daily_ready = [code for code in daily_ready if code in factor_ready]
    return valid_model_codes(
        store,
        daily_ready,
        spec.feature_frequency,
        start_date,
        end_date,
        MIN_QLIB_TRADING_DAYS,
    )


def run_backtest(
    config: AppConfig,
    spec: ModelSpec,
    qlib_dir: Path,
    model_dir: Path,
) -> dict[str, object]:
    set_log_context(progress="1/1", target=f"资金={config.backtest_capital}")
    return run_portfolio_backtest(
        config,
        spec,
        model_dir,
        backtest_directory(model_dir),
        config.backtest_capital,
        qlib_dir=qlib_dir,
    )


def evaluate_candidate(
    config: AppConfig,
    spec: ModelSpec,
    model_dir: Path,
    backtest: dict[str, object],
) -> dict[str, object]:
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    if not backtest_artifact_valid(
        backtest_directory(model_dir),
        initial_cash=config.backtest_capital,
        max_holding_days=config.trading_max_holding_days,
    ):
        raise ValueError("模型回测制品不完整")
    return {
        "createdAt": utc_now().isoformat(),
        "modelId": spec.model_id,
        "modelMetrics": metrics["metrics"],
        "backtest": {
            "capital": config.backtest_capital,
            "grossProfit": backtest["grossProfitBeforeFees"],
            "grossReturn": backtest["grossReturnBeforeFees"],
            "maxDrawdown": float(backtest["maxDrawdown"]),
            "closedTrades": backtest["closedTrades"],
            "maxHoldingDays": config.trading_max_holding_days,
            "averageHoldingDays": backtest["averageHoldingDays"],
            "winRate": backtest["winRate"],
            "turnover": backtest["turnover"],
            "retainedDays": backtest["retainedDays"],
            "monthlyPerformance": backtest["monthlyPerformance"],
            "monthlyReturnStability": backtest["monthlyReturnStability"],
        },
    }


def complete_model_artifacts_valid(
    config: AppConfig, spec: ModelSpec, model_dir: Path
) -> bool:
    if not model_artifacts_valid(model_dir, spec):
        return False
    return backtest_artifact_valid(
        backtest_directory(model_dir),
        initial_cash=config.backtest_capital,
        max_holding_days=config.trading_max_holding_days,
    )


def backtest_directory(model_dir: Path) -> Path:
    return model_dir / "backtest"


def _preparation_summary(preparation: dict[str, object]) -> dict[str, object]:
    return {
        "modelId": preparation["modelId"],
        "startDate": preparation["startDate"],
        "endDate": preparation["endDate"],
        "researchCodes": len(preparation["codes"]),
        "qlibReady": True,
        "dataQuality": preparation["dataQuality"],
    }


def _existing_prediction_summary(
    model_dir: Path,
    path: Path,
    prediction_date: date,
    expected_instruments: set[str],
) -> dict[str, object] | None:
    frame = load_valid_prediction_artifact(model_dir, prediction_date)
    if frame is None:
        return None
    actual_instruments = {
        normalize_instrument(value) for value in frame["instrument"]
    }
    if actual_instruments != expected_instruments:
        return None
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    return {
        "modelId": metrics["modelId"],
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


def _tradable_pool(
    store: ParquetMarketStore,
    codes: list[str],
    prediction_date: date,
    config: AppConfig,
) -> set[str]:
    """按预测日已落库的日线事实构建可买池，模型只对该池打分排名。"""
    day = pd.Timestamp(prediction_date)
    pool: set[str] = set()
    for code in codes:
        try:
            flags = tradable_days(
                store.read_daily_history(code),
                code,
                config.trading_min_price,
                config.trading_max_price,
                config.trading_limit_down_lookback_days,
                config.trading_max_recent_limit_downs,
            )
        except (OSError, ValueError, KeyError):
            continue
        if bool(flags.get(day, False)):
            pool.add(normalize_instrument(code))
    return pool


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


# AI_GENERATE_END --
