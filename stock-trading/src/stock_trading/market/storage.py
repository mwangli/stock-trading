# AI_GENERATE_START ------
from __future__ import annotations

import json
from datetime import date
from pathlib import Path
from uuid import uuid4

import pandas as pd

from stock_trading.market.models import AdjustmentFactor, DailyBar, MinuteBar


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

    def write_daily_history(self, code: str, bars: list[DailyBar]) -> Path:
        target = self._root / "daily_history" / f"code={code}" / "bars.parquet"
        records = [_without(bar.as_record(), "code") for bar in bars]
        return self._write(target, records)

    def write_adjustment_factors(
        self, code: str, factors: list[AdjustmentFactor]
    ) -> Path:
        target = self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        records = [_without(factor.as_record(), "code") for factor in factors]
        return self._write(target, records)

    def read_daily_history(self, code: str) -> pd.DataFrame:
        target = self._root / "daily_history" / f"code={code}" / "bars.parquet"
        frame = pd.read_parquet(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_adjustment_factors(self, code: str) -> pd.DataFrame:
        target = self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        frame = pd.read_parquet(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["operate_date"] = pd.to_datetime(frame["operate_date"])
        return frame.sort_values("operate_date").reset_index(drop=True)

    def read_minutes(self, code: str) -> pd.DataFrame:
        paths = sorted((self._root / "minute_5m" / f"code={code}").glob("trade_date=*/bars.parquet"))
        if not paths:
            return pd.DataFrame()
        frames: list[pd.DataFrame] = []
        for path in paths:
            frame = pd.read_parquet(path)
            if "code" not in frame:
                frame.insert(0, "code", code)
            if "trade_date" not in frame:
                frame.insert(1, "trade_date", path.parent.name.removeprefix("trade_date="))
            frames.append(frame)
        result = pd.concat(frames, ignore_index=True)
        result["trade_date"] = pd.to_datetime(result["trade_date"])
        result["bar_start"] = pd.to_datetime(result["bar_start"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        result["bar_end"] = pd.to_datetime(result["bar_end"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        return result.sort_values(["trade_date", "bar_end"]).reset_index(drop=True)

    def research_data_covers(self, code: str, start_date: date, end_date: date) -> bool:
        manifest = self._research_manifest_path(code)
        if manifest.exists():
            payload = json.loads(manifest.read_text(encoding="utf-8"))
            return (
                date.fromisoformat(payload["startDate"]) <= start_date
                and date.fromisoformat(payload["endDate"]) >= end_date
            )

        daily = self._root / "daily_history" / f"code={code}" / "bars.parquet"
        factors = self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        minute_dir = self._root / "minute_5m" / f"code={code}"
        minute_paths = sorted(minute_dir.glob("trade_date=*/bars.parquet"))
        if not daily.exists() or not factors.exists() or not minute_paths:
            return False
        daily_dates = pd.to_datetime(pd.read_parquet(daily, columns=["trade_date"])["trade_date"])
        minute_dates = [
            date.fromisoformat(path.parent.name.removeprefix("trade_date="))
            for path in minute_paths
        ]
        covered = (
            daily_dates.min().date() <= start_date
            and daily_dates.max().date() >= end_date
            and min(minute_dates) <= start_date
            and max(minute_dates) >= end_date
        )
        if covered:
            self.write_research_manifest(
                code, start_date, end_date, len(daily_dates), len(minute_paths)
            )
        return covered

    def write_research_manifest(
        self,
        code: str,
        start_date: date,
        end_date: date,
        daily_rows: int,
        minute_days: int,
    ) -> Path:
        target = self._research_manifest_path(code)
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
        payload = {
            "code": code,
            "startDate": start_date.isoformat(),
            "endDate": end_date.isoformat(),
            "dailyRows": daily_rows,
            "minuteDays": minute_days,
        }
        try:
            temporary.write_text(
                json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
            )
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return target

    def _research_manifest_path(self, code: str) -> Path:
        return self._root / "research_manifest" / f"code={code}" / "manifest.json"

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
# AI_GENERATE_END ------
