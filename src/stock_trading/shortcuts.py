# AI_GENERATE_START -------------------------
from __future__ import annotations

from collections.abc import Callable
from typing import Annotated

import typer

from stock_trading.cli import (
    clean_command,
    collect_command,
    order_command,
    prepare_command,
    rank_command,
    review_command,
    sync_command,
    trading_command,
    train_command,
)
from stock_trading.utils.help_renderer import build_help_command, show_command_groups


HELP_CONTEXT = {"help_option_names": ["-h", "--help"]}
COMMAND_GROUPS = (
    (
        "数据与模型",
        (
            ("stock-collect", "采集股票基础信息、日线、复权因子和 5 分钟行情。"),
            ("stock-prepare", "从现有原始数据增量、幂等准备 Qlib。"),
            ("stock-train", "使用现有 Qlib 数据训练、评估并回测模型。"),
        ),
    ),
    (
        "排名与交易",
        (
            ("stock-rank", "生成指定交易日的完整横截面排名。"),
            ("stock-trading", "卖出后立即尝试买入，也可单独执行买入或卖出。"),
        ),
    ),
    (
        "订单与复盘",
        (
            ("stock-order", "同步券商历史订单并增量结算交易闭环。"),
            ("stock-review", "汇总已实现收益并判断模型是否需要重训。"),
        ),
    ),
    (
        "维护与帮助",
        (
            ("stock-clean", "清理过期数据、历史事实和业务状态。"),
            ("stock-sync", "在本地和服务器之间同步行情、模型和状态。"),
            ("stock-help", "列出全部公开命令及其用途。"),
        ),
    ),
)


def _run(
    program: str,
    command: Callable[..., None],
    description: str,
    business: tuple[str, ...],
    examples: tuple[str, ...],
    boundaries: tuple[str, ...],
) -> None:
    command_app = typer.Typer(
        add_completion=False,
        rich_markup_mode="rich",
        pretty_exceptions_enable=False,
    )
    command_app.command(
        help=description,
        cls=build_help_command(business, examples, boundaries),
        context_settings=HELP_CONTEXT,
    )(command)
    command_app(prog_name=program)


def _show_commands() -> None:
    show_command_groups(COMMAND_GROUPS)


def show_help() -> None:
    _run(
        "stock-help",
        _show_commands,
        "列出项目提供的全部独立命令及其用途。",
        ("按数据与模型、排名与交易、订单复盘和维护帮助阶段分组。",),
        ("stock-help", "stock-order -h"),
        ("只输出帮助信息，不执行任何业务操作。",),
    )


def train() -> None:
    _run(
        "stock-train",
        train_command,
        "使用现有 Qlib 数据训练、评估并回测模型。",
        (
            "已有完整模型制品时直接启用并跳过训练。",
            "训练、验证和测试数据按交易日顺序确定性切分。",
            "只有显式指定 --force 时才重新训练。",
        ),
        ("stock-train", "stock-train --force --split 6:3:1"),
        (
            "默认训练、验证和测试比例为 70:15:15。",
            "训练失败或中断不会覆盖当前可用模型。",
        ),
    )


def collect() -> None:
    _run(
        "stock-collect",
        collect_command,
        "采集股票基础信息、日线、复权因子和 5 分钟行情。",
        (
            "先检查最近交易日的股票基础信息快照，不存在时自动下载。",
            "无参数时只扫描最近交易日；指定 --date 时只扫描该历史范围。",
            "根据缺失清单只下载缺少的原始数据，下载后重新检查。",
        ),
        (
            "stock-collect",
            "stock-collect --date 2023-11-02:2026-11-02",
        ),
        (
            "命令不准备 Qlib、不执行模型训练或排名。",
            "单类数据失败不阻止同一股票的其他类别继续处理。",
        ),
    )


def prepare() -> None:
    _run(
        "stock-prepare",
        prepare_command,
        "从现有原始数据增量、幂等准备 Qlib。",
        (
            "只读取本地 Parquet，不连接 BaoStock 或下载数据。",
            "Qlib 已覆盖目标范围时跳过，只缺少新交易日时增量追加。",
            "使用 --force 时才忽略现有 Qlib 并全量重建。",
        ),
        (
            "stock-prepare",
            "stock-prepare --date 2023-11-02:2026-11-02",
            "stock-prepare --force",
        ),
        (
            "原始数据不完整时终止并提示对应 stock-collect 命令。",
            "命令不下载数据、不训练模型、不生成排名。",
        ),
    )


def rank() -> None:
    _run(
        "stock-rank",
        rank_command,
        "生成指定交易日的完整横截面排名。",
        (
            "校验同一业务日的 stock-collect 已成功且 Qlib 已由 stock-prepare 覆盖。",
            "完整排名供交易阶段结合资金、权限和实时价格继续筛选。",
        ),
        ("stock-rank", "stock-rank --date 2026-11-02"),
        (
            "只生成排名，不提交交易委托。",
            "需要已有可用模型和对应日期的 Qlib 数据。",
        ),
    )


def _trading_command(
    buy: Annotated[
        bool,
        typer.Option("--buy", help="只执行买入。"),
    ] = False,
    sell: Annotated[
        bool,
        typer.Option("--sell", help="只执行卖出。"),
    ] = False,
) -> None:
    if not buy and not sell:
        sell = True
        buy = True
    if sell:
        trading_command("S")
    if buy:
        trading_command("B")


def trading() -> None:
    _run(
        "stock-trading",
        _trading_command,
        "卖出后立即尝试买入，也可通过参数单独执行买入或卖出。",
        (
            "读取最近完整横截面排名，Rank15以内的持仓继续保留。",
            "卖出阶段完成后立即重新读取持仓；没有持仓时进入买入流程。",
            "从Top5依次检查允许前缀、已确认权限和实时价格范围。",
        ),
        (
            "stock-trading",
            "stock-trading --sell",
            "stock-trading --buy",
            "stock-trading --sell --buy",
        ),
        (
            "无参数或同时指定 --sell 和 --buy 时，固定先卖后买。",
            "每个交易阶段开始前都会重新同步最新账户快照。",
            "最终合法性以及成交状态由券商判断。",
        ),
    )


def order() -> None:
    _run(
        "stock-order",
        order_command,
        "同步券商历史订单，并增量结算完整买卖交易闭环。",
        (
            "按月同步指定日期范围内的历史成交订单。",
            "根据成交数量、成本和费用匹配已经完成的买卖闭环。",
        ),
        (
            "stock-order",
            "stock-order --date 2026-11-02",
            "stock-order --date 2026-10-01:2026-11-02",
        ),
        (
            "只同步历史订单和结算事实，不主动提交新的买卖委托。",
            "未卖出的买入继续保留为待闭环交易。",
        ),
    )


def review() -> None:
    _run(
        "stock-review",
        review_command,
        "汇总指定交易日的已实现收益，并判断模型是否需要重训。",
        (
            "只计算已匹配数量对应的买入成本、卖出收入和费用税费。",
            "按卖出月份汇总已经完成的真实交易闭环。",
            "根据月度毛收益、盈利月份比例和毛回撤形成模型判断。",
        ),
        ("stock-review", "stock-review --date 2026-11-02"),
        (
            "应在同一业务日 stock-order 成功后执行。",
            "账户权益、浮动盈亏及入金出金不属于策略收益。",
            "未闭环交易和浮动盈亏不参与评估。",
        ),
    )


def clean() -> None:
    _run(
        "stock-clean",
        clean_command,
        "清理超过保留期的数据和已终结业务状态。",
        (
            "清理分钟数据、历史任务、快照、预测、绩效和已终结委托等。",
            "完成后执行 SQLite WAL 截断、VACUUM 和优化。",
        ),
        ("stock-clean", "stock-clean --date 2026-11-02"),
        (
            "活动委托、当前持仓和模型版本等关键状态不会按普通历史记录清理。",
            "建议由每周维护任务调用，而不是每次初始化执行。",
        ),
    )


def sync() -> None:
    _run(
        "stock-sync",
        sync_command,
        "使用 SSH/SCP 在本地和服务器之间同步运行数据。",
        (
            "market 和 model 支持拉取或推送。",
            "state 只允许从服务器拉取，并通过 SQLite 一致性快照替换本地状态库。",
            "服务器数据目录固定为 $HOME/stock-trading/data，本地为项目根目录 data。",
        ),
        (
            "stock-sync --pull all",
            "stock-sync --push market,model",
            "stock-sync --pull state",
        ),
        (
            "同一数据类型不能在一次命令中同时拉取和推送。",
            "环境文件、券商 Token 和调度锁不会同步。",
            "服务器连接只读取 SERVER_IP、SERVER_USER 和 SERVER_PASS。",
        ),
    )
# AI_GENERATE_END -------------------------
