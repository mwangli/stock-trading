# AI_GENERATE_START -----------
from __future__ import annotations

import argparse
import json
import time
from collections import defaultdict
from datetime import date
from decimal import Decimal

from stock_trading.config import AppConfig
from stock_trading.broker.client import ZXBrokerClient
from stock_trading.market.audit import audit_minute_days, expected_bar_ends
from stock_trading.market.baostock_client import BaoStockClient, BaoStockError
from stock_trading.market.models import AdjustmentFactor, DailyBar, MinuteBar
from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.model import ModelSegments, train_alpha158_lightgbm
from stock_trading.research.backtest import BacktestConfig, PortfolioBacktester
from stock_trading.research.qlib_store import QlibBinaryStore
from stock_trading.state.store import StateStore


def main() -> None:
    parser = _build_parser()
    arguments = parser.parse_args()
    config = AppConfig.from_env()

    if arguments.command == "sync-daily":
        _sync_daily(config, arguments.trade_date)
    elif arguments.command == "sync-minute":
        _sync_minute(config, arguments.code, arguments.start_date, arguments.end_date)
    elif arguments.command == "probe-minute":
        _probe_minute(config, arguments.code, arguments.trade_date)
    elif arguments.command == "show-boundaries":
        _show_boundaries(arguments.trade_date)
    elif arguments.command == "sync-research":
        _sync_research(
            config,
            arguments.codes,
            arguments.start_date,
            arguments.end_date,
        )
    elif arguments.command == "build-qlib":
        _build_qlib(config, arguments.codes)
    elif arguments.command == "train-baseline":
        _train_baseline(config, arguments)
    elif arguments.command == "backtest-portfolio":
        _backtest_portfolio(config, arguments)
    elif arguments.command == "sync-state":
        _sync_state(config, arguments.backtest_name)
    elif arguments.command == "show-state":
        _print_json(StateStore(config.state_db_path).summary())
    elif arguments.command == "recover-state":
        recovered = StateStore(config.state_db_path).recover_interrupted_runs()
        _print_json({"recoveredRuns": recovered} | StateStore(config.state_db_path).summary())
    elif arguments.command == "sync-broker-shadow":
        _sync_broker_shadow(config, arguments.business_date)
    else:
        parser.error(f"Unsupported command: {arguments.command}")


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="stock-trading")
    subparsers = parser.add_subparsers(dest="command", required=True)

    daily = subparsers.add_parser("sync-daily", help="Download one full-market daily partition")
    daily.add_argument("--date", dest="trade_date", type=date.fromisoformat, required=True)

    minute = subparsers.add_parser("sync-minute", help="Download 5-minute bars for one stock")
    minute.add_argument("--code", required=True)
    minute.add_argument("--start", dest="start_date", type=date.fromisoformat, required=True)
    minute.add_argument("--end", dest="end_date", type=date.fromisoformat, required=True)

    probe = subparsers.add_parser("probe-minute", help="Audit one stock-day against its daily bar")
    probe.add_argument("--code", required=True)
    probe.add_argument("--date", dest="trade_date", type=date.fromisoformat, required=True)

    boundaries = subparsers.add_parser("show-boundaries", help="Show expected BaoStock 5-minute ends")
    boundaries.add_argument("--date", dest="trade_date", type=date.fromisoformat, required=True)

    research = subparsers.add_parser(
        "sync-research", help="Download daily, adjustment and minute history for a fixed universe"
    )
    research.add_argument("--codes", nargs="+", required=True)
    research.add_argument("--start", dest="start_date", type=date.fromisoformat, required=True)
    research.add_argument("--end", dest="end_date", type=date.fromisoformat, required=True)

    qlib = subparsers.add_parser("build-qlib", help="Rebuild Qlib binaries from raw Parquet")
    qlib.add_argument("--codes", nargs="+", required=True)

    training = subparsers.add_parser(
        "train-baseline", help="Train Alpha158 with the custom T+1 label"
    )
    training.add_argument("--train-start", type=date.fromisoformat, required=True)
    training.add_argument("--train-end", type=date.fromisoformat, required=True)
    training.add_argument("--valid-start", type=date.fromisoformat, required=True)
    training.add_argument("--valid-end", type=date.fromisoformat, required=True)
    training.add_argument("--test-start", type=date.fromisoformat, required=True)
    training.add_argument("--test-end", type=date.fromisoformat, required=True)

    backtest = subparsers.add_parser(
        "backtest-portfolio", help="Backtest one-position afternoon-buy next-morning-sell trading"
    )
    backtest.add_argument("--initial-cash", type=float, default=10_000.0)
    backtest.add_argument("--min-price", type=float, default=5.0)
    backtest.add_argument("--max-price", type=float, default=20.0)
    backtest.add_argument("--commission-rate", type=float, default=0.0003)
    backtest.add_argument("--min-commission", type=float, default=5.0)
    backtest.add_argument("--stamp-duty-rate", type=float, default=0.0005)
    backtest.add_argument("--transfer-fee-rate", type=float, default=0.00001)
    backtest.add_argument("--slippage-bps", type=float, default=10.0)
    backtest.add_argument("--buy-slices", type=int, default=1)
    backtest.add_argument("--sell-slices", type=int, default=1)
    backtest.add_argument("--allowed-prefixes", nargs="*", default=[])
    backtest.add_argument("--output-name", default="portfolio-backtest")

    state_sync = subparsers.add_parser(
        "sync-state", help="Import model, predictions and shadow backtest into SQLite"
    )
    state_sync.add_argument("--backtest-name", default="portfolio-backtest-realistic")
    subparsers.add_parser("show-state", help="Show SQLite state counts and safety setting")
    subparsers.add_parser("recover-state", help="Fail conservatively any interrupted task runs")
    broker_sync = subparsers.add_parser(
        "sync-broker-shadow", help="Read broker facts and persist a shadow snapshot"
    )
    broker_sync.add_argument(
        "--date", dest="business_date", type=date.fromisoformat, default=date.today()
    )
    return parser


def _sync_daily(config: AppConfig, trade_date: date) -> None:
    with BaoStockClient(config.baostock_proxy) as client:
        bars = client.fetch_daily_market(trade_date)
    target = ParquetMarketStore(config.data_dir).write_daily_market(trade_date, bars)
    _print_json({"tradeDate": trade_date.isoformat(), "rows": len(bars), "path": str(target)})


def _sync_minute(config: AppConfig, code: str, start_date: date, end_date: date) -> None:
    if end_date < start_date:
        raise ValueError("end date must not be earlier than start date")
    with BaoStockClient(config.baostock_proxy) as client:
        bars = client.fetch_minutes(code, start_date, end_date)
    grouped: dict[date, list] = defaultdict(list)
    for bar in bars:
        grouped[bar.trade_date].append(bar)
    store = ParquetMarketStore(config.data_dir)
    paths = [str(store.write_minutes(code, trade_date, values)) for trade_date, values in grouped.items()]
    _print_json({"code": code, "rows": len(bars), "partitions": paths})


def _probe_minute(config: AppConfig, code: str, trade_date: date) -> None:
    with BaoStockClient(config.baostock_proxy) as client:
        minute_bars = client.fetch_minutes(code, trade_date, trade_date)
        daily_bar = client.fetch_daily(code, trade_date)
    daily = {trade_date: daily_bar} if daily_bar else {}
    audits = audit_minute_days(minute_bars, daily)
    _print_json({"code": code, "tradeDate": trade_date.isoformat(), "audits": [item.as_record() for item in audits]})


def _show_boundaries(trade_date: date) -> None:
    values = expected_bar_ends(trade_date)
    _print_json(
        {
            "tradeDate": trade_date.isoformat(),
            "barCount": len(values),
            "barEnds": [value.isoformat() for value in values],
        }
    )


def _sync_research(
    config: AppConfig, codes: list[str], start_date: date, end_date: date
) -> None:
    if end_date < start_date:
        raise ValueError("end date must not be earlier than start date")
    store = ParquetMarketStore(config.data_dir)
    rows: dict[str, dict[str, int | str]] = {}
    for code in codes:
        if store.research_data_covers(code, start_date, end_date):
            rows[code] = {"status": "skipped"}
            continue
        daily, factors, minutes = _fetch_research_code(
            config, code, start_date, end_date
        )
        grouped: dict[date, list] = defaultdict(list)
        for bar in minutes:
            grouped[bar.trade_date].append(bar)
        for trade_date, values in grouped.items():
            store.write_minutes(code, trade_date, values)
        store.write_daily_history(code, daily)
        store.write_adjustment_factors(code, factors)
        store.write_research_manifest(
            code, start_date, end_date, len(daily), len(grouped)
        )
        rows[code] = {
            "status": "downloaded",
            "daily": len(daily),
            "adjustmentFactors": len(factors),
            "minuteBars": len(minutes),
            "minuteDays": len(grouped),
        }
    _print_json(
        {
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "codes": rows,
        }
    )


def _fetch_research_code(
    config: AppConfig, code: str, start_date: date, end_date: date
) -> tuple[list[DailyBar], list[AdjustmentFactor], list[MinuteBar]]:
    for attempt in range(1, 4):
        try:
            with BaoStockClient(config.baostock_proxy) as client:
                daily = client.fetch_daily_history(code, start_date, end_date)
                factors = client.fetch_adjustment_factors(code, date(1990, 1, 1), end_date)
                minutes = client.fetch_minutes(code, start_date, end_date)
            if not factors:
                factors = [AdjustmentFactor(code, start_date, Decimal("1"))]
            return daily, factors, minutes
        except (BaoStockError, OSError, TimeoutError):
            if attempt == 3:
                raise
            time.sleep(attempt * 3)
    raise RuntimeError(f"unreachable retry state for {code}")


def _build_qlib(config: AppConfig, codes: list[str]) -> None:
    result = QlibBinaryStore(ParquetMarketStore(config.data_dir), config.qlib_dir).rebuild(codes)
    _print_json(result)


def _train_baseline(config: AppConfig, arguments: argparse.Namespace) -> None:
    segments = ModelSegments(
        train_start=arguments.train_start,
        train_end=arguments.train_end,
        valid_start=arguments.valid_start,
        valid_end=arguments.valid_end,
        test_start=arguments.test_start,
        test_end=arguments.test_end,
    )
    result = train_alpha158_lightgbm(
        config.qlib_dir,
        config.artifact_dir / "alpha158-lightgbm",
        segments,
    )
    _print_json(result)


def _backtest_portfolio(config: AppConfig, arguments: argparse.Namespace) -> None:
    backtest_config = BacktestConfig(
        initial_cash=arguments.initial_cash,
        min_price=arguments.min_price,
        max_price=arguments.max_price,
        commission_rate=arguments.commission_rate,
        min_commission=arguments.min_commission,
        stamp_duty_rate=arguments.stamp_duty_rate,
        transfer_fee_rate=arguments.transfer_fee_rate,
        slippage_bps=arguments.slippage_bps,
        buy_slices=arguments.buy_slices,
        sell_slices=arguments.sell_slices,
        allowed_prefixes=tuple(arguments.allowed_prefixes),
    )
    result = PortfolioBacktester(
        ParquetMarketStore(config.data_dir),
        config.qlib_dir / "calendars" / "day.txt",
        config.artifact_dir / "alpha158-lightgbm" / "top5.parquet",
        backtest_config,
    ).run(config.artifact_dir / arguments.output_name)
    _print_json(result)


def _sync_state(config: AppConfig, backtest_name: str) -> None:
    result = StateStore(config.state_db_path).import_research_artifacts(
        config.artifact_dir / "alpha158-lightgbm",
        config.artifact_dir / backtest_name,
        backtest_name,
    )
    _print_json(result)


def _sync_broker_shadow(config: AppConfig, business_date: date) -> None:
    store = StateStore(config.state_db_path)
    client = ZXBrokerClient(
        config,
        write_guard=store.authorize_real_write,
        cancel_guard=store.authorize_cancel_write,
    )
    snapshot = client.read_shadow_snapshot()
    result = store.sync_broker_snapshot(snapshot, business_date.isoformat())
    _print_json(result)


def _print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))
# AI_GENERATE_END -----------
