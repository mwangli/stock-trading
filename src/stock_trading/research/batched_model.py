# AI_GENERATE_START ---------
from __future__ import annotations

import gc
import hashlib
import json
import logging
import os
import shutil
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import TYPE_CHECKING, Any

import lightgbm as lgb
import numpy as np
import pandas as pd

from stock_trading.utils.logging_utils import set_log_context

if TYPE_CHECKING:
    from stock_trading.research.model import ModelSegments

LOGGER = logging.getLogger(__name__)
GIB = 1024**3
MEMORY_THRESHOLD_BYTES = 16 * GIB
CHECKPOINT_SCHEMA_VERSION = 1
FEATURE_CONFIG_VERSION = "alpha158-kbar-price-rolling-v1"


@dataclass(frozen=True)
class FeatureShard:
    feature_path: Path
    label_path: Path
    index_path: Path
    rows: int


@dataclass(frozen=True)
class BatchedTrainingResult:
    model: Any
    scored: pd.DataFrame
    train_rows: int
    valid_rows: int
    feature_count: int
    feature_batch_days: int


def total_physical_memory_bytes() -> int | None:
    """Return physical memory without adding a runtime dependency."""
    meminfo = Path("/proc/meminfo")
    try:
        for line in meminfo.read_text(encoding="ascii").splitlines():
            if line.startswith("MemTotal:"):
                return int(line.split()[1]) * 1024
    except (OSError, ValueError, IndexError):
        pass
    try:
        return int(os.sysconf("SC_PHYS_PAGES")) * int(os.sysconf("SC_PAGE_SIZE"))
    except (AttributeError, OSError, TypeError, ValueError):
        return None


def use_batched_training(total_memory: int | None) -> bool:
    return total_memory is not None and total_memory < MEMORY_THRESHOLD_BYTES


def train_alpha158_lightgbm_batched(
    qlib_dir: Path,
    work_dir: Path,
    segments: ModelSegments,
    progress_callback,
    feature_batch_days: int,
    model_parameters: dict[str, object],
) -> BatchedTrainingResult:
    """Generate Alpha158 by date batches and train from memory-mapped shards."""
    import qlib
    from qlib.constant import REG_CN

    set_log_context(step="1/5", progress="-/-", target="Qlib")
    LOGGER.info("初始化 Qlib：目录=%s", qlib_dir)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=1)
    feature_names, shards = _build_feature_shards(
        qlib_dir,
        work_dir,
        segments,
        feature_batch_days,
    )
    train_shards = shards["train"]
    valid_shards = shards["valid"]
    test_shards = shards["test"]
    if not train_shards or not valid_shards or not test_shards:
        raise ValueError("batched Alpha158 data is incomplete")

    set_log_context(
        step="3/5",
        progress="-/-",
        target="LightGBM \u6570\u636e\u96c6",
    )
    LOGGER.info(
        "构建 LightGBM 数据集：训练=%d；验证=%d；特征=%d",
        sum(value.rows for value in train_shards),
        sum(value.rows for value in valid_shards),
        len(feature_names),
    )
    train_sequences = [_NpyFeatureSequence(value.feature_path) for value in train_shards]
    valid_sequences = [_NpyFeatureSequence(value.feature_path) for value in valid_shards]
    train_labels = _load_labels(train_shards)
    valid_labels = _load_labels(valid_shards)
    dataset_params = {
        "max_bin": 255,
        "force_col_wise": True,
        "histogram_pool_size": 256,
    }
    train_set = lgb.Dataset(
        train_sequences,
        label=train_labels,
        feature_name=feature_names,
        params=dataset_params,
        free_raw_data=True,
    )
    valid_set = lgb.Dataset(
        valid_sequences,
        label=valid_labels,
        feature_name=feature_names,
        reference=train_set,
        params=dataset_params,
        free_raw_data=True,
    )
    params = {
        "objective": model_parameters["objective"],
        "metric": model_parameters["metric"],
        "learning_rate": model_parameters["learning_rate"],
        "num_leaves": model_parameters["num_leaves"],
        "max_depth": model_parameters["max_depth"],
        "bagging_fraction": model_parameters["sample_fraction"],
        "bagging_freq": 0,
        "feature_fraction": model_parameters["feature_fraction"],
        "lambda_l1": model_parameters["lambda_l1"],
        "lambda_l2": model_parameters["lambda_l2"],
        "num_threads": model_parameters["threads"],
        "seed": model_parameters["seed"],
        "verbosity": model_parameters["verbosity"],
        **dataset_params,
    }
    set_log_context(step="4/5", progress="0/300", target="LightGBM")
    LOGGER.info("开始模型迭代：最大迭代=300；早停轮数=30")
    model = lgb.train(
        params,
        train_set,
        num_boost_round=300,
        valid_sets=[valid_set],
        valid_names=["valid"],
        callbacks=[
            lgb.early_stopping(30, verbose=False),
            progress_callback,
        ],
    )
    LOGGER.info("LightGBM 训练完成：最佳迭代=%s", model.best_iteration)
    set_log_context(step="5/5", progress="-/-", target="测试集评分")
    LOGGER.info("分批生成测试集评分")
    scored = _predict_test_shards(model, test_shards)
    return BatchedTrainingResult(
        model=model,
        scored=scored,
        train_rows=len(train_labels),
        valid_rows=len(valid_labels),
        feature_count=len(feature_names),
        feature_batch_days=feature_batch_days,
    )


def load_alpha158_features_for_date(
    qlib_dir: Path,
    prediction_date: date,
) -> pd.DataFrame:
    """Build one full-market Alpha158 cross section without loading prior output dates."""
    import qlib
    from qlib.constant import REG_CN
    from qlib.data import D

    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=1)
    expressions, feature_names = _alpha158_feature_config()
    frame = D.features(
        D.instruments("all"),
        expressions,
        start_time=prediction_date,
        end_time=prediction_date,
        freq="day",
        disk_cache=0,
    )
    frame.columns = feature_names
    return _normalize_index(frame)


def _build_feature_shards(
    qlib_dir: Path,
    work_dir: Path,
    segments: ModelSegments,
    feature_batch_days: int,
) -> tuple[list[str], dict[str, list[FeatureShard]]]:
    from qlib.data import D

    expressions, feature_names = _alpha158_feature_config()
    calendar = [
        date.fromisoformat(value)
        for value in (qlib_dir / "calendars" / "day.txt").read_text(encoding="utf-8").splitlines()
        if value.strip() and segments.train_start <= date.fromisoformat(value) <= segments.test_end
    ]
    batches = [
        calendar[index : index + feature_batch_days]
        for index in range(0, len(calendar), feature_batch_days)
    ]
    if not batches:
        raise ValueError("no Qlib calendar dates for batched Alpha158 training")
    manifest = _checkpoint_manifest(
        qlib_dir,
        segments,
        feature_batch_days,
        len(batches),
        feature_names,
    )
    _prepare_checkpoint(work_dir, manifest)
    shards: dict[str, list[FeatureShard]] = {"train": [], "valid": [], "test": []}
    completed = {
        batch_index: recovered
        for batch_index, batch in enumerate(batches, start=1)
        if (
            recovered := _load_completed_batch(
                work_dir,
                batch_index,
                batch,
                segments,
                len(feature_names),
            )
        )
        is not None
    }
    if completed:
        next_batch = next(
            (index for index in range(1, len(batches) + 1) if index not in completed),
            None,
        )
        LOGGER.info(
            "恢复 Alpha158 特征断点：已完成=%d/%d；下一批=%s",
            len(completed),
            len(batches),
            "全部完成" if next_batch is None else next_batch,
        )
    universe = D.instruments("all")
    set_log_context(
        step="2/5",
        progress=f"{len(completed)}/{len(batches)}",
        target="Alpha158 特征",
    )
    LOGGER.info(
        "生成 Alpha158 特征：批次=%d；每批交易日=%d",
        len(batches),
        feature_batch_days,
    )
    for batch_index, batch in enumerate(batches, start=1):
        if batch_index in completed:
            for segment_name, shard in completed[batch_index].items():
                shards[segment_name].append(shard)
            continue
        _batch_marker_path(work_dir, batch_index).unlink(missing_ok=True)
        frame = D.features(
            universe,
            [*expressions, "$label"],
            start_time=batch[0],
            end_time=batch[-1],
            freq="day",
            disk_cache=0,
        )
        frame.columns = [*feature_names, "LABEL0"]
        frame = _normalize_index(frame)
        batch_shards: dict[str, FeatureShard] = {}
        for segment_name, start_date, end_date in _segment_ranges(segments):
            part = _slice_dates(frame, start_date, end_date)
            if part.empty:
                continue
            shard = _write_shard(
                work_dir / segment_name,
                batch_index,
                part,
                feature_names,
                normalize_label=segment_name != "test",
            )
            shards[segment_name].append(shard)
            batch_shards[segment_name] = shard
        _write_batch_marker(work_dir, batch_index, batch, batch_shards)
        set_log_context(
            progress=f"{batch_index}/{len(batches)}",
            target=f"{batch[0]}~{batch[-1]}",
        )
        LOGGER.info(
            "Alpha158 \u7279\u5f81\u6279\u6b21\u5b8c\u6210\uff1a\u884c\u6570=%d",
            len(frame),
        )
        del frame
        gc.collect()
    return feature_names, shards


def _checkpoint_manifest(
    qlib_dir: Path,
    segments: ModelSegments,
    feature_batch_days: int,
    total_batches: int,
    feature_names: list[str],
) -> dict[str, object]:
    calendar_path = qlib_dir / "calendars" / "day.txt"
    instruments_path = qlib_dir / "instruments" / "all.txt"
    feature_names_payload = json.dumps(feature_names, ensure_ascii=True).encode("ascii")
    return {
        "schemaVersion": CHECKPOINT_SCHEMA_VERSION,
        "featureConfigVersion": FEATURE_CONFIG_VERSION,
        "featureNamesSha256": hashlib.sha256(feature_names_payload).hexdigest(),
        "featureBatchDays": feature_batch_days,
        "totalBatches": total_batches,
        "segments": {
            "trainStart": segments.train_start.isoformat(),
            "trainEnd": segments.train_end.isoformat(),
            "validStart": segments.valid_start.isoformat(),
            "validEnd": segments.valid_end.isoformat(),
            "testStart": segments.test_start.isoformat(),
            "testEnd": segments.test_end.isoformat(),
        },
        "qlib": {
            "path": str(qlib_dir.resolve()),
            "directoryMtimeNs": qlib_dir.stat().st_mtime_ns,
            "calendarSha256": _file_sha256(calendar_path),
            "instrumentsSha256": _file_sha256(instruments_path),
        },
    }


def _prepare_checkpoint(work_dir: Path, manifest: dict[str, object]) -> None:
    manifest_path = work_dir / "manifest.json"
    existing = None
    try:
        existing = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        pass
    if existing != manifest:
        if work_dir.exists():
            LOGGER.info("特征断点与当前训练输入不一致，清理旧断点后重新开始")
        shutil.rmtree(work_dir, ignore_errors=True)
        work_dir.mkdir(parents=True, exist_ok=True)
        _write_json(manifest_path, manifest)
    else:
        work_dir.mkdir(parents=True, exist_ok=True)
    (work_dir / "completed").mkdir(parents=True, exist_ok=True)


def _load_completed_batch(
    work_dir: Path,
    batch_index: int,
    batch: list[date],
    segments: ModelSegments,
    feature_count: int,
) -> dict[str, FeatureShard] | None:
    marker_path = _batch_marker_path(work_dir, batch_index)
    try:
        marker = json.loads(marker_path.read_text(encoding="utf-8"))
        if (
            marker["batchIndex"] != batch_index
            or marker["startDate"] != batch[0].isoformat()
            or marker["endDate"] != batch[-1].isoformat()
        ):
            return None
        recovered: dict[str, FeatureShard] = {}
        segment_rows = marker["segmentRows"]
        expected_segments = {
            segment_name
            for segment_name, start_date, end_date in _segment_ranges(segments)
            if batch[0] <= end_date and batch[-1] >= start_date
        }
        if not segment_rows or set(segment_rows) != expected_segments:
            return None
        for segment_name, rows in segment_rows.items():
            shard = _existing_shard(
                work_dir / segment_name,
                batch_index,
                int(rows),
                feature_count,
            )
            if shard is None:
                return None
            recovered[segment_name] = shard
        return recovered
    except Exception:
        return None


def _existing_shard(
    directory: Path,
    batch_index: int,
    expected_rows: int,
    feature_count: int,
) -> FeatureShard | None:
    from pyarrow.parquet import ParquetFile

    feature_path, label_path, index_path = _shard_paths(directory, batch_index)
    if expected_rows < 0 or not all(
        path.is_file() and path.stat().st_size > 0
        for path in (feature_path, label_path, index_path)
    ):
        return None
    features = np.load(feature_path, mmap_mode="r")
    labels = np.load(label_path, mmap_mode="r")
    index_rows = ParquetFile(index_path).metadata.num_rows
    if (
        features.shape != (expected_rows, feature_count)
        or labels.shape != (expected_rows,)
        or index_rows != expected_rows
    ):
        return None
    return FeatureShard(feature_path, label_path, index_path, expected_rows)


def _write_batch_marker(
    work_dir: Path,
    batch_index: int,
    batch: list[date],
    shards: dict[str, FeatureShard],
) -> None:
    _write_json(
        _batch_marker_path(work_dir, batch_index),
        {
            "batchIndex": batch_index,
            "startDate": batch[0].isoformat(),
            "endDate": batch[-1].isoformat(),
            "segmentRows": {
                segment_name: shard.rows
                for segment_name, shard in shards.items()
            },
        },
    )


def _batch_marker_path(work_dir: Path, batch_index: int) -> Path:
    return work_dir / "completed" / f"batch-{batch_index:03d}.json"


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _write_json(path: Path, payload: dict[str, object]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        temporary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def _alpha158_feature_config() -> tuple[list[str], list[str]]:
    from qlib.contrib.data.loader import Alpha158DL

    return Alpha158DL.get_feature_config(
        {
            "kbar": {},
            "price": {"windows": [0], "feature": ["OPEN", "HIGH", "LOW", "VWAP"]},
            "rolling": {},
        }
    )


def _normalize_index(frame: pd.DataFrame) -> pd.DataFrame:
    names = list(frame.index.names)
    if "datetime" not in names or "instrument" not in names:
        raise ValueError(f"unexpected Qlib feature index: {names}")
    if names != ["datetime", "instrument"]:
        frame = frame.reorder_levels(["datetime", "instrument"])
    return frame.sort_index()


def _segment_ranges(segments: ModelSegments):
    return (
        ("train", segments.train_start, segments.train_end),
        ("valid", segments.valid_start, segments.valid_end),
        ("test", segments.test_start, segments.test_end),
    )


def _slice_dates(frame: pd.DataFrame, start_date: date, end_date: date) -> pd.DataFrame:
    dates = frame.index.get_level_values("datetime")
    mask = (dates >= pd.Timestamp(start_date)) & (dates <= pd.Timestamp(end_date))
    return frame.loc[mask]


def _write_shard(
    directory: Path,
    shard_index: int,
    frame: pd.DataFrame,
    feature_names: list[str],
    normalize_label: bool,
) -> FeatureShard:
    directory.mkdir(parents=True, exist_ok=True)
    label = frame["LABEL0"]
    if normalize_label:
        eligible = label.notna()
        frame = frame.loc[eligible]
        label = label.loc[eligible]
        grouped = label.groupby(level="datetime")
        label = (label - grouped.transform("mean")) / grouped.transform("std")
    feature_path, label_path, index_path = _shard_paths(directory, shard_index)
    _write_npy(feature_path, frame.loc[:, feature_names].to_numpy(copy=False))
    _write_npy(label_path, label.to_numpy(copy=False))
    _write_parquet(index_path, frame.index.to_frame(index=False))
    return FeatureShard(feature_path, label_path, index_path, len(frame))


def _shard_paths(directory: Path, shard_index: int) -> tuple[Path, Path, Path]:
    base = directory / f"batch-{shard_index:03d}"
    return (
        base.with_suffix(".features.npy"),
        base.with_suffix(".labels.npy"),
        base.with_suffix(".index.parquet"),
    )


def _write_npy(path: Path, values: np.ndarray) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        with temporary.open("wb") as stream:
            np.save(stream, values, allow_pickle=False)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def _write_parquet(path: Path, frame: pd.DataFrame) -> None:
    temporary = path.with_name(f".{path.name}.tmp")
    try:
        frame.to_parquet(temporary, index=False)
        temporary.replace(path)
    finally:
        temporary.unlink(missing_ok=True)


def _load_labels(shards: list[FeatureShard]) -> np.ndarray:
    return np.concatenate([np.load(value.label_path, mmap_mode="r") for value in shards])


def _predict_test_shards(model, shards: list[FeatureShard]) -> pd.DataFrame:
    frames: list[pd.DataFrame] = []
    for shard in shards:
        features = np.load(shard.feature_path, mmap_mode="r")
        index = pd.read_parquet(shard.index_path)
        labels = np.load(shard.label_path, mmap_mode="r")
        scores = model.predict(features, num_iteration=model.best_iteration)
        frame = index.assign(score=scores, label=np.asarray(labels)).set_index(
            ["datetime", "instrument"]
        )
        frames.append(frame)
    scored = pd.concat(frames).sort_index()
    scored.index.names = ["datetime", "instrument"]
    return scored


class _NpyFeatureSequence(lgb.Sequence):
    batch_size = 4096

    def __init__(self, source: Path) -> None:
        self._values = np.load(source, mmap_mode="r")

    def __len__(self) -> int:
        return len(self._values)

    def __getitem__(self, index):
        return np.asarray(self._values[index], dtype=np.float64)

# AI_GENERATE_END ---------
