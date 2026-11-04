# AI_GENERATE_START ------
from __future__ import annotations

import logging
import os
import re
import subprocess
import sys
import tempfile
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator

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


def _pull_state(config: AppConfig, server: str, password: str) -> dict[str, str]:
    target = config.state_db_path
    target.parent.mkdir(parents=True, exist_ok=True)
    LOGGER.info("开始拉取服务器 SQLite 状态库")
    _run_command(
        "scp",
        *_ssh_options(),
        "-r",
        f"{server}:{REMOTE_DATA_ROOT}/state/trading.db",
        str(target),
        password=password,
    )
    LOGGER.info("SQLite 状态拉取完成：数据库=%s", target)
    return {
        "direction": "pull",
        "scope": "state",
        "database": str(target),
    }


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


# AI_GENERATE_END ------
