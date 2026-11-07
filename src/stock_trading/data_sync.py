# AI_GENERATE_START --------------------
from __future__ import annotations

import logging
import os
import re
import sqlite3
import subprocess
import sys
import tarfile
import tempfile
from contextlib import closing, contextmanager
from pathlib import Path
from time import sleep
from typing import Callable, Iterator
from uuid import uuid4

from stock_trading.config import AppConfig
from stock_trading.utils.logging_utils import should_log_progress

LOGGER = logging.getLogger(__name__)
REMOTE_DATA_ROOT = "stock-trading/data"
SERVER_ENV_NAMES = ("SERVER_IP", "SERVER_USER", "SERVER_PASS")
SYNC_SCOPES = {"market", "qlib", "model", "state"}
DIRECTORY_SCOPES = {
    "market": ("market",),
    "qlib": ("qlib",),
    "model": ("artifacts",),
}


def compare_sync_data(
    config: AppConfig,
    progress: Callable[[str], None] | None = None,
) -> list[dict[str, object]]:
    """只读比较本地与服务器文件清单，不执行传输、删除或覆盖。"""
    server, password = _server_credentials()
    local_root = config.data_dir.parent
    rows: list[dict[str, object]] = []
    for scope in ("market", "qlib", "model", "state"):
        if progress is not None:
            progress(f"正在对比 {scope} 数据的本地与服务器文件清单...")
        local = _local_manifest(local_root, scope)
        remote = _remote_manifest(server, password, scope)
        common = local.keys() & remote.keys()
        same = sum(local[path] == remote[path] for path in common)
        conflicts = sum(local[path] != remote[path] for path in common)
        local_only = local.keys() - remote.keys()
        remote_only = remote.keys() - local.keys()
        rows.append(
            {
                "scope": scope,
                "localFiles": len(local),
                "localBytes": sum(local.values()),
                "remoteFiles": len(remote),
                "remoteBytes": sum(remote.values()),
                "sameFiles": same,
                "pullableFiles": len(remote_only),
                "pushableFiles": len(local_only),
                "conflictFiles": conflicts,
            }
        )
        if progress is not None:
            progress(
                f"{scope} 数据对比完成：本地 {len(local)} 个文件，"
                f"服务器 {len(remote)} 个文件。"
            )
    return rows


def _local_manifest(root: Path, scope: str) -> dict[str, int]:
    if scope == "state":
        path = root / "state" / "trading.db"
        return {"trading.db": path.stat().st_size} if path.is_file() else {}
    result: dict[str, int] = {}
    for directory in DIRECTORY_SCOPES[scope]:
        base = root / directory
        if not base.is_dir():
            continue
        result.update(
            {
                f"{directory}/{path.relative_to(base).as_posix()}": path.stat().st_size
                for path in base.rglob("*")
                if path.is_file()
            }
        )
    return result


def _remote_manifest(
    server: str, password: str, scope: str
) -> dict[str, int]:
    if scope == "state":
        directories = ("state",)
        file_filter = "-name 'trading.db'"
    else:
        directories = DIRECTORY_SCOPES[scope]
        file_filter = ""
    result: dict[str, int] = {}
    for directory in directories:
        remote = f"{REMOTE_DATA_ROOT}/{directory}"
        completed = _run_command(
            "ssh",
            *_ssh_options(),
            server,
            (
                f"if [ -d '{remote}' ]; then "
                f"find '{remote}' -type f {file_filter} -printf '%P\\t%s\\n'; "
                "fi"
            ),
            password=password,
        )
        for line in completed.stdout.splitlines():
            relative, size = line.rsplit("\t", maxsplit=1)
            key = relative if scope == "state" else f"{directory}/{relative}"
            result[key] = int(size)
    return result


def sync_data(
    config: AppConfig,
    pull_scopes: tuple[str, ...],
    push_scopes: tuple[str, ...],
    force: bool = False,
) -> dict[str, object]:
    """使用 SSH/SCP 在本地项目数据目录和生产数据目录之间同步。"""
    server, password = _server_credentials()

    local_data_root = config.data_dir.parent
    completed: list[dict[str, str]] = []
    for scope in pull_scopes:
        if scope == "state":
            completed.append(_pull_state(config, server, password))
            continue
        for directory in DIRECTORY_SCOPES[scope]:
            LOGGER.info("%s 数据正在拉取：目录=%s", scope, directory)
            local_data_root.mkdir(parents=True, exist_ok=True)
            _run_command(
                "scp",
                *_ssh_options(),
                "-r",
                f"{server}:{REMOTE_DATA_ROOT}/{directory}",
                str(local_data_root),
                password=password,
                show_progress=True,
            )
            LOGGER.info("%s 数据拉取完成：目录=%s", scope, directory)
            completed.append(
                {"direction": "pull", "scope": scope, "directory": directory}
            )

    for scope in push_scopes:
        if scope == "state":
            if not force:
                raise ValueError("推送 state 必须显式指定 --force")
            LOGGER.warning(
                "警告：正在强制推送 state，服务器 SQLite 状态库将被本地一致快照覆盖"
            )
            completed.append(_push_state(config, server, password))
            continue
        for directory in DIRECTORY_SCOPES[scope]:
            source = local_data_root / directory
            if not source.is_dir():
                raise ValueError(f"本地同步目录不存在：{source}")
            LOGGER.info("%s 数据正在推送：目录=%s", scope, directory)
            file_count = _push_directory_archive(
                local_data_root, directory, server, password
            )
            LOGGER.info(
                "%s 数据推送完成：目录=%s；文件数=%d",
                scope,
                directory,
                file_count,
            )
            completed.append(
                {
                    "direction": "push",
                    "scope": scope,
                    "directory": directory,
                    "files": str(file_count),
                }
            )

    return {"status": "SUCCESS", "operations": completed}


def _pull_state(config: AppConfig, server: str, password: str) -> dict[str, str]:
    target = config.state_db_path
    target.parent.mkdir(parents=True, exist_ok=True)
    _ensure_file_not_in_use(target)
    LOGGER.info("state 数据正在拉取：数据库=trading.db")
    _run_command(
        "scp",
        *_ssh_options(),
        "-r",
        f"{server}:{REMOTE_DATA_ROOT}/state/trading.db",
        str(target),
        password=password,
        show_progress=True,
    )
    LOGGER.info("state 数据拉取完成：数据库=%s", target)
    return {
        "direction": "pull",
        "scope": "state",
        "database": str(target),
    }


def _push_state(
    config: AppConfig,
    server: str,
    password: str,
) -> dict[str, str]:
    source = config.state_db_path
    if not source.is_file():
        raise ValueError(f"本地状态库不存在：{source}")
    transfer_id = uuid4().hex
    remote_temporary = f"/tmp/stock-sync-state-{transfer_id}.db"
    target = f"{REMOTE_DATA_ROOT}/state/trading.db"
    backup = f"{REMOTE_DATA_ROOT}/state/.trading.db.previous-{transfer_id}"
    descriptor, snapshot_name = tempfile.mkstemp(
        prefix="stock-sync-state-",
        suffix=".db",
    )
    os.close(descriptor)
    snapshot = Path(snapshot_name)
    try:
        _create_sqlite_snapshot(source, snapshot)
        snapshot_size = snapshot.stat().st_size
        LOGGER.info(
            "state 一致快照生成完成：数据库=trading.db；大小=%d",
            snapshot_size,
        )
        _run_command(
            "scp",
            *_ssh_options(),
            str(snapshot),
            f"{server}:{remote_temporary}",
            password=password,
            show_progress=True,
        )
        LOGGER.info("state 快照上传完成：服务器正在校验并切换数据库")
        try:
            remote = _run_command(
                "ssh",
                *_ssh_options(),
                server,
                (
                    "set -e; "
                    f"mkdir -p '{REMOTE_DATA_ROOT}/state'; "
                    f"test -s '{remote_temporary}'; "
                    f"test \"$(stat -c %s '{remote_temporary}')\" -eq '{snapshot_size}'; "
                    f"rm -f '{backup}'; "
                    f"if [ -f '{target}' ]; then mv '{target}' '{backup}'; fi; "
                    f"if mv '{remote_temporary}' '{target}'; then "
                    f"chmod 600 '{target}'; rm -f '{backup}'; "
                    "else status=$?; "
                    f"if [ -f '{backup}' ]; then mv '{backup}' '{target}'; fi; "
                    "exit $status; fi; "
                    f"stat -c %s '{target}'"
                ),
                password=password,
            )
        except Exception:
            _run_command(
                "ssh",
                *_ssh_options(),
                server,
                f"rm -f '{remote_temporary}'",
                check=False,
                password=password,
            )
            raise
    finally:
        _remove_temporary_file(snapshot)
    remote_size = int(remote.stdout.strip())
    if remote_size != snapshot_size:
        raise RuntimeError(
            "远端 state 数据库大小不一致："
            f"本地快照={snapshot_size}；远端={remote_size}"
        )
    LOGGER.info(
        "服务器 state 数据库校验与切换完成：大小=%d",
        remote_size,
    )
    return {
        "direction": "push",
        "scope": "state",
        "database": "trading.db",
        "bytes": str(remote_size),
    }


def _remove_temporary_file(path: Path) -> None:
    for attempt in range(3):
        try:
            path.unlink(missing_ok=True)
            return
        except OSError:
            if attempt == 2:
                LOGGER.warning("本地临时快照清理失败：%s", path)
                return
            sleep(0.2)


def _create_sqlite_snapshot(source: Path, target: Path) -> None:
    LOGGER.info("state 一致快照开始：数据库=trading.db")
    last_progress = -1

    def report_progress(status: int, remaining: int, total: int) -> None:
        nonlocal last_progress
        if total <= 0:
            return
        progress = int((total - remaining) * 100 / total)
        bucket = progress // 10
        if bucket > last_progress:
            last_progress = bucket
            LOGGER.info("state 一致快照进度：%d%%", min(progress, 100))

    source_uri = source.resolve().as_uri() + "?mode=ro"
    with closing(
        sqlite3.connect(source_uri, uri=True, timeout=30)
    ) as source_db:
        with closing(sqlite3.connect(target)) as target_db:
            source_db.backup(
                target_db,
                pages=2048,
                progress=report_progress,
                sleep=0.05,
            )
            check = target_db.execute("PRAGMA quick_check").fetchone()
    if check is None or check[0] != "ok":
        raise RuntimeError(
            f"本地 state 一致快照校验失败：{check[0] if check else '无结果'}"
        )


def _push_directory_archive(
    local_data_root: Path,
    directory: str,
    server: str,
    password: str,
) -> int:
    transfer_id = uuid4().hex
    remote_archive = f"/tmp/stock-sync-{transfer_id}.tar.gz"
    staging = f"{REMOTE_DATA_ROOT}/.{directory}.sync-{transfer_id}"
    backup = f"{REMOTE_DATA_ROOT}/.{directory}.previous-{transfer_id}"
    target = f"{REMOTE_DATA_ROOT}/{directory}"
    with tempfile.TemporaryDirectory(prefix="stock-sync-archive-") as temporary:
        archive = Path(temporary) / f"{directory}.tar.gz"
        file_count = _create_push_archive(
            local_data_root / directory, archive
        )
        LOGGER.info(
            "同步归档生成完成：目录=%s；文件数=%d；大小=%d",
            directory,
            file_count,
            archive.stat().st_size,
        )
        _run_command(
            "scp",
            *_ssh_options(),
            str(archive),
            f"{server}:{remote_archive}",
            password=password,
            show_progress=True,
        )
        LOGGER.info("同步归档上传完成：目录=%s；服务器正在解压并校验", directory)
        try:
            remote = _run_command(
                "ssh",
                *_ssh_options(),
                server,
                (
                    "set -e; "
                    f"mkdir -p '{REMOTE_DATA_ROOT}'; "
                    f"rm -rf '{staging}'; mkdir -p '{staging}'; "
                    f"tar -xzf '{remote_archive}' -C '{staging}'; "
                    f"test -d '{staging}/{directory}'; "
                    f"count=$(find '{staging}/{directory}' -type f | wc -l); "
                    f"test \"$count\" -eq '{file_count}'; "
                    f"rm -rf '{backup}'; "
                    f"if [ -d '{target}' ]; then mv '{target}' '{backup}'; fi; "
                    f"if mv '{staging}/{directory}' '{target}'; then "
                    f"rm -rf '{backup}' '{staging}' '{remote_archive}'; "
                    "else status=$?; "
                    f"if [ -d '{backup}' ]; then mv '{backup}' '{target}'; fi; "
                    "exit $status; fi; "
                    "printf '%s' \"$count\""
                ),
                password=password,
            )
        except Exception:
            _run_command(
                "ssh",
                *_ssh_options(),
                server,
                f"rm -rf '{staging}' '{remote_archive}'",
                check=False,
                password=password,
            )
            raise
        LOGGER.info("服务器目录校验与切换完成：目录=%s", directory)
    remote_count = int(remote.stdout.strip())
    if remote_count != file_count:
        raise RuntimeError(
            f"远端同步文件数不一致：目录={directory}；"
            f"本地={file_count}；远端={remote_count}"
        )
    return file_count


def _create_push_archive(source: Path, archive: Path) -> int:
    files = _push_files(source)
    if not files:
        raise ValueError(f"本地同步目录没有文件：{source}")
    total = len(files)
    LOGGER.info("同步归档开始：目录=%s；文件数=%d", source.name, total)
    with tarfile.open(archive, "w:gz", compresslevel=1) as stream:
        for index, path in enumerate(files, start=1):
            stream.add(path, arcname=(Path(source.name) / path.relative_to(source)))
            if should_log_progress(index, total):
                LOGGER.info(
                    "同步归档进度：目录=%s；文件=%d/%d；进度=%.1f%%",
                    source.name,
                    index,
                    total,
                    index / total * 100,
                )
    return total


def _push_files(source: Path) -> list[Path]:
    files: list[Path] = []
    LOGGER.info("同步文件扫描开始：目录=%s", source.name)
    for path in source.rglob("*"):
        if not path.is_file():
            continue
        files.append(path)
        if len(files) % 20_000 == 0:
            LOGGER.info(
                "同步文件扫描进度：目录=%s；已发现文件=%d",
                source.name,
                len(files),
            )
    LOGGER.info(
        "同步文件扫描完成：目录=%s；文件数=%d",
        source.name,
        len(files),
    )
    return sorted(files)


def _ensure_file_not_in_use(path: Path) -> None:
    if os.name != "nt" or not path.is_file():
        return

    import ctypes
    from ctypes import wintypes

    kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
    create_file = kernel32.CreateFileW
    create_file.argtypes = (
        wintypes.LPCWSTR,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.LPVOID,
        wintypes.DWORD,
        wintypes.DWORD,
        wintypes.HANDLE,
    )
    create_file.restype = wintypes.HANDLE
    close_handle = kernel32.CloseHandle
    close_handle.argtypes = (wintypes.HANDLE,)
    close_handle.restype = wintypes.BOOL
    handle = create_file(str(path), 0xC0000000, 0, None, 3, 0x80, None)
    if handle == wintypes.HANDLE(-1).value:
        error_code = ctypes.get_last_error()
        if error_code in {32, 33}:
            raise ValueError(
                f"本地文件被占用，请关闭相关资源后重试：{path}"
            )
        raise ctypes.WinError(error_code)
    close_handle(handle)


def _server_credentials() -> tuple[str, str]:
    values = {name: os.getenv(name, "").strip() for name in SERVER_ENV_NAMES}
    missing = [name for name, value in values.items() if not value]
    if missing:
        raise ValueError(f"缺少服务器环境变量：{','.join(missing)}")
    server_ip = values["SERVER_IP"]
    server_user = values["SERVER_USER"]
    if not re.fullmatch(r"[A-Za-z0-9._:-]+", server_ip):
        raise ValueError("SERVER_IP 格式无效")
    if not re.fullmatch(r"[A-Za-z0-9._-]+", server_user):
        raise ValueError("SERVER_USER 格式无效")
    return f"{server_user}@{server_ip}", values["SERVER_PASS"]


def _ssh_options() -> tuple[str, ...]:
    return (
        "-o",
        "BatchMode=no",
        "-o",
        "StrictHostKeyChecking=accept-new",
    )


@contextmanager
def _askpass_environment(password: str) -> Iterator[dict[str, str]]:
    with tempfile.TemporaryDirectory(prefix="stock-sync-") as directory:
        helper = Path(directory) / (
            "askpass.cmd" if os.name == "nt" else "askpass.sh"
        )
        python = str(Path(sys.executable).resolve())
        if os.name == "nt":
            helper.write_text(
                f'@echo off\r\n"{python}" -c "import os; print(os.environ['
                "'SERVER_PASS'])\"\r\n",
                encoding="utf-8",
            )
        else:
            helper.write_text(
                "#!/bin/sh\n"
                f'exec "{python}" -c \'import os; print(os.environ["SERVER_PASS"])\'\n',
                encoding="utf-8",
            )
            helper.chmod(0o700)
        environment = os.environ.copy()
        environment.update(
            {
                "DISPLAY": "stock-sync",
                "SSH_ASKPASS": str(helper),
                "SSH_ASKPASS_REQUIRE": "force",
                "SERVER_PASS": password,
            }
        )
        yield environment


def _run_command(
    *command: str,
    check: bool = True,
    password: str,
    show_progress: bool = False,
) -> subprocess.CompletedProcess[str]:
    try:
        with _askpass_environment(password) as environment:
            return subprocess.run(
                command,
                check=check,
                capture_output=not show_progress,
                text=True,
                encoding="utf-8",
                errors="replace",
                stdin=subprocess.DEVNULL,
                env=environment,
            )
    except FileNotFoundError as error:
        raise ValueError(f"系统未安装命令：{command[0]}") from error
    except subprocess.CalledProcessError as error:
        detail = (error.stderr or error.stdout or "远程命令执行失败").strip()
        raise RuntimeError(detail) from error


# AI_GENERATE_END --------------------
