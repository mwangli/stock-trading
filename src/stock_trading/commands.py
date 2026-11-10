# AI_GENERATE_START------------ ---------
from __future__ import annotations

import json
import logging
import re
import sys
from collections.abc import Callable
from datetime import date
from time import perf_counter
from typing import Literal, TypeVar

import typer

from stock_trading.config import AppConfig, load_project_environment
from stock_trading.research.model_config import (
    ModelSpec,
    all_model_specs,
    dataset_specs,
    resolve_model_spec,
)
from stock_trading.utils.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.utils.time_utils import shanghai_today


def echo(text: object = "") -> None:
    print(text, file=sys.stdout, flush=True)


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
            raise SystemExit(2) from None
        except Exception as error:
            LOGGER.error(
                "业务命令失败：耗时=%.2f 秒；原因=%s",
                perf_counter() - started_at,
                error,
            )
            from stock_trading.utils.notifications import push_system_failure

            push_system_failure(config, command, error)
            raise SystemExit(1) from None
        except KeyboardInterrupt:
            LOGGER.warning(
                "收到 Ctrl+C，业务命令已终止：耗时=%.2f 秒",
                perf_counter() - started_at,
            )
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
        "broker-sync",
        {"date": date_value},
        lambda config: sync_order_workflow(config, start_date, end_date),
        result_summary=_summarize_order,
    )


def permission_command(force: bool = False, resume: bool = False) -> None:
    from stock_trading.workflows import sync_permission_workflow

    _execute(
        "broker-check",
        {"force": force, "resume": resume},
        lambda config: sync_permission_workflow(
            config, resume=resume, force=force
        ),
        result_summary=_summarize_permission,
    )


def collect_command(
    date_value: str | None = None,
    codes_value: str | None = None,
    level: int = 1,
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
            level=level,
        )

    result = _execute(
        "data-collect",
        {"date": date_value, "codes": codes_value, "level": level},
        run,
        result_summary=_summarize_collection,
    )
    if result.get("status") == "PARTIAL":
        raise SystemExit(1)

def prepare_command(
    date_value: str | None = None,
    force: bool = False,
) -> None:
    from stock_trading.research.training import default_training_range
    from stock_trading.workflows import prepare_workflow

    def run(config: AppConfig) -> dict[str, object]:
        if date_value is None:
            start_date, end_date = default_training_range(config)
        else:
            latest = shanghai_today()
            start_date, end_date = _parse_date_expression(date_value, latest, latest)
        items = [
            prepare_workflow(config, spec, start_date, end_date, force=force)
            for spec in dataset_specs()
        ]
        return {
            "items": items,
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
        }

    _execute(
        "model-prepare",
        {"date": date_value, "force": force},
        run,
        result_summary=_summarize_preparation,
    )

def generate_command(
    date_value: str | None = None,
) -> None:
    from stock_trading.research.training import default_training_range
    from stock_trading.workflows import derive_workflow

    def run(config: AppConfig) -> dict[str, object]:
        latest = shanghai_today()
        if date_value is None:
            start, end = default_training_range(config)
        else:
            start, end = _parse_date_expression(date_value, latest, latest)
        return derive_workflow(config, start, end)

    _execute(
        "data-generate",
        {"date": date_value},
        run,
        result_summary=lambda result: (
            f"派生数据生成完成：日期 {result['startDate']} 至 {result['endDate']}，"
            f"研究股票 {result['researchCodes']} 只"
        ),
    )

def train_command(force: bool = False) -> None:
    from stock_trading.research.training import run_training_pipeline

    def run(config: AppConfig) -> dict[str, object]:
        items: list[dict[str, object]] = []
        errors: dict[str, str] = {}
        for spec in all_model_specs():
            try:
                items.append(run_training_pipeline(config, spec, force=force))
            except Exception as error:
                errors[spec.model_id] = str(error)
                LOGGER.error("模型批量训练失败：模型=%s；原因=%s", spec.model_id, error)
        return {
            "status": "PARTIAL" if errors else "SUCCESS",
            "items": items,
            "errors": errors,
        }

    result = _execute(
        "model-train",
        {"force": force},
        run,
        result_summary=_summarize_training,
    )
    if result.get("status") == "PARTIAL":
        raise SystemExit(1)

def rank_command(prediction_date: str | None = None) -> None:
    from stock_trading.workflows import rank_workflow

    business_date = _parse_date(prediction_date) if prediction_date else None
    _execute(
        "model-rank",
        {"date": prediction_date},
        lambda config: rank_workflow(
            config, _resolve_active_model(config), business_date
        ),
        result_summary=_summarize_rank,
    )

def settle_command(trade_type: str) -> None:
    from stock_trading.workflows import trading_workflow

    if trade_type not in {"B", "S"}:
        raise CliParameterError("交易方向只能是 B 或 S")
    side = "BUY" if trade_type == "B" else "SELL"
    business_date = shanghai_today()
    _execute(
        f"trade-{side.lower()}",
        {"side": side, "businessDate": business_date.isoformat()},
        lambda config: trading_workflow(
            config, _resolve_active_model(config), business_date, side
        ),
        result_summary=_summarize_trading,
    )

def review_command(business_date: str | None = None) -> None:
    from stock_trading.workflows import review_workflow

    target_date = _business_date(business_date)
    _execute(
        "model-evaluate",
        {"date": business_date},
        lambda config: review_workflow(
            config, _resolve_active_model(config), target_date
        ),
        result_summary=_summarize_review,
    )

def clean_command(
    reference_date: str | None = None,
) -> None:
    from stock_trading.workflows import cleanup_workflow

    target_date = _business_date(reference_date)
    _execute(
        "system-clean",
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
        "system-push" if push else "system-pull",
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


def _resolve_active_model(config: AppConfig) -> ModelSpec:
    return _resolve_model(config.active_model_id)


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
        "ensure_stock_info": "股票基础信息",
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
    items = list(result.get("items") or [])
    modes = ", ".join(
        f"{item.get('modelId', item.get('datasetId', '-'))}:{dict(item.get('qlib') or {}).get('mode', '-')}"
        for item in items
    ) or "无"
    return (
        f"Qlib 数据准备完成：数据集 {len(items)} 个，"
        f"日期 {result.get('startDate', '-')} 至 {result.get('endDate', '-')}，"
        f"状态 {modes}"
    )

def _summarize_training(result: dict[str, object]) -> str:
    items = list(result.get("items") or [])
    errors = dict(result.get("errors") or {})
    completed = ", ".join(str(item.get("modelId", "-")) for item in items) or "无"
    summary = f"模型批量训练完成：成功或复用 {len(items)} 个，模型={completed}"
    if errors:
        summary += "；失败=" + ", ".join(f"{key}: {value}" for key, value in errors.items())
    return summary

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


SyncScope = Literal["market", "prepared", "model", "state"]
HELP_CONTEXT = {"help_option_names": ["-h", "--help"]}


app = typer.Typer(
    name="stock",
    help="A 股 T+1 数据、模型、交易和运行维护命令。",
    no_args_is_help=True,
    add_completion=True,
    context_settings=HELP_CONTEXT,
)

@app.command("data-collect")
def data_collect(
    date_value: str | None = typer.Option(
        None, "--date", help="采集日期或日期范围，默认当天。"
    ),
    codes_value: str | None = typer.Option(
        None, "--codes", help="股票代码，逗号分隔，默认全市场。"
    ),
    level: int = typer.Option(
        1, "--level", help="数据层级：1=stock+日线，2=stock+5分钟，3=stock+1分钟。"
    ),
) -> None:
    """按数据层级幂等采集股票基础信息、日线和分钟原始行情。"""
    collect_command(date_value, codes_value, level)


@app.command("data-generate")
def data_generate(
    date_value: str | None = typer.Option(
        None, "--date", help="生成日期或日期范围，默认训练历史范围。"
    ),
) -> None:
    """从原始数据生成派生数据。"""
    generate_command(date_value)


@app.command("model-prepare")
def model_prepare(
    date_value: str | None = typer.Option(
        None, "--date", help="准备日期或日期范围，默认训练历史范围。"
    ),
    force: bool = typer.Option(False, "--force", help="强制准备。"),
) -> None:
    """准备全部模型所需的 Qlib 数据集。"""
    prepare_command(date_value, force)


@app.command("model-train")
def model_train(
    force: bool = typer.Option(False, "--force", help="强制训练。"),
) -> None:
    """训练、评估并回测全部已配置模型。"""
    train_command(force)


@app.command("model-rank")
def model_rank(
    date_value: str | None = typer.Option(None, "--date", help="排名日期。"),
) -> None:
    """使用当前模型生成完整横截面排名。"""
    rank_command(date_value)


@app.command("model-evaluate")
def model_evaluate(
    business_date: str | None = typer.Option(
        None, "--date", help="评估日期，默认当天。"
    ),
) -> None:
    """评估已实现交易收益并判断是否需要重训。"""
    review_command(business_date)


@app.command("trade-buy")
def trade_buy() -> None:
    """使用当前模型执行买入。"""
    settle_command("B")


@app.command("trade-sell")
def trade_sell() -> None:
    """使用当前模型执行卖出。"""
    settle_command("S")


@app.command("broker-check")
def broker_permissions(
    force: bool = typer.Option(
        False, "--force", help="重新探测全部股票，不跳过当天已有结果。"
    ),
    resume: bool = typer.Option(
        False, "--resume", help="续跑上次闭市或中断后尚未完成的股票。"
    ),
) -> None:
    """逐股检查交易权限。"""
    permission_command(force, resume)


@app.command("broker-sync")
def broker_orders(
    date_value: str | None = typer.Option(
        None, "--date", help="订单同步日期或日期范围。"
    ),
) -> None:
    """同步账户快照、历史订单和成交事实。"""
    order_command(date_value)


@app.command("system-pull")
def system_pull(
    scope: SyncScope | None = typer.Option(
        None, "--scope", help="只同步一个范围，省略时沿用默认范围。"
    ),
) -> None:
    """从服务器拉取运行数据。"""
    sync_command(pull=True, scope=scope)


@app.command("system-push")
def system_push(
    scope: SyncScope | None = typer.Option(
        None, "--scope", help="只同步一个范围，省略时沿用默认范围。"
    ),
    force: bool = typer.Option(
        False, "--force", help="同时覆盖服务器 state。"
    ),
) -> None:
    """向服务器推送运行数据。"""
    sync_command(push=True, force=force, scope=scope)


@app.command("system-clean")
def system_clean(
    reference_date: str | None = typer.Option(
        None, "--date", help="清理基准日期，默认当天。"
    ),
) -> None:
    """清理超过保留期的数据和已终结状态。"""
    clean_command(reference_date)


def main() -> None:
    app()


# AI_GENERATE_END------------ ---------
