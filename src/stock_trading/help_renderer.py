# AI_GENERATE_START -----
from __future__ import annotations

from collections.abc import Sequence

import typer


CommandGroups = Sequence[tuple[str, Sequence[tuple[str, str]]]]


def build_epilog(
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> str:
    sections = (
        ("[bold blue]业务说明[/bold blue]", business, None, ""),
        ("[bold green]示例[/bold green]", examples, "bold cyan", ""),
        ("[bold yellow]业务边界[/bold yellow]", boundaries, None, "· "),
    )
    blocks: list[str] = []
    for heading, lines, body_style, prefix in sections:
        body = []
        for line in lines:
            text = f"{prefix}{line}"
            if body_style:
                text = f"[{body_style}]{text}[/{body_style}]"
            body.append(f"  {text}")
        blocks.append("\n".join((heading, *body)))
    return "\n\n".join(blocks)


def show_command_groups(groups: CommandGroups) -> None:
    width = max(
        len(command)
        for _, commands in groups
        for command, _ in commands
    )
    typer.secho(
        "STOCK TRADING A股 T+1 自动交易命令",
        fg=typer.colors.YELLOW,
        bold=True,
    )
    for group, commands in groups:
        typer.secho(f"\n{group}", fg=typer.colors.BLUE, bold=True)
        for command, summary in commands:
            typer.secho(
                f"  {command.ljust(width)}  ",
                fg=typer.colors.CYAN,
                bold=True,
                nl=False,
            )
            typer.echo(summary)
    typer.echo("\n查看详情  stock-<command> -h")
    typer.echo("示例      stock-broker -h")
# AI_GENERATE_END -----
