# AI_GENERATE_START -
from __future__ import annotations

import json
import logging
from datetime import date, time
from pathlib import Path

from stock_trading.config import AppConfig
from stock_trading.market.domain import latest_completed_trade_date, live_codes, normalize_instrument, research_codes, shift_trade_date, trade_dates
from stock_trading.market.quality import require_data_quality
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.artifacts import model_artifacts_valid, model_directory, prediction_artifact_path, load_valid_prediction_artifact
from stock_trading.research.model_config import ModelSpec
from stock_trading.research.model import create_research_model
from stock_trading.state.store import StateStore
from stock_trading.trading.post_trade import ModelReviewService, RealizedProfitService
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.state.workflows import _record_task
from stock_trading.utils.time_utils import shanghai_now



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
    LOGGER.info(
        "开始准备研究数据：股票数=%d；日期=%s 至 %s",
        len(codes),
        start_date,
        end_date,
    )
    derived_daily_ready = [
        code
        for code in codes
        if store.daily_history_valid(
            code, start_dates[code], end_dates[code]
        )
    ]
    derived: dict[str, object] = {}
    daily_ready = store.valid_daily_codes(
        derived_daily_ready,
        start_date,
        end_date,
        start_dates=start_dates,
        end_dates=end_dates,
    )
    if "basic" in spec.required_fields:
        basic_ready = set(
            store.valid_basic_codes(
                daily_ready,
                start_date,
                end_date,
                63,
                start_dates=start_dates,
                end_dates=end_dates,
            )
        )
        daily_ready = [code for code in daily_ready if code in basic_ready]
    if "factor" in spec.required_fields:
        factor_ready = {
            code
            for code in daily_ready
            if store.factors_valid(
                code, end_dates[code], start_dates[code]
            )
        }
        daily_ready = [code for code in daily_ready if code in factor_ready]
    ready_codes = store.valid_model_codes(
        daily_ready,
        spec.feature_frequency,
        start_date,
        end_date,
        63,
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
        "derived": derived,
        "qlib": prepared,
    }



def rank_workflow(
    config: AppConfig, spec: ModelSpec, prediction_date: date | None
) -> dict[str, object]:
    """收盘且数据就绪后生成 D 信号，明确用于下一交易日买入。"""
    now = shanghai_now()
    store = ParquetMarketStore(config.market_dir)
    provider = l1_market_provider(config)
    if prediction_date is None:
        prediction_date = latest_completed_trade_date(now)
    if prediction_date > now.date() or (
        prediction_date == now.date() and now.time() < time(15, 0)
    ):
        raise ValueError("排名只能使用已经收盘的交易日数据")
    if prediction_date == trade_dates(prediction_date.year)[-1]:
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
        current_codes = store.minute5_codes_on(metadata_codes, prediction_date)
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
        ready_instruments,
    )
    if existing is None:
        prediction = model.rank(
            prediction_date, eligible_instruments=ready_instruments
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
# AI_GENERATE_END -
