# AI_GENERATE_START------------ ---------
from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
from collections.abc import Callable
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from time import perf_counter
from typing import TextIO, TypeVar

from stock_trading.config import AppConfig, load_project_environment
from stock_trading.research.model_config import ModelSpec, resolve_model_spec
from stock_trading.utils.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.utils.time_utils import shanghai_today


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


LOGGER = logging.getLogger(__name__)
T = TypeVar("T")


class CliParameterError(ValueError):
    pass


def _execute(
    command: str,
    parameters: dict[str, object],
    operation: Callable[[AppConfig], T],
    result_summary: Callable[[T], str] | None = None,
) -> T:
    project_dir = load_project_environment()
    configure_logging()
    started_at = perf_counter()
    config: AppConfig | None = None
    with log_context(
        task=command,
        step="0/1",
        progress="-/-",
        target=command,
    ):
        try:
            config = AppConfig.from_env(project_dir=project_dir)
            LOGGER.info(
                "业务命令开始：参数=%s",
                json.dumps(parameters, ensure_ascii=False, default=str),
            )
            result = operation(config)
        except CliParameterError as error:
            LOGGER.error(
                "业务命令参数错误：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            echo(f"参数错误：{error}", color=BOLD_RED, error=True)
            raise SystemExit(2) from None
        except Exception as error:
            LOGGER.error(
                "业务命令失败：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            from stock_trading.utils.notifications import push_system_failure

            push_system_failure(config, command, error)
            echo(f"执行失败：{error}", color=BOLD_RED, error=True)
            raise SystemExit(1) from None
        except KeyboardInterrupt:
            LOGGER.warning(
                "收到 Ctrl+C，业务命令已终止：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
            echo("命令已终止。", color=BOLD_RED, error=True)
            raise SystemExit(130) from None
        set_log_context(step="1/1", progress="-/-", target=command)
        echo(
            result_summary(result)
            if result_summary is not None
            else f"{command} 执行完成。"
        )
        LOGGER.info("业务命令完成：总耗时=%.2f 秒", perf_counter() - started_at)
        return result


def order_command(
    date_value: str | None = None,
) -> None:
    from stock_trading.workflows import sync_order_workflow

    today = shanghai_today()
    start_date, end_date = _parse_date_expression(date_value, today, today)
    _execute(
        "order",
        {"date": date_value},
        lambda config: sync_order_workflow(config, start_date, end_date),
        result_summary=_summarize_order,
    )


def permission_command() -> None:
    from stock_trading.workflows import sync_permission_workflow

    _execute(
        "permission",
        {},
        sync_permission_workflow,
        result_summary=_summarize_permission,
    )


def collect_command(
    date_value: str | None = None,
    codes_value: str | None = None,
    model_value: str | None = None,
    collection_type: str | None = None,
) -> None:
    from stock_trading.workflows import collect_workflow

    selected_codes = _parse_stock_codes(codes_value) if codes_value else None

    def run(config: AppConfig) -> dict[str, object]:
        today = shanghai_today()
        start, end = _parse_date_expression(date_value, today, today)
        spec = _resolve_model(model_value) if model_value else None
        return collect_workflow(
            config,
            start,
            end,
            requested_codes=selected_codes,
            required_fields=spec.required_fields if spec else None,
            requested_types=(collection_type,) if collection_type else None,
        )

    result = _execute(
        "collect",
        {
            "date": date_value,
            "codes": codes_value,
            "model": model_value,
            "type": collection_type,
        },
        run,
        result_summary=_summarize_collection,
    )
    if result.get("status") == "PARTIAL":
        raise SystemExit(1)


def prepare_command(
    date_value: str | None = None,
    force: bool = False,
    model_value: str | None = None,
) -> None:
    def run(config: AppConfig) -> dict[str, object]:
        from stock_trading.research.model_adapter import create_research_model
        from stock_trading.workflows import prepare_workflow

        spec = _resolve_model(model_value)
        latest = shanghai_today()
        model = create_research_model(config, spec)
        existing = model.date_range()
        if date_value is None:
            from stock_trading.research.training import default_training_range

            default_start, latest = default_training_range(config)
            start = existing[0] if existing is not None else default_start
            end = max(latest, existing[1]) if existing is not None else latest
        else:
            start, end = _parse_date_expression(date_value, latest, latest)
            if existing is not None and not force:
                start = min(start, existing[0])
                end = max(end, existing[1])
        return prepare_workflow(config, spec, start, end, force=force)

    _execute(
        "prepare",
        {"date": date_value, "force": force, "model": model_value},
        run,
        result_summary=_summarize_preparation,
    )


def train_command(
    split: str | None = None,
    force: bool = False,
    model_value: str | None = None,
) -> None:
    from stock_trading.research.training import run_training_pipeline

    _execute(
        "train",
        {"split": split, "force": force, "model": model_value},
        lambda config: run_training_pipeline(
            config,
            _resolve_model(model_value),
            _parse_model_split(split) if split else None,
            force=force,
        ),
        result_summary=_summarize_training,
    )


def rank_command(
    prediction_date: str | None = None,
    model_value: str | None = None,
) -> None:
    from stock_trading.workflows import rank_workflow

    business_date = (
        _parse_date(prediction_date)
        if prediction_date
        else None
    )
    _execute(
        "rank",
        {"date": prediction_date, "model": model_value},
        lambda config: rank_workflow(
            config, _resolve_model(model_value), business_date
        ),
        result_summary=_summarize_rank,
    )


def settle_command(
    trade_type: str,
    model_value: str | None = None,
) -> None:
    from stock_trading.workflows import trading_workflow

    if trade_type not in {"B", "S"}:
        raise CliParameterError("交易方向只能是 B 或 S")
    side = "BUY" if trade_type == "B" else "SELL"
    business_date = shanghai_today()
    _execute(
        "trading",
        {
            "side": side,
            "businessDate": business_date.isoformat(),
            "model": model_value,
        },
        lambda config: trading_workflow(
            config, _resolve_model(model_value), business_date, side
        ),
        result_summary=_summarize_trading,
    )


def review_command(
    business_date: str | None = None,
    model_value: str | None = None,
) -> None:
    from stock_trading.workflows import review_workflow

    target_date = _business_date(business_date)
    _execute(
        "review",
        {"date": business_date, "model": model_value},
        lambda config: review_workflow(
            config, _resolve_model(model_value), target_date
        ),
        result_summary=_summarize_review,
    )


def clean_command(
    reference_date: str | None = None,
) -> None:
    from stock_trading.workflows import cleanup_workflow

    target_date = _business_date(reference_date)
    _execute(
        "clean",
        {"date": reference_date},
        lambda config: cleanup_workflow(config, target_date),
        result_summary=_summarize_cleanup,
    )


def sync_command(
    pull: bool = False,
    push: bool = False,
    force: bool = False,
    scope: str | None = None,
) -> None:
    from stock_trading.data_sync import sync_data

    if pull == push:
        raise CliParameterError("必须且只能指定 --pull 或 --push")
    if force and not push:
        raise CliParameterError("--force 仅用于 --push")
    result = _execute(
        "sync",
        {
            "pull": pull,
            "push": push,
            "force": force,
            "scope": scope,
        },
        lambda config: sync_data(config, pull=pull, push=push, force=force, scope=scope),
        result_summary=_summarize_sync,
    )
    if result.get("status") == "PARTIAL":
        raise SystemExit(1)


def _business_date(value: str | None) -> date:
    return (
        _parse_date(value)
        if value
        else shanghai_today()
    )


def _resolve_model(value: str | None) -> ModelSpec:
    try:
        return resolve_model_spec(value)
    except ValueError as error:
        raise CliParameterError(str(error)) from None


def _parse_date(value: str) -> date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise CliParameterError(
            f"日期格式错误：{value}；请使用 YYYY-MM-DD，例如 2026-10-31"
        )
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise CliParameterError(f"日期无效：{value}；请检查年月日是否存在") from None


def _parse_date_expression(
    value: str | None,
    default_start: date,
    default_end: date,
) -> tuple[date, date]:
    if value is None:
        return default_start, default_end
    normalized = value.strip()
    parts = normalized.split(":")
    example = "正确示例：--date 2025-10-10 或 --date 2025-10-10:2025-10-30"
    if len(parts) not in {1, 2} or any(not part for part in parts):
        raise CliParameterError(f"日期参数格式错误：{value}；{example}")
    try:
        start_date = _parse_date(parts[0])
        end_date = _parse_date(parts[1]) if len(parts) == 2 else start_date
    except CliParameterError as error:
        raise CliParameterError(f"{error}；{example}") from None
    if end_date < start_date:
        raise CliParameterError(
            f"日期范围错误：结束日期 {end_date} 不能早于开始日期 {start_date}；{example}"
        )
    return start_date, end_date




def _parse_stock_code(value: str) -> str:
    normalized = value.strip().lower()
    if re.fullmatch(r"\d{6}", normalized):
        if normalized.startswith("6"):
            normalized = f"sh.{normalized}"
        elif normalized.startswith(("0", "3")):
            normalized = f"sz.{normalized}"
    if not re.fullmatch(r"(?:sh|sz)\.\d{6}", normalized):
        raise CliParameterError(
            f"股票代码格式错误：{value}；示例：600000、sh.600000 或 sz.000001"
        )
    return normalized


def _parse_stock_codes(value: str) -> list[str]:
    raw_codes = [item.strip() for item in value.split(",") if item.strip()]
    if not raw_codes:
        raise CliParameterError("股票代码列表不能为空")
    return list(dict.fromkeys(_parse_stock_code(code) for code in raw_codes))


def _parse_model_split(value: str) -> tuple[Decimal, Decimal, Decimal]:
    parts = value.split(":")
    if len(parts) != 3:
        raise CliParameterError("训练比例格式错误；请使用 训练:验证:测试，例如 6:3:1")
    try:
        ratios = tuple(Decimal(part) for part in parts)
    except InvalidOperation:
        raise CliParameterError("训练比例必须是数字；例如 6:3:1") from None
    if any(not ratio.is_finite() or ratio <= 0 for ratio in ratios):
        raise CliParameterError("训练、验证和测试比例必须全部大于 0")
    return ratios[0], ratios[1], ratios[2]


def _summarize_order(result: dict[str, object]) -> str:
    account_snapshot = dict(result["accountSnapshot"])
    settlement = dict(result["settlement"])
    return (
        f"券商账户及订单同步完成：账户采集时间 "
        f"{account_snapshot['captured_at']}，日期 "
        f"{result['startDate']} 至 {result['endDate']}，"
        f"有效订单 {result['validOrders']} 条，新增 {result['insertedOrders']} 条，"
        f"更新 {result['updatedOrders']} 条，新增闭环 "
        f"{settlement.get('realizedTrades', 0)} 条，未匹配卖出 "
        f"{settlement.get('unmatchedSellQuantity', 0)} 股。"
    )


def _summarize_permission(result: dict[str, object]) -> str:
    return (
        f"交易权限同步完成：总数 {result['total']}，本次检查 "
        f"{result['checked']}，跳过 {result['skipped']}，允许 "
        f"{result['allowed']}，拒绝 {result['denied']}。"
    )


def _summarize_collection(result: dict[str, object]) -> str:
    type_names = {
        "stock": "股票清单",
        "daily": "日线数据",
        "minute5": "5 分钟数据",
        "minute1": "1 分钟数据",
    }
    details = []
    for name, value in dict(result["collection"]).items():
        item = dict(value)
        label = type_names.get(name, name)
        if item.get("status") == "FAILED":
            details.append(f"{label}失败：{item.get('error', '-')}")
        elif item.get("skipped"):
            details.append(f"{label}已有完整数据，已跳过")
        else:
            details.append(
                f"{label}采集成功，质量"
                f"{_format_quality(item.get('dataQuality'))}"
            )
    status = "部分完成" if result.get("status") == "PARTIAL" else "完成"
    return (
        f"数据采集{status}：日期 {result['startDate']} 至 {result['endDate']}；"
        + "；".join(details)
        + "。"
    )


def _summarize_preparation(result: dict[str, object]) -> str:
    qlib = dict(result["qlib"])
    return (
        f"特征数据准备完成：模型 {result['modelId']}，日期 "
        f"{result['startDate']} 至 {result['endDate']}，研究股票 "
        f"{result['researchCodes']} 只，Qlib 模式 {qlib.get('mode', '-')}，"
        f"有效股票 {qlib.get('codes', '-')} 只，质量 "
        f"{_format_quality(result.get('dataQuality'))}。"
    )


def _summarize_training(result: dict[str, object]) -> str:
    training = dict(result["training"])
    evaluation = dict(result.get("evaluation") or {})
    backtest = dict(evaluation.get("backtest") or {})
    action = "复用现有制品" if training.get("skipped") else "完成训练与回测"
    summary = (
        f"模型训练完成：模型 {result['modelId']}，{action}，制品 "
        f"{training.get('artifactPath', '-')}"
    )
    if backtest:
        stability = dict(backtest.get("monthlyReturnStability") or {})
        summary += (
            f"，回测资金 {_format_amount(backtest.get('capital'))} 元，"
            f"毛收益 {_format_amount(backtest.get('grossProfit'))} 元，"
            f"毛收益率 {_format_percent(backtest.get('grossReturn'))}，"
            f"月收益稳定性 {float(stability.get('score') or 0):.4f}，"
            f"正收益月份占比 {_format_percent(stability.get('positiveMonthRate'))}"
        )
        months = list(backtest.get("monthlyPerformance") or [])
        if months:
            summary += "\n" + "\n".join(
                f"  {month.get('month')}：毛收益 "
                f"{_format_amount(month.get('grossProfit'))} 元，毛收益率 "
                f"{_format_percent(month.get('grossReturn'))}，闭环交易 "
                f"{month.get('closedTrades', 0)} 笔"
                for month in months
                if isinstance(month, dict)
            )
    return summary + "。"


def _summarize_rank(result: dict[str, object]) -> str:
    prediction = dict(result["prediction"])
    candidates = ", ".join(
        f"{item.get('rank')}:{item.get('instrument')}"
        for item in prediction.get("topCandidates", [])
    ) or "无"
    return (
        f"横截面排名完成：模型 {result['modelId']}，日期 "
        f"{prediction.get('predictionDate', '-')}，评分 "
        f"{prediction.get('universeRows', 0)} 只，排名 "
        f"{prediction.get('rankingRows', 0)} 只，前列候选 {candidates}。"
    )


def _summarize_trading(result: dict[str, object]) -> str:
    selection = dict(result["selection"])
    mode = {
        "AFTERNOON_BUY": "下午买入",
        "AFTERNOON_SELL": "下午卖出",
        "MORNING_SELL": "上午卖出",
    }.get(str(result["mode"]), str(result["mode"]))
    return (
        f"交易窗口执行完成：日期 {result['businessDate']}，模型 "
        f"{result['modelId']}，模式 {mode}，目标 "
        f"{len(selection.get('targets', []))} 个，执行结果 "
        f"{len(result.get('executed', []))} 个。"
    )


def _summarize_review(result: dict[str, object]) -> str:
    profit = dict(result["profit"])
    decision = dict(result["decision"])
    fees = float(profit.get("buy_fees") or 0) + float(
        profit.get("sell_fees") or 0
    )
    return (
        f"交易复盘完成：日期 {result['businessDate']}，闭环交易 "
        f"{profit.get('trade_count', 0)} 条，毛损益 "
        f"{_format_amount(profit.get('gross_pnl'))} 元，费用 "
        f"{_format_amount(fees)} 元，净损益 "
        f"{_format_amount(profit.get('net_pnl'))} 元，"
        f"模型判断 {decision.get('decision', '-')}，原因 "
        f"{decision.get('reason', '-')}。"
    )


def _summarize_cleanup(result: dict[str, object]) -> str:
    market = dict(result["market"])
    state = dict(result["state"])
    storage = dict(result.get("storage") or {})
    return (
        f"数据清理完成：基准日期 {result['referenceDate']}，处理分钟股票 "
        f"{market.get('codes', 0)} 只，删除分钟原始数据 "
        f"{market.get('deletedRawRows', 0)} 行、聚合数据 "
        f"{market.get('deletedSummaryRows', 0)} 行，SQLite 删除 "
        f"{state.get('deletedRows', 0)} 条，释放空间 "
        f"{_format_bytes(storage.get('releasedBytes'))}。"
    )


def _summarize_sync(result: dict[str, object]) -> str:
    operations = list(result.get("operations", []))
    direction_names = {"pull": "拉取", "push": "推送"}
    scopes = "，".join(
        f"{direction_names.get(str(item.get('direction')), item.get('direction'))}"
        f" {item.get('scope')}={item.get('status', 'SUCCESS')}"
        for item in operations
        if isinstance(item, dict)
    ) or "无"
    status = "部分完成" if result.get("status") == "PARTIAL" else "完成"
    return f"数据同步{status}：操作 {len(operations)} 项；{scopes}。"


def _format_quality(value: object) -> str:
    if not isinstance(value, dict):
        return "无质量数据"
    if "valid" in value:
        return "有效" if value["valid"] else "无效"
    return (
        f"{value.get('usableCodes', 0)}/{value.get('expectedCodes', 0)}"
        f"({_format_percent(value.get('coverageRate'))})"
    )


def _format_amount(value: object) -> str:
    return "-" if value is None else f"{float(value):,.2f}"


def _format_percent(value: object) -> str:
    return "-" if value is None else f"{float(value) * 100:.2f}%"


def _format_bytes(value: object) -> str:
    if value is None:
        return "-"
    size = float(value)
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if size < 1024 or unit == "TB":
            return f"{size:.0f} {unit}" if unit == "B" else f"{size:.2f} {unit}"
        size /= 1024
    return "-"


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
        if message.startswith("unrecognized arguments:"):
            message = "不支持的参数：" + message.removeprefix(
                "unrecognized arguments:"
            ).strip()
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


def _add_model_argument(parser: ChineseArgumentParser) -> None:
    parser.add_argument("--model", help="模型完整 ID。")


def _dispatch_collect(args: argparse.Namespace) -> None:
    if args.type and args.model:
        raise CliParameterError("--type 与 --model 不能同时使用")
    collect_command(args.date, args.codes, args.model, args.type)


def _add_action_parser(
    subparsers: argparse._SubParsersAction,
    name: str,
    help_text: str,
) -> ChineseArgumentParser:
    return subparsers.add_parser(
        name,
        help=help_text,
        description=help_text,
        formatter_class=ColoredHelpFormatter,
    )


def main() -> None:
    parser = _parser(
        "stock",
        "A 股 T+1 数据、研究、交易和运行维护命令。",
        (
            "所有业务使用一级动作命令，避免记忆多个入口。",
            "collect 采集原始行情，prepare/train/rank/review 管理研究链路。",
            "buy/sell/permission/order 管理券商交易链路。",
            "clean/pull/push 管理本地数据和服务器同步。",
        ),
        (
            "stock collect --type daily",
            "stock prepare --model L2_minute5_LightGBM",
            "stock train --model L2_minute5_LightGBM --split 6:3:1",
            "stock rank --model L2_minute5_LightGBM",
            "stock permission",
            "stock buy --model L2_minute5_LightGBM",
        ),
        (
            "每次只能执行一个一级动作。",
            "最终交易合法性和成交状态由券商判断。",
        ),
    )
    actions = parser.add_subparsers(dest="action", required=True, title="动作")

    collect = _add_action_parser(actions, "collect", "采集股票清单、日线和分钟原始行情。")
    collect.add_argument("--date", help="采集日期或日期范围，默认当天。")
    collect.add_argument("--codes", help="股票代码，逗号分隔，默认全市场。")
    collect.add_argument(
        "--type",
        choices=("stock", "daily", "minute5", "minute1"),
        help="只采集一个明确的数据类型。",
    )
    _add_model_argument(collect)
    collect.set_defaults(handler=_dispatch_collect)

    prepare = _add_action_parser(actions, "prepare", "从本地 Parquet 准备 Qlib。")
    prepare.add_argument("--date", help="准备日期或日期范围。")
    prepare.add_argument("--force", action="store_true", help="强制准备。")
    _add_model_argument(prepare)
    prepare.set_defaults(handler=lambda args: prepare_command(args.date, args.force, args.model))

    train = _add_action_parser(actions, "train", "训练、评估并回测模型。")
    train.add_argument("--split", help="训练、验证、测试比例，例如 6:3:1。")
    train.add_argument("--force", action="store_true", help="强制训练。")
    _add_model_argument(train)
    train.set_defaults(handler=lambda args: train_command(args.split, args.force, args.model))

    rank = _add_action_parser(actions, "rank", "生成完整横截面排名。")
    rank.add_argument("--date", help="排名日期。")
    _add_model_argument(rank)
    rank.set_defaults(handler=lambda args: rank_command(args.date, args.model))

    review = _add_action_parser(actions, "review", "汇总已实现收益并判断是否重训。")
    review.add_argument("--date", help="复盘日期，默认当天。")
    _add_model_argument(review)
    review.set_defaults(handler=lambda args: review_command(args.date, args.model))

    for name, side, help_text in (
        ("buy", "B", "执行买入。"),
        ("sell", "S", "执行卖出。"),
    ):
        trade = _add_action_parser(actions, name, help_text)
        _add_model_argument(trade)
        trade.set_defaults(handler=lambda args, side=side: settle_command(side, args.model))

    permission = _add_action_parser(actions, "permission", "同步逐股交易权限。")
    permission.set_defaults(handler=lambda args: permission_command())

    order = _add_action_parser(actions, "order", "同步账户快照和历史成交。")
    order.add_argument("--date", help="订单同步日期或日期范围。")
    order.set_defaults(handler=lambda args: order_command(args.date))

    clean = _add_action_parser(actions, "clean", "清理超过保留期的数据和已终结状态。")
    clean.add_argument("--date", help="清理基准日期，默认当天。")
    clean.set_defaults(handler=lambda args: clean_command(args.date))

    for name, pull, push, help_text in (
        ("pull", True, False, "从服务器拉取运行数据。"),
        ("push", False, True, "向服务器推送运行数据。"),
    ):
        sync = _add_action_parser(actions, name, help_text)
        sync.add_argument(
            "--scope",
            choices=("market", "prepared", "model", "state"),
            help="只同步一个范围，省略时沿用默认范围。",
        )
        if push:
            sync.add_argument("--force", action="store_true", help="同时覆盖服务器 state。")
        sync.set_defaults(
            handler=lambda args, pull=pull, push=push: sync_command(
                pull, push, getattr(args, "force", False), args.scope
            )
        )

    arguments = parser.parse_args()
    try:
        arguments.handler(arguments)
    except CliParameterError as error:
        parser.error(str(error))


# AI_GENERATE_END------------ ---------
