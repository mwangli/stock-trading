# AI_GENERATE_START ------------------------------------------
from __future__ import annotations

import logging
import os
import sys
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Iterator

_TASK = ContextVar("log_task", default="-")
_STEP = ContextVar("log_step", default="-")
_PROGRESS = ContextVar("log_progress", default="-")
_TARGET = ContextVar("log_target", default="-")

_LEVEL_COLORS = {
    logging.DEBUG: "\033[90m",
    logging.INFO: "\033[32m",
    logging.WARNING: "\033[33m",
    logging.ERROR: "\033[31m",
    logging.CRITICAL: "\033[1;31m",
}
_LEVEL_LABELS = {
    logging.DEBUG: "DEBUG",
    logging.INFO: "INFO",
    logging.WARNING: "WARN",
    logging.ERROR: "ERROR",
    logging.CRITICAL: "CRIT",
}
_ANSI_RESET = "\033[0m"


class LogContextFilter:
    """将命令产生的日志字段写入标准 LogRecord。"""

    def filter(self, record: object) -> bool:
        log_record = record
        log_record.task = _TASK.get()
        log_record.step = _STEP.get()
        log_record.progress = _PROGRESS.get()
        log_record.target = _TARGET.get()
        return True


@contextmanager
def log_context(
    *,
    task: str | None = None,
    step: str | None = None,
    progress: str | None = None,
    target: str | None = None,
) -> Iterator[None]:
    tokens = []
    values = (
        (_TASK, task),
        (_STEP, step),
        (_PROGRESS, progress),
        (_TARGET, target),
    )
    for variable, value in values:
        if value is not None:
            tokens.append((variable, variable.set(value)))
    try:
        yield
    finally:
        for variable, token in reversed(tokens):
            variable.reset(token)


def set_log_context(
    *,
    task: str | None = None,
    step: str | None = None,
    progress: str | None = None,
    target: str | None = None,
) -> None:
    """由命令或工作流更新当前日志事件的业务字段。"""
    values = (
        (_TASK, task),
        (_STEP, step),
        (_PROGRESS, progress),
        (_TARGET, target),
    )
    for variable, value in values:
        if value is not None:
            variable.set(value)


def should_log_progress(current: int, total: int, steps: int = 20) -> bool:
    """限制长循环日志数量，避免每条记录都输出。"""
    if total <= 0:
        return current == 1
    interval = max(1, total // max(1, steps))
    return current == 1 or current == total or current % interval == 0


class _ColorFormatter(logging.Formatter):
    def __init__(self, *, use_color: bool) -> None:
        super().__init__(datefmt="%Y-%m-%d %H:%M:%S")
        self._use_color = use_color

    def format(self, record: logging.LogRecord) -> str:
        label = _LEVEL_LABELS.get(record.levelno, record.levelname[:5]).ljust(5)
        color = _LEVEL_COLORS.get(record.levelno) if self._use_color else None
        record.level_label = (
            f"{color}{label}{_ANSI_RESET}" if color else label
        )
        try:
            task = getattr(record, "task", "-")
            step = getattr(record, "step", "-")
            progress = getattr(record, "progress", "-")
            target = getattr(record, "target", "-")
            parts = [f"task={task}", f"step={step}"]
            parts.extend(
                [
                    f"progress={progress}",
                    f"target={target}",
                    record.getMessage(),
                ]
            )
            return (
                f"{self.formatTime(record, self.datefmt)} | "
                f"{record.level_label} | {' | '.join(parts)}"
            )
        finally:
            record.__dict__.pop("level_label", None)


def configure_logging() -> None:
    """配置结构化控制台日志。"""
    logger = logging.getLogger("stock_trading")
    if logger.handlers:
        return
    level_name = os.getenv("STOCK_TRADING_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    interactive = bool(getattr(sys.stderr, "isatty", lambda: False)())
    use_color = interactive and "NO_COLOR" not in os.environ
    handler = logging.StreamHandler(sys.stderr)
    handler.addFilter(LogContextFilter())
    handler.setFormatter(_ColorFormatter(use_color=use_color))
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False


# AI_GENERATE_END ------------------------------------------
