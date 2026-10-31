# AI_GENERATE_START ---
from __future__ import annotations

import json
import pickle
import re
import shutil
from datetime import UTC, date, datetime
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.research.model import ModelSegments, train_alpha158_lightgbm

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
    pointer = root / "current.json"
    if pointer.is_file():
        try:
            payload = json.loads(pointer.read_text(encoding="utf-8"))
            version = str(payload["version"])
        except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
            raise ValueError(f"当前模型指针损坏: {pointer}") from error
        if not re.fullmatch(r"[A-Za-z0-9._-]+", version):
            raise ValueError(f"当前模型版本标识非法: {version}")
        candidate = root / "versions" / version
        if not model_artifacts_valid(candidate):
            raise ValueError(f"当前模型版本不完整: {candidate}")
        return candidate
    if model_artifacts_valid(root):
        return root
    raise FileNotFoundError(f"没有可用模型，请先执行 train: {root}")


def prediction_artifact_path(
    model_dir: Path, prediction_date: date, top_k: int = 5
) -> Path:
    """返回指定模型和交易日的预测制品路径。"""
    return model_dir / "predictions" / f"top{top_k}-{prediction_date.isoformat()}.parquet"


def prediction_artifact_valid(
    model_dir: Path, prediction_date: date, top_k: int = 5
) -> bool:
    """检查单日预测的文件、字段、日期和候选数量。"""
    try:
        frame = pd.read_parquet(prediction_artifact_path(model_dir, prediction_date, top_k))
        dates = pd.to_datetime(frame["datetime"]).dt.date.unique()
    except (OSError, ValueError, KeyError, AttributeError):
        return False
    return (
        len(frame) == top_k
        and {"datetime", "instrument", "score", "label"}.issubset(frame.columns)
        and len(dates) == 1
        and dates[0] == prediction_date
    )


def train_model_atomically(
    qlib_dir: Path,
    root: Path,
    segments: ModelSegments,
) -> dict[str, object]:
    """在不可变版本目录训练，校验后原子切换当前版本指针。"""
    versions = root / "versions"
    versions.mkdir(parents=True, exist_ok=True)
    version = (
        datetime.now(UTC).strftime("%Y%m%dT%H%M%S%fZ")
        + "-"
        + uuid4().hex[:8]
    )
    staging = versions / f".{version}.building"
    published = versions / version
    shutil.rmtree(staging, ignore_errors=True)
    try:
        result = train_alpha158_lightgbm(qlib_dir, staging, segments)
        validate_model_artifacts(staging)
        staging.replace(published)
        _switch_current_pointer(root, version)
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


def _switch_current_pointer(root: Path, version: str) -> None:
    root.mkdir(parents=True, exist_ok=True)
    pointer = root / "current.json"
    temporary = root / f".current.{uuid4().hex}.tmp"
    payload = {
        "version": version,
        "activatedAt": datetime.now(UTC).isoformat(),
    }
    try:
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(pointer)
    finally:
        temporary.unlink(missing_ok=True)
# AI_GENERATE_END ---
