# AI_GENERATE_START -
import pytest

torch = pytest.importorskip("torch")

from stock_models.config import LstmConfig
from stock_models.lstm.loss import MultiTaskStockLoss
from stock_models.lstm.model import MultiTaskStockLstm


def test_lstm_forward_and_loss_are_finite() -> None:
    """共享模型必须输出 batch x 3，且三个输出位于各自业务范围。"""

    config = LstmConfig(
        hidden_size=8,
        num_layers=1,
        stock_embedding_buckets=32,
        stock_embedding_size=4,
        industry_embedding_buckets=16,
        industry_embedding_size=3,
        group_head_count=4,
    )
    model = MultiTaskStockLstm(16, config)
    features = torch.randn(4, 5, 16)
    features[:, :, 11] = torch.tensor([1, 2, 3, 4]).reshape(4, 1)
    features[:, :, 12] = 2
    features[:, :, 13] = 1
    predictions = model(features)
    targets = torch.tensor(
        [[0.1, 1.0, 0.0], [-0.2, 0.0, 0.2], [0.0, 0.0, 0.0], [0.3, 1.0, 0.0]]
    )
    loss = MultiTaskStockLoss(config)(predictions, targets)
    assert predictions.shape == (4, 3)
    assert torch.all(predictions[:, 0].abs() <= 1)
    assert torch.all((predictions[:, 1:] >= 0) & (predictions[:, 1:] <= 1))
    assert torch.isfinite(loss)
# AI_GENERATE_END -
