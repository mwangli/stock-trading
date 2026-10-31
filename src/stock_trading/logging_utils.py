# AI_GENERATE_START ---------------
from __future__ import annotations

import logging
import os
import re
import shutil
import sys
import threading
import unicodedata
from contextlib import contextmanager
from datetime import datetime
from multiprocessing import Array
from typing import Any, TextIO

PROGRESS_WIDTH = 20
PROGRESS_MESSAGE_LIMIT = 320
LIVE_REFRESH_SECONDS = 0.05
GREEN_BOLD = "\033[1;32m"
LIGHT_BLUE = "\033[38;5;110m"
YELLOW = "\033[38;5;221m"
ANSI_RESET = "\033[0m"
SPINNER_FRAMES = ("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
CODE_PATTERN = re.compile(r"\b(?:sh|sz)\.\d{6}\b", re.IGNORECASE)

_PROGRESS_COMPLETED: Any | None = None
_PROGRESS_REMAINING_SECONDS: Any | None = None
_PROGRESS_MESSAGE: Any | None = None
_PROGRESS_TOTAL = 0
_LIVE_RENDERER: _LiveProgressRenderer | None = None
_LIVE_RENDERER_PID: int | None = None
_CONSOLE_LOCK = threading.RLock()
ProgressContext = tuple[Any | None, Any | None, Any | None, int]


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


class ProgressConsoleHandler(logging.StreamHandler):
    """Keep INFO progress on one live terminal line while preserving other logs."""

    def __init__(self, stream: TextIO, *, use_color: bool, interactive: bool) -> None:
        super().__init__(stream)
        self.use_color = use_color
        self.interactive = interactive

    def emit(self, record: logging.LogRecord) -> None:
        if (
            record.levelno == logging.INFO
            and self.interactive
            and _set_progress_message(record.getMessage())
        ):
            return
        with _CONSOLE_LOCK:
            if _owns_live_renderer():
                _clear_live_line()
            elif self.interactive and _PROGRESS_MESSAGE is not None:
                self.stream.write("\n")
            super().emit(record)


class _LiveProgressRenderer:
    """Redraw one concise status line at a steady animation cadence."""

    def __init__(self, stream: TextIO, *, use_color: bool) -> None:
        self.stream = stream
        self.use_color = use_color
        self._stop_event = threading.Event()
        self._thread = threading.Thread(
            target=self._run,
            name="research-progress-renderer",
            daemon=True,
        )
        self._phase = 0
        self._last_width = 0

    def start(self) -> None:
        self._render()
        self._thread.start()

    def stop(self) -> None:
        self._stop_event.set()
        self._thread.join(timeout=LIVE_REFRESH_SECONDS * 2)
        self._render()
        with _CONSOLE_LOCK:
            if self._last_width:
                self.stream.write("\n")
                self.stream.flush()
                self._last_width = 0

    def clear(self) -> None:
        if not self._last_width:
            return
        self.stream.write("\r\033[2K")
        self.stream.flush()
        self._last_width = 0

    def _run(self) -> None:
        while not self._stop_event.wait(LIVE_REFRESH_SECONDS):
            self._phase += 1
            self._render()

    def _render(self) -> None:
        rendered, width = _format_live_progress(self._phase, self.use_color)
        with _CONSOLE_LOCK:
            self.stream.write(f"\r\033[2K{rendered}")
            self.stream.flush()
            self._last_width = width


def configure_logging() -> None:
    """Configure concise console logs without mixing them into JSON stdout."""
    logger = logging.getLogger("stock_trading")
    if logger.handlers:
        return
    level_name = os.getenv("STOCK_TRADING_LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    interactive = bool(getattr(sys.stderr, "isatty", lambda: False)())
    use_color = bool(interactive and "NO_COLOR" not in os.environ)
    handler = ProgressConsoleHandler(
        sys.stderr,
        use_color=use_color,
        interactive=interactive,
    )
    handler.setFormatter(ProgressFormatter(use_color=use_color))
    logger.addHandler(handler)
    logger.setLevel(level)
    logger.propagate = False


def bind_progress_context(
    completed: Any,
    remaining_seconds: Any,
    total: int,
    message: Any | None = None,
) -> ProgressContext:
    """Bind shared progress values for the current process logger."""
    global _PROGRESS_COMPLETED, _PROGRESS_MESSAGE
    global _PROGRESS_REMAINING_SECONDS, _PROGRESS_TOTAL
    previous = (
        _PROGRESS_COMPLETED,
        _PROGRESS_REMAINING_SECONDS,
        _PROGRESS_MESSAGE,
        _PROGRESS_TOTAL,
    )
    _PROGRESS_COMPLETED = completed
    _PROGRESS_REMAINING_SECONDS = remaining_seconds
    _PROGRESS_MESSAGE = message
    _PROGRESS_TOTAL = max(0, total)
    return previous


def restore_progress_context(context: ProgressContext) -> None:
    """Restore a progress binding after a nested stage finishes."""
    global _PROGRESS_COMPLETED, _PROGRESS_MESSAGE
    global _PROGRESS_REMAINING_SECONDS, _PROGRESS_TOTAL
    (
        _PROGRESS_COMPLETED,
        _PROGRESS_REMAINING_SECONDS,
        _PROGRESS_MESSAGE,
        _PROGRESS_TOTAL,
    ) = context


def start_progress_renderer() -> bool:
    """Start one live renderer in the parent process when stderr is interactive."""
    global _LIVE_RENDERER, _LIVE_RENDERER_PID
    if _owns_live_renderer() or _PROGRESS_MESSAGE is None:
        return False
    _LIVE_RENDERER = None
    _LIVE_RENDERER_PID = None
    logger = logging.getLogger("stock_trading")
    handler = next(
        (
            candidate
            for candidate in logger.handlers
            if isinstance(candidate, ProgressConsoleHandler) and candidate.interactive
        ),
        None,
    )
    if handler is None:
        return False
    _LIVE_RENDERER = _LiveProgressRenderer(
        handler.stream,
        use_color=handler.use_color,
    )
    _LIVE_RENDERER_PID = os.getpid()
    _LIVE_RENDERER.start()
    return True


def stop_progress_renderer() -> None:
    """Stop the live renderer owned by the current process."""
    global _LIVE_RENDERER, _LIVE_RENDERER_PID
    renderer = _LIVE_RENDERER if _owns_live_renderer() else None
    _LIVE_RENDERER = None
    _LIVE_RENDERER_PID = None
    if renderer is not None:
        renderer.stop()


@contextmanager
def live_progress_session(initial_message: str):
    """Keep all interactive INFO updates in one line for one CLI operation."""
    message = Array("u", PROGRESS_MESSAGE_LIMIT, lock=True)
    _write_progress_message(message, initial_message)
    previous = bind_progress_context(None, None, 0, message)
    renderer_started = start_progress_renderer()
    try:
        yield
    finally:
        if renderer_started:
            stop_progress_renderer()
        restore_progress_context(previous)


def _set_progress_message(message: str) -> bool:
    if _PROGRESS_MESSAGE is None:
        return False
    normalized = " ".join(message.splitlines()).strip()[: PROGRESS_MESSAGE_LIMIT - 1]
    _write_progress_message(_PROGRESS_MESSAGE, normalized)
    return True


def _read_progress_message() -> str:
    if _PROGRESS_MESSAGE is None:
        return ""
    with _PROGRESS_MESSAGE.get_lock():
        return str(_PROGRESS_MESSAGE.get_obj().value)


def _write_progress_message(target: Any, message: str) -> None:
    with target.get_lock():
        target.get_obj().value = message[: PROGRESS_MESSAGE_LIMIT - 1]


def _clear_live_line() -> None:
    if _owns_live_renderer() and _LIVE_RENDERER is not None:
        _LIVE_RENDERER.clear()


def _owns_live_renderer() -> bool:
    return _LIVE_RENDERER is not None and _LIVE_RENDERER_PID == os.getpid()


def _format_live_progress(phase: int, use_color: bool) -> tuple[str, int]:
    message = _read_progress_message() or "正在准备初始化"
    stage, code = _summarize_progress_message(message)
    has_numeric_progress = (
        _PROGRESS_COMPLETED is not None
        and _PROGRESS_REMAINING_SECONDS is not None
        and _PROGRESS_TOTAL > 0
    )
    ratio = 0.0
    if has_numeric_progress:
        completed = max(0, int(_PROGRESS_COMPLETED.value))
        ratio = min(1.0, completed / _PROGRESS_TOTAL)
    spinner = (
        "✓"
        if has_numeric_progress and ratio >= 1.0
        else SPINNER_FRAMES[phase % len(SPINNER_FRAMES)]
    )
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    head = f"{timestamp} | INFO | {spinner} "
    tail = ""
    if has_numeric_progress:
        percent = f"{ratio * 100:.2f}%"
        eta = f"剩余 {_format_remaining(int(_PROGRESS_REMAINING_SECONDS.value))}"
        tail = f"  |  {code}  |  {percent}  |  {eta}"
    columns = max(40, shutil.get_terminal_size((120, 24)).columns)
    stage_width = max(0, columns - _display_width(f"{head}{tail}") - 1)
    stage = _truncate_display(stage, stage_width)
    plain = f"{head}{stage}{tail}"
    if use_color:
        rendered = f"{timestamp} | INFO | {GREEN_BOLD}{spinner}{ANSI_RESET} {stage}"
        if has_numeric_progress:
            rendered += (
                f"  |  {YELLOW}{code}{ANSI_RESET}  |  "
                f"{GREEN_BOLD}{percent}{ANSI_RESET}  |  "
                f"{LIGHT_BLUE}{eta}{ANSI_RESET}"
            )
    else:
        rendered = plain
    return rendered, _display_width(plain)


def _summarize_progress_message(message: str) -> tuple[str, str]:
    code_match = CODE_PATTERN.search(message)
    code = code_match.group(0).lower() if code_match else "--"
    if "业务命令完成：init" in message or "初始化完成" in message:
        return "初始化完成", code
    if "业务命令开始：init" in message:
        return "正在启动初始化", code
    if "初始化状态库就绪" in message:
        return "正在检查状态数据", code
    if any(
        text in message
        for text in (
            "正在请求全市场日线",
            "任务=全市场日线",
            "最近交易日确认",
        )
    ):
        return "正在检查交易日数据", code
    if "初始化计划确定" in message:
        return "正在检查初始化计划", code
    if any(
        text in message
        for text in (
            "股票基础信息",
            "全部股票基础信息",
            "证券列表",
            "股票基础资料",
            "股票池确定",
        )
    ):
        return "正在检查股票基础数据", code
    if any(
        text in message
        for text in (
            "历史日线、复权和分钟线",
            "研究数据检查完成",
        )
    ):
        return "正在检查历史行情数据", code
    if "Qlib 数据构建" in message or "Qlib" in message:
        return "正在检查 Qlib 数据", code
    if "基础模型训练" in message or "模型训练" in message:
        return "正在检查基础模型", code
    if "模型和回测状态导入" in message or "研究制品导入" in message:
        return "正在检查研究状态", code
    if "回测" in message:
        return "正在检查回测数据", code
    if "预测" in message:
        return "正在检查最新预测", code
    if "券商历史事实" in message or "账户快照" in message:
        return "正在检查券商数据", code
    if "研究数据采集完成" in message:
        return "研究数据采集完成", code
    if "5分钟K线" in message:
        return "正在采集5分钟K线", code
    if "日线历史" in message:
        return "正在采集日线", code
    if "复权因子" in message:
        return "正在采集复权因子", code
    if "下载研究数据" in message:
        return "正在下载研究数据", code
    if "数据采集中" in message:
        return "正在接收行情数据", code
    if "数据接收完成" in message:
        return "行情数据接收完成", code
    if "研究数据采集计划" in message:
        return "正在准备研究数据", code
    if "BaoStock 登录成功" in message:
        return "正在检查行情连接", code
    if "正在连接 BaoStock" in message:
        return "正在检查行情连接", code
    stage = message.split("：", 1)[0].strip() or "研究数据采集中"
    return stage, code


def _display_width(text: str) -> int:
    return sum(
        2 if unicodedata.east_asian_width(character) in {"W", "F"} else 1
        for character in text
    )


def _truncate_display(text: str, max_width: int) -> str:
    if max_width <= 0:
        return ""
    if _display_width(text) <= max_width:
        return text
    suffix = "..."
    if max_width <= len(suffix):
        return suffix[:max_width]
    target = max(0, max_width - len(suffix))
    width = 0
    result: list[str] = []
    for character in text:
        char_width = 2 if unicodedata.east_asian_width(character) in {"W", "F"} else 1
        if width + char_width > target:
            break
        result.append(character)
        width += char_width
    return f"{''.join(result)}{suffix}"


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
# AI_GENERATE_END ---------------
