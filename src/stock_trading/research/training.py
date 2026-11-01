# AI_GENERATE_START -
from __future__ import annotations

import json
import logging
import shutil
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any

from stock_trading.config import AppConfig
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import research_codes
from stock_trading.research.artifacts import (
    activate_model_artifact,
    current_model_version,
    model_root,
    resolve_current_model_dir,
    restore_model_artifact,
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

LOGGER = logging.getLogger(__name__)
EVALUATION_FILE = "evaluation.json"
BASELINE_CAPITAL = 3_000


class ModelCapabilityError(ValueError):
    """候选模型未通过发布门禁。"""


def default_training_range(config: AppConfig) -> tuple[date, date]:
    """返回只依赖本地配置的默认研究日期范围。"""
    end_date = date.today()
    return end_date - timedelta(days=config.market_history_days), end_date


def default_training_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    """从本地股票基础信息确定默认研究股票池。"""
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError("未找到股票基础信息；请先执行 init") from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def prepare_training_data(
    config: AppConfig,
    codes: list[str],
    start_date: date,
    end_date: date,
) -> dict[str, object]:
    """校验本地行情并重建 Qlib，不触发采集或训练。"""
    store = ParquetMarketStore(config.data_dir)
    daily_codes = store.valid_daily_codes(codes)
    minute5_codes = store.valid_minute5_codes(codes)
    missing_daily = [code for code in codes if code not in daily_codes]
    missing_minute5 = [code for code in codes if code not in minute5_codes]
    errors: list[str] = []
    if missing_daily:
        errors.append(f"日线或复权因子缺失：{','.join(missing_daily)}")
    if missing_minute5:
        errors.append(f"5分钟线或分钟聚合缺失：{','.join(missing_minute5)}")
    if errors:
        missing_codes = list(dict.fromkeys(missing_daily + missing_minute5))
        date_argument = _format_date_expression(start_date, end_date)
        command = (
            f"stock-trading collect --codes {','.join(missing_codes)} "
            f"--date {date_argument}"
        )
        raise ValueError(f"{'；'.join(errors)}；请执行：{command}")

    model_codes = store.model_research_codes(codes, start_date, end_date)
    LOGGER.info(
        "开始重建 Qlib：采集有效=%d；模型有效=%d；日期=%s 至 %s",
        len(codes),
        len(model_codes),
        start_date,
        end_date,
    )
    qlib_result = QlibBinaryStore(store, config.qlib_dir).rebuild(
        model_codes, start_date, end_date
    )
    LOGGER.info(
        "训练准备完成：股票数=%d；交易日=%s；标签=%s",
        qlib_result["codes"],
        qlib_result["calendarDays"],
        qlib_result["labels"],
    )
    return {
        "startDate": start_date.isoformat(),
        "endDate": end_date.isoformat(),
        "codes": model_codes,
        "qlib": qlib_result,
    }


def run_training_pipeline(
    config: AppConfig,
    split: tuple[Decimal, Decimal, Decimal] | None,
) -> dict[str, object]:
    """按准备、训练、回测、门禁和发布顺序执行完整训练生命周期。"""
    start_date, end_date = default_training_range(config)
    codes = default_training_codes(config, start_date, end_date)
    preparation = prepare_training_data(config, codes, start_date, end_date)
    lifecycle = train_prepared_model(config, split)
    return {
        "preparation": {
            "startDate": preparation["startDate"],
            "endDate": preparation["endDate"],
            "researchCodes": len(preparation["codes"]),
            "qlib": preparation["qlib"],
        },
        **lifecycle,
    }


def train_prepared_model(
    config: AppConfig,
    split: tuple[Decimal, Decimal, Decimal] | None,
) -> dict[str, object]:
    """基于现有 Qlib 训练候选模型，通过能力门禁后再发布。"""
    calendar_path = config.qlib_dir / "calendars" / "day.txt"
    if not calendar_path.is_file():
        raise ValueError("Qlib 数据不存在，请先执行 prepare")
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
    training = train_model_candidate(config.qlib_dir, root, segments)
    model_dir = Path(str(training["artifactPath"]))
    backtest_run = run_backtest_scenarios(config, model_dir)
    evaluation = evaluate_candidate(config, model_dir, backtest_run)
    _write_evaluation(model_dir, evaluation)

    store = StateStore(config.state_db_path)
    if not evaluation["passed"]:
        store.register_model_version(model_dir, "REJECTED")
        reasons = "；".join(str(value) for value in evaluation["failures"])
        raise ModelCapabilityError(
            f"模型能力评估未通过；候选版本={training['modelVersion']}；原因={reasons}"
        )

    state = import_training_state(config, store, model_dir, "CANDIDATE")
    previous_version = current_model_version(root)
    activate_model_artifact(root, str(training["modelVersion"]))
    try:
        activation = store.activate_model_version(model_dir)
    except BaseException:
        restore_model_artifact(root, previous_version)
        raise
    LOGGER.info(
        "候选模型发布完成：版本=%s；平均净收益率=%s；最大回撤=%s",
        training["modelVersion"],
        evaluation["aggregate"]["averageNetReturn"],
        evaluation["aggregate"]["worstMaxDrawdown"],
    )
    return {
        "training": training,
        "evaluation": evaluation,
        "state": state,
        "activation": activation,
    }


def run_current_model_backtests(config: AppConfig) -> dict[str, object]:
    """对当前模型执行固定五档回测并导入研究状态。"""
    model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    backtest_run = run_backtest_scenarios(config, model_dir)
    if backtest_run["errors"]:
        reasons = "；".join(
            f"{cash}元={message}"
            for cash, message in backtest_run["errors"].items()
        )
        raise ValueError(f"多资金档位回测未完成：{reasons}")
    state = import_training_state(config, StateStore(config.state_db_path), model_dir)
    return {
        "modelVersion": model_dir.name,
        "capitalScenarios": list(config.backtest_capital_scenarios),
        "backtests": backtest_run["results"],
        "state": state,
    }


def run_backtest_scenarios(
    config: AppConfig, model_dir: Path
) -> dict[str, dict[str, object]]:
    """按固定资金档位顺序执行回测，保留失败前已完成的制品。"""
    results: dict[str, object] = {}
    errors: dict[str, object] = {}
    directories = backtest_directories(config, model_dir)
    for index, cash in enumerate(config.backtest_capital_scenarios, start=1):
        LOGGER.info(
            "资金档位回测：进度=%d/%d；初始资金=%d",
            index,
            len(config.backtest_capital_scenarios),
            cash,
        )
        target = directories[str(cash)]
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
    """根据测试集 Rank IC、平均净收益和逐档回撤形成发布结论。"""
    metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
    rank_ic = float(metrics["metrics"]["rankIcMean"])
    results = backtest_run["results"]
    scenario_results: dict[str, object] = {}
    net_returns: list[float] = []
    drawdowns: list[float] = []
    failures: list[str] = []
    directories = backtest_directories(config, model_dir)

    for cash in config.backtest_capital_scenarios:
        key = str(cash)
        summary = results.get(key)
        artifact_valid = backtest_artifact_valid(directories[key])
        if summary is None or not artifact_valid:
            failures.append(f"{cash}元回测制品不完整")
            scenario_results[key] = {"artifactValid": False}
            continue
        net_return = float(summary["netReturn"])
        drawdown = abs(float(summary["maxDrawdown"]))
        net_returns.append(net_return)
        drawdowns.append(drawdown)
        scenario_results[key] = {
            "artifactValid": True,
            "netProfit": summary["netProfit"],
            "netReturn": net_return,
            "maxDrawdown": drawdown,
            "closedTrades": summary["closedTrades"],
        }
        if drawdown > config.model_max_backtest_drawdown:
            failures.append(
                f"{cash}元最大回撤 {drawdown:.6f} 超过 "
                f"{config.model_max_backtest_drawdown:.6f}"
            )

    for cash, message in backtest_run["errors"].items():
        failures.append(f"{cash}元回测失败：{message}")
    average_return = sum(net_returns) / len(config.backtest_capital_scenarios)
    worst_drawdown = max(drawdowns, default=0.0)
    if rank_ic <= config.model_min_rank_ic:
        failures.append(
            f"Rank IC {rank_ic:.6f} 未大于 {config.model_min_rank_ic:.6f}"
        )
    if average_return <= config.model_min_average_backtest_return:
        failures.append(
            f"五档平均净收益率 {average_return:.6f} 未大于 "
            f"{config.model_min_average_backtest_return:.6f}"
        )

    return {
        "createdAt": datetime.now(UTC).isoformat(),
        "modelVersion": model_dir.name,
        "passed": not failures,
        "decision": "ACCEPTED" if not failures else "REJECTED",
        "thresholds": {
            "minRankIc": config.model_min_rank_ic,
            "minAverageBacktestReturn": config.model_min_average_backtest_return,
            "maxBacktestDrawdown": config.model_max_backtest_drawdown,
        },
        "modelMetrics": {"rankIcMean": rank_ic},
        "aggregate": {
            "averageNetReturn": average_return,
            "worstMaxDrawdown": worst_drawdown,
            "completedScenarios": len(net_returns),
            "requiredScenarios": len(config.backtest_capital_scenarios),
        },
        "backtests": scenario_results,
        "failures": failures,
    }


def training_artifacts_valid(config: AppConfig, model_dir: Path) -> bool:
    """检查当前模型是否具备通过门禁的评估和五档回测制品。"""
    try:
        evaluation = json.loads(
            (model_dir / EVALUATION_FILE).read_text(encoding="utf-8")
        )
    except (OSError, ValueError, json.JSONDecodeError):
        return False
    return bool(evaluation.get("passed")) and all(
        backtest_artifact_valid(path)
        for path in backtest_directories(config, model_dir).values()
    )


def read_backtest_summaries(
    config: AppConfig, model_dir: Path
) -> dict[str, object]:
    """读取指定模型的五档回测摘要。"""
    return {
        key: read_backtest_summary(path)
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
    config: AppConfig,
    store: StateStore,
    model_dir: Path,
    model_status: str | None = None,
) -> dict[str, object]:
    """导入模型、预测和五档回测制品，不负责激活模型。"""
    directories = backtest_directories(config, model_dir)
    baseline_key = str(BASELINE_CAPITAL)
    result = store.import_research_artifacts(
        model_dir,
        directories[baseline_key],
        f"capital-{baseline_key}",
        model_status=model_status,
    )
    business_date = max(
        str(summary["tradeDate"])
        for summary in (
            _latest_equity_row(path) for path in directories.values()
        )
    )
    for cash in config.backtest_capital_scenarios:
        key = str(cash)
        if cash == BASELINE_CAPITAL:
            continue
        store.import_backtest_performance(
            directories[key], f"capital-{key}", business_date
        )
    return result


def _latest_equity_row(backtest_dir: Path) -> dict[str, Any]:
    import pandas as pd

    equity = pd.read_parquet(backtest_dir / "daily_equity.parquet")
    return equity.sort_values("tradeDate").iloc[-1].to_dict()


def _write_evaluation(model_dir: Path, evaluation: dict[str, object]) -> None:
    (model_dir / EVALUATION_FILE).write_text(
        json.dumps(evaluation, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def _format_date_expression(start_date: date, end_date: date) -> str:
    if start_date == end_date:
        return start_date.isoformat()
    return f"{start_date.isoformat()}:{end_date.isoformat()}"

# AI_GENERATE_END -
