# AI_GENERATE_START --
from __future__ import annotations

from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from torch import Tensor, nn


def export_lstm_onnx(
    model: nn.Module,
    sample_input: Tensor,
    output_path: Path,
    opset_version: int,
) -> None:
    """以 FP32 导出动态 Batch 的 LSTM ONNX，并执行结构检查。"""

    output_path.parent.mkdir(parents=True, exist_ok=True)
    model.eval()
    torch.onnx.export(
        model,
        sample_input,
        output_path,
        input_names=["features"],
        output_names=["predictions"],
        dynamic_axes={"features": {0: "batch"}, "predictions": {0: "batch"}},
        opset_version=opset_version,
        do_constant_folding=True,
    )
    onnx.checker.check_model(onnx.load(output_path))


def verify_onnx_outputs(model: nn.Module, onnx_path: Path, sample_input: Tensor) -> float:
    """比较 PyTorch 与 ONNX Runtime 输出并返回最大绝对误差。"""

    model.eval()
    cpu_input = sample_input.detach().to(device="cpu", dtype=torch.float32)
    with torch.no_grad():
        expected = model.to("cpu")(cpu_input).cpu().numpy()
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    actual = session.run(["predictions"], {"features": cpu_input.numpy()})[0]
    return float(np.max(np.abs(expected - actual)))


class _SentimentOnnxWrapper(nn.Module):
    """将 Transformers 输出对象收敛为稳定的 logits Tensor。"""

    def __init__(self, model: nn.Module) -> None:
        super().__init__()
        self.model = model

    def forward(self, input_ids: Tensor, attention_mask: Tensor) -> Tensor:
        return self.model(input_ids=input_ids, attention_mask=attention_mask).logits


def export_sentiment_onnx(
    model: nn.Module,
    input_ids: Tensor,
    attention_mask: Tensor,
    output_path: Path,
    opset_version: int,
) -> None:
    """导出 FP32 文本分类 ONNX，动态支持 Batch 和序列长度。"""

    output_path.parent.mkdir(parents=True, exist_ok=True)
    wrapper = _SentimentOnnxWrapper(model).eval()
    torch.onnx.export(
        wrapper,
        (input_ids, attention_mask),
        output_path,
        input_names=["input_ids", "attention_mask"],
        output_names=["logits"],
        dynamic_axes={
            "input_ids": {0: "batch", 1: "sequence"},
            "attention_mask": {0: "batch", 1: "sequence"},
            "logits": {0: "batch"},
        },
        opset_version=opset_version,
        do_constant_folding=True,
    )
    onnx.checker.check_model(onnx.load(output_path))


def verify_sentiment_onnx(
    model: nn.Module, onnx_path: Path, input_ids: Tensor, attention_mask: Tensor
) -> float:
    """返回 Transformers 与 ONNX Runtime logits 的最大绝对误差。"""

    model.eval().to("cpu")
    ids = input_ids.detach().cpu()
    mask = attention_mask.detach().cpu()
    with torch.no_grad():
        expected = model(input_ids=ids, attention_mask=mask).logits.numpy()
    session = ort.InferenceSession(str(onnx_path), providers=["CPUExecutionProvider"])
    actual = session.run(
        ["logits"], {"input_ids": ids.numpy(), "attention_mask": mask.numpy()}
    )[0]
    return float(np.max(np.abs(expected - actual)))
# AI_GENERATE_END --
