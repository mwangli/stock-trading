# AI_GENERATE_START -
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, time, timedelta
from decimal import Decimal
from typing import Iterable

from stock_trading.market.models import DailyBar, MinuteBar, SHANGHAI


@dataclass(frozen=True)
class MinuteDayAudit:
    code: str
    trade_date: date
    bar_count: int
    first_bar_end: datetime | None
    last_bar_end: datetime | None
    missing_bar_ends: tuple[str, ...]
    extra_bar_ends: tuple[str, ...]
    relative_errors: dict[str, float]

    @property
    def complete(self) -> bool:
        return self.bar_count == 48 and not self.missing_bar_ends and not self.extra_bar_ends

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        record["first_bar_end"] = self.first_bar_end.isoformat() if self.first_bar_end else None
        record["last_bar_end"] = self.last_bar_end.isoformat() if self.last_bar_end else None
        record["complete"] = self.complete
        return record


def expected_bar_ends(trade_date: date) -> tuple[datetime, ...]:
    morning = _time_range(trade_date, time(9, 35), time(11, 30))
    afternoon = _time_range(trade_date, time(13, 5), time(15, 0))
    return tuple(morning + afternoon)


def audit_minute_days(
    bars: Iterable[MinuteBar], daily_by_date: dict[date, DailyBar]
) -> list[MinuteDayAudit]:
    grouped: dict[tuple[str, date], list[MinuteBar]] = {}
    for bar in bars:
        grouped.setdefault((bar.code, bar.trade_date), []).append(bar)

    audits: list[MinuteDayAudit] = []
    for (code, trade_date), day_bars in sorted(grouped.items()):
        day_bars.sort(key=lambda item: item.bar_end)
        expected = set(expected_bar_ends(trade_date))
        actual = {bar.bar_end for bar in day_bars}
        daily = daily_by_date.get(trade_date)
        errors = _relative_errors(day_bars, daily) if daily else {}
        audits.append(
            MinuteDayAudit(
                code=code,
                trade_date=trade_date,
                bar_count=len(day_bars),
                first_bar_end=day_bars[0].bar_end if day_bars else None,
                last_bar_end=day_bars[-1].bar_end if day_bars else None,
                missing_bar_ends=tuple(value.isoformat() for value in sorted(expected - actual)),
                extra_bar_ends=tuple(value.isoformat() for value in sorted(actual - expected)),
                relative_errors=errors,
            )
        )
    return audits


def select_complete_bars(
    bars: Iterable[MinuteBar], window_start: datetime, window_end: datetime
) -> list[MinuteBar]:
    return sorted(
        (bar for bar in bars if bar.bar_start >= window_start and bar.bar_end <= window_end),
        key=lambda item: item.bar_end,
    )


def window_vwap(bars: Iterable[MinuteBar]) -> Decimal | None:
    selected = list(bars)
    volume = sum((bar.volume for bar in selected), Decimal("0"))
    if volume <= 0:
        return None
    amount = sum((bar.amount for bar in selected), Decimal("0"))
    return amount / volume


def _time_range(trade_date: date, start: time, end: time) -> list[datetime]:
    current = datetime.combine(trade_date, start, tzinfo=SHANGHAI)
    final = datetime.combine(trade_date, end, tzinfo=SHANGHAI)
    values: list[datetime] = []
    while current <= final:
        values.append(current)
        current += timedelta(minutes=5)
    return values


def _relative_errors(day_bars: list[MinuteBar], daily: DailyBar) -> dict[str, float]:
    minute_values = {
        "open": day_bars[0].open,
        "high": max(bar.high for bar in day_bars),
        "low": min(bar.low for bar in day_bars),
        "close": day_bars[-1].close,
        "volume": sum((bar.volume for bar in day_bars), Decimal("0")),
        "amount": sum((bar.amount for bar in day_bars), Decimal("0")),
    }
    daily_values = {
        "open": daily.open,
        "high": daily.high,
        "low": daily.low,
        "close": daily.close,
        "volume": daily.volume,
        "amount": daily.amount,
    }
    return {
        field: float(_relative_error(minute_values[field], daily_values[field]))
        for field in minute_values
    }


def _relative_error(left: Decimal, right: Decimal) -> Decimal:
    denominator = max(abs(left), abs(right), Decimal("0.00000001"))
    return abs(left - right) / denominator
# AI_GENERATE_END -
