# AI_GENERATE_START -
from __future__ import annotations

import logging
import os
import signal
import sys
import time
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path

LOGGER = logging.getLogger(__name__)
INTERRUPT_WAIT_SECONDS = 8.0
TERMINATE_WAIT_SECONDS = 3.0
KILL_WAIT_SECONDS = 1.0
POLL_SECONDS = 0.2


@contextmanager
def replace_running_init(state_db_path: Path) -> Iterator[None]:
    """Replace older Linux init processes and own the init lifecycle."""
    if sys.platform != "linux":
        yield
        return

    state_dir = state_db_path.parent
    state_dir.mkdir(parents=True, exist_ok=True)
    owner_path = state_dir / "init-owner.pid"
    current_pid = os.getpid()
    protected = _ancestor_pids(current_pid) | {current_pid}
    stale = _find_init_processes() - protected
    stale |= _descendant_pids(stale) - protected
    if stale:
        _stop_processes(stale)

    owner_path.write_text(str(current_pid), encoding="ascii")
    try:
        yield
    finally:
        try:
            if owner_path.read_text(encoding="ascii").strip() == str(current_pid):
                owner_path.unlink(missing_ok=True)
        except OSError:
            LOGGER.warning("init owner PID 文件清理失败：%s", owner_path)


def _find_init_processes() -> set[int]:
    matches: set[int] = set()
    for process_dir in _process_directories():
        arguments = _read_cmdline(process_dir)
        if _is_init_command(arguments):
            matches.add(int(process_dir.name))
    return matches


def _is_init_command(arguments: tuple[str, ...]) -> bool:
    if "init" not in arguments:
        return False
    for index, argument in enumerate(arguments):
        if (
            index <= 1
            and Path(argument).name == "stock-trading"
            and "init" in arguments[index + 1 :]
        ):
            return True
        if (
            argument == "-m"
            and index + 1 < len(arguments)
            and arguments[index + 1] == "stock_trading"
            and "init" in arguments[index + 2 :]
        ):
            return True
    return False


def _stop_processes(process_ids: set[int]) -> None:
    targets = _live_processes(process_ids)
    if not targets:
        return
    LOGGER.warning("检测到旧 init 进程，准备接管：PID=%s", _format_pids(targets))
    _signal_processes(targets, signal.SIGINT)
    remaining = _wait_for_exit(targets, INTERRUPT_WAIT_SECONDS)
    if remaining:
        LOGGER.warning(
            "旧 init 未在中断窗口退出，发送 SIGTERM：PID=%s",
            _format_pids(remaining),
        )
        _signal_processes(remaining, signal.SIGTERM)
        remaining = _wait_for_exit(remaining, TERMINATE_WAIT_SECONDS)
    if remaining:
        LOGGER.warning("旧 init 仍未退出，发送 SIGKILL：PID=%s", _format_pids(remaining))
        _signal_processes(remaining, signal.SIGKILL)
        remaining = _wait_for_exit(remaining, KILL_WAIT_SECONDS)
    if remaining:
        raise RuntimeError(f"旧 init 进程无法终止: {_format_pids(remaining)}")
    LOGGER.info("旧 init 进程已全部退出，新初始化继续执行")


def _signal_processes(process_ids: set[int], signal_number: int) -> None:
    for process_id in sorted(process_ids):
        try:
            os.kill(process_id, signal_number)
        except ProcessLookupError:
            continue
        except PermissionError as error:
            raise RuntimeError(f"无权终止旧 init 进程: {process_id}") from error


def _wait_for_exit(process_ids: set[int], timeout_seconds: float) -> set[int]:
    deadline = time.monotonic() + timeout_seconds
    remaining = _live_processes(process_ids)
    while remaining and time.monotonic() < deadline:
        time.sleep(POLL_SECONDS)
        remaining = _live_processes(remaining)
    return remaining


def _live_processes(process_ids: set[int]) -> set[int]:
    return {process_id for process_id in process_ids if _process_is_live(process_id)}


def _process_is_live(process_id: int) -> bool:
    stat = _read_process_stat(Path("/proc") / str(process_id))
    return stat is not None and stat[0] != "Z"


def _ancestor_pids(process_id: int) -> set[int]:
    ancestors: set[int] = set()
    current = process_id
    while current > 1:
        stat = _read_process_stat(Path("/proc") / str(current))
        if stat is None:
            break
        parent_id = stat[1]
        if parent_id <= 1 or parent_id in ancestors:
            break
        ancestors.add(parent_id)
        current = parent_id
    return ancestors


def _descendant_pids(parent_ids: set[int]) -> set[int]:
    if not parent_ids:
        return set()
    parent_by_pid: dict[int, int] = {}
    for process_dir in _process_directories():
        stat = _read_process_stat(process_dir)
        if stat is not None:
            parent_by_pid[int(process_dir.name)] = stat[1]
    descendants: set[int] = set()
    frontier = set(parent_ids)
    while frontier:
        children = {
            process_id
            for process_id, parent_id in parent_by_pid.items()
            if parent_id in frontier and process_id not in descendants
        }
        descendants.update(children)
        frontier = children
    return descendants


def _process_directories() -> list[Path]:
    proc = Path("/proc")
    try:
        return [entry for entry in proc.iterdir() if entry.name.isdigit()]
    except OSError as error:
        raise RuntimeError("无法扫描 Linux 进程目录 /proc") from error


def _read_cmdline(process_dir: Path) -> tuple[str, ...]:
    try:
        content = (process_dir / "cmdline").read_bytes()
    except OSError:
        return ()
    return tuple(
        part.decode("utf-8", errors="replace")
        for part in content.split(b"\0")
        if part
    )


def _read_process_stat(process_dir: Path) -> tuple[str, int] | None:
    try:
        content = (process_dir / "stat").read_text(encoding="ascii")
        fields = content.rsplit(")", 1)[1].strip().split()
        return fields[0], int(fields[1])
    except (OSError, IndexError, ValueError):
        return None


def _format_pids(process_ids: set[int]) -> str:
    return ",".join(str(process_id) for process_id in sorted(process_ids))
# AI_GENERATE_END -