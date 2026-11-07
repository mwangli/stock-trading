# AI_GENERATE_START --
from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess

import shtab


POWERSHELL_SOURCE = (
    '. "$HOME/.config/powershell/completions/stock-trading.ps1"'
)
BASH_SOURCE = (
    '[ -r "$HOME/.local/share/bash-completion/completions/stock-trading" ] '
    '&& source "$HOME/.local/share/bash-completion/completions/stock-trading"'
)


def install_completion(parser: argparse.ArgumentParser) -> str:
    if os.name == "nt":
        return _install_powershell(parser)
    return _install_bash(parser)


def _install_powershell(parser: argparse.ArgumentParser) -> str:
    completion_file = (
        Path.home()
        / ".config"
        / "powershell"
        / "completions"
        / "stock-trading.ps1"
    )
    script = shtab.complete(parser, shell="powershell").replace(
        "-CommandName stock-trading -ScriptBlock",
        "-CommandName stock-trading,stock-trading.exe -ScriptBlock",
        1,
    )
    completion_file.parent.mkdir(parents=True, exist_ok=True)
    completion_file.write_text(script, encoding="utf-8-sig")

    profiles = _powershell_profiles()
    if not profiles:
        profiles = [
            Path.home()
            / "Documents"
            / "PowerShell"
            / "Microsoft.PowerShell_profile.ps1"
        ]
    for profile in profiles:
        _append_once(profile, POWERSHELL_SOURCE, encoding="utf-8-sig")
    return "PowerShell 自动补全已安装，重新打开终端后生效。"


def _powershell_profiles() -> list[Path]:
    profiles: list[Path] = []
    for executable in ("pwsh", "powershell"):
        path = shutil.which(executable)
        if not path:
            continue
        result = subprocess.run(
            [path, "-NoProfile", "-NonInteractive", "-Command", "$PROFILE"],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        profile = result.stdout.strip()
        if result.returncode == 0 and profile:
            profile_path = Path(profile)
            if profile_path not in profiles:
                profiles.append(profile_path)
    return profiles


def _install_bash(parser: argparse.ArgumentParser) -> str:
    completion_file = (
        Path.home()
        / ".local"
        / "share"
        / "bash-completion"
        / "completions"
        / "stock-trading"
    )
    completion_file.parent.mkdir(parents=True, exist_ok=True)
    completion_file.write_text(
        shtab.complete(parser, shell="bash"),
        encoding="utf-8",
    )
    _append_once(Path.home() / ".bashrc", BASH_SOURCE)
    return "Bash 自动补全已安装，重新打开终端后生效。"


def _append_once(path: Path, line: str, encoding: str = "utf-8") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = path.read_text(encoding="utf-8-sig") if path.exists() else ""
    if line in content.splitlines():
        return
    separator = "" if not content or content.endswith("\n") else "\n"
    path.write_text(f"{content}{separator}{line}\n", encoding=encoding)


# AI_GENERATE_END --
