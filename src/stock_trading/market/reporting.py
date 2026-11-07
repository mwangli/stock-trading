# AI_GENERATE_START -
from __future__ import annotations

import json
from datetime import UTC, date, datetime
from pathlib import Path
from typing import Callable
from uuid import uuid4

import pandas as pd
import pyarrow.parquet as pq

SUMMARY_SCHEMA_VERSION = 1
SUMMARY_DIRECTORY = ".report"
COLLECTION_TYPES = ("basic", "daily", "factor", "minute5", "minute")


def read_market_summary(root: Path, data_type: str) -> dict[str, object] | None:
    path = _summary_path(root, data_type)
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    if (
        not isinstance(value, dict)
        or value.get("schemaVersion") != SUMMARY_SCHEMA_VERSION
        or value.get("dataType") != data_type
    ):
        return None
    return value


def refresh_market_summary(root: Path, data_type: str) -> dict[str, object]:
    """在采集质量检查通过后生成该类型的原子汇报摘要。"""
    if data_type not in COLLECTION_TYPES:
        raise ValueError(f"不支持的采集汇报类型：{data_type}")
    summary = _scan_market_summary(root, data_type, _expected_market_codes(root))
    summary.update(
        {
            "schemaVersion": SUMMARY_SCHEMA_VERSION,
            "dataType": data_type,
            "updatedAt": datetime.now(UTC).isoformat(),
        }
    )
    target = _summary_path(root, data_type)
    target.parent.mkdir(parents=True, exist_ok=True)
    temporary = target.with_name(f".{target.name}.{uuid4().hex}.tmp")
    try:
        temporary.write_text(
            json.dumps(summary, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        temporary.replace(target)
    finally:
        temporary.unlink(missing_ok=True)
    return summary


def _scan_market_summary(
    root: Path,
    data_type: str,
    expected: set[str],
) -> dict[str, object]:
    definitions: dict[
        str,
        tuple[
            tuple[Path, ...],
            tuple[Path, ...],
            str,
            Callable[[pd.DataFrame], pd.Series] | None,
        ],
    ] = {
        "basic": ((root / "basic",), (root / "basic",), "snapshot_date", None),
        "daily": ((root / "daily",), (root / "daily",), "trade_date", None),
        "factor": ((root / "factor",), (root / "factor",), "operate_date", None),
        "minute5": (
            (root / "minute5", root / "minute5_summary"),
            (root / "minute5_summary",),
            "trade_date",
            lambda frame: frame[
                ["morning_complete", "sell_complete", "buy_complete"]
            ]
            .fillna(False)
            .all(axis=1),
        ),
        "minute": (
            (root / "minute", root / "minute1_summary"),
            (root / "minute1_summary",),
            "trade_date",
            lambda frame: frame["minute1_complete"].fillna(False).astype(bool),
        ),
    }
    roots, quality_roots, date_field, quality = definitions[data_type]
    files = sorted(
        path
        for current in roots
        if current.is_dir()
        for path in current.rglob("*.parquet")
    )
    quality_files = sorted(
        path
        for current in quality_roots
        if current.is_dir()
        for path in current.rglob("*.parquet")
    )
    rows = 0
    dates: list[date] = []
    latest_by_code: dict[str, tuple[date, bool]] = {}
    codes: set[str] = set()
    for path in files:
        try:
            rows += pq.ParquetFile(path).metadata.num_rows
        except (OSError, ValueError):
            continue
        code = _partition_code(path)
        if code:
            codes.add(code)
    for path in quality_files:
        columns = [date_field]
        if quality is not None:
            columns.extend(
                ["minute1_complete"]
                if data_type == "minute"
                else ["morning_complete", "sell_complete", "buy_complete"]
            )
        if data_type == "basic":
            columns.append("code")
        try:
            frame = _read_parquet_columns(path, list(dict.fromkeys(columns)))
            frame[date_field] = pd.to_datetime(frame[date_field], errors="coerce")
            frame = frame.dropna(subset=[date_field])
        except (OSError, ValueError, KeyError):
            continue
        if frame.empty:
            continue
        dates.extend(frame[date_field].dt.date.tolist())
        valid = quality(frame) if quality else pd.Series(True, index=frame.index)
        if data_type == "basic":
            codes.update(frame["code"].dropna().astype(str))
            continue
        code = _partition_code(path)
        if code:
            latest_index = frame[date_field].idxmax()
            latest_by_code[code] = (
                frame.loc[latest_index, date_field].date(),
                bool(valid.loc[latest_index]),
            )
    latest = max(dates) if dates else None
    if data_type == "basic":
        if expected:
            codes = set(expected)
        valid_codes = len(codes)
    else:
        valid_codes = sum(
            value_date == latest and valid
            for value_date, valid in latest_by_code.values()
        )
    denominator = len(expected) or len(codes)
    return {
        "codes": len(codes),
        "files": len(files),
        "rows": rows,
        "startDate": min(dates).isoformat() if dates else None,
        "endDate": latest.isoformat() if latest else None,
        "bytes": sum(path.stat().st_size for path in files),
        "validCodes": valid_codes,
        "coverage": valid_codes / denominator if denominator else None,
    }


def _expected_market_codes(root: Path) -> set[str]:
    target = root / "basic" / "stocks.parquet"
    if not target.is_file():
        return set()
    try:
        frame = _read_parquet_columns(target, ["code", "status"])
    except (OSError, ValueError, KeyError):
        return set()
    active = frame[frame["status"].astype(str) == "1"]
    return set(active["code"].dropna().astype(str))


def _summary_path(root: Path, data_type: str) -> Path:
    return root / SUMMARY_DIRECTORY / f"{data_type}.json"


def _partition_code(path: Path) -> str | None:
    for parent in path.parents:
        if parent.name.startswith("code="):
            return parent.name.removeprefix("code=")
    return None


def _read_parquet_columns(path: Path, columns: list[str]) -> pd.DataFrame:
    return pq.ParquetFile(path).read(columns=columns).to_pandas()
# AI_GENERATE_END -
