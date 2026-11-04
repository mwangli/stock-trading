# AI_GENERATE_START ----
from __future__ import annotations

import logging
import os
import re
import sqlite3
import subprocess
import sys
import tempfile
from contextlib import contextmanager
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator
from uuid import uuid4

from stock_trading.config import AppConfig

LOGGER = logging.getLogger(__name__)
REMOTE_DATA_ROOT = "stock-trading/data"
SERVER_ENV_NAMES = ("SERVER_IP", "SERVER_USER", "SERVER_PASS")
SYNC_SCOPES = {"market", "model", "state"}
DIRECTORY_SCOPES = {
    "market": ("market", "qlib"),
    "model": ("artifacts",),
}
SNAPSHOT_TOKEN_PATTERN = re.compile(r"^[0-9a-f]{32}$")


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
            LOGGER.info("开始拉取数据：类型=%s；目录=%s", scope, directory)
            local_data_root.mkdir(parents=True, exist_ok=True)
            _run_command(
                "scp",
                *_ssh_options(),
                "-r",
                f"{server}:{REMOTE_DATA_ROOT}/{directory}",
                str(local_data_root),
                password=password,
            )
            completed.append(
                {"direction": "pull", "scope": scope, "directory": directory}
            )

    for scope in push_scopes:
        for directory in DIRECTORY_SCOPES[scope]:
            source = local_data_root / directory
            if not source.is_dir():
                raise ValueError(f"本地同步目录不存在：{source}")
            LOGGER.info("开始推送数据：类型=%s；目录=%s", scope, directory)
            _run_command(
                "scp",
                *_ssh_options(),
                "-r",
                str(source),
                f"{server}:{REMOTE_DATA_ROOT}/",
                password=password,
            )
            completed.append(
                {"direction": "push", "scope": scope, "directory": directory}
            )

    return {"status": "SUCCESS", "operations": completed}


def create_state_snapshot(config: AppConfig, token: str) -> dict[str, str]:
    """在服务器状态目录生成可供 SCP 下载的一致性 SQLite 快照。"""
    if not SNAPSHOT_TOKEN_PATTERN.fullmatch(token):
        raise ValueError("状态快照标识无效")
    source = config.state_db_path
    if not source.is_file():
        raise ValueError(f"SQLite 状态库不存在：{source}")
    target = source.parent / f".stock-sync-{token}.db"
    temporary = target.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    target.unlink(missing_ok=True)
    _backup_database(source, temporary)
    _validate_database(temporary)
    os.replace(temporary, target)
    return {"status": "SUCCESS", "snapshot": str(target)}


def _pull_state(config: AppConfig, server: str, password: str) -> dict[str, str]:
    token = uuid4().hex
    remote_snapshot = f"{REMOTE_DATA_ROOT}/state/.stock-sync-{token}.db"
    project_root = config.data_dir.parent.parent
    temporary_dir = project_root / ".tmp" / "stock-sync"
    temporary_dir.mkdir(parents=True, exist_ok=True)
    downloaded = temporary_dir / f"trading-{token}.db"
    try:
        LOGGER.info("开始生成服务器 SQLite 一致性快照")
        _run_command(
            "ssh",
            *_ssh_options(),
            server,
            f"stock-trading/venv/bin/stock-sync --snapshot-state {token}",
            password=password,
        )
        _run_command(
            "scp",
            *_ssh_options(),
            f"{server}:{remote_snapshot}",
            str(downloaded),
            password=password,
        )
        _validate_database(downloaded)

        target = config.state_db_path
        target.parent.mkdir(parents=True, exist_ok=True)
        backup = ""
        if target.is_file():
            backup_dir = target.parent / "backups"
            backup_dir.mkdir(parents=True, exist_ok=True)
            timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
            backup_path = backup_dir / (
                f"trading-before-sync-{timestamp}-{uuid4().hex[:8]}.db"
            )
            _backup_database(target, backup_path)
            backup = str(backup_path)
        os.replace(downloaded, target)
        LOGGER.info("SQLite 状态拉取完成：数据库=%s", target)
        return {
            "direction": "pull",
            "scope": "state",
            "database": str(target),
            "backup": backup,
        }
    finally:
        downloaded.unlink(missing_ok=True)
        try:
            _run_command(
                "ssh",
                *_ssh_options(),
                server,
                f"rm -f {remote_snapshot}",
                check=False,
                password=password,
            )
        except (ValueError, OSError):
            LOGGER.warning("服务器临时 SQLite 快照清理失败")


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


def _backup_database(source: Path, target: Path) -> None:
    target.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(source) as source_connection:
        with sqlite3.connect(target) as target_connection:
            source_connection.backup(target_connection)


def _validate_database(path: Path) -> None:
    with sqlite3.connect(path) as connection:
        quick_check = str(connection.execute("PRAGMA quick_check").fetchone()[0])
        if quick_check != "ok":
            raise ValueError(f"SQLite quick_check 失败：{quick_check}")
        foreign_keys = connection.execute("PRAGMA foreign_key_check").fetchone()
        if foreign_keys is not None:
            raise ValueError("SQLite foreign_key_check 失败")


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
) -> subprocess.CompletedProcess[str]:
    try:
        with _askpass_environment(password) as environment:
            return subprocess.run(
                command,
                check=check,
                capture_output=True,
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


# AI_GENERATE_END ----
