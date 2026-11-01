# AI_GENERATE_START -
from __future__ import annotations

import shutil
import subprocess
import tarfile
import tempfile
from pathlib import Path, PurePosixPath

from stock_trading.config import AppConfig

REMOTE_RUNTIME_DIR = "/data/stock-trading"
SUPPORTED_DATA = ("market", "qlib")


def sync_runtime_data(
    config: AppConfig,
    data_names: tuple[str, ...],
    *,
    push: bool,
) -> dict[str, object]:
    """用途：通过 SSH 同步运行数据；输入：数据类型和方向；输出：同步摘要。"""
    ssh_command = _build_ssh_command(config)
    if push:
        _push_data(config, ssh_command, data_names)
        direction = "push"
    else:
        _pull_data(config, ssh_command, data_names)
        direction = "pull"
    return {
        "direction": direction,
        "data": list(data_names),
        "remote": config.sync_host,
        "remoteRoot": REMOTE_RUNTIME_DIR,
    }


def _build_ssh_command(config: AppConfig) -> list[str]:
    ssh = shutil.which("ssh")
    if ssh is None:
        raise RuntimeError("未找到 ssh 命令，请先安装或启用 OpenSSH Client")
    if not config.sync_host:
        raise ValueError("未配置 STOCK_TRADING_SYNC_HOST，无法连接同步服务器")
    command = [
        ssh,
        "-o",
        "BatchMode=yes",
        "-p",
        str(config.sync_port),
    ]
    if config.sync_identity_file is not None:
        if not config.sync_identity_file.is_file():
            raise ValueError(f"SSH 私钥文件不存在：{config.sync_identity_file}")
        command.extend(("-i", str(config.sync_identity_file)))
    target = (
        f"{config.sync_user}@{config.sync_host}"
        if config.sync_user
        else config.sync_host
    )
    command.append(target)
    return command


def _push_data(
    config: AppConfig,
    ssh_command: list[str],
    data_names: tuple[str, ...],
) -> None:
    source_paths = {name: _local_data_path(config, name) for name in data_names}
    missing = [name for name, path in source_paths.items() if not path.is_dir()]
    if missing:
        raise ValueError(f"本地数据目录不存在：{','.join(missing)}")
    remote_command = (
        f"mkdir -p {REMOTE_RUNTIME_DIR} && "
        f"tar -xzf - -C {REMOTE_RUNTIME_DIR}"
    )
    process = subprocess.Popen(
        [*ssh_command, remote_command],
        stdin=subprocess.PIPE,
        stdout=subprocess.DEVNULL,
    )
    try:
        if process.stdin is None:
            raise RuntimeError("无法建立 SSH 上传通道")
        with tarfile.open(fileobj=process.stdin, mode="w|gz") as archive:
            for name, path in source_paths.items():
                archive.add(path, arcname=name, filter=_regular_data_member)
        return_code = process.wait()
    except BaseException:
        process.kill()
        process.wait()
        raise
    if return_code != 0:
        raise RuntimeError(f"SSH 上传失败，退出码={return_code}")


def _pull_data(
    config: AppConfig,
    ssh_command: list[str],
    data_names: tuple[str, ...],
) -> None:
    remote_command = (
        f"tar -czf - -C {REMOTE_RUNTIME_DIR} {' '.join(data_names)}"
    )
    process = subprocess.Popen(
        [*ssh_command, remote_command],
        stdout=subprocess.PIPE,
    )
    temporary_root = config.project_dir / ".tmp"
    temporary_root.mkdir(parents=True, exist_ok=True)
    try:
        if process.stdout is None:
            raise RuntimeError("无法建立 SSH 下载通道")
        with tempfile.TemporaryDirectory(
            prefix="sync-",
            dir=temporary_root,
        ) as temporary_dir:
            stage_dir = Path(temporary_dir)
            with tarfile.open(fileobj=process.stdout, mode="r|gz") as archive:
                _extract_data_archive(archive, stage_dir, data_names)
            return_code = process.wait()
            if return_code != 0:
                raise RuntimeError(f"SSH 下载失败，退出码={return_code}")
            for name in data_names:
                source = stage_dir / name
                if not source.is_dir():
                    raise RuntimeError(f"服务器未返回数据目录：{name}")
                shutil.copytree(
                    source,
                    _local_data_path(config, name),
                    dirs_exist_ok=True,
                )
    except BaseException:
        process.kill()
        process.wait()
        raise


def _extract_data_archive(
    archive: tarfile.TarFile,
    stage_dir: Path,
    data_names: tuple[str, ...],
) -> None:
    allowed = set(data_names)
    for member in archive:
        relative = PurePosixPath(member.name)
        if (
            relative.is_absolute()
            or ".." in relative.parts
            or not relative.parts
            or relative.parts[0] not in allowed
        ):
            raise RuntimeError(f"服务器返回了非法归档路径：{member.name}")
        target = stage_dir.joinpath(*relative.parts)
        if member.isdir():
            target.mkdir(parents=True, exist_ok=True)
            continue
        if not member.isfile():
            raise RuntimeError(f"服务器返回了不支持的归档成员：{member.name}")
        source = archive.extractfile(member)
        if source is None:
            raise RuntimeError(f"无法读取服务器归档成员：{member.name}")
        target.parent.mkdir(parents=True, exist_ok=True)
        with source, target.open("wb") as output:
            shutil.copyfileobj(source, output)


def _regular_data_member(member: tarfile.TarInfo) -> tarfile.TarInfo | None:
    if member.isdir() or member.isfile():
        member.uid = 0
        member.gid = 0
        member.uname = ""
        member.gname = ""
        return member
    return None


def _local_data_path(config: AppConfig, name: str) -> Path:
    if name == "market":
        return config.data_dir
    if name == "qlib":
        return config.qlib_dir
    raise ValueError(f"不支持的数据类型：{name}")
# AI_GENERATE_END -
