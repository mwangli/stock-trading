# AI_GENERATE_START ----
from __future__ import annotations

from stock_trading.research.model_config import ModelSpec
from stock_trading.contracts import TRADING_TIME_CONTRACT, BUY_START, BUY_END, SELL_START, SELL_END


QLIB_CONTRACT_VERSION = "8"



def expected_label_contract(spec: ModelSpec | None) -> str:
    frequency = spec.feature_frequency if spec else "minute5"
    horizon = spec.label_horizon if spec else 1
    return f"{TRADING_TIME_CONTRACT}:{frequency}:T+{horizon}"


def trading_time_contract(spec: ModelSpec | None) -> dict[str, object]:
    frequency = spec.feature_frequency if spec else "minute5"
    return {
        "version": TRADING_TIME_CONTRACT,
        "timezone": "Asia/Shanghai",
        "signalTiming": "after-close-and-data-ready",
        "featureCutoff": "11:30" if frequency == "minute5" else "15:00",
        "entryTradeDayOffset": 1,
        "exitTradeDayOffset": 1 + (spec.label_horizon if spec else 1),
        "buyWindow": [BUY_START.strftime("%H:%M"), BUY_END.strftime("%H:%M")],
        "sellWindow": [SELL_START.strftime("%H:%M"), SELL_END.strftime("%H:%M")],
        "priceBenchmark": "window-vwap",
    }


def expected_qlib_contract(spec: ModelSpec | None) -> str:
    model_id = spec.model_id if spec else "legacy"
    frequency = spec.feature_frequency if spec else "minute5"
    horizon = spec.label_horizon if spec else 1
    return f"{QLIB_CONTRACT_VERSION}:{model_id}:{frequency}:T+{horizon}"
# AI_GENERATE_END ----
