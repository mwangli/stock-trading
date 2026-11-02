# AI_GENERATE_START -------------
from __future__ import annotations

import json
import pickle
import shutil
from datetime import date
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.research.model import (
    ModelSegments,
    train_alpha158_lightgbm_adaptive,
)
from stock_trading.time_utils import utc_now

MODEL_DIRECTORY = "alpha158-lightgbm"
_REQUIRED_FILES = (
    "model.pkl",
    "metrics.json",
    "top5.parquet",
    "test_predictions.parquet",
)


def model_root(artifact_dir: Path) -> Path:
    """返回模型版本根目录。"""
    return artifact_dir / MODEL_DIRECTORY


def resolve_current_model_dir(root: Path) -> Path:
    """返回最新且制品完整的模型版本。"""
    versions = root / "versions"
    if versions.is_dir():
        candidates = sorted(
            (
                candidate
                for candidate in versions.iterdir()
                if candidate.is_dir() and not candidate.name.startswith(".")
            ),
            key=lambda candidate: candidate.name,
            reverse=True,
        )
        for candidate in candidates:
            if model_artifacts_valid(candidate):
                return candidate
    if model_artifacts_valid(root):
        return root
    raise FileNotFoundError(f"没有可用模型，请先执行 train: {root}")


def prediction_artifact_path(model_dir: Path, prediction_date: date) -> Path:
    """返回指定模型和交易日的预测制品路径。"""
    return model_dir / "predictions" / f"ranking-{prediction_date.isoformat()}.parquet"


def prediction_artifact_valid(model_dir: Path, prediction_date: date) -> bool:
    """检查单日完整横截面预测的文件、字段和日期。"""
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


def train_model_candidate(
    qlib_dir: Path,
    root: Path,
    segments: ModelSegments,
    feature_batch_days: int,
) -> dict[str, object]:
    """在独立版本目录训练并发布完整模型制品。"""
    versions = root / "versions"
    versions.mkdir(parents=True, exist_ok=True)
    version = (
        utc_now().strftime("%Y%m%dT%H%M%S%fZ")
        + "-"
        + uuid4().hex[:8]
    )
    staging = versions / f".{version}.building"
    published = versions / version
    checkpoint_dir = root / ".feature-checkpoint"
    shutil.rmtree(staging, ignore_errors=True)
    try:
        result = train_alpha158_lightgbm_adaptive(
            qlib_dir,
            staging,
            segments,
            feature_batch_days=feature_batch_days,
            checkpoint_dir=checkpoint_dir,
        )
        validate_model_artifacts(staging)
        staging.replace(published)
    finally:
        shutil.rmtree(staging, ignore_errors=True)
    return result | {
        "modelVersion": version,
        "artifactPath": str(published),
    }


def model_artifacts_valid(directory: Path) -> bool:
    """检查一个模型版本的必需文件、指标和预测表。"""
    try:
        if not all(
            (directory / name).is_file()
            and (directory / name).stat().st_size > 0
            for name in _REQUIRED_FILES
        ):
            return False
        metrics = json.loads(
            (directory / "metrics.json").read_text(encoding="utf-8")
        )
        top = pd.read_parquet(directory / "top5.parquet")
    except Exception:
        return False
    return metrics.get("model") == "LightGBM Regression" and not top.empty


def validate_model_artifacts(directory: Path) -> None:
    """发布前完整读取模型和配套制品，拒绝切换到损坏版本。"""
    if not model_artifacts_valid(directory):
        raise ValueError(f"训练制品检查失败: {directory}")
    try:
        predictions = pd.read_parquet(
            directory / "test_predictions.parquet",
            columns=["datetime", "instrument"],
        )
        if predictions.empty:
            raise ValueError("测试预测为空")
        with (directory / "model.pkl").open("rb") as stream:
            pickle.load(stream)
    except Exception as error:
        raise ValueError(f"训练模型无法反序列化: {directory / 'model.pkl'}") from error


# AI_GENERATE_END -------------
