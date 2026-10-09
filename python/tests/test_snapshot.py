# AI_GENERATE_START --
from __future__ import annotations

import json
import shutil
from datetime import datetime, timezone
from pathlib import Path

import pytest

from stock_models.contracts import DatasetManifest, FileDigest
from stock_models.data.snapshot import load_manifest, validate_snapshot, write_manifest
from stock_models.hashing import sha256_file


GOLDEN_ROOT = Path(__file__).parent / "golden"


def _prepare_snapshot(tmp_path: Path, dataset_type: str) -> Path:
    """复制固定 Golden Sample，并生成与临时文件摘要一致的 Manifest。"""

    source = GOLDEN_ROOT / dataset_type
    snapshot = tmp_path / dataset_type
    shutil.copytree(source, snapshot)
    data_path = snapshot / "train.jsonl"
    schema_path = snapshot / "schema.json"
    schema = json.loads(schema_path.read_text(encoding="utf-8"))
    manifest = DatasetManifest(
        dataset_id=f"golden-{dataset_type}",
        dataset_type=schema["dataset_type"],
        feature_version="panel-embedding-v3" if dataset_type == "lstm" else "sentiment-clean-v1",
        label_version="return-direction-downside-v3" if dataset_type == "lstm" else "finbert-labels-v1",
        created_at=datetime(2026, 10, 9, 8, tzinfo=timezone.utc),
        data_cutoff=datetime(2026, 10, 8, 8, tzinfo=timezone.utc),
        schema_version=schema["schema_version"],
        files=[
            FileDigest(
                path="schema.json",
                sha256=sha256_file(schema_path),
                size_bytes=schema_path.stat().st_size,
                content_type="application/json",
                split="schema",
            ),
            FileDigest(
                path="train.jsonl",
                sha256=sha256_file(data_path),
                size_bytes=data_path.stat().st_size,
                content_type="application/x-ndjson",
                split="train",
                record_count=1,
            ),
        ],
        metadata={"schema_file": "schema.json"},
    )
    manifest_path = snapshot / "manifest.json"
    write_manifest(manifest_path, manifest)
    return manifest_path


@pytest.mark.parametrize("dataset_type", ["lstm", "sentiment"])
def test_golden_snapshot_passes_strict_validation(tmp_path: Path, dataset_type: str) -> None:
    """固定 LSTM 与情感样本必须通过完整契约校验。"""

    validate_snapshot(_prepare_snapshot(tmp_path, dataset_type))


def test_snapshot_rejects_changed_field_order(tmp_path: Path) -> None:
    """字段集合相同但顺序变化时也必须拒绝，避免跨语言列错位。"""

    manifest_path = _prepare_snapshot(tmp_path, "sentiment")
    data_path = manifest_path.parent / "train.jsonl"
    record = json.loads(data_path.read_text(encoding="utf-8"))
    reordered = {"sample_index": record.pop("sample_index"), **record}
    data_path.write_text(json.dumps(reordered, ensure_ascii=False) + "\n", encoding="utf-8")
    manifest = load_manifest(manifest_path)
    data_entry = next(entry for entry in manifest.files if entry.path == "train.jsonl")
    data_entry.sha256 = sha256_file(data_path)
    data_entry.size_bytes = data_path.stat().st_size
    write_manifest(manifest_path, manifest)

    with pytest.raises(ValueError, match="field order mismatch"):
        validate_snapshot(manifest_path)
# AI_GENERATE_END --
