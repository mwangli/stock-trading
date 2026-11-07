# AI_GENERATE_START --
from __future__ import annotations

from stock_trading.research.model_config import ModelSpec


QLIB_CONTRACT_VERSION = "7"


def expected_qlib_contract(spec: ModelSpec | None) -> str:
    model_id = spec.model_id if spec else "legacy"
    frequency = spec.feature_frequency if spec else "minute5"
    horizon = spec.label_horizon if spec else 1
    return f"{QLIB_CONTRACT_VERSION}:{model_id}:{frequency}:T+{horizon}"
# AI_GENERATE_END --
