# AI_GENERATE_START ---------
from __future__ import annotations

from collections.abc import Sequence

from click import Context, HelpFormatter
from rich.console import Console, Group
from rich.panel import Panel
from rich.rule import Rule
from rich.table import Table
from rich.text import Text
from typer.core import TyperCommand


CommandGroups = Sequence[tuple[str, Sequence[tuple[str, str]]]]


def build_help_command(
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> type[TyperCommand]:
    class SectionedHelpCommand(TyperCommand):
        def format_help(self, ctx: Context, formatter: HelpFormatter) -> None:
            super().format_help(ctx, formatter)
            _show_help_sections(business, examples, boundaries)

    return SectionedHelpCommand


def _show_help_sections(
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> None:
    content: list[Text] = []
    sections = (
        ("业务说明", "bold blue", business, "", ""),
        ("示例", "bold green", examples, "", "bold cyan"),
        ("业务边界", "bold yellow", boundaries, "· ", ""),
    )
    for index, (heading, heading_style, lines, prefix, body_style) in enumerate(
        sections
    ):
        if index:
            content.append(Text())
        content.append(Text(heading, style=heading_style))
        content.extend(Text(f"  {prefix}{line}", style=body_style) for line in lines)
    Console(highlight=False).print(
        Panel(
            Group(*content),
            border_style="dim",
            title="说明",
            title_align="left",
        )
    )


def show_command_groups(groups: CommandGroups) -> None:
    width = max(
        len(command)
        for _, commands in groups
        for command, _ in commands
    )
    console = Console(highlight=False)
    console.print(
        Text("  STOCK TRADING A股 T+1 自动交易命令", style="bold yellow")
    )
    table = Table(
        box=None,
        expand=True,
        highlight=False,
        pad_edge=False,
        padding=(0, 1),
        show_header=False,
    )
    table.add_column(no_wrap=True, width=width)
    table.add_column(ratio=10)
    for group_index, (group, commands) in enumerate(groups):
        if group_index:
            table.add_row("", "")
        table.add_row(Text(group, style="bold blue"), "")
        for command, summary in commands:
            table.add_row(
                Text(command, style="bold cyan"),
                Text(summary),
            )
    footer = Text.assemble(
        ("查看详情  ", "dim"),
        ("stock-<command> -h", "bold cyan"),
    )
    console.print(
        Panel(
            Group(table, Rule(style="dim"), footer),
            border_style="dim",
            title="Commands",
            title_align="left",
        )
    )
# AI_GENERATE_END ---------
