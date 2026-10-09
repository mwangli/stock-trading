# AI_GENERATE_START -
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import torch
from torch import Tensor
from torch.utils.data import Dataset

from stock_models.contracts import DatasetManifest
from stock_models.data.snapshot import validate_snapshot


@dataclass(frozen=True)
class LstmSnapshot:
    """已校验快照的训练、验证数据和维度信息。"""

    manifest: DatasetManifest
    train: "LstmSnapshotDataset"
    validation: "LstmSnapshotDataset"
    sequence_length: int
    feature_count: int


class LstmSnapshotDataset(Dataset[tuple[Tensor, Tensor]]):
    """内存中的 LSTM Tensor 数据集；单次正式训练只加载一份快照。"""

    def __init__(self, features: Tensor, targets: Tensor) -> None:
        if features.ndim != 3 or targets.ndim != 2 or targets.shape[1] != 3:
            raise ValueError("invalid LSTM dataset tensor shape")
        if features.shape[0] != targets.shape[0]:
            raise ValueError("LSTM feature and target counts differ")
        self.features = features.to(dtype=torch.float32)
        self.targets = targets.to(dtype=torch.float32)

    def __len__(self) -> int:
        return self.features.shape[0]

    def __getitem__(self, index: int) -> tuple[Tensor, Tensor]:
        return self.features[index], self.targets[index]


def load_lstm_snapshot(manifest_path: Path) -> LstmSnapshot:
    """严格校验快照后读取 train/validation JSONL。"""

    manifest = validate_snapshot(manifest_path)
    if manifest.dataset_type != "lstm-panel":
        raise ValueError("train-lstm requires an lstm-panel snapshot")
    root = manifest_path.parent
    schema_path = root / str(manifest.metadata["schema_file"])
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    feature_column = next(column for column in schema["columns"] if column["name"] == "features")
    sequence_length, feature_count = feature_column["shape"]
    split_paths = {entry.split: root / entry.path for entry in manifest.files}
    train = _load_jsonl(split_paths["train"], sequence_length, feature_count)
    validation = _load_jsonl(split_paths["validation"], sequence_length, feature_count)
    if len(train) == 0:
        raise ValueError("LSTM training split is empty")
    return LstmSnapshot(manifest, train, validation, sequence_length, feature_count)


def _load_jsonl(path: Path, sequence_length: int, feature_count: int) -> LstmSnapshotDataset:
    features: list[list[list[float]]] = []
    targets: list[list[float]] = []
    with path.open("r", encoding="utf-8") as source:
        for raw_line in source:
            record = json.loads(raw_line)
            features.append(record["features"])
            targets.append(
                [
                    record["return_target"],
                    record["direction_target"],
                    record["downside_target"],
                ]
            )
    if features:
        feature_tensor = torch.tensor(features, dtype=torch.float32)
        target_tensor = torch.tensor(targets, dtype=torch.float32)
    else:
        feature_tensor = torch.empty((0, sequence_length, feature_count), dtype=torch.float32)
        target_tensor = torch.empty((0, 3), dtype=torch.float32)
    return LstmSnapshotDataset(feature_tensor, target_tensor)
# AI_GENERATE_END -
