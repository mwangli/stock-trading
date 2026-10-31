# AI_GENERATE_START ---
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")


def _decimal(value: str) -> Decimal:
    return Decimal(value or "0")


@dataclass(frozen=True)
class StockBasic:
    """BaoStock 股票基础信息和指定日期交易状态。"""

    code: str
    code_name: str
    ipo_date: date | None
    out_date: date | None
    security_type: str
    status: str
    trade_status: str
    snapshot_date: date

    @classmethod
    def from_baostock(
        cls,
        basic: dict[str, str],
        market: dict[str, str],
        snapshot_date: date,
    ) -> "StockBasic":
        """合并基础资料和指定日期证券状态。"""
        return cls(
            code=str(basic.get("code") or market.get("code") or "").lower(),
            code_name=str(basic.get("code_name") or market.get("code_name") or ""),
            ipo_date=_optional_date(basic.get("ipoDate")),
            out_date=_optional_date(basic.get("outDate")),
            security_type=str(basic.get("type") or ""),
            status=str(basic.get("status") or ""),
            trade_status=str(market.get("tradeStatus") or ""),
            snapshot_date=snapshot_date,
        )

    def as_record(self) -> dict[str, object]:
        """转换为 Parquet 可直接保存的记录。"""
        record = asdict(self)
        record["ipo_date"] = self.ipo_date.isoformat() if self.ipo_date else None
        record["out_date"] = self.out_date.isoformat() if self.out_date else None
        record["snapshot_date"] = self.snapshot_date.isoformat()
        return record


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


def _optional_date(value: str | None) -> date | None:
    normalized = str(value or "").strip()
    return date.fromisoformat(normalized) if normalized else None
# AI_GENERATE_END ---
