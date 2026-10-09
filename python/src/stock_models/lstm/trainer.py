# AI_GENERATE_START -
from __future__ import annotations

import json
import math
import random
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from torch import Tensor
from torch.optim import Adam
from torch.utils.data import DataLoader

from stock_models.artifact import build_artifact
from stock_models.config import AppConfig
from stock_models.contracts import ModelMetadata, ModelMetrics
from stock_models.hashing import sha256_file
from stock_models.lstm.data import LstmSnapshotDataset, load_lstm_snapshot
from stock_models.lstm.loss import MultiTaskStockLoss
from stock_models.lstm.model import MultiTaskStockLstm
from stock_models.onnx.validation import export_lstm_onnx, verify_onnx_outputs
from stock_models.runtime import create_run_context


def train_lstm(config: AppConfig, manifest_path: Path) -> Path:
    """训练共享 LSTM、评估、导出 ONNX，并在通过门槛后构建候选制品。"""

    _set_seed(config.training.seed)
    snapshot = load_lstm_snapshot(manifest_path)
    device = _resolve_device(config.training.device)
    run = create_run_context(config, "lstm")
    model = MultiTaskStockLstm(snapshot.feature_count, config.lstm).to(device)
    criterion = MultiTaskStockLoss(config.lstm)
    optimizer = Adam(model.parameters(), lr=config.lstm.learning_rate)
    generator = torch.Generator().manual_seed(config.training.seed)
    train_loader = DataLoader(
        snapshot.train,
        batch_size=config.lstm.batch_size,
        shuffle=True,
        num_workers=config.training.num_workers,
        generator=generator,
    )
    validation_loader = _loader(snapshot.validation, config, shuffle=False)
    history: list[dict[str, float | int]] = []
    best_loss = math.inf
    best_state: dict[str, Tensor] | None = None
    patience = 0
    for epoch in range(1, config.lstm.epochs + 1):
        train_loss = _train_epoch(model, train_loader, criterion, optimizer, device)
        validation_loss = (
            _evaluate_loss(model, validation_loader, criterion, device)
            if validation_loader is not None
            else train_loss
        )
        history.append(
            {"epoch": epoch, "train_loss": train_loss, "validation_loss": validation_loss}
        )
        if validation_loss + config.lstm.min_delta < best_loss:
            best_loss = validation_loss
            best_state = {
                name: value.detach().cpu().clone() for name, value in model.state_dict().items()
            }
            patience = 0
        else:
            patience += 1
            if patience >= config.lstm.patience:
                break
    if best_state is None:
        raise RuntimeError("LSTM training did not produce a finite checkpoint")
    model.load_state_dict(best_state)
    checkpoint_path = run.outputs_dir / "model.pt"
    torch.save(
        {
            "model_state_dict": best_state,
            "input_size": snapshot.feature_count,
            "sequence_length": snapshot.sequence_length,
            "lstm_config": config.lstm.model_dump(mode="json"),
            "feature_version": snapshot.manifest.feature_version,
            "label_version": snapshot.manifest.label_version,
        },
        checkpoint_path,
    )
    evaluation_dataset = snapshot.validation if len(snapshot.validation) else snapshot.train
    evaluation = _evaluate_predictions(model, evaluation_dataset, config, device)
    sample_input = evaluation_dataset.features[: min(8, len(evaluation_dataset))].to(device)
    onnx_path = run.outputs_dir / "model.onnx"
    export_lstm_onnx(model, sample_input, onnx_path, config.model.opset_version)
    max_abs_error = verify_onnx_outputs(model, onnx_path, sample_input)
    passed = (
        len(snapshot.validation) > 0
        and math.isfinite(best_loss)
        and max_abs_error <= config.lstm.onnx_max_abs_error
    )
    model_version = run.run_id
    metadata = ModelMetadata(
        model_name="lstm-global-shared",
        model_version=model_version,
        precision="fp32",
        opset_version=config.model.opset_version,
        feature_version=snapshot.manifest.feature_version,
        label_version=snapshot.manifest.label_version,
        input_names=["features"],
        output_names=["predictions"],
        model_sha256=sha256_file(onnx_path),
        created_at=datetime.now(timezone.utc),
        training_run_id=run.run_id,
        compatibility={
            "sequenceLength": snapshot.sequence_length,
            "inputSize": snapshot.feature_count,
            "outputSize": 3,
            "hashAlgorithm": "java-string-hashcode-floor-mod-v1",
            "stockEmbeddingBuckets": config.lstm.stock_embedding_buckets,
            "industryEmbeddingBuckets": config.lstm.industry_embedding_buckets,
            "groupHeadCount": config.lstm.group_head_count,
        },
    )
    metrics_values = {
        "best_validation_loss": float(best_loss),
        "rank_ic": evaluation["rank_ic"],
        "direction_accuracy": evaluation["direction_accuracy"],
        "top_k_mean_target": evaluation["top_k_mean_target"],
        "onnx_max_abs_error": max_abs_error,
        "epochs_completed": float(len(history)),
    }
    metrics = ModelMetrics(
        model_name=metadata.model_name,
        model_version=model_version,
        dataset_id=snapshot.manifest.dataset_id,
        metrics=metrics_values,
        passed=passed,
        evaluated_at=datetime.now(timezone.utc),
    )
    metadata_path = run.outputs_dir / "metadata.json"
    metrics_path = run.outputs_dir / "metrics.json"
    history_path = run.outputs_dir / "history.json"
    metadata_path.write_text(metadata.model_dump_json(indent=2) + "\n", encoding="utf-8")
    metrics_path.write_text(metrics.model_dump_json(indent=2) + "\n", encoding="utf-8")
    history_path.write_text(
        json.dumps(history, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if passed:
        build_artifact(
            config.runtime.artifacts_dir, onnx_path, metadata_path, metrics_path
        )
    return run.run_dir


def _loader(dataset: LstmSnapshotDataset, config: AppConfig, shuffle: bool) -> DataLoader | None:
    if len(dataset) == 0:
        return None
    return DataLoader(
        dataset,
        batch_size=config.lstm.batch_size,
        shuffle=shuffle,
        num_workers=config.training.num_workers,
    )


def _train_epoch(model, loader, criterion, optimizer, device: torch.device) -> float:
    model.train()
    losses: list[float] = []
    for features, targets in loader:
        optimizer.zero_grad(set_to_none=True)
        loss = criterion(model(features.to(device)), targets.to(device))
        if not torch.isfinite(loss):
            raise RuntimeError("LSTM training produced NaN or Infinity")
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), max_norm=5.0)
        optimizer.step()
        losses.append(float(loss.detach().cpu()))
    return float(np.mean(losses))


def _evaluate_loss(model, loader, criterion, device: torch.device) -> float:
    model.eval()
    losses: list[float] = []
    with torch.no_grad():
        for features, targets in loader:
            loss = criterion(model(features.to(device)), targets.to(device))
            losses.append(float(loss.cpu()))
    return float(np.mean(losses))


def _evaluate_predictions(
    model: MultiTaskStockLstm,
    dataset: LstmSnapshotDataset,
    config: AppConfig,
    device: torch.device,
) -> dict[str, float]:
    loader = _loader(dataset, config, shuffle=False)
    if loader is None:
        raise ValueError("LSTM evaluation dataset is empty")
    predictions: list[np.ndarray] = []
    targets: list[np.ndarray] = []
    model.eval()
    with torch.no_grad():
        for features, batch_targets in loader:
            predictions.append(model(features.to(device)).cpu().numpy())
            targets.append(batch_targets.numpy())
    predicted = np.concatenate(predictions)
    actual = np.concatenate(targets)
    rank_ic = _safe_correlation(predicted[:, 0], actual[:, 0])
    direction_accuracy = float(
        np.mean((predicted[:, 1] >= 0.5) == (actual[:, 1] >= 0.5))
    )
    top_k = max(1, min(20, len(predicted)))
    top_indices = np.argsort(predicted[:, 0])[-top_k:]
    return {
        "rank_ic": rank_ic,
        "direction_accuracy": direction_accuracy,
        "top_k_mean_target": float(np.mean(actual[top_indices, 0])),
    }


def _safe_correlation(left: np.ndarray, right: np.ndarray) -> float:
    if len(left) < 2 or np.std(left) == 0 or np.std(right) == 0:
        return 0.0
    return float(np.corrcoef(left, right)[0, 1])


def _resolve_device(value: str) -> torch.device:
    if value == "cuda":
        if not torch.cuda.is_available():
            raise RuntimeError("CUDA was requested but is unavailable")
        return torch.device("cuda")
    if value == "auto" and torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")


def _set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
# AI_GENERATE_END -
