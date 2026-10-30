# AI_GENERATE_START --
from __future__ import annotations

import shutil
from pathlib import Path

import numpy as np
import pandas as pd

from stock_trading.market.storage import ParquetMarketStore
from stock_trading.research.labels import build_t1_labels


class QlibBinaryStore:
    """Deterministically rebuild a minimal Qlib file provider from raw Parquet data."""

    def __init__(self, market_store: ParquetMarketStore, target: Path) -> None:
        self._market_store = market_store
        self._target = target

    def rebuild(self, codes: list[str]) -> dict[str, object]:
        frames = {code: self._adjusted_daily(code) for code in codes}
        calendar = pd.DatetimeIndex(
            sorted({value for frame in frames.values() for value in frame.index})
        )
        if len(calendar) < 63:
            raise ValueError("Qlib calendar requires at least 63 trading days for Alpha158")

        temporary = self._target.with_name(f".{self._target.name}.building")
        shutil.rmtree(temporary, ignore_errors=True)
        (temporary / "calendars").mkdir(parents=True)
        (temporary / "instruments").mkdir(parents=True)
        (temporary / "features").mkdir(parents=True)
        self._write_calendar(temporary, calendar)

        instruments: list[tuple[str, str, str]] = []
        label_count = 0
        for code, frame in frames.items():
            aligned = frame.reindex(calendar)
            labels = build_t1_labels(self._market_store.read_minutes(code), calendar)
            aligned["label"] = labels.reindex(calendar)
            qlib_code = to_qlib_code(code)
            self._write_features(temporary, qlib_code, aligned)
            valid = frame.dropna(subset=["close"])
            instruments.append(
                (
                    qlib_code,
                    valid.index.min().strftime("%Y-%m-%d"),
                    valid.index.max().strftime("%Y-%m-%d"),
                )
            )
            label_count += int(aligned["label"].notna().sum())

        instrument_path = temporary / "instruments" / "all.txt"
        instrument_path.write_text(
            "".join(f"{code}\t{start}\t{end}\n" for code, start, end in instruments),
            encoding="utf-8",
        )
        shutil.rmtree(self._target, ignore_errors=True)
        temporary.replace(self._target)
        return {
            "codes": len(codes),
            "calendarDays": len(calendar),
            "startDate": calendar.min().strftime("%Y-%m-%d"),
            "endDate": calendar.max().strftime("%Y-%m-%d"),
            "labels": label_count,
            "path": str(self._target),
        }

    def _adjusted_daily(self, code: str) -> pd.DataFrame:
        daily = self._market_store.read_daily_history(code).set_index("trade_date")
        daily = daily[
            (daily["tradestatus"].astype(str) == "1")
            & (daily["close"].astype(float) > 0)
            & (daily["volume"].astype(float) > 0)
        ]
        factors = self._market_store.read_adjustment_factors(code).set_index("operate_date")
        factor = factors["forward_factor"].reindex(daily.index, method="ffill").fillna(1.0)
        adjusted = pd.DataFrame(index=daily.index)
        for field in ("open", "high", "low", "close"):
            adjusted[field] = daily[field].astype(float) * factor
        adjusted["volume"] = daily["volume"].astype(float) / factor
        adjusted["amount"] = daily["amount"].astype(float)
        adjusted["vwap"] = adjusted["amount"] / adjusted["volume"].replace(0, np.nan)
        adjusted["factor"] = factor.astype(float)
        return adjusted

    @staticmethod
    def _write_calendar(target: Path, calendar: pd.DatetimeIndex) -> None:
        (target / "calendars" / "day.txt").write_text(
            "".join(f"{value.strftime('%Y-%m-%d')}\n" for value in calendar),
            encoding="utf-8",
        )

    @staticmethod
    def _write_features(target: Path, code: str, frame: pd.DataFrame) -> None:
        directory = target / "features" / code.lower()
        directory.mkdir(parents=True)
        for field in (
            "open",
            "high",
            "low",
            "close",
            "volume",
            "amount",
            "vwap",
            "factor",
            "label",
        ):
            values = frame[field].to_numpy(dtype="float32")
            np.hstack(([0.0], values)).astype("<f4").tofile(directory / f"{field}.day.bin")


def to_qlib_code(code: str) -> str:
    exchange, symbol = code.split(".", maxsplit=1)
    return f"{exchange.upper()}{symbol}"
# AI_GENERATE_END --
