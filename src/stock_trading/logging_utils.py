# AI_GENERATE_START -----
from __future__ import annotations

import logging
import os
import sys
from typing import Any

PROGRESS_WIDTH = 20
GREEN_BOLD = "\033[1;32m"
LIGHT_BLUE = "\033[38;5;110m"
ANSI_RESET = "\033[0m"

_PROGRESS_COMPLETED: Any | None = None
_PROGRESS_REMAINING_SECONDS: Any | None = None
_PROGRESS_TOTAL = 0


class ProgressFormatter(logging.Formatter):
    """Add the active research progress to INFO records."""

    def __init__(self, *, use_color: bool) -> None:
        super().__init__(
            fmt="%(asctime)s | %(levelname)s | %(progress_prefix)s%(message)s",
            datefmt="%Y-%m-%d %H:%M:%S",
        )
        self._use_color = use_color

    def format(self, record: logging.LogRecord) -> str:
        record.progress_prefix = (
            _format_progress_prefix(self._use_color)
            if record.levelno == logging.INFO
            else ""
        )
        return super().format(record)


def configure_logging() -> None:
    """Configure concise console logs without mixing them into JSON stdout."""
    logger = logging.getLogger("stock_trading")
    if logger.handlers:
        return
    level_name = os.getenv("STOCK_TRADING_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    handler = logging.StreamHandler(sys.stderr)
    use_color = bool(
        getattr(handler.stream, "isatty", lambda: False)()
        and "NO_COLOR" not in os.environ
    )
    handler.setFormatter(ProgressFormatter(use_color=use_color))
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False


def bind_progress_context(
    completed: Any,
    remaining_seconds: Any,
    total: int,
) -> None:
    """Bind shared progress values for the current process logger."""
    global _PROGRESS_COMPLETED, _PROGRESS_REMAINING_SECONDS, _PROGRESS_TOTAL
    _PROGRESS_COMPLETED = completed
    _PROGRESS_REMAINING_SECONDS = remaining_seconds
    _PROGRESS_TOTAL = max(0, total)


def clear_progress_context() -> None:
    """Remove the progress prefix after the long-running collection stage."""
    global _PROGRESS_COMPLETED, _PROGRESS_REMAINING_SECONDS, _PROGRESS_TOTAL
    _PROGRESS_COMPLETED = None
    _PROGRESS_REMAINING_SECONDS = None
    _PROGRESS_TOTAL = 0


def _format_progress_prefix(use_color: bool) -> str:
    if _PROGRESS_COMPLETED is None or _PROGRESS_REMAINING_SECONDS is None:
        return ""
    completed = max(0, int(_PROGRESS_COMPLETED.value))
    ratio = min(1.0, completed / _PROGRESS_TOTAL) if _PROGRESS_TOTAL else 1.0
    filled = min(PROGRESS_WIDTH, int(ratio * PROGRESS_WIDTH))
    meter = f"[{'█' * filled}{'░' * (PROGRESS_WIDTH - filled)} {ratio * 100:.2f}%]"
    remaining_seconds = int(_PROGRESS_REMAINING_SECONDS.value)
    eta = f"[剩余{_format_remaining(remaining_seconds)}]"
    if use_color:
        return f"{GREEN_BOLD}{meter}{ANSI_RESET}{LIGHT_BLUE}{eta}{ANSI_RESET} "
    return f"{meter}{eta} "


def _format_remaining(seconds: int) -> str:
    if seconds < 0:
        return "计算中"
    total_minutes = max(0, (seconds + 59) // 60)
    hours, minutes = divmod(total_minutes, 60)
    if hours:
        return f"{hours}小时{minutes:02d}分钟"
    return f"{minutes}分钟"


def should_log_progress(current: int, total: int, steps: int = 20) -> bool:
    """Return whether a bounded loop should emit the current progress point."""
    if total <= 0:
        return current == 1
    interval = max(1, total // max(1, steps))
    return current == 1 or current == total or current % interval == 0
# AI_GENERATE_END -----
