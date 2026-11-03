# AI_GENERATE_START ------------------------------------
from __future__ import annotations

import logging
import os
import sys
from contextlib import contextmanager
from contextvars import ContextVar
from logging.handlers import RotatingFileHandler
from pathlib import Path
from typing import Iterator

_TASK = ContextVar("log_task", default="-")
_STEP = ContextVar("log_step", default="-/-")
_PROGRESS = ContextVar("log_progress", default="-/-")
_TARGET = ContextVar("log_target", default="-")
_STEP_LOCKED = ContextVar("log_step_locked", default=False)
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


def _default_log_path() -> Path:
    source_project_dir = Path(__file__).resolve().parents[3]
    project_dir = source_project_dir if os.name == "nt" else Path.home() / "stock-trading"
    return project_dir / "logs" / "run.log"


class _ContextFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.task = _TASK.get()
        record.step = _STEP.get()
        record.progress = _PROGRESS.get()
        target = _TARGET.get()
        record.target = (
            target
            if target != "-"
            else record.name.removeprefix("stock_trading.") or "stock_trading"
        )
        return True


class _ColorFormatter(logging.Formatter):
    def __init__(self, *, use_color: bool) -> None:
        super().__init__(
            fmt=(
                "%(asctime)s | %(level_label)s | task=%(task)s | "
                "step=%(step)s | progress=%(progress)s | "
                "target=%(target)s | %(message)s"
            ),
            datefmt="%Y-%m-%d %H:%M:%S",
        )
        self._use_color = use_color

    def format(self, record: logging.LogRecord) -> str:
        label = _LEVEL_LABELS.get(record.levelno, record.levelname[:5]).ljust(5)
        color = _LEVEL_COLORS.get(record.levelno) if self._use_color else None
        record.level_label = (
            f"{color}{label}{_ANSI_RESET}" if color else label
        )
        try:
            return super().format(record)
        finally:
            record.__dict__.pop("level_label", None)


@contextmanager
def log_context(
    *,
    task: str | None = None,
    step: str | None = None,
    progress: str | None = None,
    target: str | None = None,
) -> Iterator[None]:
    tokens = []
    if task is not None:
        tokens.append((_TASK, _TASK.set(task)))
    if step is not None and not _STEP_LOCKED.get():
        tokens.append((_STEP, _STEP.set(step)))
    if progress is not None:
        tokens.append((_PROGRESS, _PROGRESS.set(progress)))
    if target is not None:
        tokens.append((_TARGET, _TARGET.set(target)))
    try:
        yield
    finally:
        for variable, token in reversed(tokens):
            variable.reset(token)


@contextmanager
def fixed_log_step(step: str) -> Iterator[None]:
    """固定顶层业务阶段，避免内部流程覆盖阶段编号。"""
    step_token = _STEP.set(step)
    lock_token = _STEP_LOCKED.set(True)
    try:
        yield
    finally:
        _STEP_LOCKED.reset(lock_token)
        _STEP.reset(step_token)


def set_log_context(
    *,
    step: str | None = None,
    progress: str | None = None,
    target: str | None = None,
) -> None:
    if step is not None and not _STEP_LOCKED.get():
        _STEP.set(step)
    if progress is not None:
        _PROGRESS.set(progress)
    if target is not None:
        _TARGET.set(target)


def should_log_progress(current: int, total: int, steps: int = 20) -> bool:
    """Limit long loops to a bounded number of ordinary progress logs."""
    if total <= 0:
        return current == 1
    interval = max(1, total // max(1, steps))
    return current == 1 or current == total or current % interval == 0


def configure_logging() -> None:
    """配置统一结构化控制台日志和 UTF-8 轮转文件日志。"""
    logger = logging.getLogger("stock_trading")
    if logger.handlers:
        return
    level_name = os.getenv("STOCK_TRADING_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    interactive = bool(getattr(sys.stderr, "isatty", lambda: False)())
    use_color = interactive and "NO_COLOR" not in os.environ
    handler = logging.StreamHandler(sys.stderr)
    handler.addFilter(_ContextFilter())
    handler.setFormatter(_ColorFormatter(use_color=use_color))
    logger.addHandler(handler)

    if os.getenv("STOCK_TRADING_FILE_LOG", "1") != "0":
        configured_path = os.getenv("STOCK_TASK_LOG")
        log_path = Path(configured_path) if configured_path else _default_log_path()
        log_path.parent.mkdir(parents=True, exist_ok=True)
        file_handler = RotatingFileHandler(
            log_path,
            maxBytes=10 * 1024 * 1024,
            backupCount=5,
            encoding="utf-8",
        )
        file_handler.addFilter(_ContextFilter())
        file_handler.setFormatter(_ColorFormatter(use_color=False))
        logger.addHandler(file_handler)
    logger.setLevel(level)
    logger.propagate = False
# AI_GENERATE_END ------------------------------------
