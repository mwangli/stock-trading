# AI_GENERATE_START ---------------------------------------------------------------
from __future__ import annotations

import json
import logging
import os
import re
import urllib.request
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path
from time import perf_counter
from typing import Annotated, Callable, TypeVar

import typer

from stock_trading.config import AppConfig
from stock_trading.initialization import InitializationService
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.market.baostock_client import BaoStockClient
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import live_codes, sync_research_data
from stock_trading.logging_utils import configure_logging, should_log_progress
from stock_trading.research.artifacts import (
    model_root,
    resolve_current_model_dir,
    train_model_atomically,
)
from stock_trading.research.model import (
    automatic_model_segments,
    predict_alpha158_lightgbm,
)
from stock_trading.research.backtest import run_portfolio_backtest
from stock_trading.research.qlib_store import QlibBinaryStore, to_qlib_code
from stock_trading.state.store import StateStore
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.performance import LivePerformanceService

LOGGER = logging.getLogger(__name__)
BARK_PUSH_URL = "https://api.day.app/push"
PUBLIC_LOG_URL = "http://117.72.216.67/logs/run.log"


app = typer.Typer(
    name="stock-trading",
    help="A股 T+1 数据、模型、交易和评估命令。日期统一使用 YYYY-MM-DD。",
    no_args_is_help=True,
    rich_markup_mode="rich",
    add_completion=False,
    pretty_exceptions_enable=False,
    context_settings={"help_option_names": ["-h", "--help"]},
)
T = TypeVar("T")


def main() -> None:
    app(prog_name="stock-trading")


def _execute(
    command: str,
    parameters: dict[str, object],
    operation: Callable[[AppConfig], T],
) -> T:
    configure_logging()
    started_at = perf_counter()
    config: AppConfig | None = None
    try:
        config = AppConfig.from_env()
        LOGGER.info(
            "业务命令开始：%s；参数=%s",
            command,
            json.dumps(parameters, ensure_ascii=False, default=str),
        )
        result = operation(config)
    except typer.BadParameter as error:
        LOGGER.warning("业务命令参数错误：%s；原因=%s", command, error)
        raise
    except Exception as error:
        LOGGER.error(
            "业务命令失败：%s；耗时=%.2f 秒；原因=%s",
            command,
            perf_counter() - started_at,
            error,
        )
        _push_system_failure(config, command, error)
        raise
    except KeyboardInterrupt:
        LOGGER.warning(
            "收到 Ctrl+C，业务命令已终止：%s；耗时=%.2f 秒",
            command,
            perf_counter() - started_at,
        )
        raise SystemExit(130) from None
    LOGGER.info(
        "业务命令完成：%s；总耗时=%.2f 秒",
        command,
        perf_counter() - started_at,
    )
    return result


COMMAND_CONTEXT = {"help_option_names": ["-h", "--help"]}


@app.command("init", help="初始化完整交易环境。", context_settings=COMMAND_CONTEXT)
def init_command(
    force: Annotated[
        bool,
        typer.Option("--force", help="删除项目 var 后从头初始化。"),
    ] = False,
) -> None:
    def run(config: AppConfig) -> None:
        result = InitializationService(config, lambda: _build_broker(config)).run(
            force=force
        )
        _print_json(result)

    _execute("init", {"force": force}, run)


@app.command(
    "market",
    help="更新市场行情数据。--all 与 --codes 必须二选一。",
    context_settings=COMMAND_CONTEXT,
)
def market_command(
    all_stocks: Annotated[
        bool,
        typer.Option("--all", help="同步全市场日线。"),
    ] = False,
    codes: Annotated[
        str | None,
        typer.Option(help="同步指定股票，多个代码使用逗号分隔。"),
    ] = None,
    start_date: Annotated[
        str | None,
        typer.Option(help="开始日期，格式 YYYY-MM-DD。"),
    ] = None,
    end_date: Annotated[
        str | None,
        typer.Option(help="结束日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    if all_stocks == bool(codes):
        raise typer.BadParameter("--all 与 --codes 必须且只能指定一个")

    def run(config: AppConfig) -> None:
        today = date.today()
        if all_stocks:
            start, end = _parse_date_range(
                start_date, end_date, today, today, single_date=True
            )
            _sync_daily(config, start, end)
            return
        parsed_codes = _parse_stock_codes(codes or "")
        start, end = _parse_date_range(
            start_date,
            end_date,
            today - timedelta(days=config.market_history_days),
            today,
        )
        _sync_market_codes(config, parsed_codes, start, end)

    _execute(
        "market",
        {
            "all": all_stocks,
            "codes": codes,
            "startDate": start_date,
            "endDate": end_date,
        },
        run,
    )


@app.command(
    "prepare", help="校验指定股票数据并重建 Qlib。", context_settings=COMMAND_CONTEXT
)
def prepare_command(
    codes: Annotated[
        str,
        typer.Option(help="股票代码列表，多个代码使用逗号分隔。"),
    ],
    start_date: Annotated[
        str | None,
        typer.Option(help="开始日期，格式 YYYY-MM-DD。"),
    ] = None,
    end_date: Annotated[
        str | None,
        typer.Option(help="结束日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    def run(config: AppConfig) -> None:
        today = date.today()
        start, end = _parse_date_range(
            start_date,
            end_date,
            today - timedelta(days=config.market_history_days),
            today,
        )
        _pre_train(config, _parse_stock_codes(codes), start, end)

    _execute(
        "prepare",
        {"codes": codes, "startDate": start_date, "endDate": end_date},
        run,
    )


@app.command(
    "train", help="训练 Alpha158 + LightGBM 模型。", context_settings=COMMAND_CONTEXT
)
def train_command(
    split: Annotated[
        str | None,
        typer.Option(help="训练:验证:测试比例，例如 6:3:1。"),
    ] = None,
) -> None:
    _execute(
        "train",
        {"split": split},
        lambda config: _train(config, _parse_model_split(split) if split else None),
    )


@app.command(
    "backtest", help="执行单持仓 T+1 回测。", context_settings=COMMAND_CONTEXT
)
def backtest_command(
    initial_cash: Annotated[
        str,
        typer.Option(help="初始资金，必须大于 0。"),
    ] = "10000",
) -> None:
    _execute(
        "backtest",
        {"initialCash": initial_cash},
        lambda config: _backtest(
            config, _parse_decimal(initial_cash, "初始资金", positive=True)
        ),
    )


def _run_date_command(
    command: str,
    value: str | None,
    operation: Callable[[AppConfig, date], None],
) -> None:
    business_date = _parse_date(value) if value else date.today()
    _execute(command, {"date": value}, lambda config: operation(config, business_date))


@app.command("predict", help="生成 Top 5 预测。", context_settings=COMMAND_CONTEXT)
def predict_command(
    prediction_date: Annotated[
        str | None,
        typer.Option("--date", help="预测日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    _run_date_command("predict", prediction_date, _predict)


@app.command("sell", help="执行上午实时卖出。", context_settings=COMMAND_CONTEXT)
def sell_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="业务日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    _run_date_command(
        "sell",
        business_date,
        lambda config, target_date: _run_trading_window(
            config, target_date, morning=True
        ),
    )


@app.command("buy", help="执行下午实时买入。", context_settings=COMMAND_CONTEXT)
def buy_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="业务日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    _run_date_command(
        "buy",
        business_date,
        lambda config, target_date: _run_trading_window(
            config, target_date, morning=False
        ),
    )


@app.command(
    "broker", help="同步券商事实并恢复委托。", context_settings=COMMAND_CONTEXT
)
def broker_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="业务日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    _run_date_command("broker", business_date, _sync_broker)


@app.command(
    "evaluate", help="同步绩效并执行模型评估。", context_settings=COMMAND_CONTEXT
)
def evaluate_command(
    business_date: Annotated[
        str | None,
        typer.Option("--date", help="业务日期，格式 YYYY-MM-DD。"),
    ] = None,
    external_cash_flow: Annotated[
        str,
        typer.Option(help="外部资金流，入金为正数，出金为负数。"),
    ] = "0",
) -> None:
    target_date = _parse_date(business_date) if business_date else date.today()
    cash_flow = _parse_decimal(external_cash_flow, "外部资金流")
    _execute(
        "evaluate",
        {"date": business_date, "externalCashFlow": external_cash_flow},
        lambda config: _update_live_performance(config, target_date, cash_flow),
    )


@app.command("clean", help="压缩数据并执行分级清理。", context_settings=COMMAND_CONTEXT)
def clean_command(
    reference_date: Annotated[
        str | None,
        typer.Option("--date", help="清理基准日期，格式 YYYY-MM-DD。"),
    ] = None,
) -> None:
    _run_date_command("clean", reference_date, _cleanup)


def _parse_date(value: str) -> date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        raise typer.BadParameter(
            f"日期格式错误：{value}；请使用 YYYY-MM-DD，例如 2026-10-31"
        )
    try:
        return date.fromisoformat(value)
    except ValueError:
        raise typer.BadParameter(f"日期无效：{value}；请检查年月日是否存在") from None


def _parse_date_range(
    start_value: str | None,
    end_value: str | None,
    default_start: date,
    default_end: date,
    single_date: bool = False,
) -> tuple[date, date]:
    if end_value and not start_value:
        raise typer.BadParameter("指定 --end-date 时必须同时指定 --start-date")
    start_date = _parse_date(start_value) if start_value else default_start
    if end_value:
        end_date = _parse_date(end_value)
    elif start_value and single_date:
        end_date = start_date
    else:
        end_date = default_end
    _validate_date_range(start_date, end_date)
    return start_date, end_date


def _validate_date_range(
    start_date: date,
    end_date: date,
) -> None:
    if end_date < start_date:
        raise typer.BadParameter(f"结束日期 {end_date} 不能早于开始日期 {start_date}")


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


def _parse_decimal(
    value: str,
    name: str,
    positive: bool = False,
) -> Decimal:
    try:
        result = Decimal(value)
    except InvalidOperation:
        raise typer.BadParameter(f"{name}格式错误：{value}；请输入数字") from None
    if not result.is_finite() or (positive and result <= 0):
        raise typer.BadParameter(
            f"{name}必须是{'大于 0 的' if positive else '有限'}数字"
        )
    return result


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


def _sync_daily(config: AppConfig, start_date: date, end_date: date) -> None:
    requested_days = (end_date - start_date).days + 1
    store = ParquetMarketStore(config.data_dir)
    partitions: list[dict[str, object]] = []
    skipped_dates: list[str] = []
    total_rows = 0
    LOGGER.info(
        "开始采集全市场日线数据：日期=%s 至 %s；自然日数=%d",
        start_date,
        end_date,
        requested_days,
    )
    with BaoStockClient(config.baostock_proxy) as client:
        for offset in range(requested_days):
            trade_date = start_date + timedelta(days=offset)
            LOGGER.info(
                "正在采集全市场日线：进度=%d/%d；日期=%s",
                offset + 1,
                requested_days,
                trade_date,
            )
            bars = client.fetch_daily_market(trade_date)
            if not bars:
                skipped_dates.append(trade_date.isoformat())
                LOGGER.info("当日没有行情，按非交易日跳过：日期=%s", trade_date)
                continue
            target = store.write_daily_market(trade_date, bars)
            total_rows += len(bars)
            partitions.append(
                {
                    "tradeDate": trade_date.isoformat(),
                    "rows": len(bars),
                    "path": str(target),
                }
            )
            LOGGER.info(
                "日线分区写入完成：日期=%s；记录数=%d；文件=%s",
                trade_date,
                len(bars),
                target,
            )
    LOGGER.info(
        "全市场日线同步完成：请求天数=%d；写入交易日=%d；跳过=%d；总记录=%d",
        requested_days,
        len(partitions),
        len(skipped_dates),
        total_rows,
    )
    _print_json(
        {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "requestedDays": requested_days,
            "writtenDays": len(partitions),
            "skippedDates": skipped_dates,
            "rows": total_rows,
            "partitions": partitions,
        }
    )


def _sync_market_codes(
    config: AppConfig, codes: list[str], start_date: date, end_date: date
) -> None:
    store = ParquetMarketStore(config.data_dir)
    LOGGER.info(
        "开始同步研究数据：股票数=%d；日期=%s 至 %s",
        len(codes),
        start_date,
        end_date,
    )
    result = sync_research_data(
        store,
        config.baostock_proxy,
        codes,
        start_date,
        end_date,
        config.baostock_workers,
    )
    _print_json(
        {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "codes": len(codes),
            "result": result,
        }
    )


def _pre_train(
    config: AppConfig, codes: list[str], start_date: date, end_date: date
) -> None:
    store = ParquetMarketStore(config.data_dir)
    valid_codes = store.valid_research_codes(codes, start_date, end_date)
    missing_codes = [code for code in codes if code not in valid_codes]
    if missing_codes:
        command = (
            f"stock-trading market --codes {','.join(missing_codes)} "
            f"--start-date {start_date.isoformat()} --end-date {end_date.isoformat()}"
        )
        raise ValueError(
            "本地训练行情缺失或分钟聚合不完整："
            f"{','.join(missing_codes)}；请先执行：{command}"
        )
    model_codes = store.model_research_codes(valid_codes, start_date, end_date)
    LOGGER.info(
        "开始重建 Qlib：采集有效=%d；模型有效=%d；日期=%s 至 %s",
        len(valid_codes),
        len(model_codes),
        start_date,
        end_date,
    )
    qlib_result = QlibBinaryStore(store, config.qlib_dir).rebuild(model_codes, start_date, end_date)
    LOGGER.info(
        "训练准备完成：股票数=%d；交易日=%s；标签=%s",
        qlib_result["codes"],
        qlib_result["calendarDays"],
        qlib_result["labels"],
    )
    _print_json(
        {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "codes": model_codes,
            "qlib": qlib_result,
        }
    )


def _train(
    config: AppConfig,
    split: tuple[Decimal, Decimal, Decimal] | None,
) -> None:
    calendar_path = config.qlib_dir / "calendars" / "day.txt"
    if not calendar_path.is_file():
        raise ValueError("Qlib 数据不存在，请先执行 prepare")
    segments = automatic_model_segments(calendar_path, split)
    LOGGER.info(
        "开始训练模型：特征=Alpha158；模型=LightGBM Regression；训练=%s 至 %s；验证=%s 至 %s；测试=%s 至 %s",
        segments.train_start,
        segments.train_end,
        segments.valid_start,
        segments.valid_end,
        segments.test_start,
        segments.test_end,
    )
    training_result = train_model_atomically(
        config.qlib_dir,
        model_root(config.artifact_dir),
        segments,
    )
    LOGGER.info(
        "模型训练完成：样本数=%s；预测交易日=%s",
        training_result["rows"],
        training_result["predictionDays"],
    )
    _print_json({"training": training_result})


def _backtest(config: AppConfig, initial_cash: Decimal) -> None:
    backtest_name = "portfolio-backtest"
    LOGGER.info(
        "开始组合回测：初始资金=%.2f；价格范围=%.2f-%.2f；输出=%s",
        initial_cash,
        config.trading_min_price,
        config.trading_max_price,
        backtest_name,
    )
    current_model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    backtest_result = run_portfolio_backtest(
        config,
        current_model_dir,
        config.artifact_dir / backtest_name,
        initial_cash,
    )
    LOGGER.info(
        "组合回测完成：净收益=%s；净收益率=%s；成交数=%s；最大回撤=%s",
        backtest_result["netProfit"],
        backtest_result["netReturn"],
        backtest_result["closedTrades"],
        backtest_result["maxDrawdown"],
    )
    LOGGER.info("开始自动导入回测制品到 SQLite：数据库=%s", config.state_db_path)
    state_result = StateStore(config.state_db_path).import_research_artifacts(
        current_model_dir,
        config.artifact_dir / backtest_name,
        backtest_name,
    )
    LOGGER.info("回测制品导入 SQLite 完成")
    _print_json({"backtest": backtest_result, "state": state_result})


def _cleanup(config: AppConfig, reference_date: date) -> None:
    LOGGER.info("开始清理持久化数据：基准日期=%s", reference_date)
    market = ParquetMarketStore(config.data_dir).cleanup_minutes(reference_date)
    state = StateStore(config.state_db_path).cleanup(reference_date)
    _print_json(
        {
            "referenceDate": reference_date.isoformat(),
            "market": market,
            "state": state,
        }
    )


def _sync_broker(config: AppConfig, business_date: date) -> None:
    LOGGER.info("开始同步券商事实并恢复委托状态：业务日期=%s", business_date)
    store = StateStore(config.state_db_path)
    result = OrderExecutionOrchestrator(
        config, store, _build_broker(config)
    ).recover_pending_orders(business_date)
    LOGGER.info(
        "券商事实同步和委托恢复完成：业务日期=%s；意图数=%d",
        business_date,
        len(result["intents"]),
    )
    _print_json(result)


def _predict(config: AppConfig, prediction_date: date) -> None:
    LOGGER.info("开始生成横截面预测：预测日期=%s；TopK=5", prediction_date)
    model_dir = resolve_current_model_dir(model_root(config.artifact_dir))
    store = ParquetMarketStore(config.data_dir)
    stocks = store.read_stock_basics()
    live_metadata_codes = live_codes(stocks, prediction_date)
    current_codes = store.tradable_codes_on(live_metadata_codes, prediction_date)
    prediction = predict_alpha158_lightgbm(
        config.qlib_dir,
        model_dir,
        prediction_date,
        5,
        {to_qlib_code(code) for code in current_codes},
    )
    state = StateStore(config.state_db_path).import_prediction_artifact(
        model_dir, Path(prediction["path"])
    )
    LOGGER.info(
        "横截面预测完成：全市场评分数=%s；候选数=%s；文件=%s",
        prediction["universeRows"],
        prediction["topK"],
        prediction["path"],
    )
    _print_json({"prediction": prediction, "state": state})


def _run_trading_window(
    config: AppConfig, business_date: date, morning: bool
) -> None:
    mode_name = "上午卖出" if morning else "下午买入"
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
    selection = (
        selector.select_morning_sell_targets(business_date)
        if morning
        else selector.select_afternoon_buy_target(business_date)
    )
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
            "mode": "MORNING_SELL" if morning else "AFTERNOON_BUY",
            "strategy": config.trading_execution_strategy,
            "selection": selection,
            "executed": executed,
        }
    )

def _update_live_performance(
    config: AppConfig,
    business_date: date,
    external_cash_flow: Decimal,
) -> None:
    LOGGER.info("开始更新实盘绩效：业务日期=%s；外部资金流=%s", business_date, external_cash_flow)
    store = StateStore(config.state_db_path)
    broker = _build_broker(config)
    result = LivePerformanceService(config, store, broker).update(
        business_date, external_cash_flow
    )
    LOGGER.info(
        "实盘绩效更新完成：业务日期=%s；评估决策=%s",
        business_date,
        result["evaluation"]["evaluation"]["decision"],
    )
    _print_json(result)


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
            f"日志：{PUBLIC_LOG_URL}",
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
            f"日志：{PUBLIC_LOG_URL}",
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
        "body": body,
        "group": group,
        "ttl": 600,
    }
    if target_url:
        payload["url"] = target_url
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
# AI_GENERATE_END ---------------------------------------------------------------
