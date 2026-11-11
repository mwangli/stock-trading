# AI_GENERATE_START
from __future__ import annotations

from datetime import date, datetime, time, timedelta
from decimal import Decimal, ROUND_HALF_UP
from functools import lru_cache
from importlib.resources import files

import pandas as pd


def normalize_instrument(value: object, market: str | None = None) -> str:
    """将行情、券商或 Qlib 代码统一为 SH600000 格式。"""
    raw = str(value or "").strip().upper()
    if "." in raw:
        exchange, symbol = raw.split(".", maxsplit=1)
    elif len(raw) == 8 and raw[:2] in {"SH", "SZ"}:
        exchange, symbol = raw[:2], raw[2:]
    elif len(raw) == 6 and raw.isdigit():
        normalized_market = (market or "").strip().upper()
        if normalized_market.startswith("SH"):
            exchange = "SH"
        elif normalized_market.startswith("SZ"):
            exchange = "SZ"
        else:
            exchange = "SH" if raw.startswith("6") else "SZ"
        symbol = raw
    else:
        raise ValueError(f"不支持的证券代码: {value}")

    if exchange not in {"SH", "SZ"} or len(symbol) != 6 or not symbol.isdigit():
        raise ValueError(f"不支持的证券代码: {value}")
    return f"{exchange}{symbol}"


def to_market_code(instrument: object) -> str:
    """将内部标准证券代码转换为行情存储代码。"""
    normalized = normalize_instrument(instrument)
    return f"{normalized[:2].lower()}.{normalized[2:]}"


def to_broker_code(instrument: object) -> str:
    """将内部标准证券代码转换为券商六位证券代码。"""
    return normalize_instrument(instrument)[2:]


class MissingTradeCalendarError(ValueError):
    """本地尚无该年度经数据源完整确认的交易日历。"""


@lru_cache(maxsize=None)
def trade_dates(year: int) -> tuple[date, ...]:
    """读取随程序发布的年度交易日历。"""
    resource = files("stock_trading.market").joinpath(
        "trade_dates", f"{year}.txt"
    )
    try:
        values = tuple(
            date.fromisoformat(line.strip())
            for line in resource.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    except FileNotFoundError:
        raise MissingTradeCalendarError(
            f"缺少 {year} 年交易日历文件: trade_dates/{year}.txt；"
            "该文件属于程序发布资源，请重新安装包含完整交易日历的版本"
        ) from None
    except ValueError as error:
        raise ValueError(f"{year} 年交易日文件格式错误: {error}") from error
    if not values or any(value.year != year for value in values):
        raise ValueError(f"{year} 年交易日文件内容无效")
    if values != tuple(sorted(set(values))):
        raise ValueError(f"{year} 年交易日文件必须按日期升序且不能重复")
    return values



def trading_dates_between(start_date: date, end_date: date) -> list[date]:
    """返回指定范围内的交易日。"""
    years = tuple(range(start_date.year, end_date.year + 1))
    dates = [
        value
        for year in years
        for value in trade_dates(year)
        if start_date <= value <= end_date
    ]
    return dates


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


def shift_trade_date(value: date, offset: int) -> date:
    """按发布的交易日历偏移，拒绝非交易日及缺失日历，不用自然日猜测。"""
    calendar = trade_dates(value.year)
    if value not in calendar:
        raise ValueError(f"不是交易日: {value}")
    index = calendar.index(value) + offset
    year = value.year
    while index < 0:
        year -= 1
        calendar = trade_dates(year)
        index += len(calendar)
    while index >= len(calendar):
        index -= len(calendar)
        year += 1
        calendar = trade_dates(year)
    return calendar[index]


def count_holding_trade_days(
    buy_date: date,
    end_date: date,
) -> int:
    """按交易日计算持仓天数，买入当天为 0。"""
    if end_date <= buy_date:
        return 0
    values = (
        value
        for year in range(buy_date.year, end_date.year + 1)
        for value in trade_dates(year)
    )
    return sum(buy_date < value <= end_date for value in values)

def research_codes(
    stocks: pd.DataFrame, start_date: date, end_date: date
) -> list[str]:
    """返回研究区间内有效的沪深主板股票。"""
    codes: list[str] = []
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(("sh.60", "sz.00")):
            continue
        ipo_date = (
            pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        )
        out_date = (
            pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        )
        if (ipo_date and ipo_date > end_date) or (
            out_date and out_date < start_date
        ):
            continue
        codes.append(code)
    return sorted(set(codes))


def live_codes(
    stocks: pd.DataFrame,
    snapshot_date: date,
    *,
    include_special_boards: bool = False,
) -> list[str]:
    """返回指定快照日可用的实时交易股票范围。"""
    codes: list[str] = []
    prefixes = ("sh.60", "sz.00")
    if include_special_boards:
        prefixes += ("sh.68", "sz.30")
    for row in stocks.itertuples(index=False):
        code = str(row.code).lower()
        if not code.startswith(prefixes):
            continue
        ipo_date = (
            pd.Timestamp(row.ipo_date).date() if pd.notna(row.ipo_date) else None
        )
        out_date = (
            pd.Timestamp(row.out_date).date() if pd.notna(row.out_date) else None
        )
        if (ipo_date and ipo_date > snapshot_date) or (
            out_date and out_date < snapshot_date
        ):
            continue
        if out_date and out_date <= snapshot_date + timedelta(days=30):
            continue
        if str(row.status) not in {"", "1"} or str(row.trade_status) not in {
            "",
            "1",
        }:
            continue
        codes.append(code)
    return sorted(set(codes))

def recent_limit_down_count(
    history: pd.DataFrame,
    code: str,
    end_date: date,
    lookback_days: int,
) -> int:
    """统计截至指定日期最近若干交易日的收盘跌停次数。"""
    if "trade_date" in history.columns:
        dates = pd.to_datetime(history["trade_date"])
    else:
        dates = pd.to_datetime(history.index)
    available = history.loc[dates <= pd.Timestamp(end_date)].tail(lookback_days)
    return sum(
        float(row["close"])
        <= limit_price(
            float(row["preclose"]),
            limit_rate(code, row),
            -1,
        )
        + 0.001
        for _, row in available.iterrows()
    )


def limit_rate(code: str, daily: pd.Series) -> float:
    if str(daily["is_st"]) == "1":
        return 0.05
    if code.startswith("sz.300") or code.startswith("sh.688"):
        return 0.20
    return 0.10


def limit_price(preclose: float, rate: float, direction: int) -> float:
    value = Decimal(str(preclose)) * (
        Decimal("1") + Decimal(direction) * Decimal(str(rate))
    )
    return float(value.quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))
# AI_GENERATE_END
