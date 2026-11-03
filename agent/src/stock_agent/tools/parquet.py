# AI_GENERATE_START ---
from __future__ import annotations

import glob
import re
from pathlib import Path
from typing import Any

import duckdb

from stock_agent.config import AgentConfig
from stock_agent.tools.registry import ToolDefinition, ToolRegistry


DISALLOWED_SQL = re.compile(
    r"\b(copy|attach|detach|install|load|pragma|call|export|import|insert|"
    r"update|delete|create|alter|drop|read_csv|read_json|read_parquet|read_blob|"
    r"read_text|glob|parquet_scan|csv_scan|json_scan|sqlite_scan|postgres_scan|"
    r"iceberg_scan|delta_scan|httpfs|http_get)\b",
    re.IGNORECASE,
)


class ParquetTools:
    """Sandboxed in-process DuckDB access to trading Parquet data."""

    def __init__(self, config: AgentConfig) -> None:
        self._config = config

    def register(self, registry: ToolRegistry) -> None:
        registry.register(ToolDefinition(
            "parquet_catalog",
            "列出交易 data 目录中的 Parquet 文件。",
            {
                "type": "object",
                "properties": {
                    "prefix": {"type": "string"},
                    "limit": {"type": "integer", "minimum": 1, "maximum": 200},
                },
                "additionalProperties": False,
            },
            self.parquet_catalog,
        ))
        registry.register(ToolDefinition(
            "query_parquet",
            "加载一个相对 Parquet 数据集，并对只读视图 dataset 执行 SELECT 查询。",
            {
                "type": "object",
                "properties": {
                    "dataset": {
                        "type": "string",
                        "description": "data 下的相对文件或 glob，例如 market/daily_history/**/*.parquet",
                    },
                    "sql": {
                        "type": "string",
                        "description": "只允许查询视图 dataset 的 SELECT 或 WITH 查询",
                    },
                },
                "required": ["dataset", "sql"],
                "additionalProperties": False,
            },
            self.query_parquet,
        ))

    def parquet_catalog(self, arguments: dict[str, Any]) -> dict[str, Any]:
        root = self._config.trading_data_dir.resolve()
        prefix = str(arguments.get("prefix", "")).strip().replace("\\", "/")
        if prefix.startswith("/") or ".." in Path(prefix).parts:
            raise ValueError("prefix 必须是 data 目录内的相对路径")
        limit = min(200, max(1, int(arguments.get("limit", 100))))
        base = (root / prefix).resolve()
        _ensure_within(root, base)
        files = sorted(base.rglob("*.parquet") if base.exists() else [])
        return {
            "status": "ok",
            "source": str(root),
            "data": {
                "files": [
                    str(path.relative_to(root)).replace("\\", "/")
                    for path in files[:limit]
                ],
                "total": len(files),
                "truncated": len(files) > limit,
            },
        }

    def query_parquet(self, arguments: dict[str, Any]) -> dict[str, Any]:
        dataset = str(arguments.get("dataset", "")).strip().replace("\\", "/")
        sql = str(arguments.get("sql", "")).strip()
        root = self._config.trading_data_dir.resolve()
        files = _resolve_dataset(root, dataset)
        _validate_query(sql)
        connection = duckdb.connect(database=":memory:")
        try:
            connection.execute("SET memory_limit='512MB'")
            connection.execute("SET threads=2")
            connection.execute("SET enable_progress_bar=false")
            file_list = ",".join(
                f"'{str(path).replace(chr(39), chr(39) * 2)}'" for path in files
            )
            connection.execute(
                f"CREATE VIEW dataset AS SELECT * FROM read_parquet([{file_list}])"
            )
            cursor = connection.execute(
                f"SELECT * FROM ({sql}) AS agent_query LIMIT ?",
                (self._config.max_query_rows + 1,),
            )
            columns = [column[0] for column in cursor.description or []]
            rows = cursor.fetchall()
        finally:
            connection.close()
        truncated = len(rows) > self._config.max_query_rows
        visible = rows[:self._config.max_query_rows]
        return {
            "status": "ok",
            "source": {
                "root": str(root), "dataset": dataset, "fileCount": len(files),
            },
            "data": {
                "columns": columns,
                "rows": [dict(zip(columns, row, strict=True)) for row in visible],
                "truncated": truncated,
            },
        }


def _resolve_dataset(root: Path, dataset: str) -> list[Path]:
    if not dataset or dataset.startswith("/") or ".." in Path(dataset).parts:
        raise ValueError("dataset 必须是 data 目录内的相对路径")
    matches = [
        Path(value).resolve()
        for value in glob.glob(str(root / dataset), recursive=True)
    ]
    files = sorted(
        path for path in matches if path.is_file() and path.suffix.lower() == ".parquet"
    )
    for path in files:
        _ensure_within(root, path)
    if not files:
        raise FileNotFoundError(f"未找到 Parquet 数据集: {dataset}")
    if len(files) > 20_000:
        raise ValueError("单次查询的 Parquet 文件数量不能超过 20000")
    return files


def _ensure_within(root: Path, path: Path) -> None:
    try:
        path.relative_to(root)
    except ValueError as error:
        raise ValueError("路径超出交易 data 目录") from error


def _validate_query(sql: str) -> None:
    if not sql:
        raise ValueError("SQL 不能为空")
    if ";" in sql or "--" in sql or "/*" in sql:
        raise ValueError("只允许不含注释和分号的单条查询")
    if not re.match(r"^(select|with)\b", sql, re.IGNORECASE):
        raise ValueError("只允许 SELECT 或 WITH 查询")
    if DISALLOWED_SQL.search(sql):
        raise ValueError("查询包含禁止的 DuckDB 操作或外部读取函数")
    if not re.search(r"\bdataset\b", sql, re.IGNORECASE):
        raise ValueError("查询必须从只读视图 dataset 读取")
    referenced = {
        match.group(1).lower() for match in re.finditer(
            r"\b(?:from|join)\s+[\"`\[]?([A-Za-z_][A-Za-z0-9_]*)",
            sql,
            re.IGNORECASE,
        )
    }
    ctes = {
        match.group(1).lower() for match in re.finditer(
            r"(?:\bwith|,)\s*([A-Za-z_][A-Za-z0-9_]*)\s+AS\s*\(",
            sql,
            re.IGNORECASE,
        )
    }
    invalid = referenced - {"dataset"} - ctes
    if invalid:
        raise ValueError(f"查询引用了未授权数据源: {', '.join(sorted(invalid))}")
# AI_GENERATE_END ---
