# AI_GENERATE_START ---
from __future__ import annotations

import json
import os
import sqlite3
import uuid
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


SCHEMA = """
CREATE TABLE IF NOT EXISTS agent_session (
    session_id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_message (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    role TEXT NOT NULL CHECK(role IN ('user','assistant')),
    content TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(session_id) REFERENCES agent_session(session_id)
);

CREATE INDEX IF NOT EXISTS idx_agent_message_session
ON agent_message(session_id, id);

CREATE TABLE IF NOT EXISTS agent_memory (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_tool_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id TEXT NOT NULL,
    tool_name TEXT NOT NULL,
    arguments_summary TEXT NOT NULL,
    result_status TEXT NOT NULL,
    result_summary TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_repair_task (
    task_id TEXT PRIMARY KEY,
    run_id INTEGER NOT NULL,
    repository TEXT NOT NULL,
    target_branch TEXT NOT NULL,
    work_branch TEXT,
    status TEXT NOT NULL,
    summary TEXT,
    current_sha TEXT,
    final_run_id INTEGER,
    error_message TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_agent_repair_single_running
ON agent_repair_task(status) WHERE status = 'RUNNING';

CREATE TABLE IF NOT EXISTS agent_repair_attempt (
id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL,
    attempt_no INTEGER NOT NULL,
    base_sha TEXT NOT NULL,
    commit_sha TEXT,
    run_id INTEGER,
    conclusion TEXT,
    summary TEXT NOT NULL,
    changed_paths TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(task_id) REFERENCES agent_repair_task(task_id),
    UNIQUE(task_id, attempt_no)
);
"""


class MemoryStore:
    """Local persistent conversations, memories and redacted tool audit."""

    def __init__(self, path: Path) -> None:
        self._path = path
        self._fts_available = False
        self.initialize()

    def initialize(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.executescript(SCHEMA)
            try:
                connection.execute(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS agent_message_fts
                    USING fts5(session_id UNINDEXED, role UNINDEXED, content)
                    """
                )
                connection.execute(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS agent_memory_fts
                    USING fts5(memory_id UNINDEXED, category UNINDEXED, content)
                    """
                )
                self._fts_available = True
            except sqlite3.OperationalError:
                self._fts_available = False
        if os.name != "nt" and self._path.exists():
            self._path.chmod(0o600)

    def create_session(self, title: str = "交互会话") -> str:
        session_id = uuid.uuid4().hex[:16]
        now = _now()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO agent_session(session_id, title, created_at, updated_at)
                VALUES(?, ?, ?, ?)
                """,
                (session_id, title[:120], now, now),
            )
        return session_id

    def latest_session(self) -> str | None:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT session_id FROM agent_session ORDER BY updated_at DESC LIMIT 1"
            ).fetchone()
        return str(row["session_id"]) if row else None

    def append_message(self, session_id: str, role: str, content: str) -> None:
        now = _now()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO agent_message(session_id, role, content, created_at)
                VALUES(?, ?, ?, ?)
                """,
                (session_id, role, content, now),
            )
            connection.execute(
                "UPDATE agent_session SET updated_at = ? WHERE session_id = ?",
                (now, session_id),
            )
            if self._fts_available:
                connection.execute(
                    """
                    INSERT INTO agent_message_fts(session_id, role, content)
                    VALUES(?, ?, ?)
                    """,
                    (session_id, role, content),
                )

    def load_messages(self, session_id: str, limit: int = 24) -> list[dict[str, str]]:
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT role, content FROM (
                    SELECT id, role, content FROM agent_message
                    WHERE session_id = ? ORDER BY id DESC LIMIT ?
                ) ORDER BY id
                """,
                (session_id, max(1, limit)),
            ).fetchall()
        return [{"role": row["role"], "content": row["content"]} for row in rows]

    def list_sessions(self, limit: int = 20) -> list[dict[str, Any]]:
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT session_id, title, created_at, updated_at
                FROM agent_session ORDER BY updated_at DESC LIMIT ?
                """,
                (max(1, limit),),
            ).fetchall()
        return [dict(row) for row in rows]

    def remember(self, category: str, content: str) -> int:
        now = _now()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                INSERT INTO agent_memory(category, content, created_at, updated_at)
                VALUES(?, ?, ?, ?)
                """,
                (category[:40], content[:4000], now, now),
            )
            memory_id = int(cursor.lastrowid)
            if self._fts_available:
                connection.execute(
                    """
                    INSERT INTO agent_memory_fts(memory_id, category, content)
                    VALUES(?, ?, ?)
                    """,
                    (str(memory_id), category[:40], content[:4000]),
                )
        return memory_id

    def search(self, query: str, limit: int = 10) -> list[dict[str, Any]]:
        normalized = query.strip()
        if not normalized:
            return []
        with self._connect() as connection:
            if self._fts_available:
                try:
                    rows = connection.execute(
                        """
                        SELECT 'memory' AS source, memory_id AS record_id,
                               category AS role, content
                        FROM agent_memory_fts WHERE agent_memory_fts MATCH ?
                        UNION ALL
                        SELECT 'message' AS source, session_id AS record_id,
                               role, content
                        FROM agent_message_fts WHERE agent_message_fts MATCH ?
                        LIMIT ?
                        """,
                        (normalized, normalized, max(1, limit)),
                    ).fetchall()
                except sqlite3.OperationalError:
                    rows = self._search_like(connection, normalized, limit)
            else:
                rows = self._search_like(connection, normalized, limit)
        return [dict(row) for row in rows]

    def _search_like(
        self, connection: sqlite3.Connection, query: str, limit: int
    ) -> list[sqlite3.Row]:
        pattern = f"%{query}%"
        return connection.execute(
            """
            SELECT 'memory' AS source, CAST(id AS TEXT) AS record_id,
                   category AS role, content
            FROM agent_memory WHERE content LIKE ?
            UNION ALL
            SELECT 'message' AS source, session_id AS record_id,
                   role, content
            FROM agent_message WHERE content LIKE ?
            LIMIT ?
            """,
            (pattern, pattern, max(1, limit)),
        ).fetchall()

    def list_memories(self, limit: int = 20) -> list[dict[str, Any]]:
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT id, category, content, updated_at
                FROM agent_memory ORDER BY updated_at DESC LIMIT ?
                """,
                (max(1, limit),),
            ).fetchall()
        return [dict(row) for row in rows]

    def audit_tool(
        self,
        session_id: str,
        tool_name: str,
        arguments_summary: str,
        result_status: str,
        result_summary: str,
    ) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO agent_tool_audit(
                    session_id, tool_name, arguments_summary,
                    result_status, result_summary, created_at
                ) VALUES(?, ?, ?, ?, ?, ?)
                """,
                (
                    session_id,
                    tool_name,
                    arguments_summary[:1000],
                    result_status[:40],
                    result_summary[:2000],
                    _now(),
                ),
            )

    def interrupt_running_repair_tasks(self) -> int:
        now = _now()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE agent_repair_task
                SET status = 'INTERRUPTED',
                    error_message = 'Agent 进程在任务完成前退出',
                    updated_at = ?
                WHERE status = 'RUNNING'
                """,
                (now,),
            )
        return int(cursor.rowcount)

    def create_repair_task(
        self,
        run_id: int,
        repository: str,
        target_branch: str,
        work_branch: str | None,
    ) -> str:
        task_id = uuid.uuid4().hex[:16]
        now = _now()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO agent_repair_task(
                    task_id, run_id, repository, target_branch, work_branch,
                    status, created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, 'RUNNING', ?, ?)
                """,
                (task_id, run_id, repository, target_branch, work_branch, now, now),
            )
        return task_id

    def update_repair_task(self, task_id: str, **values: Any) -> None:
        allowed = {
            "work_branch", "status", "summary", "current_sha",
            "final_run_id", "error_message",
        }
        updates = {key: value for key, value in values.items() if key in allowed}
        if not updates:
            return
        updates["updated_at"] = _now()
        assignments = ", ".join(f"{key} = ?" for key in updates)
        with self._connect() as connection:
            connection.execute(
                f"UPDATE agent_repair_task SET {assignments} WHERE task_id = ?",
                (*updates.values(), task_id),
            )

    def add_repair_attempt(
        self,
        task_id: str,
        attempt_no: int,
        base_sha: str,
        summary: str,
        changed_paths: list[str],
        *,
        commit_sha: str | None = None,
        run_id: int | None = None,
        conclusion: str | None = None,
    ) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO agent_repair_attempt(
                    task_id, attempt_no, base_sha, commit_sha, run_id,
                    conclusion, summary, changed_paths, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    task_id, attempt_no, base_sha, commit_sha, run_id,
                    conclusion, summary[:1000],
                    json.dumps(changed_paths, ensure_ascii=False), _now(),
                ),
            )

    def update_repair_attempt(
        self,
        task_id: str,
        attempt_no: int,
        *,
        commit_sha: str | None,
        run_id: int | None,
        conclusion: str,
    ) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                UPDATE agent_repair_attempt
                SET commit_sha = ?, run_id = ?, conclusion = ?
                WHERE task_id = ? AND attempt_no = ?
                """,
                (commit_sha, run_id, conclusion, task_id, attempt_no),
            )

    def get_repair_task(self, task_id: str) -> dict[str, Any] | None:
        with self._connect() as connection:
            task = connection.execute(
                "SELECT * FROM agent_repair_task WHERE task_id = ?", (task_id,)
            ).fetchone()
            if task is None:
                return None
            attempts = connection.execute(
                """
                SELECT attempt_no, base_sha, commit_sha, run_id, conclusion,
                       summary, changed_paths, created_at
                FROM agent_repair_attempt
                WHERE task_id = ? ORDER BY attempt_no
                """,
                (task_id,),
            ).fetchall()
        result = dict(task)
        result["attempts"] = [dict(row) for row in attempts]
        return result

    def list_repair_tasks(self, limit: int = 20) -> list[dict[str, Any]]:
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT task_id, run_id, repository, target_branch, work_branch,
                       status, summary, current_sha, final_run_id,
                       error_message, created_at, updated_at
                FROM agent_repair_task ORDER BY updated_at DESC LIMIT ?
                """,
                (max(1, min(limit, 100)),),
            ).fetchall()
        return [dict(row) for row in rows]

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self._path)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        return connection


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
# AI_GENERATE_END ---
