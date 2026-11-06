# AI_GENERATE_START -----------------------------
from __future__ import annotations

import json
import logging
import shutil
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path

from stock_trading.config import AppConfig
from stock_trading.market.quality import audit_research_quality, require_data_quality
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import research_codes
from stock_trading.market.trade_calendar import latest_completed_trade_date
from stock_trading.research.artifacts import (
    model_artifacts_valid,
    replace_model_directory,
    staging_model_directory,
    validate_model_artifacts,
)
from stock_trading.research.backtest import (
    backtest_artifact_valid,
    read_backtest_summary,
    run_portfolio_backtest,
)
from stock_trading.research.model import automatic_model_segments
from stock_trading.research.model_adapter import create_research_model
from stock_trading.research.model_config import ModelSpec
from stock_trading.state.store import StateStore
from stock_trading.utils.instruments import normalize_instrument
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.utils.time_utils import shanghai_now, utc_now

LOGGER = logging.getLogger(__name__)
EVALUATION_FILE = "evaluation.json"


def default_training_range(config: AppConfig) -> tuple[date, date]:
    end_date = latest_completed_trade_date(shanghai_now())
    return end_date - timedelta(days=config.market_history_days), end_date


def default_training_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到股票基础信息；请先执行 stock-collect --type basic"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def run_training_pipeline(
    config: AppConfig,
    spec: ModelSpec,
    split: tuple[Decimal, Decimal, Decimal] | None,
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
        split,
        spec.training_window_days,
    )
    staging = staging_model_directory(target)
    shutil.rmtree(staging, ignore_errors=True)
    try:
        training = model.train(staging, segments)
        validate_model_artifacts(staging, spec)
        backtest_run = run_backtest_scenarios(
            config, model.qlib_dir, staging
        )
        evaluation = evaluate_candidate(config, spec, staging, backtest_run)
        _write_evaluation(staging, evaluation)
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
    store = ParquetMarketStore(config.data_dir)
    stocks = store.read_stock_basics()
    audit = audit_research_quality(store, stocks, codes, start_date, end_date)
    raw_quality = require_data_quality(
        "train-raw",
        len(codes),
        len(audit.model_codes),
        config.data_quality_min_ratio,
        audit,
    )
    expected = {normalize_instrument(code) for code in audit.model_codes}
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
            f"模型 {spec.model_id} 的 Qlib 数据未覆盖训练范围；请先执行 stock-prepare"
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
        "marketCodes": list(audit.model_codes),
        "dataQuality": {"raw": raw_quality, "qlib": qlib_quality},
    }


def _preparation_summary(preparation: dict[str, object]) -> dict[str, object]:
    return {
        "modelId": preparation["modelId"],
        "startDate": preparation["startDate"],
        "endDate": preparation["endDate"],
        "researchCodes": len(preparation["codes"]),
        "qlibReady": True,
        "dataQuality": preparation["dataQuality"],
    }


def run_backtest_scenarios(
    config: AppConfig,
    qlib_dir: Path,
    model_dir: Path,
) -> dict[str, dict[str, object]]:
    results: dict[str, object] = {}
    errors: dict[str, object] = {}
    directories = backtest_directories(config, model_dir)
    for index, cash in enumerate(config.backtest_capital_scenarios, start=1):
        set_log_context(
            progress=f"{index}/{len(config.backtest_capital_scenarios)}",
            target=f"资金={cash}",
        )
        target = directories[str(cash)]
        try:
            results[str(cash)] = run_portfolio_backtest(
                config, model_dir, target, cash, qlib_dir=qlib_dir
            )
        except Exception as error:
            errors[str(cash)] = str(error)
            break
    return {
        "results": results,
        "errors": errors,
        "directories": {key: str(value) for key, value in directories.items()},
    }


def evaluate_candidate(
    config: AppConfig,
    spec: ModelSpec,
    model_dir: Path,
    backtest_run: dict[str, dict[str, object]],
) -> dict[str, object]:
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    results = backtest_run["results"]
    scenario_results: dict[str, object] = {}
    gross_returns: list[float] = []
    net_returns: list[float] = []
    directories = backtest_directories(config, model_dir)
    for cash in config.backtest_capital_scenarios:
        key = str(cash)
        summary = results.get(key)
        if summary is None or not backtest_artifact_valid(directories[key]):
            scenario_results[key] = {"artifactValid": False}
            continue
        gross_return = float(summary["grossReturnBeforeFees"])
        net_return = float(summary["netReturn"])
        gross_returns.append(gross_return)
        net_returns.append(net_return)
        scenario_results[key] = {
            "artifactValid": True,
            "grossProfit": summary["grossProfitBeforeFees"],
            "grossReturn": gross_return,
            "netProfit": summary["netProfit"],
            "netReturn": net_return,
            "maxDrawdown": abs(float(summary["maxDrawdown"])),
            "netMaxDrawdown": abs(float(summary["netMaxDrawdown"])),
            "closedTrades": summary["closedTrades"],
            "averageHoldingDays": summary["averageHoldingDays"],
            "retainedDays": summary["retainedDays"],
        }
    required = len(config.backtest_capital_scenarios)
    completed = len(gross_returns)
    return {
        "createdAt": utc_now().isoformat(),
        "modelId": spec.model_id,
        "modelMetrics": metrics["metrics"],
        "aggregate": {
            "averageGrossReturn": (
                sum(gross_returns) / required if completed == required else None
            ),
            "averageNetReturn": (
                sum(net_returns) / required if completed == required else None
            ),
            "completedScenarios": completed,
            "requiredScenarios": required,
        },
        "backtests": scenario_results,
        "errors": backtest_run["errors"],
    }


def complete_model_artifacts_valid(
    config: AppConfig, spec: ModelSpec, model_dir: Path
) -> bool:
    if not model_artifacts_valid(model_dir, spec, require_evaluation=True):
        return False
    return all(
        backtest_artifact_valid(path)
        for path in backtest_directories(config, model_dir).values()
    )


def read_backtest_summaries(
    config: AppConfig, model_dir: Path
) -> dict[str, object]:
    return {
        key: (
            read_backtest_summary(path)
            if backtest_artifact_valid(path)
            else {"artifactValid": False}
        )
        for key, path in backtest_directories(config, model_dir).items()
    }


def backtest_directories(
    config: AppConfig, model_dir: Path
) -> dict[str, Path]:
    return {
        str(cash): model_dir / "backtests" / f"cash-{cash}"
        for cash in config.backtest_capital_scenarios
    }


def _write_evaluation(model_dir: Path, evaluation: dict[str, object]) -> None:
    (model_dir / EVALUATION_FILE).write_text(
        json.dumps(evaluation, ensure_ascii=False, indent=2), encoding="utf-8"
    )


# AI_GENERATE_END -----------------------------
