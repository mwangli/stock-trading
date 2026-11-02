# AI_GENERATE_START -----------------------------------------------------------------------------------------------------------------------
from __future__ import annotations

import json
import logging
import os
import re
import socket
import urllib.request
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path
from time import perf_counter, sleep
from typing import Annotated, Callable, TypeVar
from zoneinfo import ZoneInfo

import typer

from stock_trading.config import AppConfig
from stock_trading.instruments import normalize_instrument
from stock_trading.initialization import InitializationService
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import (
    live_codes,
    research_codes,
    sync_collection_data,
)
from stock_trading.process_control import replace_running_command
from stock_trading.logging_utils import (
    configure_logging,
    log_context,
    set_log_context,
)
from stock_trading.research.artifacts import (
    model_root,
    resolve_current_model_dir,
)
from stock_trading.research.model import (
    predict_alpha158_lightgbm,
)
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.research.training import (
    default_training_codes,
    refresh_qlib_from_parquet,
    run_training_pipeline,
)
from stock_trading.state.store import StateStore
from stock_trading.trading.broker_sync import BrokerOrderSyncService
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.permissions import refresh_permissions
from stock_trading.trading.performance import (
    ModelEvaluationService,
    RealizedPerformanceService,
)

LOGGER = logging.getLogger(__name__)
BARK_PUSH_URL = "https://api.day.app/push"
PUBLIC_LOG_URL = os.getenv("PUBLIC_LOG_URL", "http://117.72.216.67/logs/run.log").strip()
DEPENDENCY_WAIT_SECONDS = 30 * 60
DEPENDENCY_POLL_SECONDS = 10


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
            with replace_running_command(command, config.state_db_path):
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
            _push_system_failure(config, command, error)
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


def init_command() -> None:
    def run(config: AppConfig) -> dict[str, object]:
        return InitializationService(config).run()

    result = _execute("init", {}, run)
    _print_json(result)


def broker_command(
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="业务日期或日期范围，默认当天。"),
    ] = None,
) -> None:
    today = datetime.now(ZoneInfo("Asia/Shanghai")).date()
    start_date, end_date = _parse_date_expression(date_value, today, today)
    _execute(
        "broker",
        {"date": date_value},
        lambda config: _sync_broker(config, start_date, end_date),
        result_logger=_log_broker_summary,
    )


def collect_command(
    codes: Annotated[
        str | None,
        typer.Option(help="股票代码，多个代码使用逗号分隔；默认研究股票池。"),
    ] = None,
    date_value: Annotated[
        str | None,
        typer.Option("--date", help="业务日期或日期范围，默认当天。"),
    ] = None,
) -> None:
    def run(config: AppConfig) -> dict[str, object]:
        today = datetime.now(ZoneInfo("Asia/Shanghai")).date()
        start, end = _parse_date_expression(date_value, today, today)
        parsed_codes = (
            _parse_stock_codes(codes)
            if codes
            else _default_research_codes(config, start, end)
        )
        return _collect_codes(config, parsed_codes, start, end)

    result = _execute(
        "collect",
        {
            "codes": codes,
            "date": date_value,
        },
        run,
    )
    _print_json(result)


def train_command(
    split: Annotated[
        str | None,
        typer.Option(help="训练、验证、测试比例，例如 6:3:1；默认 70:15:15。"),
    ] = None,
) -> None:
    configure_logging()
    logger = logging.getLogger("stock_trading")
    previous_level = logger.level
    logger.setLevel(logging.WARNING)
    try:
        _execute(
            "train",
            {"split": split},
            lambda config: run_training_pipeline(
                config, _parse_model_split(split) if split else None
            ),
        )
    finally:
        logger.setLevel(previous_level)


def _run_date_command(
    command: str,
    value: str | None,
    operation: Callable[[AppConfig, date], None],
) -> None:
    business_date = _business_date(value)
    _execute(command, {"date": value}, lambda config: operation(config, business_date))


def rank_command(
    prediction_date: Annotated[
        str | None,
        typer.Option("--date", help="排名业务日期，默认当天。"),
    ] = None,
) -> None:
    _run_date_command("rank", prediction_date, _rank)


def trading_command(
    trade_type: Annotated[
        str,
        typer.Option("--type"),
    ],
) -> None:
    if trade_type not in {"B", "S"}:
        raise typer.BadParameter("--type 只能是 B 或 S")
    side = "BUY" if trade_type == "B" else "SELL"
    business_date = datetime.now(ZoneInfo("Asia/Shanghai")).date()
    _execute(
        "trading",
        {"side": side, "businessDate": business_date.isoformat()},
        lambda config: _run_trading_window(config, business_date, side),
    )


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
        lambda config: _update_realized_performance(config, target_date),
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
        lambda config: _evaluate_model(config, target_date),
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
        lambda config: _cleanup(config, target_date),
        result_logger=_log_cleanup_summary,
    )


def _business_date(value: str | None) -> date:
    return (
        _parse_date(value)
        if value
        else datetime.now(ZoneInfo("Asia/Shanghai")).date()
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


def _collect_codes(
    config: AppConfig, codes: list[str], start_date: date, end_date: date
) -> dict[str, object]:
    state = StateStore(config.state_db_path)
    business_key = state.start_task("COLLECT", end_date)
    LOGGER.info(
        "开始采集股票数据：股票数=%d；日期=%s 至 %s；连接=1",
        len(codes),
        start_date,
        end_date,
    )
    try:
        result = sync_collection_data(
            ParquetMarketStore(config.data_dir),
            config.baostock_proxy,
            codes,
            start_date,
            end_date,
        )
        research_start = end_date - timedelta(days=config.market_history_days)
        research_codes = default_training_codes(config, research_start, end_date)
        qlib_refresh = refresh_qlib_from_parquet(
            config, research_codes, research_start, end_date
        )
    except BaseException as error:
        state.finish_task(business_key, False, error)
        raise
    state.finish_task(business_key, True)
    LOGGER.info(
        "股票数据采集完成：股票数=%d；完成=%s；跳过=%s；失败=%d",
        len(codes),
        result["completed"],
        result["skipped"],
        len(result["failed"]),
    )
    return {"collection": result, "qlibRefresh": qlib_refresh}


def _default_research_codes(
    config: AppConfig, start_date: date, end_date: date
) -> list[str]:
    store = ParquetMarketStore(config.data_dir)
    try:
        stocks = store.read_stock_basics()
    except (OSError, ValueError, KeyError) as error:
        raise ValueError(
            "未找到股票基础信息；collect 不会自动执行前置采集，"
            "请显式传入 --codes 或先运行 init"
        ) from error
    codes = research_codes(stocks, start_date, end_date)
    if not codes:
        raise ValueError("股票基础信息中没有满足当前日期范围的研究股票")
    return codes


def _cleanup(config: AppConfig, reference_date: date) -> dict[str, object]:
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    market = ParquetMarketStore(config.data_dir).cleanup_minutes(reference_date)
    state = StateStore(config.state_db_path).cleanup(reference_date)
    return {
        "referenceDate": reference_date.isoformat(),
        "market": market,
        "state": state,
    }


def _sync_broker(
    config: AppConfig,
    start_date: date,
    end_date: date,
    broker: ZXBrokerClient | None = None,
) -> dict[str, object]:
    LOGGER.info("开始同步券商历史订单：日期=%s 至 %s", start_date, end_date)
    store = StateStore(config.state_db_path)
    business_key = store.start_task("BROKER", end_date)
    try:
        result = BrokerOrderSyncService(
            config, store, broker or _build_broker(config)
        ).sync(start_date, end_date)
        result["permissions"] = refresh_permissions(
            store, store.broker_orders(), end_date
        )
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    LOGGER.info(
        "券商历史订单同步完成：日期=%s 至 %s；有效订单=%s；新增闭环=%s",
        start_date,
        end_date,
        result["validOrders"],
        result["settlement"]["realizedTrades"],
    )
    return result


def _rank(config: AppConfig, prediction_date: date) -> None:
    LOGGER.info("开始生成完整横截面预测：预测日期=%s", prediction_date)
    state_store = StateStore(config.state_db_path)
    _wait_for_task(state_store, "COLLECT", prediction_date)
    store = ParquetMarketStore(config.data_dir)
    qlib = QlibBinaryStore(store, config.qlib_dir)
    if not qlib.is_date_ready(prediction_date):
        raise ValueError(
            f"collect 已成功但 Qlib 未覆盖目标日期：{prediction_date.isoformat()}"
        )
    model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    stocks = store.read_stock_basics()
    live_metadata_codes = live_codes(stocks, prediction_date)
    current_codes = store.tradable_codes_on(live_metadata_codes, prediction_date)
    prediction = predict_alpha158_lightgbm(
        config.qlib_dir,
        model_dir,
        prediction_date,
        eligible_instruments={normalize_instrument(code) for code in current_codes},
    )
    state = state_store.import_prediction_artifact(
        model_dir, Path(prediction["path"])
    )
    LOGGER.info(
        "横截面预测完成：全市场评分数=%s；排名数=%s；文件=%s",
        prediction["universeRows"],
        prediction["rankingRows"],
        prediction["path"],
    )
    _print_json({"prediction": prediction, "state": state})


def _run_trading_window(
    config: AppConfig, business_date: date, side: str
) -> None:
    if side not in {"BUY", "SELL"}:
        raise ValueError("交易方向必须是 BUY 或 SELL")
    mode = "AFTERNOON_BUY" if side == "BUY" else "MORNING_SELL"
    mode_name = "下午买入" if side == "BUY" else "上午卖出"
    LOGGER.info(
        "开始执行实时交易窗口：日期=%s；时段=%s；策略=%s；窗口=%d秒",
        business_date,
        mode_name,
        config.trading_execution_strategy,
        config.trading_execution_window_seconds,
    )
    store = StateStore(config.state_db_path)
    broker = _build_broker(config)
    execution = OrderExecutionOrchestrator(config, store, broker)
    selector = LiveTradingOrchestrator(config, store, broker)
    selection = selector.select_targets(business_date, side)
    executed: list[dict[str, object]] = []
    for target in selection["targets"]:
        result = execution.execute_target(business_date, target)
        executed.append(result)
        _push_execution_result(config, business_date, result)
    LOGGER.info(
        "实时交易窗口完成：目标数=%d；执行结果数=%d",
        len(selection["targets"]),
        len(executed),
    )
    _print_json(
        {
            "businessDate": business_date.isoformat(),
            "mode": mode,
            "strategy": config.trading_execution_strategy,
            "selection": selection,
            "executed": executed,
        }
    )

def _update_realized_performance(
    config: AppConfig, business_date: date
) -> dict[str, object]:
    store = StateStore(config.state_db_path)
    _wait_for_task(store, "BROKER", business_date)
    business_key = store.start_task("PERFORMANCE", business_date)
    try:
        result = RealizedPerformanceService(store).update(business_date)
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    return result


def _evaluate_model(config: AppConfig, business_date: date) -> dict[str, object]:
    store = StateStore(config.state_db_path)
    _wait_for_task(store, "PERFORMANCE", business_date)
    business_key = store.start_task("EVALUATE", business_date)
    try:
        result = ModelEvaluationService(config, store).evaluate(business_date)
    except BaseException as error:
        store.finish_task(business_key, False, error)
        raise
    store.finish_task(business_key, True)
    return result


def _wait_for_task(
    store: StateStore, task_type: str, business_date: date
) -> None:
    deadline = perf_counter() + DEPENDENCY_WAIT_SECONDS
    while True:
        task = store.task_status(task_type, business_date)
        if task is not None and task["status"] == "SUCCESS":
            return
        if task is not None and task["status"] == "FAILED":
            raise ValueError(
                f"前置任务失败：{task_type}:{business_date.isoformat()}；"
                f"{task.get('error_message') or '未知原因'}"
            )
        if perf_counter() >= deadline:
            status = "MISSING" if task is None else str(task["status"])
            raise TimeoutError(
                f"等待前置任务超时：{task_type}:{business_date.isoformat()}；"
                f"状态={status}"
            )
        sleep(DEPENDENCY_POLL_SECONDS)


def _build_broker(config: AppConfig) -> ZXBrokerClient:
    captcha_solver = (
        BaiduCaptchaSolver(config).solve
        if config.broker_captcha_auto_submit_enabled
        else None
    )
    return ZXBrokerClient(
        config,
        captcha_solver=captcha_solver,
    )


def _push_execution_result(
    config: AppConfig,
    business_date: date,
    result: dict[str, object],
) -> None:
    """推送实时执行闭环的最终结果，不把旧计划价格当作成交事实。"""
    attempts = list(result["attempts"])
    if not attempts:
        return
    last = attempts[-1]
    side = str(last["side"])
    action = "买入" if side == "BUY" else "卖出"
    status = str(result["status"])
    if status == "FILLED":
        title = f"[交易] {action}成交完成"
        body = "\n".join(
            (
                f"日期：{business_date.isoformat()}",
                f"股票：{last['instrument']}",
                f"最终档位：{last.get('priceLevel', '恢复委托')}",
                f"最后委托价：¥{Decimal(str(last['price'])):,.3f}",
                f"最后委托数量：{int(last['quantity']):,} 股",
                f"尝试次数：{len(attempts)}",
            )
        )
        _push_bark(config, title, body, "交易")
        return
    if status not in {"REJECTED", "UNKNOWN", "STOPPED"}:
        return
    body = "\n".join(
        (
            f"日期：{business_date.isoformat()}",
            f"操作：{action} {last['instrument']}",
            f"状态：{status}",
            f"停止原因：{result['stopReason']}",
            f"原因：{_safe_error_text(last.get('errorMessage'))}",
        )
    )
    _push_bark(config, f"[交易] {action}执行未完成", body, "交易异常", PUBLIC_LOG_URL)

def _push_system_failure(
    config: AppConfig | None, command: str, error: Exception
) -> None:
    """用途：推送任意 CLI 业务异常；输入：命令和异常；输出：无。"""
    body = "\n".join(
        (
            f"任务：{command}",
            f"时间：{datetime.now().astimezone().isoformat(timespec='seconds')}",
            f"原因：{_safe_error_text(error)}",
        )
    )
    _push_bark(config, "[系统] ⚠️ 任务执行异常", body, "系统异常", PUBLIC_LOG_URL)


def _push_bark(
    config: AppConfig | None,
    title: str,
    body: str,
    group: str,
    target_url: str | None = None,
) -> None:
    """用途：尽力发送 Bark 消息且不影响业务；输入：消息字段；输出：无。"""
    device_key = config.bark_device_key if config is not None else os.getenv("DEVICE_KEY", "").strip()
    if not device_key:
        LOGGER.warning("Bark 推送已跳过：未配置 DEVICE_KEY")
        return
    payload: dict[str, object] = {
        "device_key": device_key,
        "title": title,
        "group": group,
    }
    notification_body = f"主机：{socket.gethostname()}\n{body}"
    if target_url:
        payload["markdown"] = f"{notification_body}\n\n[点击查看]({target_url})"
    else:
        payload["body"] = notification_body
    request = urllib.request.Request(
        BARK_PUSH_URL,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            if not 200 <= response.status < 300:
                raise RuntimeError(f"Bark HTTP 状态异常: {response.status}")
        LOGGER.info("Bark 推送请求完成：分组=%s；标题=%s", group, title)
    except Exception as error:
        LOGGER.warning("Bark 推送失败，不影响业务：分组=%s；原因=%s", group, error)


def _safe_error_text(value: object) -> str:
    """用途：裁剪并遮蔽异常中的敏感字段；输入：异常文本；输出：安全摘要。"""
    text = str(value or "未知原因").replace("\r", " ").replace("\n", " ")
    text = re.sub(
        r"(?i)(token|password|device_key|secret|sign_key)\s*[=:]\s*[^\s,;]+",
        r"\1=***",
        text,
    )
    return text[:500]


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
        "已实现绩效完成：日期=%s；闭环交易=%s；净损益=%s；净收益率=%s",
        result["businessDate"],
        performance.get("trade_count", 0),
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
# AI_GENERATE_END -----------------------------------------------------------------------------------------------------------------------
