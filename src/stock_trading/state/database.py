# AI_GENERATE_START ----
from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator


class SQLiteStateDatabase:
    """提供状态库初始化保护和统一事务连接。"""

    def __init__(self, path: Path) -> None:
        self._path = path

    def initialize_schema(
        self,
        schema: str,
        schema_version: int,
        migrations: dict[int, str] | None = None,
    ) -> None:
        """创建空状态库；已有版本不匹配时拒绝自动破坏性升级。"""
        self._path.parent.mkdir(parents=True, exist_ok=True)
        connection = sqlite3.connect(self._path, timeout=30, isolation_level=None)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = OFF")
        connection.execute("PRAGMA busy_timeout = 30000")
        try:
            version = int(connection.execute("PRAGMA user_version").fetchone()[0])
            if version > schema_version:
                raise ValueError(
                    f"SQLite schema version is newer than supported: {version}"
                )
            tables = connection.execute(
                "SELECT name FROM sqlite_master "
                "WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            ).fetchall()
            if version == 0 and not tables:
                connection.executescript(
                    f"BEGIN IMMEDIATE;\n{schema}\n"
                    f"PRAGMA user_version = {schema_version};\nCOMMIT;"
                )
            elif migrations and version in migrations:
                connection.executescript(
                    f"BEGIN IMMEDIATE;\n{migrations[version]}\n{schema}\n"
                    f"PRAGMA user_version = {schema_version};\nCOMMIT;"
                )
            elif (version, schema_version) in {(1, 2), (1, 3), (2, 3)}:
                connection.executescript(
                    f"BEGIN IMMEDIATE;\n{schema}\n"
                    f"PRAGMA user_version = {schema_version};\nCOMMIT;"
                )
            elif version != schema_version:
                raise ValueError(
                    "SQLite schema version mismatch: "
                    f"database={version}, supported={schema_version}; "
                    "当前版本没有已确认的无损迁移，系统不会自动删除业务状态"
                )
            else:
                connection.executescript(schema)
        except BaseException:
            if connection.in_transaction:
                connection.execute("ROLLBACK")
            raise
        finally:
            connection.close()

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        """提供自动提交或回滚的 SQLite 事务连接。"""
        connection = sqlite3.connect(self._path, timeout=30)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = DELETE")
        connection.execute("PRAGMA synchronous = FULL")
        connection.execute("PRAGMA busy_timeout = 30000")
        try:
            yield connection
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            connection.close()

    @contextmanager
    def _read_connect(self) -> Iterator[sqlite3.Connection]:
        """以 SQLite 只读模式打开已有状态库，不创建文件或执行迁移。"""
        if not self._path.is_file():
            raise FileNotFoundError(self._path)
        uri = f"{self._path.resolve().as_uri()}?mode=ro"
        connection = sqlite3.connect(uri, uri=True, timeout=30)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
        connection.execute("PRAGMA busy_timeout = 30000")
        try:
            yield connection
        finally:
            connection.close()
# AI_GENERATE_END ----
