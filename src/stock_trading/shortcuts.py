# AI_GENERATE_START ----
from __future__ import annotations

from collections.abc import Callable

import typer

from stock_trading.cli import (
    broker_command,
    clean_command,
    collect_command,
    evaluate_command,
    init_command,
    performance_command,
    rank_command,
    trading_command,
    train_command,
)


HELP_CONTEXT = {"help_option_names": ["-h", "--help"]}
COMMAND_SUMMARIES = (
    ("stock-help", "列出全部命令及其用途。"),
    ("stock-init", "初始化行情、Qlib、模型、回测和最新排名。"),
    ("stock-train", "训练、评估并回测模型。"),
    ("stock-collect", "采集行情并刷新 Qlib。"),
    ("stock-rank", "生成完整横截面排名。"),
    ("stock-sell", "执行上午实时卖出。"),
    ("stock-buy", "执行下午实时买入。"),
    ("stock-broker", "同步券商事实并结算交易闭环。"),
    ("stock-perform", "生成已实现绩效。"),
    ("stock-evaluate", "评估模型并生成重训请求。"),
    ("stock-clean", "清理过期数据和业务状态。"),
)


def _run(
    program: str,
    command: Callable[..., None],
    description: str,
    examples: tuple[str, ...],
) -> None:
    command_app = typer.Typer(
        add_completion=False,
        rich_markup_mode="rich",
        pretty_exceptions_enable=False,
    )
    command_app.command(
        help=description,
        epilog="[bold]示例[/bold]\n" + "\n".join(examples),
        context_settings=HELP_CONTEXT,
    )(command)
    command_app(prog_name=program)


def _show_commands() -> None:
    typer.echo("可用命令：")
    width = max(len(command) for command, _ in COMMAND_SUMMARIES)
    for command, summary in COMMAND_SUMMARIES:
        typer.echo(f"  {command.ljust(width)}  {summary}")
    typer.echo("\n查看具体用法：stock-broker -h")


def show_help() -> None:
    _run(
        "stock-help",
        _show_commands,
        "列出项目提供的全部独立命令及其用途。",
        ("stock-help", "stock-broker -h"),
    )


def init() -> None:
    _run(
        "stock-init",
        init_command,
        "初始化行情、Qlib、模型、回测和最新排名。",
        ("stock-init",),
    )


def train() -> None:
    _run(
        "stock-train",
        train_command,
        "使用现有 Qlib 数据训练、评估并回测模型。",
        ("stock-train", "stock-train --split 6:3:1"),
    )


def collect() -> None:
    _run(
        "stock-collect",
        collect_command,
        "采集日线、复权因子和 5 分钟行情，并刷新 Qlib。",
        (
            "stock-collect",
            "stock-collect --codes sh.600000,sz.000001 --date 2026-11-02",
        ),
    )


def rank() -> None:
    _run(
        "stock-rank",
        rank_command,
        "等待行情采集完成后生成完整横截面排名。",
        ("stock-rank", "stock-rank --date 2026-11-02"),
    )


def _sell_command() -> None:
    trading_command("S")


def sell() -> None:
    _run(
        "stock-sell",
        _sell_command,
        "在上海交易日上午窗口，按实时可卖持仓和五档行情执行卖出。",
        ("stock-sell",),
    )


def _buy_command() -> None:
    trading_command("B")


def buy() -> None:
    _run(
        "stock-buy",
        _buy_command,
        "在上海交易日下午窗口，按当日排名、实时资金和五档行情执行买入。",
        ("stock-buy",),
    )


def broker() -> None:
    _run(
        "stock-broker",
        broker_command,
        "同步券商委托和成交事实，并增量结算交易闭环。",
        (
            "stock-broker",
            "stock-broker --date 2026-11-02",
            "stock-broker --date 2026-10-01:2026-11-02",
        ),
    )


def perform() -> None:
    _run(
        "stock-perform",
        performance_command,
        "根据券商成交事实生成指定交易日的已实现绩效。",
        ("stock-perform", "stock-perform --date 2026-11-02"),
    )


def evaluate() -> None:
    _run(
        "stock-evaluate",
        evaluate_command,
        "根据已实现绩效评估当前模型并生成重训请求。",
        ("stock-evaluate", "stock-evaluate --date 2026-11-02"),
    )


def clean() -> None:
    _run(
        "stock-clean",
        clean_command,
        "清理过期分钟数据、任务记录和业务状态。",
        ("stock-clean", "stock-clean --date 2026-11-02"),
    )
# AI_GENERATE_END ----
