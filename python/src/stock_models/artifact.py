# AI_GENERATE_START ----
from __future__ import annotations

import json
import shutil
from pathlib import Path

from stock_models.contracts import ModelMetadata, ModelMetrics
from stock_models.hashing import sha256_file


def build_artifact(
    output_dir: Path,
    model_path: Path,
    metadata_path: Path,
    metrics_path: Path,
    supplemental_paths: list[Path] | None = None,
) -> Path:
    """构建版本化候选制品目录，但不修改生产 current 指针。"""

    metadata = ModelMetadata.model_validate_json(metadata_path.read_text(encoding="utf-8"))
    metrics = ModelMetrics.model_validate_json(metrics_path.read_text(encoding="utf-8"))
    actual_digest = sha256_file(model_path)
    if actual_digest != metadata.model_sha256:
        raise ValueError("model SHA-256 does not match metadata")
    if metadata.model_name != metrics.model_name or metadata.model_version != metrics.model_version:
        raise ValueError("metadata and metrics identify different models")
    if not metrics.passed:
        raise ValueError("model metrics did not pass release gates")

    # Python 只生成候选目录；是否切换生产模型由 Java 发布流程决定。
    artifact_dir = output_dir / metadata.model_name / metadata.model_version
    if artifact_dir.exists():
        raise FileExistsError(artifact_dir)
    artifact_dir.mkdir(parents=True)
    shutil.copy2(model_path, artifact_dir / "model.onnx")
    shutil.copy2(metadata_path, artifact_dir / "metadata.json")
    shutil.copy2(metrics_path, artifact_dir / "metrics.json")
    supplemental_files: list[str] = []
    for source in supplemental_paths or []:
        destination = artifact_dir / source.name
        if source.is_dir():
            shutil.copytree(source, destination)
        else:
            shutil.copy2(source, destination)
        supplemental_files.append(source.name)
    manifest = {
        "model": "model.onnx",
        "metadata": "metadata.json",
        "metrics": "metrics.json",
        "sha256": actual_digest,
        "supplemental": supplemental_files,
    }
    (artifact_dir / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return artifact_dir
# AI_GENERATE_END ----
