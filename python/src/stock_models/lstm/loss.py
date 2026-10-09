# AI_GENERATE_START -
from __future__ import annotations

import torch
from torch import Tensor, nn

from stock_models.config import LstmConfig


class MultiTaskStockLoss(nn.Module):
    """收益率 Huber、方向 BCE、下行风险 Huber 的加权组合损失。"""

    def __init__(self, config: LstmConfig) -> None:
        super().__init__()
        self.config = config

    def forward(self, predictions: Tensor, targets: Tensor) -> Tensor:
        """计算 batch x 3 预测和标签的标量损失。"""

        if predictions.shape != targets.shape or predictions.shape[-1] != 3:
            raise ValueError("predictions and targets must have matching batch x 3 shape")
        return_loss = torch.nn.functional.huber_loss(
            predictions[:, 0], targets[:, 0], delta=0.1
        )
        direction_loss = torch.nn.functional.binary_cross_entropy(
            predictions[:, 1].clamp(1.0e-7, 1.0 - 1.0e-7), targets[:, 1]
        )
        downside_loss = torch.nn.functional.huber_loss(
            predictions[:, 2], targets[:, 2], delta=0.1
        )
        return (
            return_loss * self.config.return_loss_weight
            + direction_loss * self.config.direction_loss_weight
            + downside_loss * self.config.downside_loss_weight
        )
# AI_GENERATE_END -
