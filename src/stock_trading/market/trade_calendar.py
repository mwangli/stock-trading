# AI_GENERATE_START ----
from __future__ import annotations

from datetime import date, datetime, time, timedelta
from functools import lru_cache
from importlib.resources import files


@lru_cache(maxsize=None)
def trade_dates(year: int) -> tuple[date, ...]:
    """读取随程序发布的年度 A 股交易日。"""
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
        raise ValueError(f"缺少 {year} 年交易日文件，无法确定交易日") from None
    except ValueError as error:
        raise ValueError(f"{year} 年交易日文件格式错误: {error}") from error
    if not values or any(value.year != year for value in values):
        raise ValueError(f"{year} 年交易日文件内容无效")
    if values != tuple(sorted(set(values))):
        raise ValueError(f"{year} 年交易日文件必须按日期升序且不能重复")
    return values


def latest_trade_date(today: date) -> date:
    """返回内置日历中不晚于指定日期的最近交易日。"""
    candidates = [value for value in trade_dates(today.year) if value <= today]
    if candidates:
        return candidates[-1]
    previous_dates = trade_dates(today.year - 1)
    if previous_dates:
        return previous_dates[-1]
    raise ValueError(f"{today} 之前没有可用交易日")


def latest_completed_trade_date(
    now: datetime,
    completion_time: time = time(16, 0),
) -> date:
    """返回当前时刻之前已经收盘的最近交易日。"""
    cutoff = now.date()
    if now.time().replace(tzinfo=None) < completion_time:
        cutoff -= timedelta(days=1)
    return latest_trade_date(cutoff)
# AI_GENERATE_END ----
