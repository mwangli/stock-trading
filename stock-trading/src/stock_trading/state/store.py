# AI_GENERATE_START --------------------------------
from __future__ import annotations

import hashlib
import json
import sqlite3
from contextlib import contextmanager
from dataclasses import asdict
from datetime import UTC, datetime
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterable, Iterator

import pandas as pd

from stock_trading.broker.models import BrokerOrder, BrokerSyncSnapshot

ORDER_TERMINAL_STATUSES = frozenset({"FILLED", "CANCELLED", "REJECTED"})
ORDER_ACTIVE_STATUSES = frozenset(
    {
        "PREPARED",
        "SUBMITTING",
        "SUBMITTED",
        "PARTIAL",
        "CANCEL_SUBMITTING",
        "UNKNOWN",
    }
)
ORDER_TRANSITIONS = {
    "PREPARED": {"SUBMITTING", "REJECTED", "UNKNOWN"},
    "SUBMITTING": {
        "SUBMITTED",
        "PARTIAL",
        "FILLED",
        "CANCELLED",
        "REJECTED",
        "UNKNOWN",
    },
    "SUBMITTED": {
        "PARTIAL",
        "FILLED",
        "CANCEL_SUBMITTING",
        "CANCELLED",
        "REJECTED",
        "UNKNOWN",
    },
    "PARTIAL": {"FILLED", "CANCEL_SUBMITTING", "CANCELLED", "UNKNOWN"},
    "CANCEL_SUBMITTING": {"FILLED", "CANCELLED", "REJECTED", "UNKNOWN"},
    "UNKNOWN": {"SUBMITTED", "PARTIAL", "FILLED", "CANCELLED", "REJECTED"},
    "FILLED": set(),
    "CANCELLED": set(),
    "REJECTED": set(),
}

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
    model_version_id INTEGER,
    prediction_date TEXT,
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

CREATE TABLE IF NOT EXISTS live_performance_snapshot (
    business_date TEXT PRIMARY KEY,
    model_version_id INTEGER,
    cash REAL NOT NULL,
    market_value REAL NOT NULL,
    equity REAL NOT NULL,
    external_cash_flow REAL NOT NULL,
    turnover REAL NOT NULL,
    estimated_fees REAL NOT NULL,
    gross_pnl REAL,
    net_pnl REAL,
    gross_return REAL,
    net_return REAL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE TABLE IF NOT EXISTS model_evaluation (
    business_key TEXT PRIMARY KEY,
    model_version_id INTEGER,
    business_date TEXT NOT NULL,
    window_days INTEGER NOT NULL,
    observations INTEGER NOT NULL,
    gross_positive_ratio REAL,
    total_gross_return REAL,
    total_net_return REAL,
    max_drawdown REAL,
    decision TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE TABLE IF NOT EXISTS training_request (
    business_key TEXT PRIMARY KEY,
    model_version_id INTEGER,
    trigger_type TEXT NOT NULL,
    status TEXT NOT NULL,
    requested_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE TABLE IF NOT EXISTS broker_account_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_key TEXT NOT NULL UNIQUE,
    business_date TEXT NOT NULL,
    available_cash REAL NOT NULL,
    total_assets REAL NOT NULL,
    total_position REAL NOT NULL,
    frozen_amount REAL NOT NULL,
    captured_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS broker_position_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    business_key TEXT NOT NULL,
    instrument TEXT NOT NULL,
    stock_name TEXT,
    quantity INTEGER NOT NULL,
    available_quantity INTEGER NOT NULL,
    frozen_quantity INTEGER NOT NULL,
    average_cost REAL,
    current_price REAL NOT NULL,
    market TEXT,
    captured_at TEXT NOT NULL,
    UNIQUE(business_key, instrument)
);

CREATE TABLE IF NOT EXISTS broker_order_fact (
    broker_order_id TEXT PRIMARY KEY,
    instrument TEXT,
    stock_name TEXT,
    direction TEXT NOT NULL,
    order_price REAL NOT NULL,
    order_quantity INTEGER NOT NULL,
    average_fill_price REAL,
    filled_quantity INTEGER,
    raw_status TEXT,
    status TEXT NOT NULL,
    market TEXT,
    order_time TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS broker_fill_fact (
    fill_key TEXT PRIMARY KEY,
    broker_order_id TEXT,
    instrument TEXT,
    stock_name TEXT,
    direction TEXT NOT NULL,
    fill_price REAL NOT NULL,
    fill_quantity INTEGER NOT NULL,
    fill_amount REAL NOT NULL,
    fill_time TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS broker_order_intent (
    client_order_id TEXT PRIMARY KEY,
    instrument TEXT NOT NULL,
    side TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    price REAL NOT NULL,
    status TEXT NOT NULL,
    broker_order_id TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL,
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
            self._ensure_column(
                connection, "shadow_order", "model_version_id", "INTEGER"
            )
            self._ensure_column(
                connection, "shadow_order", "prediction_date", "TEXT"
            )
            self._ensure_column(
                connection, "live_performance_snapshot", "model_version_id", "INTEGER"
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

    def sync_broker_snapshot(
        self, snapshot: BrokerSyncSnapshot, business_date: str
    ) -> dict[str, Any]:
        self.initialize()
        self.recover_interrupted_runs()
        payload = json.dumps(asdict(snapshot), default=_json_default, sort_keys=True)
        fingerprint = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        business_key = f"SYNC_BROKER:{business_date}:{fingerprint[:16]}"
        if self._successful_run_exists(business_key):
            return {"businessKey": business_key, "status": "SKIPPED"} | self.summary()

        self._start_run(business_key, "SYNC_BROKER", business_date)
        try:
            with self._connect() as connection:
                self._insert_broker_account(connection, business_key, business_date, snapshot)
                position_rows = self._insert_broker_positions(connection, business_key, snapshot)
                order_rows = self._upsert_broker_orders(connection, snapshot)
                fill_rows = self._upsert_broker_fills(connection, snapshot)
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "positions": position_rows,
            "orders": order_rows,
            "fills": fill_rows,
        } | self.summary()

    def import_prediction_artifact(
        self, model_dir: Path, prediction_path: Path
    ) -> dict[str, Any]:
        self.initialize()
        self.recover_interrupted_runs()
        predictions = pd.read_parquet(prediction_path)
        required = {"datetime", "instrument", "score", "label"}
        missing = sorted(required - set(predictions.columns))
        if missing:
            raise ValueError(f"prediction artifact is missing columns: {missing}")
        prediction_dates = pd.to_datetime(predictions["datetime"]).dt.date.unique()
        if len(prediction_dates) != 1:
            raise ValueError("prediction artifact must contain exactly one business date")
        business_date = prediction_dates[0].isoformat()
        metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
        model_hash = hashlib.sha256((model_dir / "model.pkl").read_bytes()).hexdigest()
        business_key = f"PREDICT:{business_date}:{model_hash[:16]}"
        if self._successful_run_exists(business_key):
            return {"businessKey": business_key, "status": "SKIPPED"} | self.summary()

        self._start_run(business_key, "PREDICT", business_date)
        try:
            with self._connect() as connection:
                model_id = self._upsert_model(connection, model_dir, metrics)
                prediction_rows = self._upsert_predictions(
                    connection, model_id, predictions
                )
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "predictionDate": business_date,
            "predictions": prediction_rows,
        } | self.summary()

    def latest_prediction_candidates(
        self,
        before_business_date: str,
        limit: int,
        model_version_id: int | None = None,
    ) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            if model_version_id is None:
                prediction_date = connection.execute(
                    "SELECT MAX(prediction_date) FROM prediction WHERE prediction_date < ?",
                    (before_business_date,),
                ).fetchone()[0]
            else:
                prediction_date = connection.execute(
                    """
                    SELECT MAX(prediction_date) FROM prediction
                    WHERE prediction_date < ? AND model_version_id = ?
                    """,
                    (before_business_date, model_version_id),
                ).fetchone()[0]
            if prediction_date is None:
                return []
            model_id = model_version_id
            if model_id is None:
                model_id = connection.execute(
                    """
                    SELECT model_version_id
                    FROM prediction
                    WHERE prediction_date = ?
                    GROUP BY model_version_id
                    ORDER BY MAX(created_at) DESC, model_version_id DESC
                    LIMIT 1
                    """,
                    (prediction_date,),
                ).fetchone()[0]
            rows = connection.execute(
                """
                SELECT p.model_version_id, p.prediction_date, p.instrument, p.rank, p.score,
                       mv.status AS model_status
                FROM prediction p
                JOIN model_version mv ON mv.id = p.model_version_id
                WHERE p.prediction_date = ? AND p.model_version_id = ?
                ORDER BY p.rank ASC, p.instrument ASC
                LIMIT ?
                """,
                (prediction_date, model_id, max(1, limit)),
            ).fetchall()
        return [dict(row) for row in rows]

    def save_shadow_plan(
        self,
        task_type: str,
        business_date: str,
        orders: list[dict[str, object]],
    ) -> dict[str, Any]:
        if task_type not in {"SHADOW_MORNING_SELL", "SHADOW_AFTERNOON_BUY"}:
            raise ValueError(f"unsupported shadow plan type: {task_type}")
        self.initialize()
        self.recover_interrupted_runs()
        business_key = f"{task_type}:{business_date}"
        if self._successful_run_exists(business_key):
            return {"businessKey": business_key, "status": "SKIPPED"} | self.summary()

        self._start_run(business_key, task_type, business_date)
        try:
            with self._connect() as connection:
                for order in orders:
                    self._insert_shadow_plan_order(connection, business_date, order)
            final_status = "SUCCESS" if orders else "SKIPPED"
            self._finish_run(business_key, final_status, None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        return {
            "businessKey": business_key,
            "status": final_status,
            "plannedOrders": len(orders),
        } | self.summary()

    def list_shadow_orders(self, business_date: str) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT client_order_id, business_date, instrument, side, quantity,
                       price, status, candidate_rank, model_version_id, prediction_date
                FROM shadow_order
                WHERE business_date = ?
                ORDER BY id ASC
                """,
                (business_date,),
            ).fetchall()
        return [dict(row) for row in rows]

    def prepare_real_order(
        self,
        client_order_id: str,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
    ) -> dict[str, Any]:
        if not client_order_id.strip():
            raise ValueError("client_order_id 不能为空")
        if side not in {"BUY", "SELL"}:
            raise ValueError("订单方向必须是 BUY 或 SELL")
        if quantity <= 0:
            raise ValueError("委托数量必须大于零")
        if side == "BUY" and quantity % 100 != 0:
            raise ValueError("A股买入数量必须是 100 股整数倍")
        if price <= 0:
            raise ValueError("委托价格必须大于零")
        self.initialize()
        with self._connect() as connection:
            existing = connection.execute(
                "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
            if existing is not None:
                self._assert_same_order_intent(
                    existing, instrument, side, quantity, price
                )
                return dict(existing)
            shadow_order = connection.execute(
                "SELECT * FROM shadow_order WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
            if shadow_order is None:
                raise PermissionError("真实订单必须来自已持久化的影子计划")
            self._assert_same_shadow_order(
                shadow_order, instrument, side, quantity, price
            )
            connection.execute(
                """
                INSERT INTO broker_order_intent(
                    client_order_id, instrument, side, quantity, price, status,
                    created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, 'PREPARED', ?, ?)
                """,
                (
                    client_order_id,
                    instrument,
                    side,
                    quantity,
                    float(price),
                    _now(),
                    _now(),
                ),
            )
            connection.execute(
                "UPDATE shadow_order SET status = 'PREPARED' WHERE client_order_id = ?",
                (client_order_id,),
            )
            return dict(
                connection.execute(
                    "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
                    (client_order_id,),
                ).fetchone()
            )

    def start_order_submission(self, client_order_id: str) -> None:
        self.initialize()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE broker_order_intent
                SET status = 'SUBMITTING', updated_at = ?
                WHERE client_order_id = ? AND status = 'PREPARED'
                """,
                (_now(), client_order_id),
            )
            if cursor.rowcount != 1:
                raise PermissionError("订单未预留或已提交，拒绝重复提交")
            connection.execute(
                "UPDATE shadow_order SET status = 'SUBMITTING' WHERE client_order_id = ?",
                (client_order_id,),
            )

    def finish_real_order(
        self,
        client_order_id: str,
        status: str,
        broker_order_id: str | None = None,
        error_message: str | None = None,
    ) -> None:
        if status not in ORDER_TRANSITIONS:
            raise ValueError(f"不支持的订单状态: {status}")
        self.initialize()
        with self._connect() as connection:
            self._transition_order_intent(
                connection,
                client_order_id,
                status,
                broker_order_id=broker_order_id,
                error_message=error_message,
            )

    def get_shadow_order(self, client_order_id: str) -> dict[str, Any] | None:
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM shadow_order WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
        return dict(row) if row else None

    def get_order_intent(self, client_order_id: str) -> dict[str, Any] | None:
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
        return dict(row) if row else None

    def list_order_intents(self, active_only: bool = False) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            if active_only:
                rows = connection.execute(
                    f"""
                    SELECT * FROM broker_order_intent
                    WHERE status IN ({','.join('?' for _ in ORDER_ACTIVE_STATUSES)})
                    ORDER BY created_at ASC
                    """,
                    tuple(sorted(ORDER_ACTIVE_STATUSES)),
                ).fetchall()
            else:
                rows = connection.execute(
                    "SELECT * FROM broker_order_intent ORDER BY created_at ASC"
                ).fetchall()
        return [dict(row) for row in rows]

    def reconcile_order_intents(
        self,
        orders: Iterable[BrokerOrder],
        mark_unmatched_unknown: bool = False,
    ) -> list[dict[str, Any]]:
        self.initialize()
        broker_orders = tuple(order for order in orders if order.order_id)
        with self._connect() as connection:
            intents = connection.execute(
                f"""
                SELECT * FROM broker_order_intent
                WHERE status IN ({','.join('?' for _ in ORDER_ACTIVE_STATUSES)})
                ORDER BY created_at ASC
                """,
                tuple(sorted(ORDER_ACTIVE_STATUSES)),
            ).fetchall()
            bound_order_ids = {
                str(row["broker_order_id"])
                for row in intents
                if row["broker_order_id"] is not None
            }
            matched_clients: set[str] = set()
            for intent in intents:
                matched = self._match_broker_order(
                    intent, broker_orders, bound_order_ids
                )
                if matched is None:
                    continue
                matched_clients.add(str(intent["client_order_id"]))
                bound_order_ids.add(str(matched.order_id))
                if (
                    intent["status"] == "CANCEL_SUBMITTING"
                    and matched.status in {"SUBMITTED", "PARTIAL"}
                ):
                    continue
                self._transition_order_intent(
                    connection,
                    str(intent["client_order_id"]),
                    matched.status,
                    broker_order_id=str(matched.order_id),
                    error_message=(
                        f"券商返回未知状态: {matched.raw_status}"
                        if matched.status == "UNKNOWN"
                        else None
                    ),
                )
            if mark_unmatched_unknown:
                for intent in intents:
                    client_order_id = str(intent["client_order_id"])
                    if client_order_id in matched_clients or intent["status"] == "PREPARED":
                        continue
                    self._transition_order_intent(
                        connection,
                        client_order_id,
                        "UNKNOWN",
                        error_message="重启恢复未找到唯一券商委托事实",
                    )
            rows = connection.execute(
                "SELECT * FROM broker_order_intent ORDER BY created_at ASC"
            ).fetchall()
        return [dict(row) for row in rows]

    def latest_live_performance_before(
        self, business_date: str
    ) -> dict[str, Any] | None:
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT * FROM live_performance_snapshot
                WHERE business_date < ?
                ORDER BY business_date DESC LIMIT 1
                """,
                (business_date,),
            ).fetchone()
        return dict(row) if row else None

    def active_model_id(self) -> int | None:
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT id FROM model_version
                WHERE status = 'ACTIVE'
                ORDER BY id DESC LIMIT 1
                """
            ).fetchone()
        return int(row["id"]) if row else None

    def save_live_performance(self, snapshot: dict[str, object]) -> dict[str, Any]:
        self.initialize()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO live_performance_snapshot(
                    business_date, model_version_id, cash, market_value, equity,
                    external_cash_flow, turnover, estimated_fees, gross_pnl,
                    net_pnl, gross_return, net_return, created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(business_date) DO UPDATE SET
                    model_version_id = excluded.model_version_id,
                    cash = excluded.cash,
                    market_value = excluded.market_value,
                    equity = excluded.equity,
                    external_cash_flow = excluded.external_cash_flow,
                    turnover = excluded.turnover,
                    estimated_fees = excluded.estimated_fees,
                    gross_pnl = excluded.gross_pnl,
                    net_pnl = excluded.net_pnl,
                    gross_return = excluded.gross_return,
                    net_return = excluded.net_return,
                    updated_at = excluded.updated_at
                """,
                (
                    snapshot["businessDate"],
                    snapshot["modelVersionId"],
                    snapshot["cash"],
                    snapshot["marketValue"],
                    snapshot["equity"],
                    snapshot["externalCashFlow"],
                    snapshot["turnover"],
                    snapshot["estimatedFees"],
                    snapshot["grossPnl"],
                    snapshot["netPnl"],
                    snapshot["grossReturn"],
                    snapshot["netReturn"],
                    _now(),
                    _now(),
                ),
            )
            row = connection.execute(
                "SELECT * FROM live_performance_snapshot WHERE business_date = ?",
                (snapshot["businessDate"],),
            ).fetchone()
        return dict(row)

    def recent_live_performance(
        self, model_version_id: int | None, limit: int
    ) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT * FROM live_performance_snapshot
                WHERE gross_return IS NOT NULL AND net_return IS NOT NULL
                  AND model_version_id IS ?
                ORDER BY business_date DESC LIMIT ?
                """,
                (model_version_id, max(1, limit)),
            ).fetchall()
        return [dict(row) for row in reversed(rows)]

    def save_model_evaluation(
        self,
        business_date: str,
        window_days: int,
        observations: int,
        gross_positive_ratio: float | None,
        total_gross_return: float | None,
        total_net_return: float | None,
        max_drawdown: float | None,
        decision: str,
        reason: str,
    ) -> dict[str, Any]:
        self.initialize()
        with self._connect() as connection:
            existing = connection.execute(
                """
                SELECT * FROM model_evaluation
                WHERE business_date = ?
                ORDER BY created_at DESC LIMIT 1
                """,
                (business_date,),
            ).fetchone()
            if existing is not None:
                request_key = (
                    f"TRAINING_REQUEST:{existing['model_version_id'] or 'NONE'}:"
                    f"{business_date}"
                )
                request = connection.execute(
                    "SELECT * FROM training_request WHERE business_key = ?",
                    (request_key,),
                ).fetchone()
                return {
                    "evaluation": dict(existing),
                    "trainingRequest": dict(request) if request else None,
                }
            model = connection.execute(
                """
                SELECT id FROM model_version
                WHERE status = 'ACTIVE'
                ORDER BY id DESC LIMIT 1
                """
            ).fetchone()
            model_id = int(model["id"]) if model else None
            if model_id is None and decision not in {"WARMUP", "TRAIN_REQUIRED"}:
                decision = "TRAIN_REQUIRED"
                reason = f"没有 ACTIVE 模型；{reason}"
            business_key = f"MODEL_EVALUATION:{model_id or 'NONE'}:{business_date}"
            connection.execute(
                """
                INSERT INTO model_evaluation(
                    business_key, model_version_id, business_date, window_days,
                    observations, gross_positive_ratio, total_gross_return,
                    total_net_return, max_drawdown, decision, reason, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    business_key,
                    model_id,
                    business_date,
                    window_days,
                    observations,
                    gross_positive_ratio,
                    total_gross_return,
                    total_net_return,
                    max_drawdown,
                    decision,
                    reason,
                    _now(),
                ),
            )
            training_request = None
            if decision in {"RETRAIN", "TRAIN_REQUIRED"}:
                if decision == "RETRAIN" and model_id is not None:
                    connection.execute(
                        "UPDATE model_version SET status = 'DEGRADED' WHERE id = ?",
                        (model_id,),
                    )
                request_key = f"TRAINING_REQUEST:{model_id or 'NONE'}:{business_date}"
                connection.execute(
                    """
                    INSERT INTO training_request(
                        business_key, model_version_id, trigger_type, status,
                        requested_at, updated_at
                    ) VALUES(?, ?, ?, 'REQUESTED', ?, ?)
                    ON CONFLICT(business_key) DO NOTHING
                    """,
                    (
                        request_key,
                        model_id,
                        "PERFORMANCE_DEGRADATION"
                        if decision == "RETRAIN"
                        else "NO_ACTIVE_MODEL",
                        _now(),
                        _now(),
                    ),
                )
                request = connection.execute(
                    "SELECT * FROM training_request WHERE business_key = ?",
                    (request_key,),
                ).fetchone()
                training_request = dict(request)
            evaluation = connection.execute(
                "SELECT * FROM model_evaluation WHERE business_key = ?",
                (business_key,),
            ).fetchone()
        return {
            "evaluation": dict(evaluation),
            "trainingRequest": training_request,
        }

    def list_training_requests(self) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM training_request ORDER BY requested_at ASC"
            ).fetchall()
        return [dict(row) for row in rows]

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
                    "live_performance_snapshot",
                    "model_evaluation",
                    "training_request",
                    "broker_account_snapshot",
                    "broker_position_snapshot",
                    "broker_order_fact",
                    "broker_fill_fact",
                    "broker_order_intent",
                )
            }
            latest_run = connection.execute(
                """
                SELECT business_key, status, business_date, error_message
                FROM task_run ORDER BY id DESC LIMIT 1
                """
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
        }

    @staticmethod
    def _insert_broker_account(
        connection: sqlite3.Connection,
        business_key: str,
        business_date: str,
        snapshot: BrokerSyncSnapshot,
    ) -> None:
        account = snapshot.account
        connection.execute(
            """
            INSERT INTO broker_account_snapshot(
                business_key, business_date, available_cash, total_assets,
                total_position, frozen_amount, captured_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            """,
            (
                business_key,
                business_date,
                float(account.available_cash),
                float(account.total_assets),
                float(account.total_position),
                float(account.frozen_amount),
                _now(),
            ),
        )

    @staticmethod
    def _insert_broker_positions(
        connection: sqlite3.Connection,
        business_key: str,
        snapshot: BrokerSyncSnapshot,
    ) -> int:
        rows = [
            (
                business_key,
                item.stock_code,
                item.stock_name,
                item.quantity,
                item.available_quantity,
                item.frozen_quantity,
                _optional_float(item.average_cost),
                float(item.current_price),
                item.market,
                _now(),
            )
            for item in snapshot.positions
        ]
        connection.executemany(
            """
            INSERT INTO broker_position_snapshot(
                business_key, instrument, stock_name, quantity, available_quantity,
                frozen_quantity, average_cost, current_price, market, captured_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            rows,
        )
        return len(rows)

    @staticmethod
    def _insert_shadow_plan_order(
        connection: sqlite3.Connection,
        business_date: str,
        order: dict[str, object],
    ) -> None:
        client_order_id = str(order["clientOrderId"])
        instrument = str(order["instrument"])
        side = str(order["side"])
        quantity = int(order["quantity"])
        price = Decimal(str(order["price"]))
        candidate_rank = order.get("candidateRank")
        model_version_id = order.get("modelVersionId")
        prediction_date = order.get("predictionDate")
        if not client_order_id or not instrument:
            raise ValueError("影子订单业务键和股票代码不能为空")
        if side not in {"BUY", "SELL"}:
            raise ValueError("影子订单方向必须是 BUY 或 SELL")
        if quantity <= 0:
            raise ValueError("影子订单数量必须大于零")
        if side == "BUY" and quantity % 100 != 0:
            raise ValueError("影子买入数量必须是 100 股整数倍")
        if price <= 0:
            raise ValueError("影子订单价格必须大于零")
        connection.execute(
            """
            INSERT INTO shadow_order(
                client_order_id, business_date, instrument, side, quantity,
                price, status, candidate_rank, model_version_id, prediction_date,
                created_at
            ) VALUES(?, ?, ?, ?, ?, ?, 'PLANNED', ?, ?, ?, ?)
            ON CONFLICT(client_order_id) DO NOTHING
            """,
            (
                client_order_id,
                business_date,
                instrument,
                side,
                quantity,
                float(price),
                candidate_rank,
                model_version_id,
                prediction_date,
                _now(),
            ),
        )

    @staticmethod
    def _upsert_broker_orders(
        connection: sqlite3.Connection, snapshot: BrokerSyncSnapshot
    ) -> int:
        rows = [item for item in snapshot.orders if item.order_id]
        connection.executemany(
            """
            INSERT INTO broker_order_fact(
                broker_order_id, instrument, stock_name, direction, order_price,
                order_quantity, average_fill_price, filled_quantity, raw_status,
                status, market, order_time, updated_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(broker_order_id) DO UPDATE SET
                average_fill_price = excluded.average_fill_price,
                filled_quantity = excluded.filled_quantity,
                raw_status = excluded.raw_status,
                status = excluded.status,
                updated_at = excluded.updated_at
            """,
            [
                (
                    item.order_id,
                    item.stock_code,
                    item.stock_name,
                    item.direction,
                    float(item.order_price),
                    item.order_quantity,
                    _optional_float(item.average_fill_price),
                    item.filled_quantity,
                    item.raw_status,
                    item.status,
                    item.market,
                    item.order_time.isoformat() if item.order_time else None,
                    _now(),
                )
                for item in rows
            ],
        )
        return len(rows)

    @staticmethod
    def _upsert_broker_fills(
        connection: sqlite3.Connection, snapshot: BrokerSyncSnapshot
    ) -> int:
        rows = []
        for item in snapshot.fills:
            raw_key = "|".join(
                (
                    item.order_id or "",
                    item.stock_code or "",
                    item.direction,
                    str(item.fill_price),
                    str(item.fill_quantity),
                    item.fill_time.isoformat() if item.fill_time else "",
                )
            )
            rows.append(
                (
                    hashlib.sha256(raw_key.encode("utf-8")).hexdigest(),
                    item.order_id,
                    item.stock_code,
                    item.stock_name,
                    item.direction,
                    float(item.fill_price),
                    item.fill_quantity,
                    float(item.fill_amount),
                    item.fill_time.isoformat() if item.fill_time else None,
                    _now(),
                )
            )
        connection.executemany(
            """
            INSERT INTO broker_fill_fact(
                fill_key, broker_order_id, instrument, stock_name, direction,
                fill_price, fill_quantity, fill_amount, fill_time, created_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(fill_key) DO NOTHING
            """,
            rows,
        )
        return len(rows)

    @staticmethod
    def _assert_same_order_intent(
        existing: sqlite3.Row,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
    ) -> None:
        same = (
            existing["instrument"] == instrument
            and existing["side"] == side
            and int(existing["quantity"]) == quantity
            and Decimal(str(existing["price"])) == price
        )
        if not same:
            raise ValueError("client_order_id 已绑定不同订单参数")

    @staticmethod
    def _assert_same_shadow_order(
        shadow_order: sqlite3.Row,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
    ) -> None:
        same = (
            shadow_order["instrument"] == instrument
            and shadow_order["side"] == side
            and int(shadow_order["quantity"]) == quantity
            and Decimal(str(shadow_order["price"])) == price
        )
        if not same:
            raise ValueError("影子计划与真实订单参数不一致")

    @staticmethod
    def _match_broker_order(
        intent: sqlite3.Row,
        orders: tuple[BrokerOrder, ...],
        bound_order_ids: set[str],
    ) -> BrokerOrder | None:
        broker_order_id = intent["broker_order_id"]
        if broker_order_id is not None:
            return next(
                (
                    order
                    for order in orders
                    if str(order.order_id) == str(broker_order_id)
                ),
                None,
            )
        candidates = [
            order
            for order in orders
            if str(order.order_id) not in bound_order_ids
            and _broker_order_matches_intent(order, intent)
        ]
        return candidates[0] if len(candidates) == 1 else None

    @staticmethod
    def _transition_order_intent(
        connection: sqlite3.Connection,
        client_order_id: str,
        target_status: str,
        broker_order_id: str | None = None,
        error_message: str | None = None,
    ) -> None:
        row = connection.execute(
            "SELECT status, broker_order_id FROM broker_order_intent WHERE client_order_id = ?",
            (client_order_id,),
        ).fetchone()
        if row is None:
            raise ValueError(f"订单意图不存在: {client_order_id}")
        current_status = str(row["status"])
        if (
            target_status != current_status
            and target_status not in ORDER_TRANSITIONS[current_status]
        ):
            raise ValueError(f"非法订单状态转换: {current_status} -> {target_status}")
        effective_order_id = broker_order_id or row["broker_order_id"]
        connection.execute(
            """
            UPDATE broker_order_intent
            SET status = ?, broker_order_id = ?, error_message = ?, updated_at = ?
            WHERE client_order_id = ?
            """,
            (
                target_status,
                effective_order_id,
                error_message,
                _now(),
                client_order_id,
            ),
        )
        connection.execute(
            "UPDATE shadow_order SET status = ? WHERE client_order_id = ?",
            (target_status, client_order_id),
        )

    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        connection = sqlite3.connect(self._path, timeout=30)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute("PRAGMA journal_mode = WAL")
        connection.execute("PRAGMA busy_timeout = 30000")
        try:
            yield connection
            connection.commit()
        except BaseException:
            connection.rollback()
            raise
        finally:
            connection.close()

    @staticmethod
    def _ensure_column(
        connection: sqlite3.Connection,
        table_name: str,
        column_name: str,
        column_type: str,
    ) -> None:
        columns = {
            str(row["name"])
            for row in connection.execute(f"PRAGMA table_info({table_name})").fetchall()
        }
        if column_name not in columns:
            connection.execute(
                f"ALTER TABLE {table_name} ADD COLUMN {column_name} {column_type}"
            )

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


def _json_default(value: object) -> str:
    if isinstance(value, (Decimal, datetime)):
        return str(value)
    raise TypeError(f"unsupported JSON value: {type(value).__name__}")


def _optional_float(value: Decimal | None) -> float | None:
    return float(value) if value is not None else None


def _broker_order_matches_intent(order: BrokerOrder, intent: sqlite3.Row) -> bool:
    instrument = str(intent["instrument"])
    return (
        order.stock_code == instrument[-6:]
        and order.direction == intent["side"]
        and order.order_quantity == int(intent["quantity"])
        and order.order_price == Decimal(str(intent["price"]))
    )
# AI_GENERATE_END --------------------------------
