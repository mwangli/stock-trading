# AI_GENERATE_START ---------
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
    """统一股票基础信息快照。"""

    code: str
    code_name: str
    ipo_date: date | None
    out_date: date | None
    security_type: str
    market_category: str
    status: str
    trade_status: str
    snapshot_date: date

    @classmethod
    def from_tushare(
        cls, row: dict[str, object], snapshot_date: date
    ) -> "StockBasic":
        """转换 TuShare 上市股票基础资料为项目统一结构。"""
        list_status = str(row.get("list_status") or "")
        return cls(
            code=_tushare_code(row.get("ts_code")),
            code_name=str(row.get("name") or ""),
            ipo_date=_compact_date(row.get("list_date")),
            out_date=_compact_date(row.get("delist_date")),
            security_type="1",
            market_category=str(row.get("market") or ""),
            status="1" if list_status == "L" else "0",
            trade_status="1" if list_status == "L" else "0",
            snapshot_date=snapshot_date,
        )

    @classmethod
    def from_baostock(
        cls,
        basic: dict[str, str],
        market: dict[str, str],
        snapshot_date: date,
    ) -> "StockBasic":
        return cls(
            code=str(basic.get("code") or market.get("code") or "").lower(),
            code_name=str(basic.get("code_name") or market.get("code_name") or ""),
            ipo_date=_optional_iso_date(basic.get("ipoDate")),
            out_date=_optional_iso_date(basic.get("outDate")),
            security_type=str(basic.get("type") or ""),
            market_category=_market_category(
                str(basic.get("code") or market.get("code") or "").lower()
            ),
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
class DailyBasic:
    """TuShare 每日基础指标，金额统一保存为元。"""

    trade_date: date
    code: str
    turnover_rate: Decimal | None
    volume_ratio: Decimal | None
    total_market_value: Decimal | None
    circulating_market_value: Decimal | None

    @classmethod
    def from_tushare(cls, row: dict[str, object]) -> "DailyBasic":
        return cls(
            trade_date=datetime.strptime(str(row["trade_date"]), "%Y%m%d").date(),
            code=_tushare_code(row.get("ts_code")),
            turnover_rate=_optional_decimal(row.get("turnover_rate")),
            volume_ratio=_optional_decimal(row.get("volume_ratio")),
            total_market_value=_scaled_optional_decimal(row.get("total_mv")),
            circulating_market_value=_scaled_optional_decimal(row.get("circ_mv")),
        )

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        for field in (
            "turnover_rate",
            "volume_ratio",
            "total_market_value",
            "circulating_market_value",
        ):
            record[field] = (
                float(record[field]) if record[field] is not None else None
            )
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
    def from_tushare(cls, row: dict[str, object], is_st: bool) -> "DailyBar":
        """转换 TuShare 未复权日线，并统一成交量和成交额单位。"""
        return cls(
            trade_date=datetime.strptime(str(row["trade_date"]), "%Y%m%d").date(),
            code=_tushare_code(row.get("ts_code")),
            open=_decimal(str(row.get("open") or "0")),
            high=_decimal(str(row.get("high") or "0")),
            low=_decimal(str(row.get("low") or "0")),
            close=_decimal(str(row.get("close") or "0")),
            preclose=_decimal(str(row.get("pre_close") or "0")),
            volume=_decimal(str(row.get("vol") or "0")) * Decimal("100"),
            amount=_decimal(str(row.get("amount") or "0")) * Decimal("1000"),
            tradestatus="1",
            is_st="1" if is_st else "0",
        )

    @classmethod
    def from_baostock(cls, row: dict[str, str]) -> "DailyBar":
        return cls(
            trade_date=date.fromisoformat(row["date"]),
            code=str(row["code"]).lower(),
            open=_decimal(row.get("open", "0")),
            high=_decimal(row.get("high", "0")),
            low=_decimal(row.get("low", "0")),
            close=_decimal(row.get("close", "0")),
            preclose=_decimal(row.get("preclose", "0")),
            volume=_decimal(row.get("volume", "0")),
            amount=_decimal(row.get("amount", "0")),
            tradestatus=str(row.get("tradestatus") or ""),
            is_st=str(row.get("isST") or ""),
        )

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        for field in ("open", "high", "low", "close", "preclose", "volume", "amount"):
            record[field] = float(record[field])
        return record


@dataclass(frozen=True)
class CumulativeAdjustmentFactor:
    code: str
    operate_date: date
    cumulative_factor: Decimal

    @classmethod
    def from_tushare(cls, row: dict[str, object]) -> "CumulativeAdjustmentFactor":
        return cls(
            code=_tushare_code(row.get("ts_code")),
            operate_date=datetime.strptime(str(row["trade_date"]), "%Y%m%d").date(),
            cumulative_factor=_decimal(str(row.get("adj_factor") or "0")),
        )

    @classmethod
    def from_baostock(
        cls, row: dict[str, str]
    ) -> "CumulativeAdjustmentFactor":
        return cls(
            code=str(row["code"]).lower(),
            operate_date=date.fromisoformat(row["dividOperateDate"]),
            cumulative_factor=_decimal(row.get("backAdjustFactor", "0")),
        )

    def as_record(self) -> dict[str, object]:
        return {
            "code": self.code,
            "operate_date": self.operate_date.isoformat(),
            "cumulative_factor": float(self.cumulative_factor),
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
    interval_minutes: int = 5

    @classmethod
    def from_baostock(cls, row: dict[str, str]) -> "MinuteBar":
        bar_end = datetime.strptime(row["time"], "%Y%m%d%H%M%S%f").replace(
            tzinfo=SHANGHAI
        )
        return cls(
            trade_date=date.fromisoformat(row["date"]),
            bar_end=bar_end,
            code=str(row["code"]).lower(),
            open=_decimal(row.get("open", "0")),
            high=_decimal(row.get("high", "0")),
            low=_decimal(row.get("low", "0")),
            close=_decimal(row.get("close", "0")),
            volume=_decimal(row.get("volume", "0")),
            amount=_decimal(row.get("amount", "0")),
        )

    @property
    def bar_start(self) -> datetime:
        return self.bar_end - timedelta(minutes=self.interval_minutes)

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record.pop("interval_minutes")
        record["trade_date"] = self.trade_date.isoformat()
        record["bar_start"] = self.bar_start.isoformat()
        record["bar_end"] = self.bar_end.isoformat()
        for field in ("open", "high", "low", "close", "volume", "amount"):
            record[field] = float(record[field])
        return record


def _compact_date(value: object) -> date | None:
    normalized = str(value or "").strip()
    return datetime.strptime(normalized, "%Y%m%d").date() if normalized else None


def _optional_iso_date(value: object) -> date | None:
    normalized = str(value or "").strip()
    return date.fromisoformat(normalized) if normalized else None


def _optional_decimal(value: object) -> Decimal | None:
    normalized = str(value or "").strip()
    return Decimal(normalized) if normalized else None


def _scaled_optional_decimal(value: object) -> Decimal | None:
    normalized = _optional_decimal(value)
    return normalized * Decimal("10000") if normalized is not None else None


def _tushare_code(value: object) -> str:
    symbol, exchange = str(value or "").strip().split(".", maxsplit=1)
    return f"{exchange.lower()}.{symbol}"


def _market_category(code: str) -> str:
    symbol = code.rsplit(".", maxsplit=1)[-1]
    if code.startswith("sh.") and symbol.startswith(("688", "689")):
        return "科创板"
    if code.startswith("sz.") and symbol.startswith(("300", "301")):
        return "创业板"
    if code.startswith(("sh.", "sz.")):
        return "主板"
    if code.startswith("bj."):
        return "北交所"
    return ""
# AI_GENERATE_END ---------
