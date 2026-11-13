# AI_GENERATE_START-
from __future__ import annotations

from dataclasses import dataclass, field
from datetime import date, timedelta
import logging
from pathlib import Path
from uuid import uuid4

import pandas as pd
import pyarrow.parquet as pq

from stock_trading.config import WINDOW_CONTRACT
from stock_trading.market.models import (
    BASIC_CONTRACT_BAOSTOCK,
    DAILY_CONTRACT_BAOSTOCK,
    FACTOR_CONTRACT_BAOSTOCK,
    CumulativeAdjustmentFactor,
    DailyBasic,
    DailyBar,
    MinuteBar,
    StockBasic,
)

LOGGER = logging.getLogger(__name__)
MINUTE_HISTORY_RETENTION_DAYS = 366 * 5

@dataclass(frozen=True)
class ParquetWritePolicy:
    compression: str = "snappy"
    compression_level: int | None = None

    @classmethod
    def snappy(cls) -> ParquetWritePolicy:
        return cls()

    @classmethod
    def zstd(cls, level: int = 3) -> ParquetWritePolicy:
        return cls(compression="zstd", compression_level=level)

    def pandas_kwargs(self) -> dict[str, object]:
        values: dict[str, object] = {"compression": self.compression}
        if self.compression_level is not None:
            values["compression_level"] = self.compression_level
        return values


@dataclass(frozen=True)
class CompressionResult:
    path: Path
    status: str
    codecs_before: tuple[str, ...]
    before_bytes: int
    after_bytes: int
    error: str | None = None

    def as_dict(self) -> dict[str, object]:
        return {
            "path": str(self.path),
            "status": self.status,
            "codecsBefore": list(self.codecs_before),
            "beforeBytes": self.before_bytes,
            "afterBytes": self.after_bytes,
            "savedBytes": max(0, self.before_bytes - self.after_bytes),
            "error": self.error,
        }


@dataclass
class CompressionSummary:
    results: list[CompressionResult] = field(default_factory=list)

    def add(self, result: CompressionResult) -> None:
        self.results.append(result)

    def as_dict(self) -> dict[str, object]:
        converted = [item for item in self.results if item.status == "CONVERTED"]
        skipped = [item for item in self.results if item.status == "SKIPPED"]
        failed = [item for item in self.results if item.status == "FAILED"]
        before_bytes = sum(item.before_bytes for item in self.results)
        after_bytes = sum(item.after_bytes for item in self.results)
        return {
            "scannedFiles": len(self.results),
            "convertedFiles": len(converted),
            "skippedFiles": len(skipped),
            "failedFiles": len(failed),
            "beforeBytes": before_bytes,
            "afterBytes": after_bytes,
            "savedBytes": max(0, before_bytes - after_bytes),
            "failureSamples": [item.as_dict() for item in failed[:20]],
        }


def discover_parquet_files(paths: list[Path]) -> list[Path]:
    files: set[Path] = set()
    for path in paths:
        if path.is_file() and path.suffix.lower() == ".parquet":
            files.add(path)
        elif path.is_dir():
            files.update(path.rglob("*.parquet"))
    return sorted(files, key=lambda item: str(item).lower())


def inspect_parquet_codecs(path: Path) -> tuple[str, ...]:
    metadata = pq.read_metadata(path)
    codecs = {
        str(metadata.row_group(group).column(column).compression).upper()
        for group in range(metadata.num_row_groups)
        for column in range(metadata.row_group(group).num_columns)
    }
    return tuple(sorted(codecs))


def recompress_parquet(path: Path, level: int = 3) -> CompressionResult:
    temporary = path.with_name(f".{path.name}.{uuid4().hex}.tmp")
    before_bytes = path.stat().st_size
    codecs_before: tuple[str, ...] = ()
    try:
        source = pq.ParquetFile(path)
        try:
            source_metadata = source.metadata
            source_schema = source.schema_arrow
            table = source.read()
        finally:
            source.close()
        codecs_before = inspect_parquet_codecs(path)
        if codecs_before == ("ZSTD",):
            return CompressionResult(
                path=path,
                status="SKIPPED",
                codecs_before=codecs_before,
                before_bytes=before_bytes,
                after_bytes=before_bytes,
            )
        pq.write_table(
            table,
            temporary,
            compression="zstd",
            compression_level=level,
        )
        converted = pq.ParquetFile(temporary)
        try:
            converted_metadata = converted.metadata
            converted_schema = converted.schema_arrow
        finally:
            converted.close()
        if converted_metadata.num_rows != source_metadata.num_rows:
            raise ValueError("row count changed after parquet recompression")
        if not converted_schema.equals(source_schema, check_metadata=True):
            raise ValueError("schema changed after parquet recompression")
        if inspect_parquet_codecs(temporary) != ("ZSTD",):
            raise ValueError("recompressed parquet is not fully ZSTD")
        temporary.replace(path)
        return CompressionResult(
            path=path,
            status="CONVERTED",
            codecs_before=codecs_before,
            before_bytes=before_bytes,
            after_bytes=path.stat().st_size,
        )
    except Exception as error:
        return CompressionResult(
            path=path,
            status="FAILED",
            codecs_before=codecs_before,
            before_bytes=before_bytes,
            after_bytes=before_bytes,
            error=str(error),
        )
    finally:
        temporary.unlink(missing_ok=True)


def normalize_parquet_compression(
    paths: list[Path], level: int = 3
) -> CompressionSummary:
    summary = CompressionSummary()
    files = discover_parquet_files(paths)
    LOGGER.info("Parquet 压缩检查开始：文件=%d；目标=ZSTD-%d", len(files), level)
    progress_interval = max(1, len(files) // 20)
    for index, path in enumerate(files, start=1):
        result = recompress_parquet(path, level=level)
        summary.add(result)
        if result.status == "FAILED":
            LOGGER.warning("Parquet 压缩失败：文件=%s；原因=%s", path, result.error)
        if index == 1 or index == len(files) or index % progress_interval == 0:
            values = summary.as_dict()
            LOGGER.info(
                "Parquet 压缩进度：%d/%d；转换=%d；跳过=%d；失败=%d",
                index,
                len(files),
                values["convertedFiles"],
                values["skippedFiles"],
                values["failedFiles"],
            )
    return summary

class ParquetMarketStore:
    """管理股票清单、分级行情和模型基础指标。"""

    def __init__(
        self,
        root: Path,
        source: str = "raw",
        write_policy: ParquetWritePolicy | None = None,
    ) -> None:
        if source not in {"raw", "derived"}:
            raise ValueError(f"不支持的市场数据层：{source}")
        self._root = root / source
        self._derived_root = root / "derived"
        self._source = source
        self._daily_root = root / "raw" if source == "derived" else self._root
        self._write_policy = write_policy or ParquetWritePolicy.snappy()

    def write_stock_basics(self, stocks: list[StockBasic]) -> Path:
        """原子写入当前股票基础信息快照。"""
        target = self._root / "stock" / "stocks.parquet"
        return self._write(target, [stock.as_record() for stock in stocks])

    def read_stock_basics(self) -> pd.DataFrame:
        """读取股票基础信息快照。"""
        target = self._root / "stock" / "stocks.parquet"
        frame = _read_parquet_file(target)
        for field in ("ipo_date", "out_date", "snapshot_date"):
            frame[field] = pd.to_datetime(frame[field], errors="coerce")
        return frame.sort_values("code").reset_index(drop=True)



    def write_daily_history(self, code: str, bars: list[DailyBar]) -> Path:
        target = self._root / "daily" / f"code={code}" / "bars.parquet"
        records = [_without(bar.as_record(), "code") for bar in bars]
        if target.is_file():
            existing = _read_parquet_file(target)
            existing_contracts = (
                set(existing["source_contract"].dropna().astype(str))
                if "source_contract" in existing
                else set()
            )
            if existing_contracts != {DAILY_CONTRACT_BAOSTOCK}:
                return self._write(target, records)
        return self._merge_history(target, records, "trade_date")

    def write_basic_history(
        self, code: str, values: list[DailyBasic]
    ) -> Path:
        target = (
            self._derived_root / "basic" / f"code={code}" / "indicators.parquet"
        )
        records = [_without(value.as_record(), "code") for value in values]
        if target.is_file():
            existing = _read_parquet_file(target)
            contracts = set(existing.get("source_contract", pd.Series(dtype=str)))
            if contracts != {BASIC_CONTRACT_BAOSTOCK}:
                return self._write(target, records)
        return self._merge_history(target, records, "trade_date")

    def read_basic_panel(
        self,
        start_date: date,
        end_date: date,
        codes: set[str] | None = None,
    ) -> pd.DataFrame:
        """一次读取日期范围内的每日基础指标，供模型准备复用。"""
        directory = self._derived_root / "basic"
        selected_codes = codes or {
            path.parent.name.removeprefix("code=")
            for path in directory.glob("code=*/indicators.parquet")
        }
        paths = [
            directory / f"code={code}" / "indicators.parquet"
            for code in sorted(selected_codes)
            if (directory / f"code={code}" / "indicators.parquet").is_file()
        ]
        if not paths:
            return pd.DataFrame()
        frames: list[pd.DataFrame] = []
        for path in paths:
            values = _read_parquet_file(path)
            if "code" not in values:
                values.insert(0, "code", path.parent.name.removeprefix("code="))
            frames.append(values)
        frame = pd.concat(frames, ignore_index=True)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        if "source_contract" not in frame:
            return frame.iloc[0:0].copy()
        frame = frame[frame["source_contract"].eq(BASIC_CONTRACT_BAOSTOCK)]
        dates = frame["trade_date"].dt.date
        frame = frame[dates.between(start_date, end_date)]
        return (
            frame.drop_duplicates(subset=["trade_date", "code"], keep="last")
            .sort_values(["code", "trade_date"])
            .reset_index(drop=True)
        )


    def write_factors(
        self,
        code: str,
        factors: list[CumulativeAdjustmentFactor],
    ) -> Path:
        """保存单一来源合同的累计复权因子。"""
        target = (
            self._derived_root / "factor" / f"code={code}" / "factors.parquet"
        )
        records = [_without(factor.as_record(), "code") for factor in factors]
        contracts = {str(record["factor_contract"]) for record in records}
        if contracts != {FACTOR_CONTRACT_BAOSTOCK}:
            raise ValueError(f"复权因子来源合同无效：{code}")
        if target.is_file():
            existing = _read_parquet_file(target)
            existing_contracts = (
                set(existing["factor_contract"].dropna().astype(str))
                if "factor_contract" in existing
                else set()
            )
            if existing_contracts != contracts:
                return self._write(target, records)
        return self._merge_history(target, records, "operate_date")


    def read_daily_history(self, code: str) -> pd.DataFrame:
        target = self._daily_root / "daily" / f"code={code}" / "bars.parquet"
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"])
        return frame.sort_values("trade_date").reset_index(drop=True)


    def read_factors(self, code: str) -> pd.DataFrame:
        target = (
            self._derived_root / "factor" / f"code={code}" / "factors.parquet"
        )
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["operate_date"] = pd.to_datetime(frame["operate_date"])
        return frame.sort_values("operate_date").reset_index(drop=True)





























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
            frames.insert(0, existing)
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






    def _write(self, target: Path, rows: list[dict[str, object]]) -> Path:
        if not rows:
            raise ValueError(f"Refusing to write an empty market data partition: {target}")
        return self._write_frame(target, pd.DataFrame.from_records(rows))

    def _write_frame(self, target: Path, frame: pd.DataFrame) -> Path:
        target.parent.mkdir(parents=True, exist_ok=True)
        temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
        try:
            frame.to_parquet(
                temporary,
                index=False,
                **self._write_policy.pandas_kwargs(),
            )
            temporary.replace(target)
        finally:
            temporary.unlink(missing_ok=True)
        return target

    def write_minute_history(
        self,
        code: str,
        bars: list[MinuteBar],
        reference_date: date,
        required_start_date: date | None = None,
        required_end_date: date | None = None,
    ) -> dict[str, object]:
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = pd.DataFrame.from_records([bar.as_record() for bar in bars])
        if (
            required_start_date is not None
            and required_end_date is not None
            and not self._minute_frame_valid(
                incoming,
                required_start_date,
                required_end_date,
            )
        ):
            raise ValueError(f"5分钟响应字段或日期范围无效：{code}")
        existing = self.read_minutes(code)
        frames = [value for value in (existing, incoming) if not value.empty]
        result = self._store_minute_raw(
            code, pd.concat(frames, ignore_index=True), reference_date
        )
        return result

    @staticmethod
    def _minute_frame_valid(
        frame: pd.DataFrame,
        start_date: date,
        end_date: date,
    ) -> bool:
        required = {
            "trade_date",
            "bar_start",
            "bar_end",
            "open",
            "high",
            "low",
            "close",
            "volume",
            "amount",
        }
        if frame.empty or not required.issubset(frame.columns):
            return False
        try:
            response_dates = pd.to_datetime(
                frame["trade_date"], errors="raise"
            ).dt.date
        except (ValueError, KeyError, AttributeError):
            return False
        return bool(
            response_dates.notna().all()
            and response_dates.between(start_date, end_date).all()
        )

    def write_minute1_history(self, code: str, bars: list[MinuteBar]) -> Path:
        """按股票和月份只保存券商 1 分钟原始行情。"""
        if not bars:
            raise ValueError(f"Refusing to write empty minute history: {code}")
        incoming = _normalize_minute1_frame(
            pd.DataFrame.from_records([bar.as_record() for bar in bars]), code
        )
        written: list[Path] = []
        months = incoming["trade_date"].dt.strftime("%Y-%m")
        for month, values in incoming.groupby(months, sort=True):
            target = self._minute1_partition_path(code, str(month))
            existing = (
                _normalize_minute1_frame(_read_parquet_file(target), code)
                if target.is_file()
                else pd.DataFrame()
            )
            merged = _normalize_minute1_frame(
                pd.concat(
                    [frame for frame in (existing, values) if not frame.empty],
                    ignore_index=True,
                ),
                code,
            )
            self._write_frame(target, merged)
            written.append(target)
        return written[-1]

    def read_minutes(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        target = self._root / "minute5" / f"code={code}" / "bars.parquet"
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        if "code" not in frame:
            frame.insert(0, "code", code)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        for field in ("bar_start", "bar_end"):
            frame[field] = pd.to_datetime(frame[field], utc=True).dt.tz_convert(
                "Asia/Shanghai"
            )
        dates = frame["trade_date"].dt.date
        if start_date is not None:
            frame = frame[dates >= start_date]
            dates = frame["trade_date"].dt.date
        if end_date is not None:
            frame = frame[dates <= end_date]
        return frame.sort_values("bar_end").reset_index(drop=True)

    def read_minute1_history(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        paths = self._minute1_history_paths(code, start_date, end_date)
        if not paths:
            return pd.DataFrame()
        frame = _normalize_minute1_frame(
            pd.concat([_read_parquet_file(path) for path in paths], ignore_index=True),
            code,
        )
        if start_date is not None:
            frame = frame[frame["trade_date"].dt.date >= start_date]
        if end_date is not None:
            frame = frame[frame["trade_date"].dt.date <= end_date]
        return frame.reset_index(drop=True)

    def _minute1_history_paths(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> list[Path]:
        directory = self._root / "minute1" / f"code={code}"
        paths = sorted(directory.glob("month=*/bars.parquet"))
        start_month = start_date.strftime("%Y-%m") if start_date else None
        end_month = end_date.strftime("%Y-%m") if end_date else None
        return [
            path
            for path in paths
            if (
                start_month is None
                or path.parent.name.removeprefix("month=") >= start_month
            )
            and (
                end_month is None
                or path.parent.name.removeprefix("month=") <= end_month
            )
        ]

    def read_minute1_summaries(self, code: str) -> pd.DataFrame:
        target = self._minute1_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        return frame.sort_values("trade_date").reset_index(drop=True)

    def read_minute_summaries(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        """只读取指定范围内符合当前窗口合同的分钟摘要。"""
        frame = self._read_minute_summary_frame(code, start_date, end_date)
        if frame.empty:
            return frame
        if "window_contract" not in frame:
            return frame.iloc[0:0].copy()
        return (
            frame[frame["window_contract"].eq(WINDOW_CONTRACT)]
            .sort_values("trade_date")
            .reset_index(drop=True)
        )

    def rebuild_minute_summaries(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        """从原始 5 分钟行情重建指定范围的窗口摘要。"""
        from stock_trading.market.transform import summarize_trading_windows

        rebuilt_codes = 0
        rebuilt_rows = 0
        missing_codes: list[str] = []
        for code in codes:
            raw = self.read_minutes(code, start_date, end_date)
            if raw.empty:
                missing_codes.append(code)
                continue
            generated = summarize_trading_windows(raw)
            if generated.empty:
                missing_codes.append(code)
                continue
            target = self._minute_summary_path(code)
            existing = self._read_minute_summary_frame(code)
            generated_dates = set(generated["trade_date"])
            if not existing.empty and "window_contract" in existing:
                existing = existing[
                    existing["window_contract"].eq(WINDOW_CONTRACT)
                    & ~existing["trade_date"].isin(generated_dates)
                ].reindex(columns=generated.columns)
            else:
                existing = pd.DataFrame(columns=generated.columns)
            merged = pd.concat([existing, generated], ignore_index=True)
            merged = (
                merged.drop_duplicates(subset=["trade_date"], keep="last")
                .sort_values("trade_date")
                .reset_index(drop=True)
            )
            self._write_frame(target, merged)
            rebuilt_codes += 1
            rebuilt_rows += len(generated)
        return {
            "requestedCodes": len(codes),
            "rebuiltCodes": rebuilt_codes,
            "rebuiltRows": rebuilt_rows,
            "missingRawCodes": len(missing_codes),
            "missingRawSamples": missing_codes[:20],
        }

    def rebuild_minute1_summaries(
        self,
        codes: list[str],
        start_date: date,
        end_date: date,
    ) -> dict[str, object]:
        """从原始 1 分钟行情重建指定范围的日级特征。"""
        from stock_trading.market.transform import summarize_minute1_days

        rebuilt_codes = 0
        rebuilt_rows = 0
        missing_codes: list[str] = []
        for code in codes:
            raw = self.read_minute1_history(code, start_date, end_date)
            if raw.empty:
                missing_codes.append(code)
                continue
            generated = summarize_minute1_days(raw)
            if generated.empty:
                missing_codes.append(code)
                continue
            self._write_minute1_summaries(code, generated)
            rebuilt_codes += 1
            rebuilt_rows += len(generated)
        return {
            "requestedCodes": len(codes),
            "rebuiltCodes": rebuilt_codes,
            "rebuiltRows": rebuilt_rows,
            "missingRawCodes": len(missing_codes),
            "missingRawSamples": missing_codes[:20],
        }

    def _read_minute_summary_frame(
        self,
        code: str,
        start_date: date | None = None,
        end_date: date | None = None,
    ) -> pd.DataFrame:
        target = self._minute_summary_path(code)
        if not target.is_file():
            return pd.DataFrame()
        frame = _read_parquet_file(target)
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        dates = frame["trade_date"].dt.date
        if start_date is not None:
            frame = frame[dates >= start_date]
            dates = frame["trade_date"].dt.date
        if end_date is not None:
            frame = frame[dates <= end_date]
        return frame.sort_values("trade_date").reset_index(drop=True)


    def cleanup_minutes(self, reference_date: date) -> dict[str, int]:
        root = self._root / "minute5"
        directories = sorted(root.glob("code=*")) if root.is_dir() else []
        before_raw_rows = 0
        after_raw_rows = 0
        for directory in directories:
            code = directory.name.removeprefix("code=")
            minutes = self.read_minutes(code)
            if minutes.empty:
                continue
            result = self._store_minute_raw(code, minutes, reference_date)
            before_raw_rows += int(result["beforeRawRows"])
            after_raw_rows += int(result["rawRows"])
        return {
            "codes": len(directories),
            "beforeRawRows": before_raw_rows,
            "rawRows": after_raw_rows,
            "deletedRawRows": max(0, before_raw_rows - after_raw_rows),
        }

    def _store_minute_raw(
        self, code: str, minutes: pd.DataFrame, reference_date: date
    ) -> dict[str, object]:
        frame = minutes.copy()
        frame["trade_date"] = pd.to_datetime(frame["trade_date"], errors="raise")
        for field in ("bar_start", "bar_end"):
            frame[field] = pd.to_datetime(frame[field], utc=True).dt.tz_convert(
                "Asia/Shanghai"
            )
        frame = (
            frame.drop_duplicates(subset=["bar_end"], keep="last")
            .sort_values("bar_end")
            .reset_index(drop=True)
        )
        before_raw_rows = len(frame)
        retention_date = max(reference_date, frame["trade_date"].dt.date.max())
        raw_cutoff = retention_date - timedelta(days=MINUTE_HISTORY_RETENTION_DAYS)
        raw = frame[frame["trade_date"].dt.date >= raw_cutoff]
        raw_path = self._root / "minute5" / f"code={code}" / "bars.parquet"
        if raw.empty:
            raw_path.unlink(missing_ok=True)
        else:
            self._write(raw_path, raw.to_dict(orient="records"))
        return {
            "rawPath": str(raw_path),
            "rawRows": len(raw),
            "beforeRawRows": before_raw_rows,
        }

    def _minute_summary_path(self, code: str) -> Path:
        return self._derived_root / "minute5_summary" / f"code={code}" / "windows.parquet"

    def _minute1_partition_path(self, code: str, month: str) -> Path:
        return (
            self._root
            / "minute1"
            / f"code={code}"
            / f"month={month}"
            / "bars.parquet"
        )

    def _minute1_summary_path(self, code: str) -> Path:
        return self._derived_root / "minute1_summary" / f"code={code}" / "features.parquet"

    def _write_minute1_summaries(
        self, code: str, generated: pd.DataFrame
    ) -> Path:
        target = self._minute1_summary_path(code)
        existing = self.read_minute1_summaries(code)
        frames = [frame for frame in (existing, generated) if not frame.empty]
        if not frames:
            raise ValueError(f"Refusing to write empty minute1 summary: {code}")
        summary = pd.concat(frames, ignore_index=True)
        summary["trade_date"] = pd.to_datetime(
            summary["trade_date"], errors="raise"
        )
        summary = (
            summary.drop_duplicates(subset=["trade_date"], keep="last")
            .sort_values("trade_date")
            .reset_index(drop=True)
        )
        return self._write_frame(target, summary)

def _without(record: dict[str, object], *fields: str) -> dict[str, object]:
    return {key: value for key, value in record.items() if key not in fields}


def _read_parquet_file(path: Path) -> pd.DataFrame:
    """读取单个文件，避免 key=value 父目录触发 Hive 分区字段合并。"""
    return pq.ParquetFile(path).read().to_pandas()

def _normalize_minute1_frame(frame: pd.DataFrame, code: str) -> pd.DataFrame:
    if frame.empty:
        return frame
    normalized = frame.copy()
    if "code" not in normalized:
        normalized.insert(0, "code", code)
    normalized["trade_date"] = pd.to_datetime(normalized["trade_date"], errors="raise")
    for field in ("bar_start", "bar_end"):
        normalized[field] = pd.to_datetime(normalized[field], utc=True).dt.tz_convert("Asia/Shanghai")
    return normalized.drop_duplicates(subset=["bar_end"], keep="last").sort_values("bar_end").reset_index(drop=True)
# AI_GENERATE_END-
