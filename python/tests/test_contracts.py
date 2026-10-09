# AI_GENERATE_START --
from datetime import datetime, timezone

import pytest
from pydantic import ValidationError

from stock_models.contracts import ModelMetadata


def test_int8_requires_resource_fallback_reason() -> None:
    """验证 INT8 制品必须说明 FP32 未通过哪项资源门槛。"""

    with pytest.raises(ValidationError):
        ModelMetadata(
            model_name="sentiment",
            model_version="v1",
            precision="int8",
            opset_version=17,
            feature_version="sentiment-v1",
            label_version="labels-v1",
            input_names=["input_ids"],
            output_names=["logits"],
            model_sha256="0" * 64,
            created_at=datetime.now(timezone.utc),
            training_run_id="run-1",
        )


def test_fp32_is_valid_without_fallback_reason() -> None:
    """验证 FP32 是默认生产精度且不要求降级原因。"""

    metadata = ModelMetadata(
        model_name="sentiment",
        model_version="v1",
        precision="fp32",
        opset_version=17,
        feature_version="sentiment-v1",
        label_version="labels-v1",
        input_names=["input_ids"],
        output_names=["logits"],
        model_sha256="0" * 64,
        created_at=datetime.now(timezone.utc),
        training_run_id="run-1",
    )
    assert metadata.precision == "fp32"
# AI_GENERATE_END --
