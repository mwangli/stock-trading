# AI_GENERATE_START -
from __future__ import annotations

import json
import math
import random
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import torch
from sklearn.metrics import accuracy_score, f1_score, recall_score
from torch.optim import AdamW
from torch.utils.data import DataLoader
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from stock_models.artifact import build_artifact
from stock_models.config import AppConfig
from stock_models.contracts import ModelMetadata, ModelMetrics
from stock_models.hashing import sha256_file
from stock_models.onnx.validation import export_sentiment_onnx, verify_sentiment_onnx
from stock_models.runtime import create_run_context
from stock_models.sentiment.data import TokenizedSentimentDataset, load_sentiment_snapshot


def train_sentiment(config: AppConfig, manifest_path: Path) -> Path:
    """微调金融情感模型、评估并构建带 Tokenizer 的候选 ONNX 制品。"""

    _set_seed(config.training.seed)
    snapshot = load_sentiment_snapshot(manifest_path)
    if not snapshot.validation:
        raise ValueError("sentiment validation split is required for release evaluation")
    device = _resolve_device(config.training.device)
    run = create_run_context(config, "sentiment")
    tokenizer = AutoTokenizer.from_pretrained(
        config.sentiment.pretrained_model, cache_dir=config.runtime.cache_dir
    )
    model = AutoModelForSequenceClassification.from_pretrained(
        config.sentiment.pretrained_model,
        num_labels=3,
        cache_dir=config.runtime.cache_dir,
        ignore_mismatched_sizes=True,
    ).to(device)
    train_dataset = TokenizedSentimentDataset(
        snapshot.train, tokenizer, config.sentiment.max_sequence_length
    )
    validation_dataset = TokenizedSentimentDataset(
        snapshot.validation, tokenizer, config.sentiment.max_sequence_length
    )
    train_loader = DataLoader(
        train_dataset,
        batch_size=config.sentiment.batch_size,
        shuffle=True,
        num_workers=config.training.num_workers,
    )
    validation_loader = DataLoader(
        validation_dataset,
        batch_size=config.sentiment.batch_size,
        shuffle=False,
        num_workers=config.training.num_workers,
    )
    optimizer = AdamW(
        model.parameters(),
        lr=config.sentiment.learning_rate,
        weight_decay=config.sentiment.weight_decay,
    )
    history: list[dict[str, float | int]] = []
    best_macro_f1 = -1.0
    best_state = None
    for epoch in range(1, config.sentiment.epochs + 1):
        train_loss = _train_epoch(model, train_loader, optimizer, device)
        evaluation = _evaluate(model, validation_loader, device, config.sentiment.negative_label_id)
        history.append({"epoch": epoch, "train_loss": train_loss, **evaluation})
        if evaluation["macro_f1"] > best_macro_f1:
            best_macro_f1 = evaluation["macro_f1"]
            best_state = {
                name: value.detach().cpu().clone() for name, value in model.state_dict().items()
            }
    if best_state is None:
        raise RuntimeError("sentiment training did not produce a checkpoint")
    model.load_state_dict(best_state)
    model_dir = run.outputs_dir / "pytorch-model"
    tokenizer_dir = run.outputs_dir / "tokenizer"
    model.save_pretrained(model_dir)
    tokenizer.save_pretrained(tokenizer_dir)
    evaluation = _evaluate(model, validation_loader, device, config.sentiment.negative_label_id)
    first_batch = next(iter(validation_loader))
    input_ids = first_batch["input_ids"][: min(4, len(first_batch["input_ids"]))].to(device)
    attention_mask = first_batch["attention_mask"][: len(input_ids)].to(device)
    onnx_path = run.outputs_dir / "model.onnx"
    export_sentiment_onnx(
        model, input_ids, attention_mask, onnx_path, config.model.opset_version
    )
    max_abs_error = verify_sentiment_onnx(
        model, onnx_path, input_ids, attention_mask
    )
    passed = (
        math.isfinite(evaluation["macro_f1"])
        and evaluation["negative_recall"] > 0.0
        and max_abs_error <= config.sentiment.onnx_max_abs_error
    )
    model_version = run.run_id
    metadata = ModelMetadata(
        model_name="sentiment-finbert",
        model_version=model_version,
        precision="fp32",
        opset_version=config.model.opset_version,
        feature_version=snapshot.manifest.feature_version,
        label_version=snapshot.manifest.label_version,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        model_sha256=sha256_file(onnx_path),
        created_at=datetime.now(timezone.utc),
        training_run_id=run.run_id,
        compatibility={
            "maxSequenceLength": config.sentiment.max_sequence_length,
            "id2label": {"0": "neutral", "1": "positive", "2": "negative"},
            "tokenizerDirectory": "tokenizer",
        },
    )
    metrics_values = {
        **evaluation,
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
    metadata_path.write_text(metadata.model_dump_json(indent=2) + "\n", encoding="utf-8")
    metrics_path.write_text(metrics.model_dump_json(indent=2) + "\n", encoding="utf-8")
    (run.outputs_dir / "history.json").write_text(
        json.dumps(history, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    if passed:
        build_artifact(
            config.runtime.artifacts_dir,
            onnx_path,
            metadata_path,
            metrics_path,
            supplemental_paths=[tokenizer_dir],
        )
    return run.run_dir


def _train_epoch(model, loader, optimizer, device: torch.device) -> float:
    model.train()
    losses: list[float] = []
    for batch in loader:
        optimizer.zero_grad(set_to_none=True)
        outputs = model(
            input_ids=batch["input_ids"].to(device),
            attention_mask=batch["attention_mask"].to(device),
            labels=batch["labels"].to(device),
        )
        if not torch.isfinite(outputs.loss):
            raise RuntimeError("sentiment training produced NaN or Infinity")
        outputs.loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        optimizer.step()
        losses.append(float(outputs.loss.detach().cpu()))
    return float(np.mean(losses))


def _evaluate(model, loader, device: torch.device, negative_label_id: int) -> dict[str, float]:
    model.eval()
    predicted: list[int] = []
    actual: list[int] = []
    with torch.no_grad():
        for batch in loader:
            logits = model(
                input_ids=batch["input_ids"].to(device),
                attention_mask=batch["attention_mask"].to(device),
            ).logits
            predicted.extend(logits.argmax(dim=1).cpu().tolist())
            actual.extend(batch["labels"].tolist())
    return {
        "accuracy": float(accuracy_score(actual, predicted)),
        "macro_f1": float(f1_score(actual, predicted, average="macro", zero_division=0)),
        "negative_recall": float(
            recall_score(
                actual,
                predicted,
                labels=[negative_label_id],
                average="macro",
                zero_division=0,
            )
        ),
    }


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
