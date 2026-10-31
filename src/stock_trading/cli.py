# AI_GENERATE_START --------------------------------------------------------
from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sys
import urllib.request
from datetime import date, datetime, timedelta
from decimal import Decimal, InvalidOperation
from pathlib import Path
from time import perf_counter

from stock_trading.config import AppConfig
from stock_trading.initialization import InitializationService
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.broker.captcha import BaiduCaptchaSolver
from stock_trading.market.baostock_client import BaoStockClient
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.market.sync import sync_research_data
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
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.state.store import StateStore
from stock_trading.trading.execution import OrderExecutionOrchestrator
from stock_trading.trading.orchestrator import LiveTradingOrchestrator
from stock_trading.trading.performance import LivePerformanceService

LOGGER = logging.getLogger(__name__)
BARK_PUSH_URL = "https://api.day.app/push"
PUBLIC_LOG_URL = "http://117.72.216.67/logs/run.log"


COMMANDS = (
    ("init", "初始化完整交易环境", "[--force]"),
    (
        "market",
        "更新市场行情数据",
        "<all|股票代码列表> [开始日期] [结束日期]",
    ),
    (
        "prepare",
        "校验数据并重建 Qlib",
        "<股票代码列表> [开始日期] [结束日期]",
    ),
    (
        "train",
        "训练Alpha158+LightGBM模型",
        "[训练比例:验证比例:测试比例]",
    ),
    ("backtest", "执行单持仓T+1回测", "[资金规模]"),
    ("predict", "生成Top5预测", "[日期]"),
    ("sell", "执行上午实时卖出", "[日期]"),
    ("buy", "执行下午实时买入", "[日期]"),
    ("broker", "同步券商事实并恢复委托", "[日期]"),
    ("evaluate", "同步绩效并执行模型评估", "[日期] [资金规模]"),
    ("clean", "压缩数据并执行分级清理", "[日期]"),
)


class ChineseArgumentParser(argparse.ArgumentParser):
    """提供中文帮助标题和中文参数错误的命令行解析器。"""

    def __init__(self, *args: object, **kwargs: object) -> None:
        show_help = bool(kwargs.pop("add_help", True))
        kwargs["add_help"] = False
        super().__init__(*args, **kwargs)
        self._positionals.title = "位置参数"
        self._optionals.title = "选项"
        if show_help:
            self.add_argument("-h", "--help", action="help", help="显示帮助信息并退出")

    def format_usage(self) -> str:
        return super().format_usage().replace("usage:", "用法：", 1)

    def format_help(self) -> str:
        return super().format_help().replace("usage:", "用法：", 1)

    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        self.exit(2, f"{self.prog}: 参数错误：{_translate_parser_error(message)}\n")


class CompactCommandParser(ChineseArgumentParser):
    """仅显示一行说明和命令列表的根命令解析器。"""

    def format_help(self) -> str:
        command_lines = "\n".join(
            f"\tstock-trading {name:<9}{summary}; 参数: {parameters}"
            for name, summary, parameters in COMMANDS
        )
        return f"{self.description}\n{command_lines}\n"


def main() -> None:
    configure_logging()
    parser = _build_parser()
    arguments = parser.parse_args()
    if arguments.command is None:
        parser.print_help()
        return
    started_at = perf_counter()
    config: AppConfig | None = None
    try:
        config = AppConfig.from_env()
        LOGGER.info(
            "业务命令开始：%s；参数=%s",
            arguments.command,
            json.dumps(
                {
                    key: value
                    for key, value in vars(arguments).items()
                    if not key.startswith("_") and key != "command"
                },
                ensure_ascii=False,
                default=str,
            ),
        )
        _dispatch(arguments, config, parser)
    except Exception as error:
        LOGGER.error(
            "业务命令失败：%s；耗时=%.2f 秒；原因=%s",
            arguments.command,
            perf_counter() - started_at,
            error,
        )
        _push_system_failure(config, arguments.command, error)
        raise
    except KeyboardInterrupt:
        LOGGER.warning(
            "收到 Ctrl+C，业务命令已终止：%s；耗时=%.2f 秒",
            arguments.command,
            perf_counter() - started_at,
        )
        raise SystemExit(130) from None
    LOGGER.info(
        "业务命令完成：%s；总耗时=%.2f 秒",
        arguments.command,
        perf_counter() - started_at,
    )


def _dispatch(
    arguments: argparse.Namespace,
    config: AppConfig,
    parser: argparse.ArgumentParser,
) -> None:
    """将精简后的命令分派到对应业务流程。"""

    command_parser = arguments._command_parser
    values = arguments.values
    today = date.today()
    if arguments.command == "init":
        _require_argument_count(values, 0, 0, command_parser)
        result = InitializationService(
            config, lambda: _build_broker(config)
        ).run(force=bool(arguments.force))
        _print_json(result)
    elif arguments.command == "market":
        _require_argument_count(values, 1, 3, command_parser)
        if values[0].lower() == "all":
            start_date, end_date = _parse_optional_date_range(
                values[1:], command_parser, today
            )
            _sync_daily(config, start_date, end_date)
        else:
            codes = _parse_stock_codes(values[0], command_parser)
            start_date, end_date = _parse_history_date_range(
                values[1:], command_parser, today
            )
            _sync_market_codes(config, codes, start_date, end_date)
    elif arguments.command == "prepare":
        _require_argument_count(values, 1, 3, command_parser)
        codes = _parse_stock_codes(values[0], command_parser)
        start_date, end_date = _parse_history_date_range(
            values[1:], command_parser, today
        )
        _pre_train(config, codes, start_date, end_date)
    elif arguments.command == "train":
        _require_argument_count(values, 0, 1, command_parser)
        split = _parse_model_split(values[0], command_parser) if values else None
        _train(config, split)
    elif arguments.command == "backtest":
        _require_argument_count(values, 0, 1, command_parser)
        initial_cash = (
            _parse_decimal(values[0], "初始资金", command_parser, positive=True)
            if values
            else Decimal("10000")
        )
        _backtest(config, initial_cash)
    elif arguments.command == "predict":
        prediction_date = _parse_optional_date(values, command_parser, today)
        _predict(config, prediction_date)
    elif arguments.command == "clean":
        cleanup_date = _parse_optional_date(values, command_parser, today)
        _cleanup(config, cleanup_date)
    elif arguments.command == "broker":
        business_date = _parse_optional_date(values, command_parser, today)
        _sync_broker(config, business_date)
    elif arguments.command == "sell":
        business_date = _parse_optional_date(values, command_parser, today)
        _run_trading_window(config, business_date, morning=True)
    elif arguments.command == "buy":
        business_date = _parse_optional_date(values, command_parser, today)
        _run_trading_window(config, business_date, morning=False)
    elif arguments.command == "evaluate":
        _require_argument_count(values, 0, 2, command_parser)
        business_date = _parse_date(values[0], command_parser) if values else today
        external_cash_flow = (
            _parse_decimal(values[1], "外部资金流", command_parser)
            if len(values) == 2
            else Decimal("0")
        )
        _update_live_performance(config, business_date, external_cash_flow)
    else:
        parser.error(f"不支持的命令：{arguments.command}")


def _build_parser() -> argparse.ArgumentParser:
    parser = CompactCommandParser(
        prog="stock-trading",
        description=(
            "A股T+1数据.模型,交易和评估命令,日期统一使用YYYY-MM-DD"
            " <参数>必填,[参数]可选,|表示二选一."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(
        dest="command",
        title="可用命令",
        metavar="命令",
        parser_class=ChineseArgumentParser,
    )
    for name, summary, parameters in COMMANDS:
        _add_command(subparsers, name, summary, parameters)
    return parser


def _add_command(
    subparsers: argparse._SubParsersAction,
    name: str,
    summary: str,
    parameters: str,
) -> None:
    command = subparsers.add_parser(
        name,
        help=f"{summary}; 参数: {parameters}",
        add_help=False,
    )
    if name == "init":
        command.add_argument("--force", action="store_true")
    command.add_argument("values", nargs="*", metavar="参数")
    command.set_defaults(_command_parser=command)


def _translate_parser_error(message: str) -> str:
    invalid_choice = re.search(r"invalid choice: '([^']+)'", message)
    if invalid_choice:
        return f"未知命令“{invalid_choice.group(1)}”，请使用 --help 查看可用命令"
    if message.startswith("unrecognized arguments:"):
        return f"无法识别的参数：{message.partition(':')[2].strip()}"
    if message.startswith("the following arguments are required:"):
        return f"缺少必要参数：{message.partition(':')[2].strip()}"
    return message


def _require_argument_count(
    values: list[str],
    minimum: int,
    maximum: int,
    parser: argparse.ArgumentParser,
) -> None:
    if minimum <= len(values) <= maximum:
        return
    if minimum == maximum:
        parser.error(f"需要 {minimum} 个参数，实际收到 {len(values)} 个")
    parser.error(f"只接受 {minimum} 至 {maximum} 个参数，实际收到 {len(values)} 个")


def _parse_date(value: str, parser: argparse.ArgumentParser) -> date:
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        parser.error(f"日期格式错误：{value}；请使用 YYYY-MM-DD，例如 2026-10-31")
    try:
        return date.fromisoformat(value)
    except ValueError:
        parser.error(f"日期无效：{value}；请检查年月日是否存在")
    raise AssertionError("日期解析失败后应由参数解析器退出")


def _parse_optional_date(
    values: list[str], parser: argparse.ArgumentParser, default: date
) -> date:
    _require_argument_count(values, 0, 1, parser)
    return _parse_date(values[0], parser) if values else default


def _parse_optional_date_range(
    values: list[str], parser: argparse.ArgumentParser, default: date
) -> tuple[date, date]:
    _require_argument_count(values, 0, 2, parser)
    if not values:
        return default, default
    start_date = _parse_date(values[0], parser)
    end_date = _parse_date(values[1], parser) if len(values) == 2 else start_date
    _validate_date_range(start_date, end_date, parser)
    return start_date, end_date


def _parse_history_date_range(
    values: list[str], parser: argparse.ArgumentParser, today: date
) -> tuple[date, date]:
    _require_argument_count(values, 0, 2, parser)
    if not values:
        return today - timedelta(days=365 * 3), today
    start_date = _parse_date(values[0], parser)
    end_date = _parse_date(values[1], parser) if len(values) == 2 else today
    _validate_date_range(start_date, end_date, parser)
    return start_date, end_date


def _validate_date_range(
    start_date: date, end_date: date, parser: argparse.ArgumentParser
) -> None:
    if end_date < start_date:
        parser.error(f"结束日期 {end_date} 不能早于开始日期 {start_date}")


def _parse_stock_code(value: str, parser: argparse.ArgumentParser) -> str:
    normalized = value.strip().lower()
    if not re.fullmatch(r"(?:sh|sz)\.\d{6}", normalized):
        parser.error(f"股票代码格式错误：{value}；示例：sh.600000 或 sz.000001")
    return normalized


def _parse_stock_codes(value: str, parser: argparse.ArgumentParser) -> list[str]:
    raw_codes = [item.strip() for item in value.split(",") if item.strip()]
    if not raw_codes:
        parser.error("股票代码列表不能为空")
    return list(dict.fromkeys(_parse_stock_code(code, parser) for code in raw_codes))


def _parse_decimal(
    value: str,
    name: str,
    parser: argparse.ArgumentParser,
    positive: bool = False,
) -> Decimal:
    try:
        result = Decimal(value)
    except InvalidOperation:
        parser.error(f"{name}格式错误：{value}；请输入数字")
    if not result.is_finite() or (positive and result <= 0):
        parser.error(f"{name}必须是{'大于 0 的' if positive else '有限'}数字")
    return result


def _parse_model_split(
    value: str, parser: argparse.ArgumentParser
) -> tuple[Decimal, Decimal, Decimal]:
    parts = value.split(":")
    if len(parts) != 3:
        parser.error("训练比例格式错误；请使用 训练:验证:测试，例如 6:3:1")
    try:
        ratios = tuple(Decimal(part) for part in parts)
    except InvalidOperation:
        parser.error("训练比例必须是数字；例如 6:3:1")
    if any(not ratio.is_finite() or ratio <= 0 for ratio in ratios):
        parser.error("训练、验证和测试比例必须全部大于 0")
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
        store, config.baostock_proxy, codes, start_date, end_date
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
            "stock-trading market "
            f"{','.join(missing_codes)} {start_date.isoformat()} {end_date.isoformat()}"
        )
        raise ValueError(
            "本地训练行情缺失或分钟聚合不完整："
            f"{','.join(missing_codes)}；请先执行：{command}"
        )
    LOGGER.info(
        "开始重建 Qlib：股票数=%d；日期=%s 至 %s",
        len(valid_codes),
        start_date,
        end_date,
    )
    qlib_result = QlibBinaryStore(store, config.qlib_dir).rebuild(valid_codes)
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
            "codes": valid_codes,
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
    prediction = predict_alpha158_lightgbm(
        config.qlib_dir,
        model_dir,
        prediction_date,
        5,
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
# AI_GENERATE_END --------------------------------------------------------
