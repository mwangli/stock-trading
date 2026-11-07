# AI_GENERATE_START---------------------- ----------------------------------------------------------------------------------------------------------------------------------------
from __future__ import annotations

import json
import logging
import re
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from time import perf_counter
from typing import Callable, TypeVar

from stock_trading.config import AppConfig, load_project_environment
from stock_trading.utils.console import BOLD_RED, echo
from stock_trading.utils.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.research.model_config import ModelSpec, resolve_model_spec
from stock_trading.reports import render_report, summary_report
from stock_trading.utils.time_utils import shanghai_today

LOGGER = logging.getLogger(__name__)
T = TypeVar("T")


class CliParameterError(ValueError):
    pass


def _execute(
    command: str,
    parameters: dict[str, object],
    operation: Callable[[AppConfig], T],
    result_summary: Callable[[T], str] | None = None,
    console_logs: bool = False,
) -> T:
    load_project_environment()
    configure_logging(console=console_logs)
    started_at = perf_counter()
    config: AppConfig | None = None
    with log_context(
        task=command,
        step="0/1",
        progress="-/-",
        target=command,
    ):
        try:
            config = AppConfig.from_env()
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


def _execute_report(
    operation: Callable[[AppConfig], tuple[object, ...]],
) -> None:
    """执行只读汇报，不配置业务日志、不写通知或任务状态。"""
    try:
        load_project_environment()
        render_report(operation(AppConfig.from_env()))
    except CliParameterError:
        raise
    except Exception as error:
        echo(f"汇报失败：{error}", color=BOLD_RED, error=True)
        raise SystemExit(1) from None


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


def model_command(
    split: str | None = None,
    force: bool = False,
    model_value: str | None = None,
) -> None:
    from stock_trading.research.training import run_training_pipeline

    load_project_environment()
    configure_logging(console=False)
    logger = logging.getLogger("stock_trading")
    previous_level = logger.level
    logger.setLevel(logging.WARNING)
    try:
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
    finally:
        logger.setLevel(previous_level)


def rank_command(
    prediction_date: str | None = None,
    model_value: str | None = None,
) -> None:
    from stock_trading.workflows import rank_workflow

    business_date = _business_date(prediction_date)
    _execute(
        "rank",
        {"date": prediction_date, "model": model_value},
        lambda config: rank_workflow(
            config, _resolve_model(model_value), business_date
        ),
        result_summary=_summarize_rank,
    )


def trading_command(
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
        console_logs=True,
    )
    if result.get("status") == "PARTIAL":
        raise SystemExit(1)


def report_command(
    model_value: str | None = None,
    date_value: str | None = None,
) -> None:
    spec = _resolve_model(model_value) if model_value else None
    start_date = end_date = None
    if date_value is not None:
        start_date, end_date = _parse_date_expression(
            date_value, shanghai_today(), shanghai_today()
        )
    _execute_report(
        lambda config: summary_report(config, spec, start_date, end_date)
    )


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


# AI_GENERATE_END---------------------- ----------------------------------------------------------------------------------------------------------------------------------------
