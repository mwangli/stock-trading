# AI_GENERATE_START -
from __future__ import annotations

import logging
import os
import sys


def configure_logging() -> None:
    """Configure concise console logs without mixing them into JSON stdout."""
    logger = logging.getLogger("stock_trading")
    if logger.handlers:
        return
    level_name = os.getenv("STOCK_TRADING_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    handler = logging.StreamHandler(sys.stderr)
    handler.setFormatter(
        logging.Formatter(
            fmt="%(asctime)s | %(levelname)s | %(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
    )
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False


def should_log_progress(current: int, total: int, steps: int = 20) -> bool:
    """Return whether a bounded loop should emit the current progress point."""
    if total <= 0:
        return current == 1
    interval = max(1, total // max(1, steps))
    return current == 1 or current == total or current % interval == 0
# AI_GENERATE_END -
