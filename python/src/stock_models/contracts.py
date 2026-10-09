# AI_GENERATE_START ----
from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


class FileDigest(BaseModel):
    """数据快照或模型制品中单个文件的 SHA-256 信息。"""

    model_config = ConfigDict(extra="forbid")

    path: str = Field(min_length=1)
    sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    size_bytes: int = Field(ge=0)
    content_type: Literal["application/json", "application/x-ndjson"]
    split: Literal["schema", "train", "validation", "test"]
    record_count: int = Field(default=0, ge=0)


class DatasetColumn(BaseModel):
    """JSONL 记录中的单个字段定义，字段列表顺序即文件字段顺序。"""

    model_config = ConfigDict(extra="forbid")

    name: str = Field(min_length=1)
    dtype: Literal["string", "int64", "float32", "date", "datetime", "float32_matrix"]
    nullable: bool = False
    shape: list[int] | None = None

    @model_validator(mode="after")
    def validate_shape(self) -> "DatasetColumn":
        """矩阵字段必须固定 Shape，标量字段不能声明 Shape。"""

        if self.dtype == "float32_matrix":
            if not self.shape or any(dimension <= 0 for dimension in self.shape):
                raise ValueError("float32_matrix column requires a positive shape")
        elif self.shape is not None:
            raise ValueError("scalar column must not define shape")
        return self


class DatasetSchema(BaseModel):
    """Java 导出与 Python 读取共同遵守的 JSONL Schema。"""

    model_config = ConfigDict(extra="forbid")

    schema_version: str = Field(min_length=1)
    dataset_type: Literal["lstm-panel", "sentiment-text"]
    columns: list[DatasetColumn] = Field(min_length=1)

    @model_validator(mode="after")
    def validate_unique_columns(self) -> "DatasetSchema":
        """拒绝重复字段，避免同名字段在跨语言解析时被静默覆盖。"""

        names = [column.name for column in self.columns]
        if len(names) != len(set(names)):
            raise ValueError("dataset schema contains duplicate columns")
        return self


class DatasetManifest(BaseModel):
    """跨 Java/Python 使用的不可变训练数据快照契约。"""

    model_config = ConfigDict(extra="forbid")

    dataset_id: str = Field(min_length=1)
    dataset_type: Literal["lstm-panel", "sentiment-text"]
    feature_version: str = Field(min_length=1)
    label_version: str = Field(min_length=1)
    created_at: datetime
    data_cutoff: datetime
    schema_version: str = Field(min_length=1)
    files: list[FileDigest] = Field(min_length=1)
    metadata: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_snapshot_contract(self) -> "DatasetManifest":
        """校验文件路径唯一、Schema 引用存在及数据截止时间不晚于导出时间。"""

        paths = [entry.path for entry in self.files]
        if len(paths) != len(set(paths)):
            raise ValueError("dataset manifest contains duplicate file paths")
        schema_file = self.metadata.get("schema_file")
        if not isinstance(schema_file, str) or not schema_file:
            raise ValueError("dataset manifest requires metadata.schema_file")
        schema_entries = [entry for entry in self.files if entry.split == "schema"]
        if len(schema_entries) != 1 or schema_entries[0].path != schema_file:
            raise ValueError("dataset manifest schema entry does not match metadata.schema_file")
        if self.created_at.tzinfo is None or self.data_cutoff.tzinfo is None:
            raise ValueError("dataset timestamps must include timezone information")
        if self.data_cutoff > self.created_at:
            raise ValueError("dataset data_cutoff must not be later than created_at")
        return self


class ModelMetadata(BaseModel):
    """Java 加载 ONNX 前必须校验的模型兼容契约。"""

    model_config = ConfigDict(extra="forbid")

    model_name: str = Field(min_length=1)
    model_version: str = Field(min_length=1)
    artifact_format: Literal["onnx"] = "onnx"
    precision: Literal["fp32", "int8"] = "fp32"
    opset_version: int = Field(ge=13)
    feature_version: str = Field(min_length=1)
    label_version: str = Field(min_length=1)
    input_names: list[str] = Field(min_length=1)
    output_names: list[str] = Field(min_length=1)
    model_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    created_at: datetime
    training_run_id: str = Field(min_length=1)
    compatibility: dict[str, Any] = Field(default_factory=dict)

    @model_validator(mode="after")
    def validate_int8_reason(self) -> "ModelMetadata":
        """INT8 只能作为资源不足时的降级制品，必须记录降级原因。"""

        if self.precision == "int8" and not self.compatibility.get("int8FallbackReason"):
            raise ValueError("INT8 artifact requires compatibility.int8FallbackReason")
        return self


class ModelMetrics(BaseModel):
    """随模型制品发布的离线评估结果摘要。"""

    model_config = ConfigDict(extra="forbid")

    model_name: str = Field(min_length=1)
    model_version: str = Field(min_length=1)
    dataset_id: str = Field(min_length=1)
    metrics: dict[str, float]
    passed: bool
    evaluated_at: datetime
# AI_GENERATE_END ----
