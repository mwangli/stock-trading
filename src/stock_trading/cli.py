# AI_GENERATE_START -----------------------------------------------------------------------------------------------------------------------------
from __future__ import annotations

import json
import logging
import re
from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
from time import perf_counter
from typing import Annotated, Callable, TypeVar

import typer

from stock_trading.config import AppConfig
from stock_trading.utils.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.trade_calendar import latest_completed_trade_date
from stock_trading.utils.notifications import push_system_failure
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import (
    run_training_pipeline,
)
from stock_trading.utils.time_utils import shanghai_now, shanghai_today
from stock_trading.utils.workflows import (
    cleanup_workflow,
    collect_workflow,
    evaluate_workflow,
    performance_workflow,
    prepare_workflow,
    rank_workflow,
    sync_broker_workflow,
    trading_workflow,
)

LOGGER = logging.getLogger(__name__)
T = TypeVar("T")


def _execute(
    command: str,
    parameters: dict[str, object],
    operation: Callable[[AppConfig], T],
    result_logger: Callable[[T], None] | None = None,
) -> T:
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
            config = AppConfig.from_env()
            LOGGER.info(
                "业务命令开始：参数=%s",
                json.dumps(parameters, ensure_ascii=False, default=str),
            )
            result = operation(config)
        except typer.BadParameter as error:
            LOGGER.warning("业务命令参数错误：原因=%s", error)
            raise
        except Exception as error:
            LOGGER.error(
                "业务命令失败：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            push_system_failure(config, command, error)
            raise
        except KeyboardInterrupt:
            LOGGER.warning(
                "收到 Ctrl+C，业务命令已终止：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
            raise SystemExit(130) from None
        set_log_context(step="1/1", progress="-/-", target=command)
        if result_logger is not None:
            result_logger(result)
        LOGGER.info("业务命令完成：总耗时=%.2f 秒", perf_counter() - started_at)
        return result


def broker_command(
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="业务日期或日期范围，默认当天。"),
    ] = None,
) -> None:
    today = shanghai_today()
    start_date, end_date = _parse_date_expression(date_value, today, today)
    _execute(
        "broker",
        {"date": date_value},
        lambda config: sync_broker_workflow(config, start_date, end_date),
        result_logger=_log_broker_summary,
    )


def collect_command(
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="采集日期或日期范围，默认最近交易日。"),
    ] = None,
) -> None:
    def run(config: AppConfig) -> dict[str, object]:
        latest = latest_completed_trade_date(shanghai_now())
        start, end = _parse_date_expression(date_value, latest, latest)
        return collect_workflow(
            config,
            start,
            end,
            latest,
        )

    result = _execute(
        "collect",
        {"date": date_value},
        run,
    )
    _print_json(result)


def prepare_command(
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="Qlib 日期或日期范围，默认延续现有范围至最近交易日。"),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", help="忽略现有 Qlib 并从原始 Parquet 全量重建。"),
    ] = False,
) -> None:
    def run(config: AppConfig) -> dict[str, object]:
        latest = latest_completed_trade_date(shanghai_now())
        qlib = QlibBinaryStore(
            ParquetMarketStore(config.data_dir), config.qlib_dir
        )
        existing = qlib.date_range()
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
        return prepare_workflow(config, start, end, force=force)

    result = _execute(
        "prepare",
        {"date": date_value, "force": force},
        run,
    )
    _print_json(result)


def train_command(
    split: Annotated[
        str | None,
        typer.Option(help="训练、验证、测试比例，例如 6:3:1；默认 70:15:15。"),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", help="忽略已有可用模型并重新训练。"),
    ] = False,
) -> None:
    configure_logging()
    logger = logging.getLogger("stock_trading")
    previous_level = logger.level
    logger.setLevel(logging.WARNING)
    try:
        _execute(
            "train",
            {"split": split, "force": force},
            lambda config: run_training_pipeline(
                config,
                _parse_model_split(split) if split else None,
                force=force,
            ),
        )
    finally:
        logger.setLevel(previous_level)


def rank_command(
    prediction_date: Annotated[
        str | None,
        typer.Option("--date", help="排名业务日期，默认当天。"),
    ] = None,
) -> None:
    business_date = _business_date(prediction_date)
    result = _execute(
        "rank",
        {"date": prediction_date},
        lambda config: rank_workflow(config, business_date),
    )
    _print_json(result)


def trading_command(
    trade_type: Annotated[
        str,
        typer.Option("--type"),
    ],
) -> None:
    if trade_type not in {"B", "S"}:
        raise typer.BadParameter("--type 只能是 B 或 S")
    side = "BUY" if trade_type == "B" else "SELL"
    business_date = shanghai_today()
    result = _execute(
        "trading",
        {"side": side, "businessDate": business_date.isoformat()},
        lambda config: trading_workflow(config, business_date, side),
    )
    _print_json(result)


def performance_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="绩效业务日期，默认当天。"),
    ] = None,
) -> None:
    target_date = _business_date(business_date)
    _execute(
        "performance",
        {"date": business_date},
        lambda config: performance_workflow(config, target_date),
        result_logger=_log_performance_summary,
    )


def evaluate_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="模型评估业务日期，默认当天。"),
    ] = None,
) -> None:
    target_date = _business_date(business_date)
    _execute(
        "evaluate",
        {"date": business_date},
        lambda config: evaluate_workflow(config, target_date),
        result_logger=_log_evaluation_summary,
    )


def clean_command(
    reference_date: Annotated[
        str | None,
        typer.Option("--date", help="清理基准日期，默认当天。"),
    ] = None,
) -> None:
    target_date = _business_date(reference_date)
    _execute(
        "clean",
        {"date": reference_date},
        lambda config: cleanup_workflow(config, target_date),
        result_logger=_log_cleanup_summary,
    )


def _business_date(value: str | None) -> date:
    return (
        _parse_date(value)
        if value
        else shanghai_today()
    )


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




def _parse_stock_code(value: str) -> str:
    normalized = value.strip().lower()
    if not re.fullmatch(r"(?:sh|sz)\.\d{6}", normalized):
        raise typer.BadParameter(
            f"股票代码格式错误：{value}；示例：sh.600000 或 sz.000001"
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


def _print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))


def _log_broker_summary(result: dict[str, object]) -> None:
    settlement = dict(result["settlement"])
    LOGGER.info(
        "券商同步完成：日期=%s:%s；有效订单=%s；新增=%s；更新=%s；"
        "新增闭环=%s；未匹配卖出=%s",
        result["startDate"],
        result["endDate"],
        result["validOrders"],
        result["insertedOrders"],
        result["updatedOrders"],
        settlement.get("realizedTrades", 0),
        settlement.get("unmatchedSellQuantity", 0),
    )


def _log_performance_summary(result: dict[str, object]) -> None:
    performance = dict(result["performance"])
    LOGGER.info(
        "已实现绩效完成：日期=%s；闭环交易=%s；毛损益=%s；毛收益率=%s；"
        "费用=%s；净损益=%s；净收益率=%s",
        result["businessDate"],
        performance.get("trade_count", 0),
        _format_amount(performance.get("gross_pnl")),
        _format_rate(performance.get("gross_return")),
        _format_amount(
            float(performance.get("buy_fees") or 0)
            + float(performance.get("sell_fees") or 0)
        ),
        _format_amount(performance.get("net_pnl")),
        _format_rate(performance.get("net_return")),
    )


def _log_evaluation_summary(result: dict[str, object]) -> None:
    evaluation = dict(result["evaluation"])
    LOGGER.info(
        "模型评估完成：日期=%s；模型决策=%s；原因=%s",
        result["businessDate"],
        evaluation.get("decision", "-"),
        evaluation.get("reason", "-"),
    )


def _log_cleanup_summary(result: dict[str, object]) -> None:
    market = dict(result["market"])
    state = dict(result["state"])
    LOGGER.info(
        "数据清理完成：日期=%s；分钟股票=%s；原始行=%s；聚合行=%s；"
        "SQLite删除=%s",
        result["referenceDate"],
        market.get("codes", 0),
        market.get("rawRows", 0),
        market.get("summaryRows", 0),
        state.get("deletedRows", 0),
    )


def _format_amount(value: object) -> str:
    return "-" if value is None else f"{float(value):,.2f}"


def _format_rate(value: object) -> str:
    return "-" if value is None else f"{float(value):.2%}"
# AI_GENERATE_END -----------------------------------------------------------------------------------------------------------------------------
