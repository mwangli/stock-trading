# AI_GENERATE_START --
from __future__ import annotations

import hashlib
import json
import sqlite3
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import pandas as pd

SCHEMA = """
CREATE TABLE IF NOT EXISTS task_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_key TEXT NOT NULL UNIQUE,
    task_type TEXT NOT NULL,
    business_date TEXT NOT NULL,
    status TEXT NOT NULL,
    started_at TEXT,
    finished_at TEXT,
    error_message TEXT
);

CREATE TABLE IF NOT EXISTS model_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    artifact_hash TEXT NOT NULL UNIQUE,
    model_name TEXT NOT NULL,
    feature_set TEXT NOT NULL,
    status TEXT NOT NULL,
    artifact_path TEXT NOT NULL,
    metrics_json TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS prediction (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_version_id INTEGER NOT NULL,
    prediction_date TEXT NOT NULL,
    instrument TEXT NOT NULL,
    rank INTEGER NOT NULL,
    score REAL NOT NULL,
    label REAL,
    created_at TEXT NOT NULL,
    UNIQUE(model_version_id, prediction_date, instrument),
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE TABLE IF NOT EXISTS shadow_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_order_id TEXT NOT NULL UNIQUE,
    business_date TEXT NOT NULL,
    instrument TEXT NOT NULL,
    side TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    price REAL NOT NULL,
    status TEXT NOT NULL,
    candidate_rank INTEGER,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS shadow_execution (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    execution_id TEXT NOT NULL UNIQUE,
    order_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL,
    price REAL NOT NULL,
    fee REAL NOT NULL,
    occurred_at TEXT NOT NULL,
    FOREIGN KEY(order_id) REFERENCES shadow_order(id)
);

CREATE TABLE IF NOT EXISTS position (
    instrument TEXT PRIMARY KEY,
    quantity INTEGER NOT NULL,
    available_quantity INTEGER NOT NULL,
    average_cost REAL NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS performance_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    scenario TEXT NOT NULL,
    business_date TEXT NOT NULL,
    cash REAL NOT NULL,
    market_value REAL NOT NULL,
    equity REAL NOT NULL,
    created_at TEXT NOT NULL,
    UNIQUE(scenario, business_date)
);

CREATE TABLE IF NOT EXISTS system_setting (
    setting_key TEXT PRIMARY KEY,
    setting_value TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
"""


class StateStore:
    """Transactional SQLite store for idempotent research and shadow-trading state."""

    def __init__(self, path: Path) -> None:
        self._path = path

    def initialize(self) -> None:
        self._path.parent.mkdir(parents=True, exist_ok=True)
        with self._connect() as connection:
            connection.executescript(SCHEMA)
            connection.execute(
                """
                INSERT INTO system_setting(setting_key, setting_value, updated_at)
                VALUES('REAL_TRADING_ENABLED', 'false', ?)
                ON CONFLICT(setting_key) DO UPDATE SET
                    setting_value = excluded.setting_value,
                    updated_at = excluded.updated_at
                """,
                (_now(),),
            )

    def recover_interrupted_runs(self) -> int:
        self.initialize()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE task_run
                SET status = 'FAILED', finished_at = ?,
                    error_message = 'application restarted before task completion'
                WHERE status IN ('QUEUED', 'RUNNING')
                """,
                (_now(),),
            )
            return cursor.rowcount

    def import_research_artifacts(
        self,
        model_dir: Path,
        backtest_dir: Path,
        scenario: str,
    ) -> dict[str, Any]:
        self.initialize()
        self.recover_interrupted_runs()
        metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
        topk = pd.read_parquet(model_dir / "top5.parquet")
        business_date = pd.to_datetime(topk["datetime"]).max().date().isoformat()
        fingerprint = _artifact_fingerprint(
            model_dir / "model.pkl",
            model_dir / "top5.parquet",
            backtest_dir / "summary.json",
        )
        business_key = f"IMPORT_RESEARCH:{scenario}:{business_date}:{fingerprint[:16]}"
        if self._successful_run_exists(business_key):
            return {"businessKey": business_key, "status": "SKIPPED"} | self.summary()

        self._start_run(business_key, "IMPORT_RESEARCH", business_date)
        try:
            with self._connect() as connection:
                model_id = self._upsert_model(connection, model_dir, metrics)
                prediction_rows = self._upsert_predictions(connection, model_id, topk)
                order_rows, execution_rows = self._upsert_shadow_trades(
                    connection, backtest_dir
                )
                performance_rows = self._upsert_performance(
                    connection, backtest_dir, scenario
                )
                position_rows = self._replace_position(connection, backtest_dir)
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "predictions": prediction_rows,
            "orders": order_rows,
            "executions": execution_rows,
            "positions": position_rows,
            "performanceSnapshots": performance_rows,
        } | self.summary()

    def summary(self) -> dict[str, Any]:
        self.initialize()
        with self._connect() as connection:
            counts = {
                table: connection.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
                for table in (
                    "task_run",
                    "model_version",
                    "prediction",
                    "shadow_order",
                    "shadow_execution",
                    "position",
                    "performance_snapshot",
                )
            }
            latest_run = connection.execute(
                """
                SELECT business_key, status, business_date, error_message
                FROM task_run ORDER BY id DESC LIMIT 1
                """
            ).fetchone()
            setting = connection.execute(
                "SELECT setting_value FROM system_setting WHERE setting_key = 'REAL_TRADING_ENABLED'"
            ).fetchone()
            latest_model = connection.execute(
                """
                SELECT model_name, feature_set, status, artifact_hash
                FROM model_version ORDER BY id DESC LIMIT 1
                """
            ).fetchone()
        return {
            "database": str(self._path),
            "counts": counts,
            "latestRun": dict(latest_run) if latest_run else None,
            "latestModel": dict(latest_model) if latest_model else None,
            "realTradingEnabled": setting[0] == "true" if setting else False,
        }

    def _connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self._path, timeout=30)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        connection.execute("PRAGMA busy_timeout = 30000")
        return connection

    def _successful_run_exists(self, business_key: str) -> bool:
        with self._connect() as connection:
            row = connection.execute(
                "SELECT 1 FROM task_run WHERE business_key = ? AND status = 'SUCCESS'",
                (business_key,),
            ).fetchone()
            return row is not None

    def _start_run(self, business_key: str, task_type: str, business_date: str) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO task_run(
                    business_key, task_type, business_date, status, started_at
                ) VALUES(?, ?, ?, 'RUNNING', ?)
                ON CONFLICT(business_key) DO UPDATE SET
                    status = 'RUNNING', started_at = excluded.started_at,
                    finished_at = NULL, error_message = NULL
                """,
                (business_key, task_type, business_date, _now()),
            )

    def _finish_run(self, business_key: str, status: str, error: str | None) -> None:
        with self._connect() as connection:
            connection.execute(
                """
                UPDATE task_run SET status = ?, finished_at = ?, error_message = ?
                WHERE business_key = ?
                """,
                (status, _now(), error, business_key),
            )

    @staticmethod
    def _upsert_model(
        connection: sqlite3.Connection, model_dir: Path, metrics: dict[str, Any]
    ) -> int:
        model_path = model_dir / "model.pkl"
        artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
        model_status = "REJECTED" if metrics["metrics"]["rankIcMean"] <= 0 else "CANDIDATE"
        connection.execute(
            """
            INSERT INTO model_version(
                artifact_hash, model_name, feature_set, status,
                artifact_path, metrics_json, created_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(artifact_hash) DO UPDATE SET
                status = excluded.status,
                artifact_path = excluded.artifact_path,
                metrics_json = excluded.metrics_json
            """,
            (
                artifact_hash,
                metrics["model"],
                metrics["featureSet"],
                model_status,
                str(model_path),
                json.dumps(metrics, ensure_ascii=False),
                _now(),
            ),
        )
        return connection.execute(
            "SELECT id FROM model_version WHERE artifact_hash = ?", (artifact_hash,)
        ).fetchone()[0]

    @staticmethod
    def _upsert_predictions(
        connection: sqlite3.Connection, model_id: int, topk: pd.DataFrame
    ) -> int:
        rows = []
        ordered = topk.sort_values(["datetime", "score"], ascending=[True, False])
        for prediction_date, group in ordered.groupby("datetime", sort=True):
            for rank, row in enumerate(group.itertuples(index=False), start=1):
                label = None if pd.isna(row.label) else float(row.label)
                rows.append(
                    (
                        model_id,
                        pd.Timestamp(prediction_date).date().isoformat(),
                        row.instrument,
                        rank,
                        float(row.score),
                        label,
                        _now(),
                    )
                )
        connection.executemany(
            """
            INSERT INTO prediction(
                model_version_id, prediction_date, instrument, rank, score, label, created_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(model_version_id, prediction_date, instrument) DO UPDATE SET
                rank = excluded.rank, score = excluded.score, label = excluded.label
            """,
            rows,
        )
        return len(rows)

    @staticmethod
    def _upsert_shadow_trades(
        connection: sqlite3.Connection, backtest_dir: Path
    ) -> tuple[int, int]:
        trades = pd.read_parquet(backtest_dir / "trades.parquet")
        orders = 0
        executions = 0
        for trade in trades.itertuples(index=False):
            for side, business_date, price, fee in (
                ("BUY", trade.buyDate, trade.buyPrice, trade.buyFees),
                ("SELL", trade.sellDate, trade.sellPrice, trade.sellFees),
            ):
                client_order_id = (
                    f"SHADOW:{business_date}:{trade.instrument}:{side}:{trade.candidateRank}"
                )
                connection.execute(
                    """
                    INSERT INTO shadow_order(
                        client_order_id, business_date, instrument, side, quantity,
                        price, status, candidate_rank, created_at
                    ) VALUES(?, ?, ?, ?, ?, ?, 'FILLED', ?, ?)
                    ON CONFLICT(client_order_id) DO UPDATE SET
                        quantity = excluded.quantity, price = excluded.price,
                        status = excluded.status
                    """,
                    (
                        client_order_id,
                        str(business_date),
                        trade.instrument,
                        side,
                        int(trade.quantity),
                        float(price),
                        int(trade.candidateRank),
                        _now(),
                    ),
                )
                order_id = connection.execute(
                    "SELECT id FROM shadow_order WHERE client_order_id = ?",
                    (client_order_id,),
                ).fetchone()[0]
                execution_id = f"{client_order_id}:FILL"
                connection.execute(
                    """
                    INSERT INTO shadow_execution(
                        execution_id, order_id, quantity, price, fee, occurred_at
                    ) VALUES(?, ?, ?, ?, ?, ?)
                    ON CONFLICT(execution_id) DO UPDATE SET
                        quantity = excluded.quantity, price = excluded.price, fee = excluded.fee
                    """,
                    (
                        execution_id,
                        order_id,
                        int(trade.quantity),
                        float(price),
                        float(fee),
                        f"{business_date}T00:00:00+08:00",
                    ),
                )
                orders += 1
                executions += 1
        return orders, executions

    @staticmethod
    def _upsert_performance(
        connection: sqlite3.Connection, backtest_dir: Path, scenario: str
    ) -> int:
        equity = pd.read_parquet(backtest_dir / "daily_equity.parquet")
        rows = [
            (
                scenario,
                str(row.tradeDate),
                float(row.cash),
                float(row.marketValue),
                float(row.equity),
                _now(),
            )
            for row in equity.itertuples(index=False)
        ]
        connection.executemany(
            """
            INSERT INTO performance_snapshot(
                scenario, business_date, cash, market_value, equity, created_at
            ) VALUES(?, ?, ?, ?, ?, ?)
            ON CONFLICT(scenario, business_date) DO UPDATE SET
                cash = excluded.cash, market_value = excluded.market_value,
                equity = excluded.equity
            """,
            rows,
        )
        return len(rows)

    @staticmethod
    def _replace_position(connection: sqlite3.Connection, backtest_dir: Path) -> int:
        summary = json.loads((backtest_dir / "summary.json").read_text(encoding="utf-8"))
        open_position = summary.get("openPosition")
        connection.execute("DELETE FROM position")
        if open_position is None:
            return 0
        equity = pd.read_parquet(backtest_dir / "daily_equity.parquet")
        latest_date = pd.to_datetime(equity["tradeDate"]).max().date().isoformat()
        available = (
            int(open_position["quantity"])
            if open_position["buyDate"] < latest_date
            else 0
        )
        average_cost = (
            float(open_position["buyNotional"]) + float(open_position["buyFees"])
        ) / int(open_position["quantity"])
        connection.execute(
            """
            INSERT INTO position(
                instrument, quantity, available_quantity, average_cost, updated_at
            ) VALUES(?, ?, ?, ?, ?)
            """,
            (
                open_position["instrument"],
                int(open_position["quantity"]),
                available,
                average_cost,
                _now(),
            ),
        )
        return 1


def _now() -> str:
    return datetime.now(UTC).isoformat()


def _artifact_fingerprint(*paths: Path) -> str:
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path).encode("utf-8"))
        digest.update(path.read_bytes() if path.exists() else b"MISSING")
    return digest.hexdigest()
# AI_GENERATE_END --
