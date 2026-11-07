# AI_GENERATE_START --
from __future__ import annotations

import argparse
from collections.abc import Callable

from stock_trading.cli import (
    CliParameterError,
    clean_command,
    collect_command,
    order_command,
    prepare_command,
    rank_command,
    review_command,
    sync_command,
    settle_command,
    train_command,
)
from stock_trading.utils.console import BOLD_BLUE, BOLD_CYAN, BOLD_YELLOW, style


class ColoredHelpFormatter(argparse.RawDescriptionHelpFormatter):
    def start_section(self, heading: str | None) -> None:
        super().start_section(style(heading, BOLD_BLUE) if heading else heading)

    def _format_action(self, action: argparse.Action) -> str:
        rendered = super()._format_action(action)
        invocation = super()._format_action_invocation(action)
        return rendered.replace(invocation, style(invocation, BOLD_CYAN), 1)


class ChineseArgumentParser(argparse.ArgumentParser):
    def format_help(self) -> str:
        return super().format_help().replace(
            "usage:", style("用法:", BOLD_YELLOW), 1
        )

    def format_usage(self) -> str:
        return super().format_usage().replace(
            "usage:", style("用法:", BOLD_YELLOW), 1
        )

    def error(self, message: str) -> None:
        translations = {
            "one of the arguments --buy --sell is required": "必须指定 --buy 或 --sell",
            "one of the arguments --pull --push is required": "必须指定 --pull 或 --push",
            "argument --sell: not allowed with argument --buy": "--buy 与 --sell 不能同时使用",
            "argument --push: not allowed with argument --pull": "--pull 与 --push 不能同时使用",
        }
        if message.startswith("unrecognized arguments:"):
            message = "不支持的参数：" + message.removeprefix(
                "unrecognized arguments:"
            ).strip()
        else:
            message = translations.get(message, message)
        self.print_usage()
        self.exit(2, f"{self.prog}: 参数错误：{message}\n")


def _section(title: str, lines: tuple[str, ...], prefix: str = "") -> str:
    return "\n".join(
        (style(title, BOLD_BLUE), *(f"  {prefix}{line}" for line in lines))
    )


def _parser(
    prog: str,
    description: str,
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> ChineseArgumentParser:
    parser = ChineseArgumentParser(
        prog=prog,
        description=description,
        epilog="\n\n".join(
            (
                _section("业务说明", business),
                _section("示例", examples, "  "),
                _section("业务边界", boundaries, "· "),
            )
        ),
        formatter_class=ColoredHelpFormatter,
    )
    parser._optionals.title = "参数"
    return parser


def _run(
    parser: ChineseArgumentParser,
    action: Callable[[argparse.Namespace], None],
) -> None:
    arguments = parser.parse_args()
    try:
        action(arguments)
    except CliParameterError as error:
        parser.error(str(error))


def collect_main() -> None:
    parser = _parser(
        "stock-collect",
        "依次采集基础、复权、日线、5分钟和1分钟行情。",
        (
            "固定按 basic、factor、daily、minute5、minute 顺序执行。",
            "已有完整数据自动跳过，单项失败不阻断后续采集。",
        ),
        (
            "stock-collect",
            "stock-collect --date 2026-11-07",
            "stock-collect --codes 600000,000001",
        ),
        ("不准备 Qlib、不训练模型、不生成排名。",),
    )
    parser.add_argument("--date", help="采集日期或日期范围，默认当天。")
    parser.add_argument("--codes", help="股票代码，逗号分隔，默认全市场。")
    _run(parser, lambda args: collect_command(args.date, args.codes))


def prepare_main() -> None:
    parser = _parser(
        "stock-prepare",
        "从现有原始数据增量、幂等准备 Qlib。",
        ("只读取本地 Parquet。", "使用 --force 才全量重建。"),
        (
            "stock-prepare",
            "stock-prepare --model v1.2",
            "stock-prepare --force",
        ),
        ("数据不完整时终止。", "不下载数据、不训练模型。"),
    )
    parser.add_argument("--date", help="Qlib 日期或日期范围。")
    parser.add_argument("--force", action="store_true", help="全量重建 Qlib。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _run(
        parser,
        lambda args: prepare_command(args.date, args.force, args.model),
    )


def train_main() -> None:
    parser = _parser(
        "stock-train",
        "使用现有 Qlib 数据训练、评估并回测模型。",
        ("已有完整制品时直接复用。", "使用 --force 才重新训练。"),
        (
            "stock-train --model v2.1",
            "stock-train --force --split 6:3:1",
        ),
        ("默认切分比例为 70:15:15。", "失败或中断不会覆盖当前模型。"),
    )
    parser.add_argument("--split", help="训练、验证、测试比例，例如 6:3:1。")
    parser.add_argument("--force", action="store_true", help="强制重新训练。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _run(parser, lambda args: train_command(args.split, args.force, args.model))


def rank_main() -> None:
    parser = _parser(
        "stock-rank",
        "生成指定交易日的完整横截面排名。",
        ("读取已经准备好的 Qlib 和模型制品。",),
        (
            "stock-rank --model v2.1",
            "stock-rank --model v1 --date 2026-11-07",
        ),
        ("只生成排名，不提交交易委托。",),
    )
    parser.add_argument("--date", help="排名业务日期，默认当天。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _run(parser, lambda args: rank_command(args.date, args.model))


def settle_main() -> None:
    parser = _parser(
        "stock-settle",
        "在独立下午窗口执行买入或卖出。",
        (
            "13:00-14:00 卖出，14:00-15:00 买入。",
            "从排名候选依次检查并尝试交易。",
        ),
        (
            "stock-settle --sell --model v1.2",
            "stock-settle --buy --model v2.1",
        ),
        ("必须且只能指定一个方向。", "最终合法性和成交状态由券商判断。"),
    )
    direction = parser.add_mutually_exclusive_group(required=True)
    direction.add_argument("--buy", action="store_true", help="只执行买入。")
    direction.add_argument("--sell", action="store_true", help="只执行卖出。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _run(
        parser,
        lambda args: settle_command("B" if args.buy else "S", args.model),
    )


def order_main() -> None:
    parser = _parser(
        "stock-order",
        "同步券商账户快照和历史成交订单。",
        ("同步账户快照、历史成交并匹配交易闭环。",),
        ("stock-order", "stock-order --date 2026-11-07"),
        ("不主动提交新的买卖委托。", "未卖出的买入继续保留。"),
    )
    parser.add_argument("--date", help="业务日期或日期范围，默认当天。")
    _run(parser, lambda args: order_command(args.date))


def review_main() -> None:
    parser = _parser(
        "stock-review",
        "汇总已实现收益并判断模型是否需要重训。",
        ("只计算已完成买卖闭环的真实收益。",),
        (
            "stock-review --model v2.1",
            "stock-review --date 2026-11-07",
        ),
        ("未闭环交易、浮动盈亏和入金出金不参与评估。",),
    )
    parser.add_argument("--date", help="复盘业务日期，默认当天。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _run(parser, lambda args: review_command(args.date, args.model))


def clean_main() -> None:
    parser = _parser(
        "stock-clean",
        "清理超过保留期的数据和已终结业务状态。",
        ("清理过期分钟数据、历史任务和已终结状态。",),
        ("stock-clean", "stock-clean --date 2026-11-07"),
        ("活动委托、当前持仓和模型版本不会按普通历史记录清理。",),
    )
    parser.add_argument("--date", help="清理基准日期，默认当天。")
    _run(parser, lambda args: clean_command(args.date))


def sync_main() -> None:
    parser = _parser(
        "stock-sync",
        "在本地与服务器之间依次同步全部运行数据。",
        (
            "拉取包含 market、qlib、model、state。",
            "推送默认不含 state，使用 --force 才覆盖服务器状态库。",
            "单项失败不阻断后续范围，未变化数据自动跳过。",
        ),
        (
            "stock-sync --pull",
            "stock-sync --push",
            "stock-sync --push --force",
        ),
        ("必须且只能指定一个方向。", "环境文件、Token 和调度锁不会同步。"),
    )
    direction = parser.add_mutually_exclusive_group(required=True)
    direction.add_argument("--pull", action="store_true", help="拉取全部数据。")
    direction.add_argument("--push", action="store_true", help="推送运行数据。")
    parser.add_argument(
        "--force",
        action="store_true",
        help="推送时同时覆盖服务器 state。",
    )
    _run(
        parser,
        lambda args: sync_command(args.pull, args.push, args.force),
    )


# AI_GENERATE_END --
