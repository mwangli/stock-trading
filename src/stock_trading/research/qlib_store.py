# AI_GENERATE_START -------
from __future__ import annotations

import logging
import shutil
from datetime import date, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

from stock_trading.market.storage import ParquetMarketStore
from stock_trading.logging_utils import should_log_progress
from stock_trading.research.labels import build_t1_labels

LOGGER = logging.getLogger(__name__)


class QlibBinaryStore:
    """Deterministically rebuild a minimal Qlib file provider from raw Parquet data."""

    def __init__(self, market_store: ParquetMarketStore, target: Path) -> None:
        self._market_store = market_store
        self._target = target

    def rebuild(
        self, codes: list[str], start_date: date, end_date: date
    ) -> dict[str, object]:
        frames: dict[str, pd.DataFrame] = {}
        total_codes = len(codes)
        LOGGER.info("Qlib 构建阶段 1/3：读取并复权日线；股票数=%d", total_codes)
        for index, code in enumerate(codes, start=1):
            frames[code] = self._adjusted_daily(code, start_date, end_date)
            if should_log_progress(index, total_codes):
                LOGGER.info("Qlib 日线处理进度：%d/%d；股票=%s", index, total_codes, code)
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
        LOGGER.info("Qlib 构建阶段 2/3：生成标签和特征；交易日数=%d", len(calendar))
        for index, (code, frame) in enumerate(frames.items(), start=1):
            aligned = frame.reindex(calendar)
            labels = build_t1_labels(self._market_store.read_minute_summaries(code), calendar)
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
            if should_log_progress(index, total_codes):
                LOGGER.info(
                    "Qlib 特征写入进度：%d/%d；股票=%s；累计标签=%d",
                    index,
                    total_codes,
                    code,
                    label_count,
                )

        instrument_path = temporary / "instruments" / "all.txt"
        instrument_path.write_text(
            "".join(f"{code}\t{start}\t{end}\n" for code, start, end in instruments),
            encoding="utf-8",
        )
        shutil.rmtree(self._target, ignore_errors=True)
        temporary.replace(self._target)
        LOGGER.info("Qlib 构建阶段 3/3：制品切换完成；目录=%s", self._target)
        return {
            "codes": len(codes),
            "calendarDays": len(calendar),
            "startDate": calendar.min().strftime("%Y-%m-%d"),
            "endDate": calendar.max().strftime("%Y-%m-%d"),
            "labels": label_count,
            "path": str(self._target),
        }

    def is_valid(
        self, codes: list[str], start_date: date, end_date: date
    ) -> bool:
        """检查 Qlib 日历、股票清单和特征文件是否覆盖目标范围。"""
        calendar_path = self._target / "calendars" / "day.txt"
        instruments_path = self._target / "instruments" / "all.txt"
        try:
            calendar = [
                date.fromisoformat(value)
                for value in calendar_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            ]
            instruments = {
                value.split("\t", maxsplit=1)[0]
                for value in instruments_path.read_text(encoding="utf-8").splitlines()
                if value.strip()
            }
        except (OSError, ValueError):
            return False
        expected = {to_qlib_code(code) for code in codes}
        return (
            len(calendar) >= 120
            and start_date <= calendar[0] <= start_date + timedelta(days=15)
            and end_date - timedelta(days=15) <= calendar[-1] <= end_date
            and instruments == expected
            and all(self._feature_files_valid(instrument) for instrument in expected)
        )

    def _feature_files_valid(self, instrument: str) -> bool:
        directory = self._target / "features" / instrument.lower()
        fields = (
            "open", "high", "low", "close", "volume",
            "amount", "vwap", "factor", "label",
        )
        try:
            return all(
                (directory / f"{field}.day.bin").is_file()
                and (directory / f"{field}.day.bin").stat().st_size > 4
                for field in fields
            )
        except OSError:
            return False
    def _adjusted_daily(
        self, code: str, start_date: date, end_date: date
    ) -> pd.DataFrame:
        daily = self._market_store.read_daily_history(code).set_index("trade_date")
        daily = daily[
            (daily.index.date >= start_date) & (daily.index.date <= end_date)
        ]
        daily = daily[
            (daily["tradestatus"].astype(str) == "1")
            & (daily["is_st"].astype(str) != "1")
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
# AI_GENERATE_END -------
