# AI_GENERATE_START ----------------
from __future__ import annotations

from collections.abc import Callable

import typer

from stock_trading.cli import (
    broker_command,
    clean_command,
    collect_command,
    evaluate_command,
    performance_command,
    prepare_command,
    rank_command,
    trading_command,
    train_command,
)
from stock_trading.help_renderer import build_help_command, show_command_groups


HELP_CONTEXT = {"help_option_names": ["-h", "--help"]}
COMMAND_GROUPS = (
    (
        "数据准备",
        (
            ("stock-collect", "采集股票基础信息、日线、复权因子和 5 分钟行情。"),
            ("stock-prepare", "从现有原始数据增量、幂等准备 Qlib。"),
        ),
    ),
    (
        "模型研究",
        (
            ("stock-train", "使用现有 Qlib 数据训练、评估并回测模型。"),
            ("stock-rank", "生成指定交易日的完整横截面排名。"),
        ),
    ),
    (
        "交易执行",
        (
            ("stock-sell", "上午卖出跌出保留排名的可卖持仓。"),
            ("stock-buy", "下午无持仓时从Top5筛选买入。"),
        ),
    ),
    (
        "结算与评估",
        (
            ("stock-broker", "同步券商事实并增量结算交易闭环。"),
            ("stock-perform", "根据完整买卖闭环生成已实现绩效。"),
            ("stock-evaluate", "根据已实现绩效评估模型并生成重训请求。"),
        ),
    ),
    (
        "维护",
        (("stock-clean", "清理过期数据、历史事实和业务状态。"),),
    ),
    (
        "帮助",
        (("stock-help", "列出全部公开命令及其用途。"),),
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
        ("按数据准备、模型研究、交易执行、结算评估和维护阶段分组。",),
        ("stock-help", "stock-broker -h"),
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


def _sell_command() -> None:
    trading_command("S")


def sell() -> None:
    _run(
        "stock-sell",
        _sell_command,
        "在上午窗口卖出跌出保留排名的可卖持仓。",
        (
            "读取最近完整横截面排名，Rank15以内的持仓继续保留。",
            "每次尝试前重新读取券商实时持仓和报价。",
            "部分成交时先确认撤单终态，再按最新事实处理剩余数量。",
        ),
        ("stock-sell",),
        (
            "不接受日期参数，只处理上海时区当天交易。",
            "委托是否合法以及最终状态以券商事实为准。",
        ),
    )


def _buy_command() -> None:
    trading_command("B")


def buy() -> None:
    _run(
        "stock-buy",
        _buy_command,
        "在下午窗口无持仓时从Top5依次筛选买入。",
        (
            "已有持仓时跳过买入，避免重复交易同一股票。",
            "从Top5依次检查允许前缀、已确认权限和实时价格范围。",
            "固定首个通过候选，并按实时可用资金计算整手数量。",
        ),
        ("stock-buy",),
        (
            "不接受日期参数，只处理上海时区当天交易。",
            "最终合法性以及成交状态由券商判断。",
        ),
    )


def broker() -> None:
    _run(
        "stock-broker",
        broker_command,
        "同步券商事实，并增量结算完整买卖交易闭环。",
        (
            "同步委托、成交、持仓等事实，可处理当天、单日或日期范围。",
            "根据成交数量、成本和费用匹配已经完成的买卖闭环。",
        ),
        (
            "stock-broker",
            "stock-broker --date 2026-11-02",
            "stock-broker --date 2026-10-01:2026-11-02",
        ),
        (
            "只同步和结算事实，不主动提交新的买卖委托。",
            "未卖出的买入继续保留为待闭环交易。",
        ),
    )


def perform() -> None:
    _run(
        "stock-perform",
        performance_command,
        "根据完整买卖闭环生成指定交易日的已实现绩效。",
        (
            "只计算已匹配数量对应的买入成本、卖出收入和费用税费。",
            "账户权益、浮动盈亏及入金出金不属于策略收益。",
        ),
        ("stock-perform", "stock-perform --date 2026-11-02"),
        (
            "应在同一业务日 stock-broker 成功后执行。",
            "部分卖出只确认已经闭环数量的收益。",
        ),
    )


def evaluate() -> None:
    _run(
        "stock-evaluate",
        evaluate_command,
        "根据已实现绩效评估模型并生成重训请求。",
        (
            "按卖出月份汇总已经完成的真实交易闭环。",
            "固定买卖费用只展示，不参与模型能力评价。",
            "评估结果和重训请求写入 SQLite，不在本命令内直接训练。",
        ),
        ("stock-evaluate", "stock-evaluate --date 2026-11-02"),
        (
            "应在同一业务日 stock-perform 成功后执行。",
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
# AI_GENERATE_END ----------------
