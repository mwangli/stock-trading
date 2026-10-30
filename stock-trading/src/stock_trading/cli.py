# AI_GENERATE_START -
from __future__ import annotations

import argparse
import json
from collections import defaultdict
from datetime import date

from stock_trading.config import AppConfig
from stock_trading.market.audit import audit_minute_days, expected_bar_ends
from stock_trading.market.baostock_client import BaoStockClient
from stock_trading.market.storage import ParquetMarketStore


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


def _print_json(value: object) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2))
# AI_GENERATE_END -
