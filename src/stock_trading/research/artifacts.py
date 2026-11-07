# AI_GENERATE_START- -------------------------
from __future__ import annotations

import json
import pickle
import shutil
from datetime import date
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.research.model_config import ModelSpec
from stock_trading.research.qlib_contract import TRADING_TIME_CONTRACT, expected_label_contract
from stock_trading.market.trade_calendar import shift_trade_date

_REQUIRED_FILES = (
    "model.pkl",
    "metrics.json",
    "top5.parquet",
    "test_predictions.parquet",
)


def model_directory(artifact_dir: Path, spec: ModelSpec) -> Path:
    """返回模型 ID 唯一对应的当前制品目录。"""
    return spec.artifact_dir(artifact_dir)


def staging_model_directory(target: Path) -> Path:
    return target.with_name(f".{target.name}.{uuid4().hex[:8]}.building")


def replace_model_directory(staging: Path, target: Path) -> None:
    """校验完成后原子替换同一模型 ID 的当前制品。"""
    backup = target.with_name(f".{target.name}.{uuid4().hex[:8]}.previous")
    if target.exists():
        target.replace(backup)
    try:
        staging.replace(target)
    except BaseException:
        if target.exists():
            shutil.rmtree(target)
        if backup.exists():
            backup.replace(target)
        raise
    # 保留上一版模型作为历史基线，不覆盖旧合同制品。


def prediction_artifact_path(model_dir: Path, prediction_date: date) -> Path:
    return model_dir / "predictions" / f"ranking-{prediction_date.isoformat()}.parquet"


def load_valid_prediction_artifact(
    model_dir: Path, prediction_date: date, calendar_dir: Path | None = None,
) -> pd.DataFrame | None:
    """读取一次排名制品，合同检查通过后返回可复用的数据。"""
    try:
        frame = pd.read_parquet(prediction_artifact_path(model_dir, prediction_date))
        dates = pd.to_datetime(frame["datetime"]).dt.date.unique()
        execution_date = shift_trade_date(prediction_date, 1, calendar_dir).isoformat()
        generated = pd.to_datetime(frame["generated_at"], utc=True, errors="coerce")
        earliest = pd.Timestamp(prediction_date).tz_localize("Asia/Shanghai") + pd.Timedelta(hours=15)
        latest = pd.Timestamp(execution_date).tz_localize("Asia/Shanghai") + pd.Timedelta(hours=14)
        contract_valid = (
            frame["execution_date"].eq(execution_date).all()
            and frame["contract_version"].eq(TRADING_TIME_CONTRACT).all()
            and generated.notna().all()
            and generated.nunique() == 1
            and generated.ge(earliest).all()
            and generated.lt(latest).all()
        )
    except (OSError, ValueError, KeyError, AttributeError):
        return None
    valid = (
        not frame.empty
        and contract_valid
        and {"datetime", "instrument", "rank", "score", "label"}.issubset(frame.columns)
        and len(dates) == 1
        and dates[0] == prediction_date
        and frame["instrument"].is_unique
        and frame["score"].notna().all()
        and frame["rank"].tolist() == list(range(1, len(frame) + 1))
    )
    return frame if valid else None


def prediction_artifact_valid(
    model_dir: Path, prediction_date: date, calendar_dir: Path | None = None,
) -> bool:
    """保留布尔校验入口；需要内容的调用方直接使用加载函数。"""
    return (
        load_valid_prediction_artifact(model_dir, prediction_date, calendar_dir)
        is not None
    )


def model_artifacts_valid(
    directory: Path,
    spec: ModelSpec | None = None,
    require_evaluation: bool = False,
) -> bool:
    """检查模型、指标和测试预测是否属于同一模型配置。"""
    try:
        if not all(
            (directory / name).is_file()
            and (directory / name).stat().st_size > 0
            for name in _REQUIRED_FILES
        ):
            return False
        metrics = json.loads((directory / "metrics.json").read_text(encoding="utf-8"))
        evaluation = (
            json.loads(
                (directory / "evaluation.json").read_text(encoding="utf-8")
            )
            if require_evaluation
            else None
        )
        predictions = pd.read_parquet(
            directory / "test_predictions.parquet",
            columns=["datetime", "instrument", "score"],
        )
        if not isinstance(metrics, dict) or (
            evaluation is not None and not isinstance(evaluation, dict)
        ):
            return False
        backtest = evaluation.get("backtest") if evaluation else None
        backtest_capital = (
            float(backtest.get("capital", 0))
            if isinstance(backtest, dict)
            else 0
        )
    except Exception:
        return False
    return (
        not predictions.empty
        and predictions["score"].notna().any()
        and metrics.get("timeContractVersion") == TRADING_TIME_CONTRACT
        and metrics.get("labelContract") == expected_label_contract(spec)
        and (spec is None or metrics.get("modelId") == spec.model_id)
        and metrics.get("algorithm") in {
            "LightGBM",
            "XGBoost",
            "XGBoostRanker",
            None,
        }
        and (
            evaluation is None
            or (
                evaluation.get("modelId") == metrics.get("modelId")
                and isinstance(backtest, dict)
                and backtest_capital > 0
                and backtest.get("grossProfit") is not None
            )
        )
    )


def validate_model_artifacts(directory: Path, spec: ModelSpec) -> None:
    if not model_artifacts_valid(directory, spec):
        raise ValueError(f"训练制品检查失败: {directory}")
    try:
        with (directory / "model.pkl").open("rb") as stream:
            pickle.load(stream)
    except Exception as error:
        raise ValueError(f"训练模型无法反序列化: {directory / 'model.pkl'}") from error


# AI_GENERATE_END- -------------------------
