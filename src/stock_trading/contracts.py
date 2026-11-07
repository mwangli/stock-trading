# AI_GENERATE_START -
from __future__ import annotations

from datetime import time

# Shared time values; no data access or execution side effects.
TRADING_TIME_CONTRACT = "eod-next-day-buy-afternoon-exit-v1"
WINDOW_CONTRACT = "eod-afternoon-v1"
SELL_START = time(13, 0)
SELL_END = time(14, 0)
BUY_START = time(14, 0)
BUY_END = time(15, 0)
# AI_GENERATE_END -
