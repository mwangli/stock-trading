# AI_GENERATE_START -
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

import torch
from torch.utils.data import Dataset

from stock_models.contracts import DatasetManifest
from stock_models.data.snapshot import validate_snapshot


@dataclass(frozen=True)
class SentimentRecord:
    """一条可追溯的金融文本情感样本。"""

    sample_id: str
    text: str
    label: int


@dataclass(frozen=True)
class SentimentSnapshot:
    """严格校验后的情感训练与验证记录。"""

    manifest: DatasetManifest
    train: list[SentimentRecord]
    validation: list[SentimentRecord]


class TokenizedSentimentDataset(Dataset[dict[str, torch.Tensor]]):
    """将固定文本记录按随模型发布的 Tokenizer 编码。"""

    def __init__(self, records, tokenizer, max_length: int) -> None:
        self.records = records
        self.tokenizer = tokenizer
        self.max_length = max_length

    def __len__(self) -> int:
        return len(self.records)

    def __getitem__(self, index: int) -> dict[str, torch.Tensor]:
        record = self.records[index]
        encoded = self.tokenizer(
            record.text,
            truncation=True,
            padding="max_length",
            max_length=self.max_length,
            return_tensors="pt",
        )
        return {
            "input_ids": encoded["input_ids"].squeeze(0),
            "attention_mask": encoded["attention_mask"].squeeze(0),
            "labels": torch.tensor(record.label, dtype=torch.long),
        }


def load_sentiment_snapshot(manifest_path: Path) -> SentimentSnapshot:
    """校验 sentiment-text 快照并按 Manifest 中的 split 读取。"""

    manifest = validate_snapshot(manifest_path)
    if manifest.dataset_type != "sentiment-text":
        raise ValueError("train-sentiment requires a sentiment-text snapshot")
    root = manifest_path.parent
    paths = {entry.split: root / entry.path for entry in manifest.files}
    train = _load_records(paths["train"])
    validation = _load_records(paths["validation"])
    if not train:
        raise ValueError("sentiment training split is empty")
    return SentimentSnapshot(manifest, train, validation)


def _load_records(path: Path) -> list[SentimentRecord]:
    records: list[SentimentRecord] = []
    with path.open("r", encoding="utf-8") as source:
        for raw_line in source:
            record = json.loads(raw_line)
            records.append(
                SentimentRecord(record["sample_id"], record["text"], int(record["label"]))
            )
    return records
# AI_GENERATE_END -
