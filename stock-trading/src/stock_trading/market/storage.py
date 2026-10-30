# AI_GENERATE_START --
from __future__ import annotations

from datetime import date
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.market.models import DailyBar, MinuteBar


class ParquetMarketStore:
    """Immutable, partitioned Parquet storage for raw market data."""

    def __init__(self, root: Path) -> None:
        self._root = root

    def write_daily_market(self, trade_date: date, bars: list[DailyBar]) -> Path:
        target = self._root / "daily" / f"trade_date={trade_date.isoformat()}" / "bars.parquet"
        records = [_without(bar.as_record(), "trade_date") for bar in bars]
        return self._write(target, records)

    def write_minutes(self, code: str, trade_date: date, bars: list[MinuteBar]) -> Path:
        target = (
            self._root
            / "minute_5m"
            / f"code={code}"
            / f"trade_date={trade_date.isoformat()}"
            / "bars.parquet"
        )
        records = [_without(bar.as_record(), "code", "trade_date") for bar in bars]
        return self._write(target, records)

    @staticmethod
    def _write(target: Path, rows: list[dict[str, object]]) -> Path:
        if not rows:
            raise ValueError(f"Refusing to write an empty market data partition: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
        try:
            pd.DataFrame.from_records(rows).to_parquet(temporary, index=False)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return target


def _without(record: dict[str, object], *fields: str) -> dict[str, object]:
    return {key: value for key, value in record.items() if key not in fields}
# AI_GENERATE_END --
