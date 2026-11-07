# AI_GENERATE_START --
from __future__ import annotations

import os
import sys
from typing import TextIO


BOLD_BLUE = "1;34"
BOLD_CYAN = "1;36"
BOLD_RED = "1;31"
BOLD_YELLOW = "1;33"
DIM = "2"


def style(text: str, code: str, stream: TextIO = sys.stdout) -> str:
    if not stream.isatty() or "NO_COLOR" in os.environ:
        return text
    return f"\033[{code}m{text}\033[0m"


def echo(
    text: object = "",
    *,
    color: str | None = None,
    error: bool = False,
) -> None:
    stream = sys.stderr if error else sys.stdout
    value = str(text)
    print(
        style(value, color, stream) if color else value,
        file=stream,
        flush=True,
    )
# AI_GENERATE_END --
