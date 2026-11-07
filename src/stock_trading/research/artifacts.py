# AI_GENERATE_START ---------------------
from __future__ import annotations

import json
import pickle
import shutil
from datetime import date
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.research.model_config import ModelSpec

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
    backup = target.with_name(f".{target.name}.previous")
    shutil.rmtree(backup, ignore_errors=True)
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
    else:
        shutil.rmtree(backup, ignore_errors=True)


def prediction_artifact_path(model_dir: Path, prediction_date: date) -> Path:
    return model_dir / "predictions" / f"ranking-{prediction_date.isoformat()}.parquet"


def prediction_artifact_valid(model_dir: Path, prediction_date: date) -> bool:
    try:
        frame = pd.read_parquet(prediction_artifact_path(model_dir, prediction_date))
        dates = pd.to_datetime(frame["datetime"]).dt.date.unique()
    except (OSError, ValueError, KeyError, AttributeError):
        return False
    return (
        not frame.empty
        and {"datetime", "instrument", "rank", "score", "label"}.issubset(frame.columns)
        and len(dates) == 1
        and dates[0] == prediction_date
        and frame["instrument"].is_unique
        and frame["score"].notna().all()
        and frame["rank"].tolist() == list(range(1, len(frame) + 1))
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


# AI_GENERATE_END ---------------------
