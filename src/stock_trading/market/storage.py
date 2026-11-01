# AI_GENERATE_START ---------------------------------
from __future__ import annotations

import logging
import shutil
from datetime import date, timedelta
from pathlib import Path
from uuid import uuid4

import pandas as pd
import pyarrow.parquet as pq

from stock_trading.market.minute_summary import summarize_t1_windows
from stock_trading.market.models import AdjustmentFactor, DailyBar, MinuteBar, StockBasic
from stock_trading.logging_utils import set_log_context, should_log_progress

LOGGER = logging.getLogger(__name__)
RAW_MINUTE_RETENTION_DAYS = 90
MINUTE_SUMMARY_RETENTION_DAYS = 366 * 5


class ParquetMarketStore:
    """管理日线、紧凑分钟原始数据和长期交易窗口聚合结果。"""

    def __init__(self, root: Path) -> None:
        self._root = root

    @property
    def root(self) -> Path:
        return self._root

    def write_stock_basics(self, stocks: list[StockBasic]) -> Path:
        """原子写入当前股票基础信息快照。"""
        target = self._root / "instruments" / "stocks.parquet"
        return self._write(target, [stock.as_record() for stock in stocks])

    def read_stock_basics(self) -> pd.DataFrame:
        """读取股票基础信息快照。"""
        target = self._root / "instruments" / "stocks.parquet"
        frame = _read_parquet_file(target)
        for field in ("ipo_date", "out_date", "snapshot_date"):
            frame[field] = pd.to_datetime(frame[field], errors="coerce")
        return frame.sort_values("code").reset_index(drop=True)

    def stock_basics_valid(self, snapshot_date: date) -> bool:
        """检查基础信息文件是否可读、字段完整且属于目标快照日。"""
        try:
            frame = self.read_stock_basics()
        except (OSError, ValueError, KeyError):
            return False
        required = {
            "code", "code_name", "ipo_date", "out_date", "security_type",
            "status", "trade_status", "snapshot_date",
        }
        return (
            required.issubset(frame.columns)
            and len(frame) >= 100
            and frame["code"].astype(str).str.match(r"^(sh|sz)\.\d{6}$").all()
            and frame["snapshot_date"].dt.date.eq(snapshot_date).all()
        )

    def write_minute_history(
        self,
        code: str,
        bars: list[MinuteBar],
        reference_date: date,
    ) -> dict[str, object]:
        """合并分钟行情，原始数据保留 90 天，交易窗口聚合保留 5 年。"""
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = pd.DataFrame.from_records([bar.as_record() for bar in bars])
        existing = self.read_minutes(code)
        frames = [frame for frame in (existing, incoming) if not frame.empty]
        combined = pd.concat(frames, ignore_index=True)
        return self._store_minute_frames(code, combined, reference_date)

    def write_daily_history(self, code: str, bars: list[DailyBar]) -> Path:
        target = self._root / "daily_history" / f"code={code}" / "bars.parquet"
        records = [_without(bar.as_record(), "code") for bar in bars]
        return self._merge_history(target, records, "trade_date")

    def write_adjustment_factors(
        self,
        code: str,
        factors: list[AdjustmentFactor],
    ) -> Path:
        target = self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        records = [_without(factor.as_record(), "code") for factor in factors]
        if not records:
            if target.is_file():
                return target
            return self._write_frame(
                target,
                pd.DataFrame(columns=["operate_date", "forward_factor"]),
            )
        return self._merge_history(target, records, "operate_date")

    def read_daily_history(self, code: str) -> pd.DataFrame:
        target = self._root / "daily_history" / f"code={code}" / "bars.parquet"
        frame = _read_parquet_file(target)
        frame = frame.drop(columns=["coverage_start", "coverage_end"], errors="ignore")
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_adjustment_factors(self, code: str) -> pd.DataFrame:
        target = self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        frame = _read_parquet_file(target)
        frame = frame.drop(columns=["coverage_start", "coverage_end"], errors="ignore")
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["operate_date"] = pd.to_datetime(frame["operate_date"])
        return frame.sort_values("operate_date").reset_index(drop=True)

    def read_minutes(self, code: str) -> pd.DataFrame:
        """读取紧凑原始文件，并兼容尚未迁移的按日旧分区。"""
        directory = self._root / "minute_5m" / f"code={code}"
        paths: list[Path] = []
        compact = directory / "bars.parquet"
        if compact.is_file():
            paths.append(compact)
        paths.extend(sorted(directory.glob("trade_date=*/bars.parquet")))
        if not paths:
            return pd.DataFrame()
        frames: list[pd.DataFrame] = []
        for path in paths:
            frame = _read_parquet_file(path)
            if "code" not in frame:
                frame.insert(0, "code", code)
            if "trade_date" not in frame and path.parent.name.startswith("trade_date="):
                frame.insert(1, "trade_date", path.parent.name.removeprefix("trade_date="))
            frames.append(frame)
        result = pd.concat(frames, ignore_index=True)
        result = result.drop_duplicates(subset=["bar_end"], keep="last")
        result["trade_date"] = pd.to_datetime(result["trade_date"])
        result["bar_start"] = pd.to_datetime(result["bar_start"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        result["bar_end"] = pd.to_datetime(result["bar_end"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        return result.sort_values(["trade_date", "bar_end"]).reset_index(drop=True)

    def read_minute_summaries(self, code: str) -> pd.DataFrame:
        """读取长期保留的 T+1 买卖窗口聚合结果。"""
        target = self._minute_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame = frame.drop(columns=["coverage_start", "coverage_end"], errors="ignore")
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        return frame.sort_values("trade_date").reset_index(drop=True)

    def has_daily_history(self, code: str) -> bool:
        """检查指定股票编号是否已有日线文件。"""
        return (
            self._root / "daily_history" / f"code={code}" / "bars.parquet"
        ).is_file()

    def has_adjustment_factors(self, code: str) -> bool:
        """检查指定股票编号是否已有复权因子文件。"""
        return (
            self._root / "adjustment_factor" / f"code={code}" / "factors.parquet"
        ).is_file()

    def has_minute5_data(self, code: str) -> bool:
        """检查指定股票编号是否已有分钟聚合文件。"""
        return self._minute_summary_path(code).is_file()

    def has_daily_data(self, code: str) -> bool:
        """检查指定股票编号的日线和复权文件是否都存在。"""
        return self.has_daily_history(code) and self.has_adjustment_factors(code)

    def has_research_data(self, code: str) -> bool:
        """检查指定股票编号的研究数据文件是否都存在。"""
        return self.has_daily_data(code) and self.has_minute5_data(code)

    def valid_daily_codes(self, codes: list[str]) -> list[str]:
        """返回已有日线和复权文件的股票编号。"""
        return [code for code in codes if self.has_daily_data(code)]

    def valid_minute5_codes(self, codes: list[str]) -> list[str]:
        """返回已有分钟聚合文件的股票编号。"""
        return [code for code in codes if self.has_minute5_data(code)]

    def valid_research_codes(self, codes: list[str]) -> list[str]:
        """返回三类研究数据文件均存在的股票编号。"""
        return [code for code in codes if self.has_research_data(code)]

    def model_research_codes(
        self, codes: list[str], start_date: date, end_date: date
    ) -> list[str]:
        """返回研究窗口内至少存在一个非 ST 可交易日的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = self._eligible_daily_rows(self.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.between(start_date, end_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

    def tradable_codes_on(self, codes: list[str], trade_date: date) -> list[str]:
        """返回指定交易日具备有效非 ST 日线事实的股票。"""
        result: list[str] = []
        for code in codes:
            try:
                daily = self._eligible_daily_rows(self.read_daily_history(code))
                dates = pd.to_datetime(daily["trade_date"], errors="coerce").dt.date
                if dates.eq(trade_date).any():
                    result.append(code)
            except (OSError, ValueError, KeyError):
                continue
        return result

    @staticmethod
    def _eligible_daily_rows(frame: pd.DataFrame) -> pd.DataFrame:
        return frame[
            (frame["tradestatus"].astype(str) == "1")
            & (frame["is_st"].astype(str) != "1")
            & (frame["close"].astype(float) > 0)
            & (frame["volume"].astype(float) > 0)
        ]

    def cleanup_minutes(self, reference_date: date) -> dict[str, int]:
        """迁移旧分钟分区并执行原始 90 天、聚合 5 年保留规则。"""
        root = self._root / "minute_5m"
        code_directories = sorted(root.glob("code=*")) if root.is_dir() else []
        compacted = 0
        raw_rows = 0
        summary_rows = 0
        removed_partitions = 0
        total_codes = len(code_directories)
        for index, directory in enumerate(code_directories, start=1):
            code = directory.name.removeprefix("code=")
            legacy_count = sum(1 for _ in directory.glob("trade_date=*"))
            minutes = self.read_minutes(code)
            if minutes.empty:
                continue
            result = self._store_minute_frames(code, minutes, reference_date)
            compacted += 1
            raw_rows += int(result["rawRows"])
            summary_rows += int(result["summaryRows"])
            removed_partitions += legacy_count
            if should_log_progress(index, total_codes):
                set_log_context(progress=f"{index}/{total_codes}", target=code)
                LOGGER.info("分钟数据清理完成：移除旧分区=%d", removed_partitions)
        set_log_context(progress=f"{total_codes}/{total_codes}", target="分钟数据")
        LOGGER.info(
            "分钟数据清理完成：股票=%d；原始行=%d；聚合行=%d；移除旧分区=%d",
            compacted,
            raw_rows,
            summary_rows,
            removed_partitions,
        )
        return {
            "codes": compacted,
            "rawRows": raw_rows,
            "summaryRows": summary_rows,
            "legacyPartitionsRemoved": removed_partitions,
        }

    def _store_minute_frames(
        self,
        code: str,
        minutes: pd.DataFrame,
        reference_date: date,
    ) -> dict[str, object]:
        frame = minutes.copy()
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        frame["bar_start"] = pd.to_datetime(frame["bar_start"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        frame["bar_end"] = pd.to_datetime(frame["bar_end"], utc=True).dt.tz_convert(
            "Asia/Shanghai"
        )
        frame = (
            frame.drop_duplicates(subset=["bar_end"], keep="last")
            .sort_values("bar_end")
            .reset_index(drop=True)
        )

        generated = summarize_t1_windows(frame)
        existing_summary = self.read_minute_summaries(code)
        summaries = [value for value in (existing_summary, generated) if not value.empty]
        summary = (
            pd.concat(summaries, ignore_index=True)
            if summaries
            else generated.copy()
        )
        for field in ("buy_complete", "sell_complete"):
            summary[field] = summary[field].fillna(False).astype(bool)
        summary = (
            summary.drop_duplicates(subset=["trade_date"], keep="last")
            .sort_values("trade_date")
            .reset_index(drop=True)
        )
        summary_cutoff = reference_date - timedelta(days=MINUTE_SUMMARY_RETENTION_DAYS)
        summary = summary[summary["trade_date"].dt.date >= summary_cutoff]

        raw_cutoff = reference_date - timedelta(days=RAW_MINUTE_RETENTION_DAYS - 1)
        raw = frame[frame["trade_date"].dt.date >= raw_cutoff]
        raw_path = self._root / "minute_5m" / f"code={code}" / "bars.parquet"
        summary_path = self._minute_summary_path(code)
        if raw.empty:
            raw_path.unlink(missing_ok=True)
        else:
            self._write(raw_path, raw.to_dict(orient="records"))
        if summary.empty:
            summary_path.unlink(missing_ok=True)
        else:
            self._write(summary_path, summary.to_dict(orient="records"))

        for legacy in raw_path.parent.glob("trade_date=*"):
            if legacy.is_dir():
                shutil.rmtree(legacy)
        return {
            "rawPath": str(raw_path),
            "summaryPath": str(summary_path),
            "rawRows": len(raw),
            "summaryRows": len(summary),
        }

    def _minute_summary_path(self, code: str) -> Path:
        return self._root / "minute_summary" / f"code={code}" / "windows.parquet"

    def _merge_history(
        self,
        target: Path,
        records: list[dict[str, object]],
        date_field: str,
    ) -> Path:
        if not records:
            raise ValueError(f"Refusing to write empty market history: {target}")
        frames = [pd.DataFrame.from_records(records)]
        existing = pd.DataFrame()
        if target.is_file():
            try:
                existing = _read_parquet_file(target)
            except Exception:
                existing = pd.DataFrame()
        if not existing.empty:
            frames.insert(
                0,
                existing.drop(
                    columns=["coverage_start", "coverage_end"],
                    errors="ignore",
                ),
            )
        merged = pd.concat(frames, ignore_index=True)
        merged[date_field] = pd.to_datetime(
            merged[date_field], errors="raise"
        ).dt.strftime("%Y-%m-%d")
        merged = (
            merged.drop_duplicates(subset=[date_field], keep="last")
            .sort_values(date_field)
            .reset_index(drop=True)
        )
        return self._write(target, merged.to_dict(orient="records"))

    @staticmethod
    def _write(target: Path, rows: list[dict[str, object]]) -> Path:
        if not rows:
            raise ValueError(f"Refusing to write an empty market data partition: {target}")
        return ParquetMarketStore._write_frame(
            target, pd.DataFrame.from_records(rows)
        )

    @staticmethod
    def _write_frame(target: Path, frame: pd.DataFrame) -> Path:
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
        try:
            frame.to_parquet(temporary, index=False)
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return target


def _without(record: dict[str, object], *fields: str) -> dict[str, object]:
    return {key: value for key, value in record.items() if key not in fields}


def _read_parquet_file(path: Path) -> pd.DataFrame:
    """读取单个文件，避免 key=value 父目录触发 Hive 分区字段合并。"""
    return pq.ParquetFile(path).read().to_pandas()
# AI_GENERATE_END ---------------------------------
