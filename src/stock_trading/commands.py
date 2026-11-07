# AI_GENERATE_START -----
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


def collect_command(
    date_value: str | None = None,
    codes_value: str | None = None,
) -> None:
    from stock_trading.workflows import collect_workflow

    selected_codes = _parse_stock_codes(codes_value) if codes_value else None

    def run(config: AppConfig) -> dict[str, object]:
        today = shanghai_today()
        start, end = _parse_date_expression(date_value, today, today)
        return collect_workflow(
            config,
            start,
            end,
            requested_codes=selected_codes,
        )

    result = _execute(
        "collect",
        {
            "date": date_value,
            "codes": codes_value,
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
            start = (
                existing[0]
                if existing is not None
                else latest - timedelta(days=config.market_history_days)
            )
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
        },
        lambda config: sync_data(config, pull=pull, push=push, force=force),
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
        return resolve_model_spec(value, warning=LOGGER.warning)
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


def _summarize_collection(result: dict[str, object]) -> str:
    type_names = {
        "basic": "股票基础信息",
        "daily": "日线数据",
        "factor": "复权因子",
        "minute5": "5 分钟数据",
        "minute": "分钟数据",
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
        summary += (
            f"，回测资金 {_format_amount(backtest.get('capital'))} 元，"
            f"毛收益 {_format_amount(backtest.get('grossProfit'))} 元"
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


def _add_model_argument(parser: ChineseArgumentParser) -> None:
    parser.add_argument("--model", help="模型完整 ID、版本前缀或算法前缀。")


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
    _add_model_argument(parser)
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
    _add_model_argument(parser)
    _run(parser, lambda args: train_command(args.split, args.force, args.model))


def rank_main() -> None:
    parser = _parser(
        "stock-rank",
        "收盘后生成指定特征日的完整排名，供下一交易日买入。",
        ("读取已经准备好的 Qlib 和模型制品。",),
        (
            "stock-rank --model v2.1",
            "stock-rank --model v1 --date 2026-11-06",
        ),
        ("只生成排名，不提交交易委托。",),
    )
    parser.add_argument("--date", help="特征日期，默认最近已收盘交易日。")
    _add_model_argument(parser)
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
    _add_model_argument(parser)
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
    _add_model_argument(parser)
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


# AI_GENERATE_END -----
