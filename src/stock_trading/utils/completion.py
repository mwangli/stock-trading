# AI_GENERATE_START -----
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
POWERSHELL_TAB_COMPLETION = """

# Cycle through completions without printing a candidate list.
if (Get-Command Set-PSReadLineKeyHandler -ErrorAction SilentlyContinue) {
  Set-PSReadLineKeyHandler -Key Tab -ScriptBlock {
    [string]$line = ''
    [int]$cursor = 0
    [Microsoft.PowerShell.PSConsoleReadLine]::GetBufferState(
      [ref]$line,
      [ref]$cursor
    )
    if ($script:stockTradingCompletionOptions -and
        $cursor -eq $line.Length -and
        $line -eq ($script:stockTradingCompletionBase +
          $script:stockTradingCompletionOptions[
            $script:stockTradingCompletionIndex
          ])) {
      $current = $script:stockTradingCompletionOptions[
        $script:stockTradingCompletionIndex
      ]
      $script:stockTradingCompletionIndex = (
        $script:stockTradingCompletionIndex + 1
      ) % $script:stockTradingCompletionOptions.Count
      $next = $script:stockTradingCompletionOptions[
        $script:stockTradingCompletionIndex
      ]
      [Microsoft.PowerShell.PSConsoleReadLine]::Replace(
        $cursor - $current.Length,
        $current.Length,
        $next
      )
      return
    }
    $script:stockTradingCompletionOptions = $null
    $words = @($line.Substring(0, $cursor) -split '\\s+' | Where-Object { $_ })
    if ($words.Count -ge 2 -and
        $words[0] -in @('stock-trading', 'stock-trading.exe') -and
        $words[-1] -in @('-', '--')) {
      $prefix = '_shtab_stock_trading'
      if ($words.Count -ge 3 -and
          $_shtab_stock_trading_subparsers[$prefix] -contains $words[1]) {
        $prefix += '_' + (_shtab_powershell_replace_nonword $words[1])
      }
      $options = @(
        $_shtab_stock_trading_option_strings[$prefix] |
          Where-Object { $_ -like "$($words[-1])*" }
      )
      if ($options.Count -gt 0) {
        $script:stockTradingCompletionOptions = $options
        $script:stockTradingCompletionIndex = 0
        $script:stockTradingCompletionBase = $line.Substring(
          0,
          $cursor - $words[-1].Length
        )
        [Microsoft.PowerShell.PSConsoleReadLine]::Replace(
          $cursor - $words[-1].Length,
          $words[-1].Length,
          $options[0]
        )
        return
      }
    }
    [Microsoft.PowerShell.PSConsoleReadLine]::TabCompleteNext()
  }
}
"""


def ensure_completion(parser: argparse.ArgumentParser) -> None:
    try:
        if os.name == "nt":
            _ensure_powershell(parser)
        else:
            _ensure_bash(parser)
    except OSError:
        return


def _powershell_script(parser: argparse.ArgumentParser) -> str:
    script = shtab.complete(parser, shell="powershell").replace(
        "-CommandName stock-trading -ScriptBlock",
        "-CommandName stock-trading,stock-trading.exe -ScriptBlock",
        1,
    )
    return script + POWERSHELL_TAB_COMPLETION


def _ensure_powershell(parser: argparse.ArgumentParser) -> None:
    completion_file = (
        Path.home()
        / ".config"
        / "powershell"
        / "completions"
        / "stock-trading.ps1"
    )
    script = _powershell_script(parser)
    if (
        completion_file.exists()
        and completion_file.read_text(encoding="utf-8-sig") == script
    ):
        return

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
    completion_file.parent.mkdir(parents=True, exist_ok=True)
    completion_file.write_text(script, encoding="utf-8-sig")


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


def _ensure_bash(parser: argparse.ArgumentParser) -> None:
    completion_file = (
        Path.home()
        / ".local"
        / "share"
        / "bash-completion"
        / "completions"
        / "stock-trading"
    )
    script = shtab.complete(parser, shell="bash")
    if completion_file.exists() and completion_file.read_text() == script:
        return
    _append_once(Path.home() / ".bashrc", BASH_SOURCE)
    completion_file.parent.mkdir(parents=True, exist_ok=True)
    completion_file.write_text(script, encoding="utf-8")


def _append_once(path: Path, line: str, encoding: str = "utf-8") -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    content = path.read_text(encoding="utf-8-sig") if path.exists() else ""
    if line in content.splitlines():
        return
    separator = "" if not content or content.endswith("\n") else "\n"
    path.write_text(f"{content}{separator}{line}\n", encoding=encoding)


# AI_GENERATE_END -----
