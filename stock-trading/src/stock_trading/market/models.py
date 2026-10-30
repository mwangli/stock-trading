# AI_GENERATE_START --
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")


def _decimal(value: str) -> Decimal:
    return Decimal(value or "0")


@dataclass(frozen=True)
class DailyBar:
    trade_date: date
    code: str
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    preclose: Decimal
    volume: Decimal
    amount: Decimal
    tradestatus: str
    is_st: str

    @classmethod
    def from_baostock(cls, row: dict[str, str]) -> "DailyBar":
        return cls(
            trade_date=date.fromisoformat(row["date"]),
            code=row["code"],
            open=_decimal(row["open"]),
            high=_decimal(row["high"]),
            low=_decimal(row["low"]),
            close=_decimal(row["close"]),
            preclose=_decimal(row.get("preclose", "0")),
            volume=_decimal(row["volume"]),
            amount=_decimal(row["amount"]),
            tradestatus=row.get("tradestatus", ""),
            is_st=row.get("isST", ""),
        )

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        for field in ("open", "high", "low", "close", "preclose", "volume", "amount"):
            record[field] = float(record[field])
        return record


@dataclass(frozen=True)
class AdjustmentFactor:
    code: str
    operate_date: date
    forward_factor: Decimal

    @classmethod
    def from_baostock(cls, row: dict[str, str]) -> "AdjustmentFactor":
        return cls(
            code=row["code"],
            operate_date=date.fromisoformat(row["dividOperateDate"]),
            forward_factor=_decimal(row["foreAdjustFactor"]),
        )

    def as_record(self) -> dict[str, object]:
        return {
            "code": self.code,
            "operate_date": self.operate_date.isoformat(),
            "forward_factor": float(self.forward_factor),
        }


@dataclass(frozen=True)
class MinuteBar:
    trade_date: date
    bar_end: datetime
    code: str
    open: Decimal
    high: Decimal
    low: Decimal
    close: Decimal
    volume: Decimal
    amount: Decimal

    @classmethod
    def from_baostock(cls, row: dict[str, str]) -> "MinuteBar":
        bar_end = datetime.strptime(row["time"], "%Y%m%d%H%M%S%f").replace(tzinfo=SHANGHAI)
        return cls(
            trade_date=date.fromisoformat(row["date"]),
            bar_end=bar_end,
            code=row["code"],
            open=_decimal(row["open"]),
            high=_decimal(row["high"]),
            low=_decimal(row["low"]),
            close=_decimal(row["close"]),
            volume=_decimal(row["volume"]),
            amount=_decimal(row["amount"]),
        )

    @property
    def bar_start(self) -> datetime:
        return self.bar_end - timedelta(minutes=5)

    @property
    def vwap(self) -> Decimal | None:
        if self.volume <= 0:
            return None
        return self.amount / self.volume

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        record["bar_start"] = self.bar_start.isoformat()
        record["bar_end"] = self.bar_end.isoformat()
        for field in ("open", "high", "low", "close", "volume", "amount"):
            record[field] = float(record[field])
        return record
# AI_GENERATE_END --
