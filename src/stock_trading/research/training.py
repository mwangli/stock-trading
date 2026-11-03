# AI_GENERATE_START -----------------
from __future__ import annotations

import json
import logging
import shutil
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path

from stock_trading.config import AppConfig
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import research_codes
from stock_trading.market.trade_calendar import latest_completed_trade_date
from stock_trading.research.artifacts import (
    model_artifacts_valid,
    model_root,
    resolve_current_model_dir,
    train_model_candidate,
)
from stock_trading.research.backtest import (
    backtest_artifact_valid,
    read_backtest_summary,
    run_portfolio_backtest,
)
from stock_trading.research.model import automatic_model_segments
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.state.store import StateStore
from stock_trading.logging_utils import set_log_context
from stock_trading.time_utils import shanghai_now, utc_now

LOGGER = logging.getLogger(__name__)
EVALUATION_FILE = "evaluation.json"


def default_training_range(config: AppConfig) -> tuple[date, date]:
    """返回只依赖本地配置的默认研究日期范围。"""
    end_date = latest_completed_trade_date(shanghai_now())
    return end_date - timedelta(days=config.market_history_days), end_date


def default_training_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    """从本地股票基础信息确定默认研究股票池。"""
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError("未找到股票基础信息；请先执行 stock-collect") from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def run_training_pipeline(
    config: AppConfig,
    split: tuple[Decimal, Decimal, Decimal] | None,
    force: bool = False,
) -> dict[str, object]:
    """校验现有 Qlib 后执行训练、评估、回测和发布。"""
    root = model_root(config.artifact_dir)
    if not force:
        try:
            model_dir = resolve_current_model_dir(root)
        except (OSError, ValueError):
            pass
        else:
            activation = activate_trained_model(config, model_dir)
            return {
                "training": {
                    "skipped": True,
                    "modelVersion": model_dir.name,
                    "artifactPath": str(model_dir),
                },
                **activation,
            }
    start_date, end_date = default_training_range(config)
    codes = default_training_codes(config, start_date, end_date)
    preparation = assert_training_data_ready(config, codes, start_date, end_date)
    lifecycle = train_prepared_model(config, split)
    return {
        "preparation": {
            "startDate": preparation["startDate"],
            "endDate": preparation["endDate"],
            "researchCodes": len(preparation["codes"]),
            "qlibReady": True,
        },
        **lifecycle,
    }


def assert_training_data_ready(
    config: AppConfig,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    """确认 collect 已生成当前训练范围的 Qlib。"""
    store = ParquetMarketStore(config.data_dir)
    model_codes = store.model_research_codes(codes, start_date, end_date)
    if not QlibBinaryStore(store, config.qlib_dir).is_valid(
        model_codes, start_date, end_date
    ):
        raise ValueError("Qlib 数据未覆盖当前训练范围；请先执行 stock-prepare")
    return {
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "codes": model_codes,
    }


def train_prepared_model(
    config: AppConfig,
    split: tuple[Decimal, Decimal, Decimal] | None,
) -> dict[str, object]:
    """基于现有 Qlib 训练候选模型，制品完整后直接发布。"""
    calendar_path = config.qlib_dir / "calendars" / "day.txt"
    if not calendar_path.is_file():
        raise ValueError("Qlib 数据不存在，无法训练")
    segments = automatic_model_segments(calendar_path, split)
    LOGGER.info(
        "开始训练候选模型：训练=%s 至 %s；验证=%s 至 %s；测试=%s 至 %s",
        segments.train_start,
        segments.train_end,
        segments.valid_start,
        segments.valid_end,
        segments.test_start,
        segments.test_end,
    )
    root = model_root(config.artifact_dir)
    training = train_model_candidate(
        config.qlib_dir,
        root,
        segments,
        config.model_feature_batch_days,
    )
    model_dir = Path(str(training["artifactPath"]))
    store = StateStore(config.state_db_path)
    state = import_training_state(store, model_dir, "CANDIDATE")
    activation = store.activate_model_version(model_dir)
    completed_requests = store.complete_training_requests(
        int(activation["activeModel"]["id"])
    )
    LOGGER.info(
        "候选模型训练完成并启用：版本=%s",
        training["modelVersion"],
    )

    backtest_run = run_backtest_scenarios(config, model_dir)
    evaluation = evaluate_candidate(config, model_dir, backtest_run)
    _write_evaluation(model_dir, evaluation)
    LOGGER.info(
        "模型回测记录完成：版本=%s；五档平均毛收益率=%s",
        training["modelVersion"],
        evaluation["aggregate"]["averageGrossReturn"],
    )
    return {
        "training": training,
        "evaluation": evaluation,
        "state": state,
        "activation": activation,
        "completedTrainingRequests": completed_requests,
    }


def activate_trained_model(
    config: AppConfig, model_dir: Path
) -> dict[str, object]:
    """直接激活已有完整模型，必要时先补录 SQLite 状态。"""
    store = StateStore(config.state_db_path)
    state = None
    try:
        activation = store.activate_model_version(model_dir)
    except ValueError:
        state = import_training_state(store, model_dir, "CANDIDATE")
        activation = store.activate_model_version(model_dir)
    completed_requests = store.complete_training_requests(
        int(activation["activeModel"]["id"])
    )
    LOGGER.info("已有完整模型制品已启用：版本=%s", model_dir.name)
    return {
        "state": state,
        "activation": activation,
        "completedTrainingRequests": completed_requests,
    }


def run_backtest_scenarios(
    config: AppConfig, model_dir: Path
) -> dict[str, dict[str, object]]:
    """按固定资金档位顺序执行回测，保留失败前已完成的制品。"""
    results: dict[str, object] = {}
    errors: dict[str, object] = {}
    directories = backtest_directories(config, model_dir)
    for index, cash in enumerate(config.backtest_capital_scenarios, start=1):
        set_log_context(
            progress=f"{index}/{len(config.backtest_capital_scenarios)}",
            target=f"资金={cash}",
        )
        target = directories[str(cash)]
        if backtest_artifact_valid(
            target,
            initial_cash=float(cash),
            entry_rank=config.trading_entry_rank,
            exit_rank=config.trading_exit_rank,
        ):
            LOGGER.info("资金档位回测已完成，复用现有制品")
            results[str(cash)] = read_backtest_summary(target)
            continue
        LOGGER.info("开始资金档位回测")
        shutil.rmtree(target, ignore_errors=True)
        try:
            results[str(cash)] = run_portfolio_backtest(
                config, model_dir, target, cash
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
    model_dir: Path,
    backtest_run: dict[str, dict[str, object]],
) -> dict[str, object]:
    """记录测试指标和五档回测结果，不形成模型启用门槛。"""
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    results = backtest_run["results"]
    scenario_results: dict[str, object] = {}
    gross_returns: list[float] = []
    net_returns: list[float] = []
    directories = backtest_directories(config, model_dir)

    for cash in config.backtest_capital_scenarios:
        key = str(cash)
        summary = results.get(key)
        artifact_valid = backtest_artifact_valid(directories[key])
        if summary is None or not artifact_valid:
            scenario_results[key] = {"artifactValid": False}
            continue
        net_return = float(summary["netReturn"])
        gross_return = float(summary["grossReturnBeforeFees"])
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
    average_gross_return = (
        sum(gross_returns) / required if completed == required else None
    )
    average_net_return = (
        sum(net_returns) / required if completed == required else None
    )

    return {
        "createdAt": utc_now().isoformat(),
        "modelVersion": model_dir.name,
        "modelMetrics": metrics["metrics"],
        "aggregate": {
            "averageGrossReturn": average_gross_return,
            "averageNetReturn": average_net_return,
            "completedScenarios": completed,
            "requiredScenarios": required,
        },
        "backtests": scenario_results,
        "errors": backtest_run["errors"],
    }


def training_artifacts_valid(model_dir: Path) -> bool:
    """检查当前模型是否具备完整、可读取的训练制品。"""
    return model_artifacts_valid(model_dir)


def read_backtest_summaries(
    config: AppConfig, model_dir: Path
) -> dict[str, object]:
    """读取已完成的回测摘要，缺失回测不阻断模型使用。"""
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
    """返回模型版本内固定资金档位对应的回测目录。"""
    return {
        str(cash): model_dir / "backtests" / f"cash-{cash}"
        for cash in config.backtest_capital_scenarios
    }


def import_training_state(
    store: StateStore,
    model_dir: Path,
    model_status: str | None = None,
) -> dict[str, object]:
    """登记模型和预测，不把回测明细复制进 SQLite。"""
    return store.import_model_artifacts(model_dir, model_status=model_status)


def _write_evaluation(model_dir: Path, evaluation: dict[str, object]) -> None:
    (model_dir / EVALUATION_FILE).write_text(
        json.dumps(evaluation, ensure_ascii=False, indent=2), encoding="utf-8"
    )


# AI_GENERATE_END -----------------
