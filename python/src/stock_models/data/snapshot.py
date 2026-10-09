# AI_GENERATE_START ------
from __future__ import annotations

import json
import math
from datetime import date, datetime
from pathlib import Path
from typing import Any

from stock_models.contracts import DatasetColumn, DatasetManifest, DatasetSchema, FileDigest
from stock_models.hashing import sha256_file


def load_manifest(path: Path) -> DatasetManifest:
    """使用 Pydantic 严格读取训练数据 Manifest。"""

    return DatasetManifest.model_validate_json(path.read_text(encoding="utf-8"))


def validate_snapshot(manifest_path: Path) -> DatasetManifest:
    """校验文件摘要、Schema、JSONL 字段顺序、类型、Shape 和记录数。"""

    manifest = load_manifest(manifest_path)
    root = manifest_path.parent.resolve()
    # Manifest 中只能使用相对路径，防止快照引用数据目录之外的文件。
    for expected in manifest.files:
        candidate = (root / expected.path).resolve()
        if root != candidate and root not in candidate.parents:
            raise ValueError(f"snapshot file escapes dataset directory: {expected.path}")
        if not candidate.is_file():
            raise FileNotFoundError(candidate)
        if candidate.stat().st_size != expected.size_bytes:
            raise ValueError(f"snapshot size mismatch: {expected.path}")
        actual_digest = sha256_file(candidate)
        if actual_digest != expected.sha256:
            raise ValueError(f"snapshot digest mismatch: {expected.path}")
    schema = _load_schema(root, manifest)
    for expected in manifest.files:
        if expected.content_type == "application/x-ndjson":
            _validate_jsonl(root / expected.path, expected, schema)
    return manifest


def _load_schema(root: Path, manifest: DatasetManifest) -> DatasetSchema:
    """读取 Manifest 指定的 Schema，并校验数据集类型和版本。"""

    schema_path = root / str(manifest.metadata["schema_file"])
    schema = DatasetSchema.model_validate_json(schema_path.read_text(encoding="utf-8"))
    if schema.dataset_type != manifest.dataset_type:
        raise ValueError("dataset schema type does not match manifest")
    if schema.schema_version != manifest.schema_version:
        raise ValueError("dataset schema version does not match manifest")
    return schema


def _validate_jsonl(path: Path, expected: FileDigest, schema: DatasetSchema) -> None:
    """逐行校验 JSONL，避免大快照一次性加载进内存。"""

    expected_names = [column.name for column in schema.columns]
    record_count = 0
    with path.open("r", encoding="utf-8") as source:
        for line_number, raw_line in enumerate(source, start=1):
            if not raw_line.strip():
                raise ValueError(f"blank JSONL record: {path.name}:{line_number}")
            pairs = json.loads(raw_line, object_pairs_hook=lambda value: value)
            if not isinstance(pairs, list) or any(
                    not isinstance(pair, tuple) or len(pair) != 2 for pair in pairs
            ):
                raise ValueError(f"JSONL record must be an object: {path.name}:{line_number}")
            names = [name for name, _ in pairs]
            if len(names) != len(set(names)):
                raise ValueError(f"JSONL contains duplicate fields: {path.name}:{line_number}")
            record = dict(pairs)
            if list(record) != expected_names:
                raise ValueError(f"JSONL field order mismatch: {path.name}:{line_number}")
            for column in schema.columns:
                _validate_value(record[column.name], column, path.name, line_number)
            record_count += 1
    if record_count != expected.record_count:
        raise ValueError(f"snapshot record count mismatch: {expected.path}")


def _validate_value(value: Any, column: DatasetColumn, file_name: str, line_number: int) -> None:
    """按声明类型校验单个字段，错误中保留文件和行号。"""

    location = f"{file_name}:{line_number}:{column.name}"
    if value is None:
        if column.nullable:
            return
        raise ValueError(f"non-nullable value is null: {location}")
    if column.dtype == "string":
        if not isinstance(value, str):
            raise ValueError(f"expected string: {location}")
        return
    if column.dtype == "int64":
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError(f"expected int64: {location}")
        return
    if column.dtype == "float32":
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError(f"expected finite float32: {location}")
        return
    if column.dtype == "date":
        if not isinstance(value, str):
            raise ValueError(f"expected ISO date: {location}")
        date.fromisoformat(value)
        return
    if column.dtype == "datetime":
        if not isinstance(value, str):
            raise ValueError(f"expected ISO datetime: {location}")
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            raise ValueError(f"expected timezone-aware datetime: {location}")
        return
    if column.dtype == "float32_matrix":
        _validate_matrix(value, column.shape or [], location)
        return
    raise AssertionError(f"unsupported dataset dtype: {column.dtype}")


def _validate_matrix(value: Any, shape: list[int], location: str) -> None:
    """递归校验固定维度浮点矩阵。"""

    if not shape:
        if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(value):
            raise ValueError(f"expected finite matrix value: {location}")
        return
    if not isinstance(value, list) or len(value) != shape[0]:
        raise ValueError(f"matrix shape mismatch: {location}")
    for item in value:
        _validate_matrix(item, shape[1:], location)


def write_manifest(path: Path, manifest: DatasetManifest) -> None:
    """以稳定字段顺序写出无 BOM UTF-8 Manifest。"""

    payload = json.dumps(
        manifest.model_dump(mode="json"), ensure_ascii=False, indent=2, sort_keys=True
    )
    path.write_text(payload + "\n", encoding="utf-8")
# AI_GENERATE_END ------
