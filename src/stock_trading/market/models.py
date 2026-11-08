# AI_GENERATE_START -------------
from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import date, datetime, timedelta
from decimal import Decimal
from zoneinfo import ZoneInfo

SHANGHAI = ZoneInfo("Asia/Shanghai")
STOCK_CONTRACT_BAOSTOCK = "baostock-stock-basic-v1"
DAILY_CONTRACT_BAOSTOCK = "baostock-daily-v1"
BASIC_CONTRACT_BAOSTOCK = "baostock-daily-derived-basic-v1"
FACTOR_CONTRACT_BAOSTOCK = "baostock-daily-derived-adjust-v1"


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
    source_contract: str

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
            source_contract=STOCK_CONTRACT_BAOSTOCK,
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
    """由 BaoStock 未复权日线确定性派生的 L1 基础指标。"""

    trade_date: date
    code: str
    turnover_rate: Decimal | None
    volume_ratio: Decimal | None
    circulating_market_value: Decimal | None
    pe_ttm: Decimal | None
    pb_mrq: Decimal | None
    ps_ttm: Decimal | None
    pcf_ncf_ttm: Decimal | None
    source_contract: str

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        for field in (
            "turnover_rate",
            "volume_ratio",
            "circulating_market_value",
            "pe_ttm",
            "pb_mrq",
            "ps_ttm",
            "pcf_ncf_ttm",
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
    turnover_rate: Decimal | None
    pe_ttm: Decimal | None
    pb_mrq: Decimal | None
    ps_ttm: Decimal | None
    pcf_ncf_ttm: Decimal | None
    tradestatus: str
    is_st: str
    source_contract: str

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
            turnover_rate=_optional_decimal(row.get("turn")),
            pe_ttm=_optional_decimal(row.get("peTTM")),
            pb_mrq=_optional_decimal(row.get("pbMRQ")),
            ps_ttm=_optional_decimal(row.get("psTTM")),
            pcf_ncf_ttm=_optional_decimal(row.get("pcfNcfTTM")),
            tradestatus=str(row.get("tradestatus") or ""),
            is_st=str(row.get("isST") or ""),
            source_contract=DAILY_CONTRACT_BAOSTOCK,
        )

    def as_record(self) -> dict[str, object]:
        record = asdict(self)
        record["trade_date"] = self.trade_date.isoformat()
        for field in ("open", "high", "low", "close", "preclose", "volume", "amount"):
            record[field] = float(record[field])
        for field in (
            "turnover_rate",
            "pe_ttm",
            "pb_mrq",
            "ps_ttm",
            "pcf_ncf_ttm",
        ):
            record[field] = (
                float(record[field]) if record[field] is not None else None
            )
        return record


@dataclass(frozen=True)
class CumulativeAdjustmentFactor:
    code: str
    operate_date: date
    cumulative_factor: Decimal
    factor_contract: str

    def as_record(self) -> dict[str, object]:
        return {
            "code": self.code,
            "operate_date": self.operate_date.isoformat(),
            "cumulative_factor": float(self.cumulative_factor),
            "factor_contract": self.factor_contract,
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


def _optional_iso_date(value: object) -> date | None:
    normalized = str(value or "").strip()
    return date.fromisoformat(normalized) if normalized else None


def _optional_decimal(value: object) -> Decimal | None:
    normalized = str(value or "").strip()
    return Decimal(normalized) if normalized else None


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
# AI_GENERATE_END -------------
