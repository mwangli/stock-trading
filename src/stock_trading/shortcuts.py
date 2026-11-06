# AI_GENERATE_START--- --------------------------------
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
            ("stock-collect", "按类型采集基础、日线、复权和分钟行情。"),
            ("stock-prepare", "从现有原始数据增量、幂等准备 Qlib。"),
            ("stock-train", "使用现有 Qlib 数据训练、评估并回测模型。"),
        ),
    ),
    (
        "排名与交易",
        (
            ("stock-rank", "生成指定交易日的完整横截面排名。"),
            ("stock-trading", "在独立下午窗口执行买入或卖出。"),
        ),
    ),
    (
        "订单与复盘",
        (
            ("stock-order", "同步券商账户快照或历史成交订单。"),
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
            "已有同一模型 ID 的完整制品时直接复用并跳过训练。",
            "训练、验证和测试数据按交易日顺序确定性切分。",
            "只有显式指定 --force 时才重新训练。",
        ),
        (
            "stock-train --model v2.1",
            "stock-train --model LightGBM --force --split 6:3:1",
        ),
        (
            "默认训练、验证和测试比例为 70:15:15。",
            "训练失败或中断不会覆盖当前可用模型。",
        ),
    )


def collect() -> None:
    _run(
        "stock-collect",
        collect_command,
        "按类型采集基础、日线、复权和分钟行情。",
        (
            "--type 支持 basic、daily、factor、minute5、minute 逗号多选。",
            "--codes 默认全市场；--date 默认当天，也支持日期范围。",
            "basic、daily、factor 使用 TuShare，minute5 使用 BaoStock，minute 使用券商接口。",
        ),
        (
            "stock-collect --type basic",
            "stock-collect --type daily,factor --date 2026-11-03",
            "stock-collect --type minute5 --date 2026-11-03",
            "stock-collect --type minute --date 2026-11-03 --codes 600000,000001",
            "stock-collect --type daily,factor --date 2023-11-02:2026-11-02",
        ),
        (
            "命令不准备 Qlib、不执行模型训练或排名。",
            "各类型独立执行；basic 始终维护今日全市场快照。",
        ),
    )


def prepare() -> None:
    _run(
        "stock-prepare",
        prepare_command,
        "从现有原始数据增量、幂等准备 Qlib。",
        (
            "只读取本地 Parquet，不连接行情源或下载数据。",
            "新增交易日时增量追加；范围不变时刷新最新特征和已成熟标签。",
            "使用 --force 时才忽略现有 Qlib 并全量重建。",
        ),
        (
            "stock-prepare",
            "stock-prepare --model v1.2",
            "stock-prepare --date 2023-11-02:2026-11-02",
            "stock-prepare --force",
        ),
        (
            "日线、复权因子或5分钟聚合不完整时终止。",
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
        (
            "stock-rank --model v2.1",
            "stock-rank --model v1 --date 2026-11-02",
        ),
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
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
) -> None:
    if buy == sell:
        raise typer.BadParameter("必须且只能指定 --sell 或 --buy")
    trading_command("B" if buy else "S", model_value)


def trading() -> None:
    _run(
        "stock-trading",
        _trading_command,
        "在独立下午窗口执行买入或卖出。",
        (
            "读取当天上午完整横截面排名，Rank10以内的持仓继续保留。",
            "13:00-14:00 执行卖出，14:00-15:00 执行买入。",
            "从Top5依次检查允许前缀、已确认权限和实时价格范围。",
        ),
        (
            "stock-trading --sell --model v1.2",
            "stock-trading --buy --model v2.1",
        ),
        (
            "必须且只能指定一个方向，两个窗口由调度分别调用。",
            "每个交易阶段开始前都会重新同步最新账户快照。",
            "最终合法性以及成交状态由券商判断。",
        ),
    )


def order() -> None:
    _run(
        "stock-order",
        order_command,
        "同步券商账户快照，或按原语义同步历史成交订单。",
        (
            "--account 查询券商账户资金并覆盖保存最新快照。",
            "未指定 --account 时，按月同步指定日期范围内的历史成交订单。",
            "根据成交数量、成本和费用匹配已经完成的买卖闭环。",
        ),
        (
            "stock-order --account",
            "stock-order",
            "stock-order --date 2026-11-02",
            "stock-order --date 2026-10-01:2026-11-02",
        ),
        (
            "--account 只保存最新一条账户资金快照，不保留历史序列。",
            "默认只同步历史订单和结算事实，不主动提交新的买卖委托。",
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
        ("stock-review --model v2.1", "stock-review --model v1 --date 2026-11-02"),
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
            "清理分钟原始数据、历史任务、快照、预测、绩效和已终结委托等。",
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
            "market、qlib 和 model 支持拉取或推送。",
            "market 对应 stock-collect 数据，qlib 对应 stock-prepare 数据。",
            "state 只允许从服务器拉取，并直接以服务器 SQLite 覆盖本地状态库。",
            "服务器数据目录固定为 $HOME/stock-trading/data，本地为项目根目录 data。",
        ),
        (
            "stock-sync --pull all",
            "stock-sync --push market,qlib,model",
            "stock-sync --pull state",
        ),
        (
            "同一数据类型不能在一次命令中同时拉取和推送。",
            "环境文件、券商 Token 和调度锁不会同步。",
            "服务器连接只读取 SERVER_IP、SERVER_USER 和 SERVER_PASS。",
        ),
    )
# AI_GENERATE_END--- --------------------------------
