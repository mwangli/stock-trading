# AI_GENERATE_START -
from __future__ import annotations

import torch
from torch import Tensor, nn

from stock_models.config import LstmConfig


class MultiTaskStockLstm(nn.Module):
    """与 Java 共享模型结构对齐的多任务 LSTM。"""

    stock_index_column = 11
    industry_index_column = 12
    group_index_column = 13
    continuous_tail_start = 14
    output_size = 3

    def __init__(self, input_size: int, config: LstmConfig) -> None:
        super().__init__()
        if input_size <= self.continuous_tail_start:
            raise ValueError("LSTM input_size does not contain required categorical columns")
        self.input_size = input_size
        self.config = config
        temporal_size = self.stock_index_column + input_size - self.continuous_tail_start
        self.temporal_backbone = nn.LSTM(
            input_size=temporal_size,
            hidden_size=config.hidden_size,
            num_layers=config.num_layers,
            dropout=config.dropout if config.num_layers > 1 else 0.0,
            batch_first=True,
        )
        self.temporal_norm = nn.LayerNorm(config.hidden_size)
        self.stock_embedding = nn.Embedding(
            config.stock_embedding_buckets, config.stock_embedding_size
        )
        self.industry_embedding = nn.Embedding(
            config.industry_embedding_buckets, config.industry_embedding_size
        )
        representation_size = (
            config.hidden_size + config.stock_embedding_size + config.industry_embedding_size
        )
        projection_size = max(8, config.hidden_size // 2)
        self.shared_projection = nn.Sequential(
            nn.Linear(representation_size, projection_size), nn.ReLU()
        )
        self.shared_output = nn.Linear(projection_size, self.output_size)
        self.group_head_weights = nn.Embedding(
            config.group_head_count, representation_size * self.output_size
        )
        self.group_head_bias = nn.Embedding(config.group_head_count, self.output_size)
        self.stock_residual = nn.Embedding(config.stock_embedding_buckets, self.output_size)
        self.representation_size = representation_size
        self._reset_parameters()

    def _reset_parameters(self) -> None:
        """使用 Xavier 初始化附加 Head 和 Embedding，与 Java 初始策略保持一致。"""

        for parameter in (
            self.stock_embedding.weight,
            self.industry_embedding.weight,
            self.group_head_weights.weight,
            self.group_head_bias.weight,
            self.stock_residual.weight,
        ):
            nn.init.xavier_uniform_(parameter)

    def forward(self, sequence: Tensor) -> Tensor:
        """接收 batch x sequenceLength x featureCount，输出三个任务结果。"""

        if sequence.ndim != 3 or sequence.shape[-1] != self.input_size:
            raise ValueError("LSTM input shape must be batch x sequence x input_size")
        temporal_input = torch.cat(
            (
                sequence[:, :, : self.stock_index_column],
                sequence[:, :, self.continuous_tail_start :],
            ),
            dim=2,
        )
        temporal_output, _ = self.temporal_backbone(temporal_input)
        temporal_vector = self.temporal_norm(temporal_output[:, -1, :])
        stock_indices = self._indices(
            sequence, self.stock_index_column, self.config.stock_embedding_buckets
        )
        industry_indices = self._indices(
            sequence, self.industry_index_column, self.config.industry_embedding_buckets
        )
        group_indices = self._indices(
            sequence, self.group_index_column, self.config.group_head_count
        )
        representation = torch.cat(
            (
                temporal_vector,
                self.stock_embedding(stock_indices),
                self.industry_embedding(industry_indices),
            ),
            dim=1,
        )
        shared_raw = self.shared_output(self.shared_projection(representation))
        group_weights = self.group_head_weights(group_indices).reshape(
            -1, self.representation_size, self.output_size
        )
        group_raw = (
            torch.bmm(representation.unsqueeze(1), group_weights).squeeze(1)
            + self.group_head_bias(group_indices)
        ) * self.config.group_head_scale
        combined = shared_raw + group_raw
        if self.config.residual_head_enabled:
            combined = combined + (
                self.stock_residual(stock_indices) * self.config.residual_head_scale
            )
        return torch.stack(
            (
                torch.tanh(combined[:, 0]),
                torch.sigmoid(combined[:, 1]),
                torch.sigmoid(combined[:, 2]),
            ),
            dim=1,
        )

    @staticmethod
    def _indices(sequence: Tensor, column: int, category_count: int) -> Tensor:
        """从最后时间步读取 Java 已计算的离散哈希索引。"""

        return sequence[:, -1, column].clamp(0, category_count - 1).long()
# AI_GENERATE_END -
