# AI_GENERATE_START ----
from __future__ import annotations

from pathlib import Path
from typing import Literal

import yaml
from pydantic import BaseModel, ConfigDict, Field


class RuntimeConfig(BaseModel):
    """离线模型任务使用的运行目录配置。"""

    model_config = ConfigDict(extra="forbid")

    root_dir: Path = Path("./runtime")
    datasets_dir: Path = Path("./runtime/datasets")
    runs_dir: Path = Path("./runtime/runs")
    artifacts_dir: Path = Path("./runtime/artifacts")
    cache_dir: Path = Path("./runtime/cache")


class ModelConfig(BaseModel):
    """模型制品的默认格式与精度配置。"""

    model_config = ConfigDict(extra="forbid")

    artifact_format: Literal["onnx"] = "onnx"
    default_precision: Literal["fp32"] = "fp32"
    opset_version: int = Field(default=17, ge=13)


class TrainingConfig(BaseModel):
    """LSTM 与情感模型共用的离线训练运行参数。"""

    model_config = ConfigDict(extra="forbid")

    seed: int = 20261009
    device: Literal["auto", "cpu", "cuda"] = "auto"
    num_workers: int = Field(default=4, ge=0, le=16)


class LstmConfig(BaseModel):
    """共享多任务 LSTM 的结构、训练和验收参数。"""

    model_config = ConfigDict(extra="forbid")

    hidden_size: int = Field(default=50, ge=8)
    num_layers: int = Field(default=2, ge=1, le=8)
    dropout: float = Field(default=0.2, ge=0.0, lt=1.0)
    stock_embedding_buckets: int = Field(default=8192, ge=2)
    stock_embedding_size: int = Field(default=16, ge=1)
    industry_embedding_buckets: int = Field(default=512, ge=2)
    industry_embedding_size: int = Field(default=8, ge=1)
    group_head_count: int = Field(default=8, ge=2)
    residual_head_enabled: bool = True
    group_head_scale: float = Field(default=0.1, ge=0.0)
    residual_head_scale: float = Field(default=0.1, ge=0.0)
    return_loss_weight: float = Field(default=0.5, gt=0.0)
    direction_loss_weight: float = Field(default=0.3, gt=0.0)
    downside_loss_weight: float = Field(default=0.2, gt=0.0)
    batch_size: int = Field(default=32, ge=1)
    epochs: int = Field(default=100, ge=1)
    learning_rate: float = Field(default=0.001, gt=0.0)
    patience: int = Field(default=10, ge=1)
    min_delta: float = Field(default=0.0001, ge=0.0)
    onnx_max_abs_error: float = Field(default=0.0001, gt=0.0)


class SentimentConfig(BaseModel):
    """情感模型微调、评估和 ONNX 导出参数。"""

    model_config = ConfigDict(extra="forbid")

    pretrained_model: str = "yiyanghkust/finbert-tone-chinese"
    max_sequence_length: int = Field(default=128, ge=16, le=512)
    batch_size: int = Field(default=16, ge=1)
    epochs: int = Field(default=5, ge=1)
    learning_rate: float = Field(default=0.00002, gt=0.0)
    weight_decay: float = Field(default=0.01, ge=0.0)
    negative_label_id: int = Field(default=2, ge=0)
    onnx_max_abs_error: float = Field(default=0.0001, gt=0.0)


class ReleaseConfig(BaseModel):
    """模型制品发布边界，防止 Python 直接激活生产模型。"""

    model_config = ConfigDict(extra="forbid")

    allow_int8_fallback: bool = True
    int8_requires_resource_failure: bool = True
    activate_from_python: bool = False


class AppConfig(BaseModel):
    """Python 模型端的根配置对象。"""

    model_config = ConfigDict(extra="forbid")

    runtime: RuntimeConfig = RuntimeConfig()
    model: ModelConfig = ModelConfig()
    training: TrainingConfig = TrainingConfig()
    lstm: LstmConfig = LstmConfig()
    sentiment: SentimentConfig = SentimentConfig()
    release: ReleaseConfig = ReleaseConfig()


def load_config(path: Path) -> AppConfig:
    """读取 YAML 配置并进行严格字段校验，未知字段会被拒绝。"""

    raw = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return AppConfig.model_validate(raw)
# AI_GENERATE_END ----
