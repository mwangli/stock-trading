# AI_GENERATE_START --
from __future__ import annotations

import sys

from stock_trading.cli import app


def _run(program: str, command: list[str]) -> None:
    app(args=[*command, *sys.argv[1:]], prog_name=program)


def collect() -> None:
    _run("stock-collect", ["collect"])


def rank() -> None:
    _run("stock-rank", ["rank"])


def sell() -> None:
    _run("stock-sell", ["trading", "--type", "S"])


def buy() -> None:
    _run("stock-buy", ["trading", "--type", "B"])


def broker() -> None:
    _run("stock-broker", ["broker"])


def perform() -> None:
    _run("stock-perform", ["performance"])


def evaluate() -> None:
    _run("stock-evaluate", ["evaluate"])


def clean() -> None:
    _run("stock-clean", ["clean"])
# AI_GENERATE_END --
