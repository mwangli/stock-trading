# AI_GENERATE_START ---
from __future__ import annotations

from datetime import UTC, date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from stock_trading.market.trade_calendar import trade_dates

SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")


def shanghai_now() -> datetime:
    """返回带时区的上海当前时间，供交易业务统一确定日期和时间。"""
    return datetime.now(SHANGHAI_ZONE)


def shanghai_today() -> date:
    """返回上海时区业务日期。"""
    return shanghai_now().date()


def utc_now() -> datetime:
    """返回带 UTC 时区的当前时间，供持久化时间戳使用。"""
    return datetime.now(UTC)


def count_holding_trade_days(
    buy_date: date,
    end_date: date,
    calendar_path: Path | None = None,
) -> int:
    """按交易日计算持仓天数，买入当天为 0。"""
    if end_date <= buy_date:
        return 0
    if calendar_path is not None and calendar_path.is_file():
        values = (
            date.fromisoformat(value)
            for value in calendar_path.read_text(encoding="utf-8").splitlines()
            if value.strip()
        )
        return sum(buy_date < value <= end_date for value in values)
    values = (
        value
        for year in range(buy_date.year, end_date.year + 1)
        for value in trade_dates(year)
    )
    return sum(buy_date < value <= end_date for value in values)
# AI_GENERATE_END ---
