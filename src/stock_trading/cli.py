# AI_GENERATE_START------------ ----------------------------------------------------------------------------------------------------------------------------------------
from __future__ import annotations

import json
import logging
import re
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from time import perf_counter
from typing import Annotated, Callable, TypeVar

import typer

from stock_trading.config import AppConfig, load_project_environment
from stock_trading.data_sync import SYNC_SCOPES, sync_data
from stock_trading.utils.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.utils.notifications import push_system_failure
from stock_trading.research.model_adapter import create_research_model
from stock_trading.research.model_config import ModelSpec, resolve_model_spec
from stock_trading.research.training import (
    run_training_pipeline,
)
from stock_trading.reports import (
    clean_report,
    collect_report,
    order_report,
    prepare_report,
    render_report,
    review_report,
    sync_report,
    trading_report,
    model_report,
)
from stock_trading.utils.time_utils import shanghai_today
from stock_trading.utils.workflows import (
    COLLECTION_TYPES,
    cleanup_workflow,
    collect_workflow,
    prepare_workflow,
    rank_workflow,
    review_workflow,
    sync_account_workflow,
    sync_order_workflow,
    trading_workflow,
)

LOGGER = logging.getLogger(__name__)
T = TypeVar("T")
ReportOption = Annotated[
    int | None,
    typer.Option(
        "--report",
        min=1,
        max=1000,
        help="只读汇报当前已有数据，可指定明细条数，默认 10。",
    ),
]


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
        except typer.BadParameter as error:
            LOGGER.error(
                "业务命令参数错误：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            typer.echo(f"参数错误：{error}", err=True)
            raise typer.Exit(code=2) from None
        except Exception as error:
            LOGGER.error(
                "业务命令失败：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            push_system_failure(config, command, error)
            typer.echo(f"执行失败：{error}", err=True)
            raise typer.Exit(code=1) from None
        except KeyboardInterrupt:
            LOGGER.warning(
                "收到 Ctrl+C，业务命令已终止：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
            typer.echo("命令已终止。", err=True)
            raise SystemExit(130) from None
        set_log_context(step="1/1", progress="-/-", target=command)
        typer.echo(
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
    except typer.BadParameter:
        raise
    except Exception as error:
        typer.echo(f"汇报失败：{error}", err=True)
        raise typer.Exit(code=1) from None


def order_command(
    account: Annotated[
        bool,
        typer.Option("--account", help="同步最新券商账户资金快照。"),
    ] = False,
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="业务日期或日期范围，默认当天。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if account:
            raise typer.BadParameter("--report 不支持同时指定 --account")
        start_date = end_date = None
        if date_value is not None:
            start_date, end_date = _parse_date_expression(
                date_value, shanghai_today(), shanghai_today()
            )
        _execute_report(
            lambda config: order_report(config, report, start_date, end_date)
        )
        return
    if account:
        if date_value is not None:
            raise typer.BadParameter("--account 不支持同时指定 --date")
        _execute(
            "order-account",
            {"account": True},
            sync_account_workflow,
            result_summary=_summarize_account,
        )
        return
    today = shanghai_today()
    start_date, end_date = _parse_date_expression(date_value, today, today)
    _execute(
        "order",
        {"date": date_value},
        lambda config: sync_order_workflow(config, start_date, end_date),
        result_summary=_summarize_order,
    )


def collect_command(
    type_value: Annotated[
        str | None,
        typer.Option(
            "--type",
            help=(
                "采集类型，支持 basic,daily,factor,minute5,minute 逗号多选；"
                "普通采集默认 daily。"
            ),
        ),
    ] = None,
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="采集日期或日期范围，默认当天。"),
    ] = None,
    codes_value: Annotated[
        str | None,
        typer.Option("--codes", help="股票代码，多个代码使用逗号分隔，默认全市场。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if date_value is not None or codes_value is not None:
            raise typer.BadParameter("--report 不支持同时指定 --date 或 --codes")
        selected = _parse_collection_types(type_value) if type_value else None
        _execute_report(lambda config: collect_report(config, selected))
        return
    selected_types = _parse_collection_types(type_value or "daily")
    selected_codes = _parse_stock_codes(codes_value) if codes_value else None

    def run(config: AppConfig) -> dict[str, object]:
        today = shanghai_today()
        start, end = _parse_date_expression(date_value, today, today)
        return collect_workflow(
            config,
            start,
            end,
            selected_types,
            requested_codes=selected_codes,
        )

    _execute(
        "collect",
        {
            "type": list(selected_types),
            "date": date_value,
            "codes": codes_value,
        },
        run,
        result_summary=_summarize_collection,
    )


def prepare_command(
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="Qlib 日期或日期范围，默认延续现有范围至最近交易日。"),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", help="忽略现有 Qlib 并从原始 Parquet 全量重建。"),
    ] = False,
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if date_value is not None or force:
            raise typer.BadParameter("--report 不支持同时指定 --date 或 --force")
        spec = _resolve_model(model_value) if model_value else None
        _execute_report(lambda config: prepare_report(config, spec))
        return
    def run(config: AppConfig) -> dict[str, object]:
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
    split: Annotated[
        str | None,
        typer.Option(help="训练、验证、测试比例，例如 6:3:1；默认 70:15:15。"),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", help="忽略已有可用模型并重新训练。"),
    ] = False,
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if split is not None or force:
            raise typer.BadParameter("--report 不支持同时指定 --split 或 --force")
        spec = _resolve_model(model_value) if model_value else None
        _execute_report(lambda config: model_report(config, spec))
        return
    load_project_environment()
    configure_logging(console=False)
    logger = logging.getLogger("stock_trading")
    previous_level = logger.level
    logger.setLevel(logging.WARNING)
    try:
        _execute(
            "model",
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
    prediction_date: Annotated[
        str | None,
        typer.Option("--date", help="排名业务日期，默认当天。"),
    ] = None,
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
) -> None:
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
    trade_type: Annotated[
        str,
        typer.Option("--type"),
    ],
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
) -> None:
    if trade_type not in {"B", "S"}:
        raise typer.BadParameter("--type 只能是 B 或 S")
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


def trading_report_command(direction: str | None, limit: int) -> None:
    """汇报最近已成交买卖事实，不调用券商。"""
    _execute_report(lambda config: trading_report(config, limit, direction))


def review_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="复盘业务日期，默认当天。"),
    ] = None,
    model_value: Annotated[
        str | None,
        typer.Option("--model", help="模型完整 ID、版本前缀或算法前缀。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if business_date is not None:
            raise typer.BadParameter("--report 不支持同时指定 --date")
        spec = _resolve_model(model_value) if model_value else None
        _execute_report(lambda config: review_report(config, spec))
        return
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
    reference_date: Annotated[
        str | None,
        typer.Option("--date", help="清理基准日期，默认当天。"),
    ] = None,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if reference_date is not None:
            raise typer.BadParameter("--report 不支持同时指定 --date")
        _execute_report(lambda config: clean_report(config, report))
        return
    target_date = _business_date(reference_date)
    _execute(
        "clean",
        {"date": reference_date},
        lambda config: cleanup_workflow(config, target_date),
        result_summary=_summarize_cleanup,
    )


def sync_command(
    pull: Annotated[
        str | None,
        typer.Option(
            "--pull", help="从服务器拉取 market、qlib、model、state 或 all。"
        ),
    ] = None,
    push: Annotated[
        str | None,
        typer.Option(
            "--push",
            help="向服务器推送 market、qlib、model、state 或 all。",
        ),
    ] = None,
    force: Annotated[
        bool,
        typer.Option(
            "--force",
            help="允许强制推送 state，并覆盖服务器 SQLite 状态库。",
        ),
    ] = False,
    report: ReportOption = None,
) -> None:
    if report is not None:
        if pull is not None or push is not None or force:
            raise typer.BadParameter(
                "--report 不支持同时指定 --pull、--push 或 --force"
            )
        _execute_report(sync_report)
        return
    pull_scopes = _parse_sync_scopes(pull, "pull")
    push_scopes = _parse_sync_scopes(push, "push")
    if not pull_scopes and not push_scopes:
        raise typer.BadParameter("必须指定 --pull 或 --push")
    overlap = set(pull_scopes) & set(push_scopes)
    if overlap:
        raise typer.BadParameter(
            f"同一数据类型不能同时拉取和推送：{','.join(sorted(overlap))}"
        )
    if "state" in push_scopes and not force:
        raise typer.BadParameter("推送 state 必须显式指定 --force")
    if force and "state" not in push_scopes:
        raise typer.BadParameter("--force 仅用于 --push state")
    _execute(
        "sync",
        {
            "pull": pull,
            "push": push,
            "force": force,
        },
        lambda config: sync_data(
            config,
            pull_scopes,
            push_scopes,
            force=force,
        ),
        result_summary=_summarize_sync,
        console_logs=True,
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
        raise typer.BadParameter(str(error)) from None


def _parse_date(value: str) -> date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise typer.BadParameter(
            f"日期格式错误：{value}；请使用 YYYY-MM-DD，例如 2026-10-31"
        )
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise typer.BadParameter(f"日期无效：{value}；请检查年月日是否存在") from None


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
        raise typer.BadParameter(f"日期参数格式错误：{value}；{example}")
    try:
        start_date = _parse_date(parts[0])
        end_date = _parse_date(parts[1]) if len(parts) == 2 else start_date
    except typer.BadParameter as error:
        raise typer.BadParameter(f"{error}；{example}") from None
    if end_date < start_date:
        raise typer.BadParameter(
            f"日期范围错误：结束日期 {end_date} 不能早于开始日期 {start_date}；{example}"
        )
    return start_date, end_date




def _parse_collection_types(value: str) -> tuple[str, ...]:
    values = tuple(
        dict.fromkeys(item.strip().lower() for item in value.split(","))
    )
    if not values or any(not item for item in values):
        raise typer.BadParameter("--type 不能为空")
    unknown = sorted(set(values) - set(COLLECTION_TYPES))
    if unknown:
        raise typer.BadParameter(
            f"--type 包含未知类型：{','.join(unknown)}；"
            f"可选值为 {','.join(COLLECTION_TYPES)}"
        )
    return values


def _parse_stock_code(value: str) -> str:
    normalized = value.strip().lower()
    if re.fullmatch(r"\d{6}", normalized):
        if normalized.startswith("6"):
            normalized = f"sh.{normalized}"
        elif normalized.startswith(("0", "3")):
            normalized = f"sz.{normalized}"
    if not re.fullmatch(r"(?:sh|sz)\.\d{6}", normalized):
        raise typer.BadParameter(
            f"股票代码格式错误：{value}；示例：600000、sh.600000 或 sz.000001"
        )
    return normalized


def _parse_stock_codes(value: str) -> list[str]:
    raw_codes = [item.strip() for item in value.split(",") if item.strip()]
    if not raw_codes:
        raise typer.BadParameter("股票代码列表不能为空")
    return list(dict.fromkeys(_parse_stock_code(code) for code in raw_codes))


def _parse_model_split(value: str) -> tuple[Decimal, Decimal, Decimal]:
    parts = value.split(":")
    if len(parts) != 3:
        raise typer.BadParameter("训练比例格式错误；请使用 训练:验证:测试，例如 6:3:1")
    try:
        ratios = tuple(Decimal(part) for part in parts)
    except InvalidOperation:
        raise typer.BadParameter("训练比例必须是数字；例如 6:3:1") from None
    if any(not ratio.is_finite() or ratio <= 0 for ratio in ratios):
        raise typer.BadParameter("训练、验证和测试比例必须全部大于 0")
    return ratios[0], ratios[1], ratios[2]


def _parse_sync_scopes(value: str | None, direction: str) -> tuple[str, ...]:
    if value is None:
        return ()
    scopes = tuple(dict.fromkeys(item.strip().lower() for item in value.split(",")))
    if not scopes or any(not scope for scope in scopes):
        raise typer.BadParameter(f"--{direction} 数据类型不能为空")
    unknown = sorted(set(scopes) - SYNC_SCOPES - {"all"})
    if unknown:
        raise typer.BadParameter(
            f"--{direction} 包含未知数据类型：{','.join(unknown)}"
        )
    if "all" in scopes:
        if len(scopes) != 1:
            raise typer.BadParameter(f"--{direction} all 不能与其他类型同时使用")
        return ("market", "qlib", "model", "state") if direction == "pull" else (
            "market",
            "qlib",
            "model",
        )
    return scopes


def _summarize_order(result: dict[str, object]) -> str:
    settlement = dict(result["settlement"])
    return (
        f"券商订单同步完成：日期 {result['startDate']} 至 {result['endDate']}，"
        f"有效订单 {result['validOrders']} 条，新增 {result['insertedOrders']} 条，"
        f"更新 {result['updatedOrders']} 条，新增闭环 "
        f"{settlement.get('realizedTrades', 0)} 条，未匹配卖出 "
        f"{settlement.get('unmatchedSellQuantity', 0)} 股。"
    )


def _summarize_account(result: dict[str, object]) -> str:
    return (
        f"券商账户同步完成：采集时间 {result['captured_at']}，可用资金 "
        f"{_format_amount(result['available_cash'])} 元，总资产 "
        f"{_format_amount(result['total_assets'])} 元，持仓市值 "
        f"{_format_amount(result['total_position'])} 元，冻结金额 "
        f"{_format_amount(result['frozen_amount'])} 元。"
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
        details.append(
            f"{type_names.get(name, name)}采集成功，质量"
            f"{_format_quality(item.get('dataQuality'))}"
        )
    return (
        f"数据采集完成：日期 {result['startDate']} 至 {result['endDate']}；"
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
    aggregate = dict(evaluation.get("aggregate") or {})
    action = "复用现有制品" if training.get("skipped") else "完成训练与回测"
    summary = (
        f"模型训练完成：模型 {result['modelId']}，{action}，制品 "
        f"{training.get('artifactPath', '-')}"
    )
    if aggregate:
        summary += (
            f"，资金场景 {aggregate.get('completedScenarios', 0)}/"
            f"{aggregate.get('requiredScenarios', 0)}，平均毛收益率 "
            f"{_format_percent(aggregate.get('averageGrossReturn'))}"
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
    scopes = ", ".join(
        f"{direction_names.get(str(item.get('direction')), item.get('direction'))}"
        f" {item.get('scope')}"
        for item in operations
        if isinstance(item, dict)
    ) or "无"
    return f"数据同步完成：操作 {len(operations)} 项，范围 {scopes}。"


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


# AI_GENERATE_END------------ ----------------------------------------------------------------------------------------------------------------------------------------
