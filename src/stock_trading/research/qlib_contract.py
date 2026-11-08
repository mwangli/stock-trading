# AI_GENERATE_START-------- ------
from __future__ import annotations

import hashlib
import json

from stock_trading.contracts import TRADING_TIME_CONTRACT
from stock_trading.research.model_config import ModelSpec

QLIB_CONTRACT_VERSION = "15"
MIN_QLIB_TRADING_DAYS = 63


def strategy_contract(spec: ModelSpec) -> dict[str, object]:
    strategy = spec.strategy
    return {
        "version": TRADING_TIME_CONTRACT,
        "strategyId": strategy.strategy_id,
        "featureLevel": spec.data_level,
        "featureFrequency": spec.feature_frequency,
        "signalTiming": strategy.signal_timing,
        "sellWindow": list(strategy.sell_window),
        "buyWindow": list(strategy.buy_window),
        "priceBenchmark": strategy.price_benchmark,
        "labelContract": strategy.label_contract,
        "entryRank": 5,
        "retainRank": 15,
    }


def strategy_contract_hash(spec: ModelSpec) -> str:
    payload = json.dumps(strategy_contract(spec), ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def expected_label_contract(spec: ModelSpec) -> str:
    return f"{spec.strategy.strategy_id}:{spec.strategy.label_contract}"


def trading_time_contract(spec: ModelSpec) -> dict[str, object]:
    return strategy_contract(spec)


def expected_qlib_contract(spec: ModelSpec) -> str:
    return f"{QLIB_CONTRACT_VERSION}:{spec.dataset_id}:{strategy_contract_hash(spec)}"


# AI_GENERATE_END-------- ------
