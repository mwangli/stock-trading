# AI_GENERATE_START -----------
from __future__ import annotations

import logging
import os
import re
import subprocess
import sys
import tarfile
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator
from uuid import uuid4

from stock_trading.config import AppConfig

LOGGER = logging.getLogger(__name__)
REMOTE_DATA_ROOT = "stock-trading/data"
SERVER_ENV_NAMES = ("SERVER_IP", "SERVER_USER", "SERVER_PASS")
SYNC_SCOPES = {"market", "qlib", "model", "state"}
DIRECTORY_SCOPES = {
    "market": ("market",),
    "qlib": ("qlib",),
    "model": ("artifacts",),
}
MARKET_ARCHIVE_DIRECTORIES = ("basic", "calendar", "daily", "factor")
MODEL_REQUIRED_FILES = (
    "model.pkl",
    "metrics.json",
    "top5.parquet",
    "test_predictions.parquet",
    "evaluation.json",
)


def sync_data(
    config: AppConfig,
    pull_scopes: tuple[str, ...],
    push_scopes: tuple[str, ...],
    dry_run: bool = False,
) -> dict[str, object]:
    """使用 SSH/SCP 在本地项目数据目录和生产数据目录之间同步。"""
    server, password = _server_credentials()

    local_data_root = config.data_dir.parent
    plan = _build_plan(local_data_root, pull_scopes, push_scopes)
    if dry_run:
        LOGGER.info("数据同步计划生成完成：操作数=%d", len(plan))
        return {"status": "DRY_RUN", "operations": plan}

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


def _push_directory_archive(
    local_data_root: Path,
    directory: str,
    server: str,
    password: str,
) -> int:
    transfer_id = uuid4().hex
    remote_archive = f"/tmp/stock-sync-{transfer_id}.tar.gz"
    staging = f"{REMOTE_DATA_ROOT}/.{directory}.sync-{transfer_id}"
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
                f"rm -rf '{target}'; "
                f"mv '{staging}/{directory}' '{target}'; "
                f"rm -rf '{staging}' '{remote_archive}'; "
                "printf '%s' \"$count\""
            ),
            password=password,
        )
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
    with tarfile.open(archive, "w:gz", compresslevel=1) as stream:
        for path in files:
            stream.add(path, arcname=(Path(source.name) / path.relative_to(source)))
    return len(files)


def _push_files(source: Path) -> list[Path]:
    if source.name == "artifacts":
        versions = source / "alpha158-lightgbm" / "versions"
        candidates = sorted(
            (
                path
                for path in versions.iterdir()
                if path.is_dir() and not path.name.startswith(".")
            ),
            reverse=True,
        )
        current = next(
            (
                path
                for path in candidates
                if all((path / name).is_file() for name in MODEL_REQUIRED_FILES)
            ),
            None,
        )
        if current is None:
            raise ValueError(f"没有可同步的完整模型版本：{versions}")
        return sorted(path for path in current.rglob("*") if path.is_file())
    if source.name != "market":
        return sorted(path for path in source.rglob("*") if path.is_file())
    files: list[Path] = []
    for name in MARKET_ARCHIVE_DIRECTORIES:
        root = source / name
        if not root.is_dir():
            raise ValueError(f"本地 market 数据目录缺失：{root}")
        if name == "daily":
            files.extend(
                path
                for partition in root.glob("code=*")
                for path in partition.rglob("*")
                if path.is_file()
            )
            continue
        files.extend(path for path in root.rglob("*") if path.is_file())
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


def _build_plan(
    local_data_root: Path,
    pull_scopes: tuple[str, ...],
    push_scopes: tuple[str, ...],
) -> list[dict[str, str]]:
    operations: list[dict[str, str]] = []
    for direction, scopes in (("pull", pull_scopes), ("push", push_scopes)):
        for scope in scopes:
            if scope == "state":
                operations.append(
                    {
                        "direction": direction,
                        "scope": scope,
                        "path": str(local_data_root / "state" / "trading.db"),
                    }
                )
                continue
            for directory in DIRECTORY_SCOPES[scope]:
                operations.append(
                    {
                        "direction": direction,
                        "scope": scope,
                        "path": str(local_data_root / directory),
                    }
                )
    return operations


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


# AI_GENERATE_END -----------
