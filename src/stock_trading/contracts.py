# AI_GENERATE_START ----
from __future__ import annotations

from datetime import time

# Shared time values; no data access or execution side effects.
TRADING_TIME_CONTRACT = "fixed-model-matrix-v1"
WINDOW_CONTRACT = "full-day-minute5-v3"
SELL_START = time(9, 30)
SELL_END = time(11, 30)
BUY_START = time(14, 0)
BUY_END = time(15, 0)


def execution_window_seconds(side: str) -> int:
    """Return the full continuous-session execution budget for one side."""
    if side not in {"BUY", "SELL"}:
        raise ValueError("side must be BUY or SELL")
    start, end = (BUY_START, BUY_END) if side == "BUY" else (SELL_START, SELL_END)
    return ((end.hour - start.hour) * 60 + end.minute - start.minute) * 60
# AI_GENERATE_END ----
