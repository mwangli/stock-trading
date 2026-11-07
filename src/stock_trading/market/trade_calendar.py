# AI_GENERATE_START ------
from __future__ import annotations

import json
from datetime import date, datetime, time, timedelta
from functools import lru_cache
from importlib.resources import files
from pathlib import Path


class MissingTradeCalendarError(ValueError):
    """本地尚无该年度经数据源完整确认的交易日历。"""


@lru_cache(maxsize=None)
def trade_dates(year: int, calendar_dir: Path | None = None) -> tuple[date, ...]:
    """优先读取随程序发布的日历，否则读取经供应商确认的完整年度缓存。"""
    resource = files("stock_trading.market").joinpath(
        f"trade_dates_{year}.txt"
    )
    try:
        values = tuple(
            date.fromisoformat(line.strip())
            for line in resource.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    except FileNotFoundError:
        cache = calendar_dir / f"trade_dates_{year}.json" if calendar_dir else None
        if cache is None or not cache.is_file():
            raise MissingTradeCalendarError(
                f"缺少 {year} 年交易日历；请通过 stock-prepare/stock-rank 获取数据源完整年度日历"
            ) from None
        payload = json.loads(cache.read_text(encoding="utf-8"))
        if (
            payload.get("year") != year
            or payload.get("source") not in {"baostock", "tushare"}
            or payload.get("completeYear") is not True
        ):
            raise ValueError(f"{year} 年交易日历缓存来源或覆盖范围无效")
        values = tuple(date.fromisoformat(value) for value in payload["tradeDates"])
    except ValueError as error:
        raise ValueError(f"{year} 年交易日文件格式错误: {error}") from error
    if not values or any(value.year != year for value in values):
        raise ValueError(f"{year} 年交易日文件内容无效")
    if values != tuple(sorted(set(values))):
        raise ValueError(f"{year} 年交易日文件必须按日期升序且不能重复")
    return values


def latest_trade_date(today: date, calendar_dir: Path | None = None) -> date:
    """返回内置日历中不晚于指定日期的最近交易日。"""
    candidates = [value for value in trade_dates(today.year, calendar_dir) if value <= today]
    if candidates:
        return candidates[-1]
    previous_dates = trade_dates(today.year - 1, calendar_dir)
    if previous_dates:
        return previous_dates[-1]
    raise ValueError(f"{today} 之前没有可用交易日")


def latest_completed_trade_date(
    now: datetime,
    completion_time: time = time(16, 0),
    calendar_dir: Path | None = None,
) -> date:
    """返回当前时刻之前已经收盘的最近交易日。"""
    cutoff = now.date()
    if now.time().replace(tzinfo=None) < completion_time:
        cutoff -= timedelta(days=1)
    return latest_trade_date(cutoff, calendar_dir)


def shift_trade_date(value: date, offset: int, calendar_dir: Path | None = None) -> date:
    """按发布的交易日历偏移，拒绝非交易日及缺失日历，不用自然日猜测。"""
    calendar = trade_dates(value.year, calendar_dir)
    if value not in calendar:
        raise ValueError(f"不是交易日: {value}")
    index = calendar.index(value) + offset
    year = value.year
    while index < 0:
        year -= 1
        calendar = trade_dates(year, calendar_dir)
        index += len(calendar)
    while index >= len(calendar):
        index -= len(calendar)
        year += 1
        calendar = trade_dates(year, calendar_dir)
    return calendar[index]


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
# AI_GENERATE_END ------
