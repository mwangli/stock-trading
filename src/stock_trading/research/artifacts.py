# AI_GENERATE_START ----------
from __future__ import annotations

import json
import logging
import pickle
import re
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

LOGGER = logging.getLogger(__name__)
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
    """解析当前模型版本；兼容尚未生成指针的旧固定目录。"""
    version = current_model_version(root)
    if version is not None:
        candidate = root / "versions" / version
        if not model_artifacts_valid(candidate):
            raise ValueError(f"当前模型版本不完整: {candidate}")
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
    """在独立版本目录训练候选模型，不切换当前模型指针。"""
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
        shutil.rmtree(checkpoint_dir, ignore_errors=True)
        if checkpoint_dir.exists():
            LOGGER.warning("特征断点目录清理失败：%s", checkpoint_dir)
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


def current_model_version(root: Path) -> str | None:
    """读取当前模型版本；没有版本指针时返回空值。"""
    pointer = root / "current.json"
    if not pointer.is_file():
        return None
    try:
        payload = json.loads(pointer.read_text(encoding="utf-8"))
        version = str(payload["version"])
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        raise ValueError(f"当前模型指针损坏: {pointer}") from error
    if not re.fullmatch(r"[A-Za-z0-9._-]+", version):
        raise ValueError(f"当前模型版本标识非法: {version}")
    return version


def activate_model_artifact(root: Path, version: str) -> None:
    """校验候选模型后原子切换当前模型指针。"""
    candidate = root / "versions" / version
    validate_model_artifacts(candidate)
    _write_current_pointer(root, version)


def restore_model_artifact(root: Path, version: str | None) -> None:
    """在状态激活失败时恢复原模型指针。"""
    if version is None:
        (root / "current.json").unlink(missing_ok=True)
        return
    _write_current_pointer(root, version)


def _write_current_pointer(root: Path, version: str) -> None:
    root.mkdir(parents=True, exist_ok=True)
    pointer = root / "current.json"
    temporary = root / f".current.{uuid4().hex}.tmp"
    payload = {
        "version": version,
        "activatedAt": utc_now().isoformat(),
    }
    try:
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(pointer)
    finally:
        temporary.unlink(missing_ok=True)
# AI_GENERATE_END ----------
