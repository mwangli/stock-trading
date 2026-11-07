# AI_GENERATE_START------------------ --------------------------------
from __future__ import annotations

import argparse
from collections.abc import Callable

import shtab

from stock_trading.cli import (
    CliParameterError,
    clean_command,
    collect_command,
    model_command,
    order_command,
    prepare_command,
    rank_command,
    report_command,
    review_command,
    sync_command,
    trading_command,
)
from stock_trading.utils.console import BOLD_BLUE, style
from stock_trading.utils.completion import install_completion


class InstallCompletionAction(argparse.Action):
    def __init__(self, option_strings, dest, **kwargs):
        super().__init__(option_strings, dest, nargs=0, **kwargs)

    def __call__(self, parser, namespace, values, option_string=None):
        print(install_completion(parser))
        parser.exit(0)


class ChineseArgumentParser(argparse.ArgumentParser):
    def format_help(self) -> str:
        return super().format_help().replace("usage:", "用法:", 1)

    def format_usage(self) -> str:
        return super().format_usage().replace("usage:", "用法:", 1)

    def error(self, message: str) -> None:
        translations = {
            "one of the arguments --buy --sell is required": "必须指定 --buy 或 --sell",
            "one of the arguments --pull --push is required": "必须指定 --pull 或 --push",
            "argument --sell: not allowed with argument --buy": "--buy 与 --sell 不能同时使用",
            "argument --push: not allowed with argument --pull": "--pull 与 --push 不能同时使用",
            "the following arguments are required: COMMAND": "必须指定命令",
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


def _command_parser(
    subparsers: argparse._SubParsersAction,
    name: str,
    description: str,
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> ChineseArgumentParser:
    parser = subparsers.add_parser(
        name,
        help=description,
        description=description,
        epilog="\n\n".join(
            (
                _section("业务说明", business),
                _section("示例", examples, "  "),
                _section("业务边界", boundaries, "· "),
            )
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        add_help=False,
    )
    parser.add_argument("-h", "--help", action="help", help="显示帮助信息并退出。")
    parser._optionals.title = "参数"
    return parser


def _set_action(
    parser: argparse.ArgumentParser,
    action: Callable[[argparse.Namespace], None],
) -> None:
    parser.set_defaults(_action=action, _command_parser=parser)


def _add_collect(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "collect",
        "依次采集基础、复权、日线、5分钟和1分钟行情。",
        (
            "固定按 basic、factor、daily、minute5、minute 顺序执行。",
            "已有完整数据自动跳过，单项失败不阻断后续采集。",
        ),
        (
            "stock-trading collect",
            "stock-trading collect --date 2026-11-07",
            "stock-trading collect --codes 600000,000001",
        ),
        ("不准备 Qlib、不训练模型、不生成排名。",),
    )
    parser.add_argument("--date", help="采集日期或日期范围，默认当天。")
    parser.add_argument("--codes", help="股票代码，逗号分隔，默认全市场。")
    _set_action(parser, lambda a: collect_command(a.date, a.codes))


def _add_prepare(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "prepare",
        "从现有原始数据增量、幂等准备 Qlib。",
        ("只读取本地 Parquet。", "使用 --force 才全量重建。"),
        (
            "stock-trading prepare",
            "stock-trading prepare --model v1.2",
            "stock-trading prepare --force",
        ),
        ("数据不完整时终止。", "不下载数据、不训练模型。"),
    )
    parser.add_argument("--date", help="Qlib 日期或日期范围。")
    parser.add_argument("--force", action="store_true", help="全量重建 Qlib。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _set_action(parser, lambda a: prepare_command(a.date, a.force, a.model))


def _add_train(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "train",
        "使用现有 Qlib 数据训练、评估并回测模型。",
        ("已有完整制品时直接复用。", "使用 --force 才重新训练。"),
        (
            "stock-trading train --model v2.1",
            "stock-trading train --force --split 6:3:1",
        ),
        ("默认切分比例为 70:15:15。", "失败或中断不会覆盖当前模型。"),
    )
    parser.add_argument("--split", help="训练、验证、测试比例，例如 6:3:1。")
    parser.add_argument("--force", action="store_true", help="强制重新训练。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _set_action(parser, lambda a: model_command(a.split, a.force, a.model))


def _add_rank(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "rank",
        "生成指定交易日的完整横截面排名。",
        ("读取已经准备好的 Qlib 和模型制品。",),
        (
            "stock-trading rank --model v2.1",
            "stock-trading rank --model v1 --date 2026-11-07",
        ),
        ("只生成排名，不提交交易委托。",),
    )
    parser.add_argument("--date", help="排名业务日期，默认当天。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _set_action(parser, lambda a: rank_command(a.date, a.model))


def _add_trade(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "trade",
        "在独立下午窗口执行买入或卖出。",
        (
            "13:00-14:00 卖出，14:00-15:00 买入。",
            "从排名候选依次检查并尝试交易。",
        ),
        (
            "stock-trading trade --sell --model v1.2",
            "stock-trading trade --buy --model v2.1",
        ),
        ("必须且只能指定一个方向。", "最终合法性和成交状态由券商判断。"),
    )
    direction = parser.add_mutually_exclusive_group(required=True)
    direction.add_argument("--buy", action="store_true", help="只执行买入。")
    direction.add_argument("--sell", action="store_true", help="只执行卖出。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _set_action(
        parser,
        lambda a: trading_command("B" if a.buy else "S", a.model),
    )


def _add_order(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "order",
        "同步券商账户快照和历史成交订单。",
        ("同步账户快照、历史成交并匹配交易闭环。",),
        ("stock-trading order", "stock-trading order --date 2026-11-07"),
        ("不主动提交新的买卖委托。", "未卖出的买入继续保留。"),
    )
    parser.add_argument("--date", help="业务日期或日期范围，默认当天。")
    _set_action(parser, lambda a: order_command(a.date))


def _add_review(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "review",
        "汇总已实现收益并判断模型是否需要重训。",
        ("只计算已完成买卖闭环的真实收益。",),
        (
            "stock-trading review --model v2.1",
            "stock-trading review --date 2026-11-07",
        ),
        ("未闭环交易、浮动盈亏和入金出金不参与评估。",),
    )
    parser.add_argument("--date", help="复盘业务日期，默认当天。")
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")
    _set_action(parser, lambda a: review_command(a.date, a.model))


def _add_clean(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "clean",
        "清理超过保留期的数据和已终结业务状态。",
        ("清理过期分钟数据、历史任务和已终结状态。",),
        ("stock-trading clean", "stock-trading clean --date 2026-11-07"),
        ("活动委托、当前持仓和模型版本不会按普通历史记录清理。",),
    )
    parser.add_argument("--date", help="清理基准日期，默认当天。")
    _set_action(parser, lambda a: clean_command(a.date))


def _add_sync(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "sync",
        "在本地与服务器之间依次同步全部运行数据。",
        (
            "拉取包含 market、qlib、model、state。",
            "推送默认不含 state，使用 --force 才覆盖服务器状态库。",
            "单项失败不阻断后续范围，未变化数据自动跳过。",
        ),
        (
            "stock-trading sync --pull",
            "stock-trading sync --push",
            "stock-trading sync --push --force",
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
    _set_action(parser, lambda a: sync_command(a.pull, a.push, a.force))


def _add_report(subparsers: argparse._SubParsersAction) -> None:
    parser = _command_parser(
        subparsers,
        "report",
        "汇总数据、模型和最近10条已闭环交易。",
        (
            "只读取本地摘要、模型制品和 SQLite。",
            "固定输出数据状态、模型状态和闭环交易三个部分。",
        ),
        (
            "stock-trading report",
            "stock-trading report --model v2.1",
            "stock-trading report --date 2026-11-01:2026-11-07",
        ),
        ("不采集、不训练、不交易、不清理、不执行文件同步。",),
    )
    parser.add_argument("--model", help="只筛选指定模型。")
    parser.add_argument("--date", help="闭环交易日期或日期范围。")
    _set_action(parser, lambda a: report_command(a.model, a.date))


def build_parser() -> ChineseArgumentParser:
    parser = ChineseArgumentParser(
        prog="stock-trading",
        description="A股 T+1 数据、模型、交易与维护命令。",
        add_help=False,
    )
    parser.add_argument("-h", "--help", action="help", help="显示帮助信息并退出。")
    shtab.add_argument_to(
        parser,
        "--print-completion",
        help="输出指定 shell 的自动补全脚本。",
    )
    parser.add_argument(
        "--install-completion",
        action=InstallCompletionAction,
        help="安装当前系统的自动补全，重新打开终端后生效。",
    )
    parser._optionals.title = "参数"
    subparsers = parser.add_subparsers(
        dest="command",
        title="命令",
        metavar="COMMAND",
        required=True,
        parser_class=ChineseArgumentParser,
    )
    for add_command in (
        _add_collect,
        _add_prepare,
        _add_train,
        _add_rank,
        _add_trade,
        _add_order,
        _add_review,
        _add_clean,
        _add_sync,
        _add_report,
    ):
        add_command(subparsers)
    return parser


def main() -> None:
    parser = build_parser()
    arguments = parser.parse_args()
    try:
        arguments._action(arguments)
    except CliParameterError as error:
        arguments._command_parser.error(str(error))


# AI_GENERATE_END------------------ --------------------------------
