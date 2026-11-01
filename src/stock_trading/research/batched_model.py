# AI_GENERATE_START ---
from __future__ import annotations

import gc
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

from stock_trading.logging_utils import set_log_context

if TYPE_CHECKING:
    from stock_trading.research.model import ModelSegments

LOGGER = logging.getLogger(__name__)
GIB = 1024**3
MEMORY_THRESHOLD_BYTES = 16 * GIB
FEATURE_BATCH_DAYS = 20


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
) -> BatchedTrainingResult:
    """Generate Alpha158 by date batches and train from memory-mapped shards."""
    import qlib
    from qlib.constant import REG_CN

    set_log_context(progress="1/5", target="Qlib")
    LOGGER.info("初始化 Qlib：目录=%s", qlib_dir)
    qlib.init(provider_uri=str(qlib_dir), region=REG_CN, kernels=1)
    feature_names, shards = _build_feature_shards(qlib_dir, work_dir, segments)
    train_shards = shards["train"]
    valid_shards = shards["valid"]
    test_shards = shards["test"]
    if not train_shards or not valid_shards or not test_shards:
        raise ValueError("batched Alpha158 data is incomplete")

    set_log_context(progress="3/5", target="LightGBM \u6570\u636e\u96c6")
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
        "objective": "regression",
        "metric": "l2",
        "learning_rate": 0.05,
        "num_leaves": 31,
        "max_depth": 6,
        "bagging_fraction": 0.8,
        "bagging_freq": 0,
        "feature_fraction": 0.8,
        "lambda_l1": 1.0,
        "lambda_l2": 1.0,
        "num_threads": 2,
        "seed": 20261030,
        "verbosity": -1,
        **dataset_params,
    }
    set_log_context(progress="4/5", target="LightGBM")
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
    set_log_context(progress="5/5", target="测试集评分")
    LOGGER.info("分批生成测试集评分")
    scored = _predict_test_shards(model, test_shards)
    return BatchedTrainingResult(
        model=model,
        scored=scored,
        train_rows=len(train_labels),
        valid_rows=len(valid_labels),
        feature_count=len(feature_names),
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
) -> tuple[list[str], dict[str, list[FeatureShard]]]:
    from qlib.data import D

    expressions, feature_names = _alpha158_feature_config()
    calendar = [
        date.fromisoformat(value)
        for value in (qlib_dir / "calendars" / "day.txt").read_text(encoding="utf-8").splitlines()
        if value.strip() and segments.train_start <= date.fromisoformat(value) <= segments.test_end
    ]
    batches = [
        calendar[index : index + FEATURE_BATCH_DAYS]
        for index in range(0, len(calendar), FEATURE_BATCH_DAYS)
    ]
    if not batches:
        raise ValueError("no Qlib calendar dates for batched Alpha158 training")
    shutil.rmtree(work_dir, ignore_errors=True)
    work_dir.mkdir(parents=True, exist_ok=True)
    shards: dict[str, list[FeatureShard]] = {"train": [], "valid": [], "test": []}
    universe = D.instruments("all")
    set_log_context(progress="2/5", target="Alpha158 特征")
    LOGGER.info(
        "生成 Alpha158 特征：批次=%d；每批交易日=%d",
        len(batches),
        FEATURE_BATCH_DAYS,
    )
    for batch_index, batch in enumerate(batches, start=1):
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
        for segment_name, start_date, end_date in _segment_ranges(segments):
            part = _slice_dates(frame, start_date, end_date)
            if part.empty:
                continue
            shards[segment_name].append(
                _write_shard(
                    work_dir / segment_name,
                    len(shards[segment_name]),
                    part,
                    feature_names,
                    normalize_label=segment_name != "test",
                )
            )
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
    base = directory / f"part-{shard_index:04d}"
    feature_path = base.with_suffix(".features.npy")
    label_path = base.with_suffix(".labels.npy")
    index_path = base.with_suffix(".index.parquet")
    _write_npy(feature_path, frame.loc[:, feature_names].to_numpy(copy=False))
    _write_npy(label_path, label.to_numpy(copy=False))
    _write_parquet(index_path, frame.index.to_frame(index=False))
    return FeatureShard(feature_path, label_path, index_path, len(frame))


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
        return np.asarray(self._values[index])

# AI_GENERATE_END ---
