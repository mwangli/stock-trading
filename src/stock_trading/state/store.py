# AI_GENERATE_START----------- ------------------------------------------------------------------------------
from __future__ import annotations

import hashlib
import json
import logging
import sqlite3
from datetime import UTC, date, datetime, time, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from stock_trading.broker.models import BrokerAccount, BrokerOrder
from stock_trading.utils.logging_utils import set_log_context
from stock_trading.state.database import (
    SQLiteStateDatabase, SCHEMA, SCHEMA_VERSION, SCHEMA_MIGRATIONS,
)
from stock_trading.market.domain import shift_trade_date
from stock_trading.config import TRADING_TIME_CONTRACT

SHANGHAI_ZONE = ZoneInfo("Asia/Shanghai")

LOGGER = logging.getLogger(__name__)

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
        "CANCEL_SUBMITTING",
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
    "UNKNOWN": {
        "SUBMITTED",
        "PARTIAL",
        "FILLED",
        "CANCEL_SUBMITTING",
        "CANCELLED",
        "REJECTED",
    },
    "FILLED": set(),
    "CANCELLED": set(),
    "REJECTED": set(),
}

class StateStore(SQLiteStateDatabase):
    """Transactional SQLite store for model, broker, and realized-trade state."""

    def __init__(self, path: Path) -> None:
        super().__init__(path)

    def initialize(self) -> None:
        """创建或校验当前状态库结构。"""
        self.initialize_schema(SCHEMA, SCHEMA_VERSION, SCHEMA_MIGRATIONS)

    def append_notification(
        self,
        category: str,
        level: str,
        title: str,
        content: str,
        source: str,
        retention_days: int,
        event_key: str | None = None,
    ) -> bool:
        """保存通知；交易通知可通过 event_key 幂等去重。"""
        if category not in {"SYSTEM", "TRADE"}:
            raise ValueError(f"不支持的通知分类: {category}")
        if level not in {"INFO", "SUCCESS", "WARNING", "ERROR"}:
            raise ValueError(f"不支持的通知级别: {level}")
        if retention_days <= 0:
            raise ValueError("通知保留天数必须大于 0")
        self.initialize()
        created_at = datetime.now(UTC)
        expires_at = created_at + timedelta(days=retention_days)
        with self._connect() as connection:
            inserted = connection.execute(
                """
                INSERT INTO notify_msg(
                    category, level, title, content, source, event_key,
                    created_at, expires_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(event_key) DO NOTHING
                """,
                (
                    category,
                    level,
                    title,
                    content,
                    source,
                    event_key,
                    created_at.isoformat(),
                    expires_at.isoformat(),
                ),
            ).rowcount
        return inserted == 1

    def cleanup(self, reference_date: date) -> dict[str, object]:
        """按数据价值分级清理 SQLite，并保留活动委托和长期基础事实。"""
        self.initialize()
        task_cutoff = (reference_date - timedelta(days=180)).isoformat()
        history_cutoff = (reference_date - timedelta(days=366 * 5)).isoformat()
        notification_cutoff = datetime.now(UTC).isoformat()
        deleted: dict[str, int] = {}
        with self._connect() as connection:
            rules = (
                (
                    "task_run",
                    "business_date < ? AND status NOT IN ('QUEUED','RUNNING')",
                    task_cutoff,
                ),
                ("prediction_rank", "prediction_date < ?", history_cutoff),
                ("model_evaluation", "business_date < ?", history_cutoff),
                (
                    "order_attempt",
                    "substr(updated_at, 1, 10) < ? "
                    "AND status IN ('FILLED','CANCELLED','REJECTED')",
                    history_cutoff,
                ),
                ("notify_msg", "expires_at <= ?", notification_cutoff),
            )
            for table, condition, cutoff in rules:
                deleted[table] = connection.execute(
                    f"DELETE FROM {table} WHERE {condition}", (cutoff,)
                ).rowcount

        deleted_rows = sum(deleted.values())
        if deleted_rows:
            LOGGER.info("SQLite 开始回收文件空间：删除记录=%d", deleted_rows)
            with sqlite3.connect(
                self._path, timeout=30, isolation_level=None
            ) as connection:
                connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
                connection.execute("VACUUM")
                connection.execute("PRAGMA optimize")
        LOGGER.info(
            "SQLite 分级清理完成：删除=%d；任务截止=%s；历史截止=%s",
            deleted_rows,
            task_cutoff,
            history_cutoff,
        )
        return {
            "deletedRows": deleted_rows,
            "deletedByTable": deleted,
            "taskCutoff": task_cutoff,
            "historyCutoff": history_cutoff,
            "notificationCutoff": notification_cutoff,
        }

    def import_model_artifacts(
        self,
        model_dir: Path,
        model_status: str | None = None,
    ) -> dict[str, Any]:
        """事务登记模型及其历史预测，回测明细继续由制品文件负责。"""
        import pandas as pd

        self.initialize()
        set_log_context(progress="-/-", target="模型制品")
        LOGGER.info("读取模型指标和完整测试集排名")
        metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
        rankings = pd.read_parquet(model_dir / "test_predictions.parquet").dropna(
            subset=["score"]
        )
        business_date = pd.to_datetime(rankings["datetime"]).max().date().isoformat()
        fingerprint = _artifact_fingerprint(
            model_dir / "model.pkl",
            model_dir / "test_predictions.parquet",
        )
        business_key = f"IMPORT_MODEL:{business_date}:{fingerprint[:16]}"
        self._start_run(business_key, "IMPORT_MODEL", business_date)
        try:
            with self._connect() as connection:
                set_log_context(progress="-/-", target="模型和预测")
                LOGGER.info("写入模型和预测")
                model_id = self._upsert_model(
                    connection, model_dir, metrics, model_status
                )
                prediction_rows = self._upsert_predictions(connection, model_id, rankings)
            set_log_context(progress="-/-", target="模型制品")
            LOGGER.info("提交事务并记录成功状态")
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        LOGGER.info("模型制品导入完成：预测=%d", prediction_rows)
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "modelId": metrics["modelId"],
            "modelVersionId": model_id,
            "predictions": prediction_rows,
        }

    def upsert_broker_orders(self, orders: tuple[BrokerOrder, ...]) -> dict[str, int]:
        """增量保存券商成交事实，不删除本次接口未返回的历史事实。"""
        self.initialize()
        order_keys = {
            (order.order_time.date().isoformat(), str(order.order_id))
            for order in orders
        }
        now = _now()
        with self._connect() as connection:
            existing_keys = set()
            if order_keys:
                dates = [key[0] for key in order_keys]
                existing_keys = {
                    (str(row["trade_date"]), str(row["broker_order_id"]))
                    for row in connection.execute(
                        """
                        SELECT trade_date, broker_order_id FROM broker_order
                        WHERE trade_date BETWEEN ? AND ?
                        """,
                        (min(dates), max(dates)),
                    ).fetchall()
                } & order_keys
            connection.executemany(
                """
                INSERT INTO broker_order(
                    broker_order_id, trade_date, instrument, stock_name, market,
                    direction, order_price, order_quantity, average_fill_price,
                    filled_quantity, model_version_id, prediction_date,
                    raw_status, status, order_time,
                    created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    (SELECT model_version_id FROM order_attempt WHERE business_date = ? AND broker_order_id = ? ORDER BY created_at DESC LIMIT 1),
                    (SELECT prediction_date FROM order_attempt WHERE business_date = ? AND broker_order_id = ? ORDER BY created_at DESC LIMIT 1),
                    ?, 'FILLED', ?, ?, ?)
                ON CONFLICT(trade_date, broker_order_id) DO UPDATE SET
                    instrument = excluded.instrument,
                    stock_name = excluded.stock_name,
                    market = excluded.market,
                    direction = excluded.direction,
                    order_price = excluded.order_price,
                    order_quantity = excluded.order_quantity,
                    average_fill_price = excluded.average_fill_price,
                    filled_quantity = excluded.filled_quantity,
                    model_version_id = COALESCE(broker_order.model_version_id, excluded.model_version_id),
                    prediction_date = COALESCE(broker_order.prediction_date, excluded.prediction_date),
                    raw_status = excluded.raw_status,
                    order_time = excluded.order_time,
                    updated_at = excluded.updated_at
                """,
                [
                    (
                        str(order.order_id), order.order_time.date().isoformat(),
                        str(order.stock_code), order.stock_name, order.market,
                        order.direction, float(order.order_price),
                        int(order.order_quantity), float(order.average_fill_price),
                        int(order.filled_quantity), order.order_time.date().isoformat(),
                        str(order.order_id), order.order_time.date().isoformat(),
                        str(order.order_id), order.raw_status,
                        order.order_time.isoformat(), now, now,
                    )
                    for order in orders
                ],
            )
            fee_orders = [order for order in orders if order.fee is not None]
            connection.executemany(
                """
                INSERT INTO broker_order_fee(
                    trade_date, broker_order_id, base_fee, commission,
                    stamp_tax, other_broker_fee, aggregate_total, total_fee,
                    fee_source, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(trade_date, broker_order_id) DO UPDATE SET
                    base_fee = excluded.base_fee,
                    commission = excluded.commission,
                    stamp_tax = excluded.stamp_tax,
                    other_broker_fee = excluded.other_broker_fee,
                    aggregate_total = excluded.aggregate_total,
                    total_fee = excluded.total_fee,
                    fee_source = excluded.fee_source,
                    updated_at = excluded.updated_at
                """,
                [
                    (
                        order.order_time.date().isoformat(), str(order.order_id),
                        float(order.fee.base_fee), float(order.fee.commission),
                        float(order.fee.stamp_tax),
                        float(order.fee.other_broker_fee),
                        (
                            float(order.fee.aggregate_total)
                            if order.fee.aggregate_total is not None
                            else None
                        ),
                        float(order.fee.total_fee), order.fee.source, now,
                    )
                    for order in fee_orders
                ],
            )
        return {
            "validOrders": len(order_keys),
            "insertedOrders": len(order_keys - existing_keys),
            "updatedOrders": len(order_keys & existing_keys),
        }

    def broker_orders(self) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM broker_order ORDER BY order_time, broker_order_id"
            ).fetchall()
        return [dict(row) for row in rows]

    def unsettled_broker_orders(self) -> list[dict[str, Any]]:
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                WITH settled AS (
                    SELECT buy_date AS trade_date, buy_order_id AS broker_order_id,
                           SUM(quantity) AS quantity
                    FROM realized_trade
                    GROUP BY buy_date, buy_order_id
                    UNION ALL
                    SELECT sell_date AS trade_date, sell_order_id AS broker_order_id,
                           SUM(quantity) AS quantity
                    FROM realized_trade
                    GROUP BY sell_date, sell_order_id
                ), matched_total AS (
                    SELECT trade_date, broker_order_id, SUM(quantity) AS quantity
                    FROM settled
                    GROUP BY trade_date, broker_order_id
                )
                SELECT broker_order.*,
                       broker_order_fee.base_fee,
                       broker_order_fee.commission,
                       broker_order_fee.stamp_tax,
                       broker_order_fee.other_broker_fee,
                       broker_order_fee.aggregate_total,
                       broker_order_fee.total_fee,
                       broker_order_fee.fee_source,
                       COALESCE(matched_total.quantity, 0) AS matched_quantity
                FROM broker_order
                LEFT JOIN broker_order_fee USING(trade_date, broker_order_id)
                LEFT JOIN matched_total USING(trade_date, broker_order_id)
                WHERE COALESCE(matched_total.quantity, 0) < filled_quantity
                ORDER BY order_time, broker_order_id
                """
            ).fetchall()
        return [dict(row) for row in rows]

    def apply_realized_trades(
        self,
        trades: list[dict[str, object]],
    ) -> int:
        """原子写入新增 FIFO 闭环，订单已结算数量由闭环汇总得出。"""
        self.initialize()
        now = _now()
        with self._connect() as connection:
            connection.executemany(
                """
                INSERT INTO realized_trade(
                    trade_key, model_version_id, instrument, buy_order_id,
                    sell_order_id, buy_date, sell_date, quantity, buy_price,
                    sell_price, buy_fee, sell_fee, cost_basis, sell_amount,
                    gross_profit, net_profit, gross_return, net_return,
                    buy_time, sell_time, created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(trade_key) DO UPDATE SET
                    quantity = realized_trade.quantity + excluded.quantity,
                    buy_price = (
                        realized_trade.cost_basis + excluded.cost_basis
                    ) / (realized_trade.quantity + excluded.quantity),
                    sell_price = (
                        realized_trade.sell_amount + excluded.sell_amount
                    ) / (realized_trade.quantity + excluded.quantity),
                    buy_fee = realized_trade.buy_fee + excluded.buy_fee,
                    sell_fee = realized_trade.sell_fee + excluded.sell_fee,
                    cost_basis = realized_trade.cost_basis + excluded.cost_basis,
                    sell_amount = realized_trade.sell_amount + excluded.sell_amount,
                    gross_profit = realized_trade.gross_profit + excluded.gross_profit,
                    net_profit = realized_trade.net_profit + excluded.net_profit,
                    gross_return = (
                        realized_trade.gross_profit + excluded.gross_profit
                    ) / (realized_trade.cost_basis + excluded.cost_basis),
                    net_return = (
                        realized_trade.net_profit + excluded.net_profit
                    ) / (
                        realized_trade.cost_basis + excluded.cost_basis
                        + realized_trade.buy_fee + excluded.buy_fee
                    ),
                    updated_at = excluded.updated_at
                """,
                [
                    (
                        trade["tradeKey"], trade["modelVersionId"],
                        trade["instrument"], trade["buyOrderId"],
                        trade["sellOrderId"], trade["buyDate"], trade["sellDate"],
                        int(trade["quantity"]), float(Decimal(str(trade["buyPrice"]))),
                        float(Decimal(str(trade["sellPrice"]))),
                        float(Decimal(str(trade["buyFee"]))),
                        float(Decimal(str(trade["sellFee"]))),
                        float(Decimal(str(trade["costBasis"]))),
                        float(Decimal(str(trade["sellAmount"]))),
                        float(Decimal(str(trade["grossProfit"]))),
                        float(Decimal(str(trade["netProfit"]))),
                        float(Decimal(str(trade["grossReturn"]))),
                        float(Decimal(str(trade["netReturn"]))),
                        trade["buyTime"], trade["sellTime"], now, now,
                    )
                    for trade in trades
                ],
            )
        return len(trades)

    def save_broker_permissions(
        self, permissions: list[dict[str, str]]
    ) -> list[dict[str, Any]]:
        """按权限范围幂等保存券商权限证据。"""
        self.initialize()
        with self._connect() as connection:
            connection.executemany(
                """
                INSERT INTO broker_permission(
                    scope, status, evidence_type, evidence_detail,
                    observed_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?)
                ON CONFLICT(scope) DO UPDATE SET
                    status = excluded.status,
                    evidence_type = excluded.evidence_type,
                    evidence_detail = excluded.evidence_detail,
                    observed_at = excluded.observed_at,
                    updated_at = excluded.updated_at
                """,
                [
                    (
                        item["scope"], item["status"], item["evidenceType"],
                        item.get("evidenceDetail"), item["observedAt"], _now(),
                    )
                    for item in permissions
                ],
            )
            rows = connection.execute(
                "SELECT * FROM broker_permission ORDER BY scope"
            ).fetchall()
        return [dict(row) for row in rows]

    def broker_permissions(self) -> list[dict[str, Any]]:
        """读取全部券商权限事实。"""
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM broker_permission ORDER BY scope"
            ).fetchall()
        return [dict(row) for row in rows]

    def save_broker_stock_permission(
        self, permission: dict[str, str]
    ) -> dict[str, Any]:
        """逐股幂等保存一次真实券商权限探测结果。"""
        self.initialize()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO broker_stock_permission(
                    instrument, stock_code, scope, status, evidence_type,
                    error_no, evidence_detail, response_json, response_msg,
                    observed_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(instrument) DO UPDATE SET
                    stock_code = excluded.stock_code,
                    scope = excluded.scope,
                    status = excluded.status,
                    evidence_type = excluded.evidence_type,
                    error_no = excluded.error_no,
                    evidence_detail = excluded.evidence_detail,
                    response_json = excluded.response_json,
                    response_msg = excluded.response_msg,
                    observed_at = excluded.observed_at,
                    updated_at = excluded.updated_at
                """,
                (
                    permission["instrument"],
                    permission["stockCode"],
                    permission["scope"],
                    permission["status"],
                    permission["evidenceType"],
                    permission.get("errorNo"),
                    permission.get("evidenceDetail"),
                    permission.get("responseJson"),
                    permission.get("responseMsg"),
                    permission["observedAt"],
                    _now(),
                ),
            )
            row = connection.execute(
                "SELECT * FROM broker_stock_permission WHERE instrument = ?",
                (permission["instrument"],),
            ).fetchone()
        if row is None:
            raise RuntimeError("逐股券商权限写入后读取失败")
        return dict(row)

    def broker_stock_permissions(self) -> list[dict[str, Any]]:
        """读取全部逐股券商权限事实。"""
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                "SELECT * FROM broker_stock_permission ORDER BY instrument"
            ).fetchall()
        return [dict(row) for row in rows]

    def save_account_snapshot(self, account: BrokerAccount) -> dict[str, Any]:
        """覆盖保存最近一次主动读取的券商账户资金事实。"""
        self.initialize()
        captured_at = _now()
        with self._connect() as connection:
            connection.execute(
                """
                INSERT INTO account_snapshot(
                    id, available_cash, total_assets, total_position,
                    frozen_amount, captured_at
                ) VALUES(1, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    available_cash = excluded.available_cash,
                    total_assets = excluded.total_assets,
                    total_position = excluded.total_position,
                    frozen_amount = excluded.frozen_amount,
                    captured_at = excluded.captured_at
                """,
                (
                    float(account.available_cash),
                    float(account.total_assets),
                    float(account.total_position),
                    float(account.frozen_amount),
                    captured_at,
                ),
            )
            snapshot = connection.execute(
                "SELECT * FROM account_snapshot WHERE id = 1"
            ).fetchone()
        return dict(snapshot)

    def model_artifacts_registered(self, model_dir: Path) -> bool:
        """检查模型和完整历史预测是否已经登记到 SQLite。"""
        import pandas as pd

        self.initialize()
        model_path = model_dir / "model.pkl"
        if not model_path.is_file():
            return False
        try:
            artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
            rankings = pd.read_parquet(
                model_dir / "test_predictions.parquet", columns=["datetime", "score"]
            ).dropna(subset=["score"])
            ranking_dates = pd.to_datetime(rankings["datetime"]).dt.date
            first_prediction_date = ranking_dates.min().isoformat()
            prediction_date = ranking_dates.max().isoformat()
            expected_prediction_count = len(rankings)
        except (OSError, ValueError, KeyError, AttributeError):
            return False
        with self._connect() as connection:
            model = connection.execute(
                "SELECT id FROM model_version WHERE artifact_hash = ?",
                (artifact_hash,),
            ).fetchone()
            if model is None:
                return False
            prediction_count = int(connection.execute(
                """
                SELECT COUNT(*) FROM prediction_rank
                WHERE model_version_id = ? AND prediction_date BETWEEN ? AND ?
                """,
                (model["id"], first_prediction_date, prediction_date),
            ).fetchone()[0])
        return prediction_count == expected_prediction_count

    def prediction_registered(
        self, model_path: Path, prediction_date: str, prediction_path: Path
    ) -> bool:
        """核对单日完整排名及唯一时间元数据，残留或部分导入应重新写入。"""
        import pandas as pd

        self.initialize()
        if not model_path.is_file() or not prediction_path.is_file():
            return False
        try:
            artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
            frame = pd.read_parquet(prediction_path)
        except (OSError, ValueError):
            return False
        columns = {"datetime", "instrument", "score", "execution_date",
                   "generated_at", "contract_version"}
        if frame.empty or not columns.issubset(frame.columns):
            return False
        if frame["instrument"].duplicated().any():
            return False
        dates = pd.to_datetime(frame["datetime"]).dt.date
        if not (dates == date.fromisoformat(prediction_date)).all():
            return False
        metadata = frame[["execution_date", "generated_at", "contract_version"]].drop_duplicates()
        if len(metadata) != 1:
            return False
        execution_date, generated_at, contract_version = metadata.iloc[0]
        expected_execution = shift_trade_date(date.fromisoformat(prediction_date), 1)
        if execution_date != expected_execution.isoformat() or contract_version != TRADING_TIME_CONTRACT:
            return False
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT p.instrument, p.rank, p.score, p.execution_date,
                       p.generated_at, p.contract_version
                FROM prediction_rank p
                JOIN model_version mv ON mv.id = p.model_version_id
                WHERE mv.artifact_hash = ? AND p.prediction_date = ?
                ORDER BY p.rank ASC, p.instrument ASC
                """,
                (artifact_hash, prediction_date),
            ).fetchall()
        expected = frame.sort_values(["score", "instrument"], ascending=[False, True])
        if len(rows) != len(expected):
            return False
        return all(
            actual["instrument"] == row.instrument
            and actual["rank"] == rank
            and actual["score"] == float(row.score)
            and actual["execution_date"] == execution_date
            and actual["generated_at"] == generated_at
            and actual["contract_version"] == contract_version
            for rank, (actual, row) in enumerate(
                zip(rows, expected.itertuples(index=False)), start=1
            )
        )

    def import_prediction_artifact(
        self, model_dir: Path, prediction_path: Path
    ) -> dict[str, Any]:
        """用途：导入单日预测制品并绑定模型；输入：模型目录与预测文件；输出：导入摘要。"""
        import pandas as pd

        self.initialize()
        set_log_context(progress="-/-", target=str(prediction_path))
        LOGGER.info("读取预测制品")
        predictions = pd.read_parquet(prediction_path)
        required = {
            "datetime", "instrument", "score", "label",
            "execution_date", "generated_at", "contract_version",
        }
        missing = sorted(required - set(predictions.columns))
        if missing:
            raise ValueError(f"prediction artifact is missing columns: {missing}")
        prediction_dates = pd.to_datetime(predictions["datetime"]).dt.date.unique()
        if len(prediction_dates) != 1:
            raise ValueError("prediction artifact must contain exactly one business date")
        business_date = prediction_dates[0].isoformat()
        if predictions["instrument"].duplicated().any():
            raise ValueError("单日排名制品包含重复证券")
        metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
        if metrics.get("timeContractVersion") != TRADING_TIME_CONTRACT:
            raise ValueError("模型时间合同已过期，请重新训练后生成排名")
        feature_date = prediction_dates[0]
        expected_execution = shift_trade_date(feature_date, 1)
        earliest = datetime.combine(feature_date, time(15, 0), SHANGHAI_ZONE)
        latest = datetime.combine(expected_execution, time(14, 0), SHANGHAI_ZONE)
        time_metadata = predictions[
            ["execution_date", "generated_at", "contract_version"]
        ].drop_duplicates()
        if len(time_metadata) != 1:
            raise ValueError("同一排名制品必须具有唯一的执行日期、生成时间和合同")
        for row in time_metadata.itertuples(index=False):
            generated_at = datetime.fromisoformat(str(row.generated_at))
            if (
                str(row.execution_date) != expected_execution.isoformat()
                or str(row.contract_version) != TRADING_TIME_CONTRACT
                or generated_at.tzinfo is None
                or not earliest <= generated_at < latest
            ):
                raise ValueError("排名的执行日期、生成时间或时间合同不符合盘后信号口径")
        model_hash = hashlib.sha256((model_dir / "model.pkl").read_bytes()).hexdigest()
        business_key = f"PREDICT:{business_date}:{model_hash[:16]}"
        self._start_run(business_key, "PREDICT", business_date)
        try:
            with self._connect() as connection:
                set_log_context(progress="-/-", target="模型版本")
                LOGGER.info("绑定模型版本")
                model_id = self._upsert_model(
                    connection, model_dir, metrics, artifact_hash=model_hash
                )
                set_log_context(progress="-/-", target="横截面排名")
                LOGGER.info("写入横截面排名：候选数=%d", len(predictions))
                # 全横截面原子替换，避免重排后被剔除证券仍留在旧快照中。
                connection.execute(
                    "DELETE FROM prediction_rank WHERE model_version_id = ? AND prediction_date = ?",
                    (model_id, business_date),
                )
                prediction_rows = self._upsert_predictions(
                    connection, model_id, predictions
                )
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        LOGGER.info("预测制品导入完成：日期=%s；写入数量=%d", business_date, prediction_rows)
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "predictionDate": business_date,
            "predictions": prediction_rows,
        }

    def prediction_candidates_for_execution(
        self,
        business_date: str,
        model_id: str,
        limit: int | None = None,
    ) -> list[dict[str, Any]]:
        """只读取前一交易日特征、绑定本执行日且合同匹配的排名。"""
        self.initialize()
        execution_date = date.fromisoformat(business_date)
        prediction_date = shift_trade_date(execution_date, -1).isoformat()
        with self._connect() as connection:
            model_version_id = connection.execute(
                """
                SELECT MAX(p.model_version_id)
                FROM prediction_rank p
                JOIN model_version mv ON mv.id = p.model_version_id
                WHERE p.prediction_date = ? AND p.execution_date = ?
                  AND p.contract_version = ? AND mv.model_id = ?
                """,
                (prediction_date, business_date, TRADING_TIME_CONTRACT, model_id),
            ).fetchone()[0]
            if model_version_id is None:
                return []
            query = """
                SELECT p.model_version_id, p.prediction_date, p.execution_date,
                       p.generated_at, p.contract_version, p.instrument, p.rank, p.score,
                       mv.model_id, mv.status AS model_status
                FROM prediction_rank p
                JOIN model_version mv ON mv.id = p.model_version_id
                WHERE p.prediction_date = ? AND p.execution_date = ?
                  AND p.contract_version = ? AND p.model_version_id = ?
                ORDER BY p.rank ASC, p.instrument ASC
            """
            parameters: tuple[object, ...] = (
                prediction_date, business_date, TRADING_TIME_CONTRACT, model_version_id,
            )
            if limit is not None:
                query += " LIMIT ?"
                parameters += (max(1, limit),)
            rows = connection.execute(query, parameters).fetchall()
        return [dict(row) for row in rows]

    def prepare_real_order(
        self,
        client_order_id: str,
        business_date: date,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
        model_version_id: int | None,
        prediction_date: str | None,
    ) -> dict[str, Any]:
        """用途：预留一次实时券商提交；输入：尝试标识和实时委托参数；输出：委托流水。"""
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
                "SELECT * FROM order_attempt WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
            if existing is not None:
                self._assert_same_order_intent(
                    existing, business_date, instrument, side, quantity, price,
                    model_version_id, prediction_date,
                )
                return dict(existing)

            connection.execute(
                """
                INSERT INTO order_attempt(
                    client_order_id, business_date, instrument, side, quantity, price,
                    model_version_id, prediction_date, status, created_at, updated_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, 'PREPARED', ?, ?)
                """,
                (
                    client_order_id,
                    business_date.isoformat(),
                    instrument,
                    side,
                    quantity,
                    float(price),
                    model_version_id,
                    prediction_date,
                    _now(),
                    _now(),
                ),
            )

            return dict(
                connection.execute(
                    "SELECT * FROM order_attempt WHERE client_order_id = ?",
                    (client_order_id,),
                ).fetchone()
            )

    def start_order_submission(self, client_order_id: str) -> None:
        """用途：原子地把预留订单切换为提交中；输入：客户端订单号；输出：无。"""
        self.initialize()
        with self._connect() as connection:
            cursor = connection.execute(
                """
                UPDATE order_attempt
                SET status = 'SUBMITTING', updated_at = ?
                WHERE client_order_id = ? AND status = 'PREPARED'
                """,
                (_now(), client_order_id),
            )
            if cursor.rowcount != 1:
                raise PermissionError("订单未预留或已提交，拒绝重复提交")

    def start_order_cancel(self, client_order_id: str) -> None:
        """原子记录撤单申请，只有券商终态确认后才允许重新报价。"""
        self.initialize()
        with self._connect() as connection:
            self._transition_order_intent(
                connection, client_order_id, "CANCEL_SUBMITTING"
            )

    def finish_real_order(
        self,
        client_order_id: str,
        status: str,
        broker_order_id: str | None = None,
        error_message: str | None = None,
    ) -> None:
        """用途：记录券商接受、拒绝或未知结果；输入：订单号、目标状态及可选结果；输出：无。"""
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

    def get_order_intent(self, client_order_id: str) -> dict[str, Any] | None:
        """用途：按客户端订单号读取委托意图；输入：客户端订单号；输出：委托意图或空值。"""
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                "SELECT * FROM order_attempt WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
        return dict(row) if row else None

    def list_order_intents(self, active_only: bool = False) -> list[dict[str, Any]]:
        """用途：查询全部或仅活动委托意图；输入：是否仅查活动状态；输出：委托意图列表。"""
        self.initialize()
        with self._connect() as connection:
            if active_only:
                rows = connection.execute(
                    f"""
                    SELECT * FROM order_attempt
                    WHERE status IN ({','.join('?' for _ in ORDER_ACTIVE_STATUSES)})
                    ORDER BY created_at ASC
                    """,
                    tuple(sorted(ORDER_ACTIVE_STATUSES)),
                ).fetchall()
            else:
                rows = connection.execute(
                    "SELECT * FROM order_attempt ORDER BY created_at ASC"
                ).fetchall()
        return [dict(row) for row in rows]

    def reconcile_order_intents(
        self,
        orders: Iterable[BrokerOrder],
        mark_unmatched_unknown: bool = False,
    ) -> list[dict[str, Any]]:
        """用途：用券商订单事实推进本地状态机；输入：券商订单和未匹配处理策略；输出：更新后的意图列表。"""
        self.initialize()
        broker_orders = tuple(order for order in orders if order.order_id)
        LOGGER.info("委托状态匹配开始：有效券商委托=%d；未匹配转UNKNOWN=%s", len(broker_orders), mark_unmatched_unknown)
        with self._connect() as connection:
            intents = connection.execute(
                f"""
                SELECT * FROM order_attempt
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
                "SELECT * FROM order_attempt ORDER BY created_at ASC"
            ).fetchall()
        LOGGER.info("委托状态匹配完成：活动意图=%d；成功匹配=%d；全部意图=%d", len(intents), len(matched_clients), len(rows))
        return [dict(row) for row in rows]

    def latest_model_version_id(self, model_id: str) -> int | None:
        """返回稳定模型 ID 最近一次实际训练制品主键。"""
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT id FROM model_version
                WHERE model_id = ?
                ORDER BY id DESC LIMIT 1
                """,
                (model_id,),
            ).fetchone()
        return int(row["id"]) if row else None

    def realized_performance(
        self, model_id: str, business_date: date
    ) -> dict[str, Any]:
        """汇总指定模型配置和卖出日的已实现交易。"""
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT
                    COUNT(*) AS trade_count,
                    COALESCE(SUM(quantity), 0) AS quantity,
                    COALESCE(SUM(cost_basis), 0) AS cost_basis,
                    COALESCE(SUM(sell_amount), 0) AS sell_amount,
                    COALESCE(SUM(buy_fee), 0) AS buy_fees,
                    COALESCE(SUM(sell_fee), 0) AS sell_fees,
                    COALESCE(SUM(gross_profit), 0) AS gross_pnl,
                    COALESCE(SUM(net_profit), 0) AS net_pnl,
                    SUM(gross_profit) / NULLIF(SUM(cost_basis), 0) AS gross_return,
                    SUM(net_profit) / NULLIF(SUM(cost_basis) + SUM(buy_fee), 0) AS net_return
                FROM realized_trade rt
                JOIN model_version mv ON mv.id = rt.model_version_id
                WHERE rt.sell_date = ? AND mv.model_id = ?
                """,
                (business_date.isoformat(), model_id),
            ).fetchone()
        return dict(row)

    def recent_realized_performance(
        self, model_version_id: int | None, business_date: date, limit: int
    ) -> list[dict[str, Any]]:
        """按卖出日汇总指定模型最近的已实现收益序列。"""
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                WITH daily AS (
                    SELECT
                        sell_date AS business_date,
                        SUM(gross_profit) / NULLIF(SUM(cost_basis), 0) AS gross_return,
                        SUM(net_profit) / NULLIF(SUM(cost_basis) + SUM(buy_fee), 0) AS net_return
                    FROM realized_trade
                    WHERE model_version_id IS ? AND sell_date <= ?
                    GROUP BY sell_date
                    ORDER BY sell_date DESC LIMIT ?
                )
                SELECT * FROM daily ORDER BY business_date
                """,
                (model_version_id, business_date.isoformat(), max(1, limit)),
            ).fetchall()
        return [dict(row) for row in rows]

    def recent_monthly_realized_performance(
        self, model_id: str, business_date: date, limit: int
    ) -> list[dict[str, Any]]:
        """按卖出月份汇总最近的已实现闭环，费用字段保留但不影响毛收益。"""
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                WITH monthly AS (
                    SELECT
                        substr(sell_date, 1, 7) AS business_month,
                        COUNT(*) AS trade_count,
                        SUM(gross_profit) AS gross_profit,
                        SUM(net_profit) AS net_profit,
                        SUM(buy_fee) + SUM(sell_fee) AS total_fees,
                        SUM(gross_profit) / NULLIF(SUM(cost_basis), 0) AS gross_return,
                        SUM(net_profit) / NULLIF(SUM(cost_basis) + SUM(buy_fee), 0)
                            AS net_return
                    FROM realized_trade rt
                    JOIN model_version mv ON mv.id = rt.model_version_id
                    WHERE mv.model_id = ?
                      AND substr(rt.sell_date, 1, 7) < substr(?, 1, 7)
                    GROUP BY substr(sell_date, 1, 7)
                    ORDER BY business_month DESC LIMIT ?
                )
                SELECT * FROM monthly ORDER BY business_month
                """,
                (model_id, business_date.isoformat(), max(1, limit)),
            ).fetchall()
        return [dict(row) for row in rows]

    def save_model_evaluation(
        self,
        model_id: str,
        model_version_id: int | None,
        business_date: str,
        window_observations: int,
        observations: int,
        positive_period_ratio: float | None,
        total_gross_return: float | None,
        total_net_return: float | None,
        max_drawdown: float | None,
        decision: str,
        reason: str,
    ) -> dict[str, Any]:
        """保存已实现收益观测窗口的模型评估结论。"""
        self.initialize()
        with self._connect() as connection:
            business_key = f"MODEL_EVALUATION:{model_id}:{business_date}"
            connection.execute(
                """
                INSERT INTO model_evaluation(
                    business_key, model_id, model_version_id, business_date,
                    window_observations, observations, positive_period_ratio,
                    total_gross_return,
                    total_net_return, max_drawdown, decision, reason, created_at
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(business_key) DO UPDATE SET
                    model_id = excluded.model_id,
                    model_version_id = excluded.model_version_id,
                    window_observations = excluded.window_observations,
                    observations = excluded.observations,
                    positive_period_ratio = excluded.positive_period_ratio,
                    total_gross_return = excluded.total_gross_return,
                    total_net_return = excluded.total_net_return,
                    max_drawdown = excluded.max_drawdown,
                    decision = excluded.decision,
                    reason = excluded.reason,
                    created_at = excluded.created_at
                """,
                (
                    business_key,
                    model_id,
                    model_version_id,
                    business_date,
                    window_observations,
                    observations,
                    positive_period_ratio,
                    total_gross_return,
                    total_net_return,
                    max_drawdown,
                    decision,
                    reason,
                    _now(),
                ),
            )
            evaluation = connection.execute(
                "SELECT * FROM model_evaluation WHERE business_key = ?",
                (business_key,),
            ).fetchone()
            if decision == "RETRAIN":
                connection.execute(
                    """
                    INSERT INTO training_request(
                        model_id, evaluation_key, requested_model_version_id, status,
                        requested_at, completed_model_version_id, completed_at
                    ) VALUES(?, ?, ?, 'PENDING', ?, NULL, NULL)
                    ON CONFLICT(evaluation_key) DO UPDATE SET
                        model_id = excluded.model_id,
                        requested_model_version_id = excluded.requested_model_version_id,
                        status = CASE
                            WHEN training_request.status = 'COMPLETED'
                            THEN training_request.status
                            ELSE 'PENDING'
                        END,
                        requested_at = CASE
                            WHEN training_request.status = 'COMPLETED'
                            THEN training_request.requested_at
                            ELSE excluded.requested_at
                        END
                    """,
                    (model_id, business_key, model_version_id, _now()),
                )
            else:
                connection.execute(
                    """
                    UPDATE training_request
                    SET status = 'CANCELLED', completed_at = ?
                    WHERE evaluation_key = ? AND status = 'PENDING'
                    """,
                    (_now(), business_key),
                )
        LOGGER.info(
            "模型评估保存完成：日期=%s；模型版本=%s；样本=%d/%d；决策=%s",
            business_date,
            model_version_id,
            observations,
            window_observations,
            decision,
        )
        return dict(evaluation)

    def pending_training_requests(self) -> list[dict[str, Any]]:
        """返回尚未由同一模型 ID 新制品完成的重训请求。"""
        self.initialize()
        with self._connect() as connection:
            rows = connection.execute(
                """
                SELECT * FROM training_request
                WHERE status = 'PENDING'
                ORDER BY requested_at, id
                """
            ).fetchall()
        return [dict(row) for row in rows]

    def complete_training_requests(
        self, model_id: str, model_version_id: int
    ) -> int:
        """仅完成同一稳定模型 ID 的待处理重训请求。"""
        self.initialize()
        with self._connect() as connection:
            completed = connection.execute(
                """
                UPDATE training_request
                SET status = 'COMPLETED', completed_model_version_id = ?,
                    completed_at = ?
                WHERE status = 'PENDING' AND model_id = ?
                """,
                (model_version_id, _now(), model_id),
            ).rowcount
        return completed

    def start_task(self, task_type: str, business_date: date) -> str:
        """登记一个每日步骤开始，状态只用于依赖和审计。"""
        self.initialize()
        business_key = f"{task_type}:{business_date.isoformat()}"
        self._start_run(business_key, task_type, business_date.isoformat())
        return business_key

    def finish_task(
        self,
        business_key: str,
        success: bool,
        error: BaseException | None = None,
    ) -> None:
        """完成每日步骤并保存成功或失败状态。"""
        self._finish_run(
            business_key,
            "SUCCESS" if success else "FAILED",
            None if error is None else str(error),
        )

    def task_status(
        self, task_type: str, business_date: date
    ) -> dict[str, Any] | None:
        """读取指定步骤和业务日期的最新状态。"""
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT * FROM task_run
                WHERE business_key = ?
                """,
                (f"{task_type}:{business_date.isoformat()}",),
            ).fetchone()
        return dict(row) if row else None

    def latest_task_status(self, task_type: str) -> dict[str, Any] | None:
        """读取指定类型最近一次任务状态。"""
        self.initialize()
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT * FROM task_run
                WHERE task_type = ?
                ORDER BY id DESC
                LIMIT 1
                """,
                (task_type,),
            ).fetchone()
        return dict(row) if row else None

    @staticmethod
    def _assert_same_order_intent(
        existing: sqlite3.Row,
        business_date: date,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
        model_version_id: int | None,
        prediction_date: str | None,
    ) -> None:
        """用途：防止同一客户端订单号绑定不同参数；输入：已有意图和新参数；输出：无。"""
        same = (
            existing["business_date"] == business_date.isoformat()
            and existing["instrument"] == instrument
            and existing["side"] == side
            and int(existing["quantity"]) == quantity
            and Decimal(str(existing["price"])) == price
            and existing["model_version_id"] == model_version_id
            and existing["prediction_date"] == prediction_date
        )
        if not same:
            raise ValueError("client_order_id 已绑定不同订单参数")

    @staticmethod
    def _match_broker_order(
        intent: sqlite3.Row,
        orders: tuple[BrokerOrder, ...],
        bound_order_ids: set[str],
    ) -> BrokerOrder | None:
        """用途：按委托号或唯一参数匹配券商事实；输入：本地意图、券商订单和已绑定编号；输出：唯一匹配或空值。"""
        broker_order_id = intent["broker_order_id"]
        if broker_order_id is not None:
            return next(
                (
                    order
                    for order in orders
                    if str(order.order_id) == str(broker_order_id)
                    and order.order_time is not None
                    and order.order_time.date().isoformat()
                    == str(intent["business_date"])
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
        """用途：校验并更新委托尝试状态；输入：连接、尝试标识、目标状态和结果；输出：无。"""
        row = connection.execute(
            "SELECT status, broker_order_id FROM order_attempt WHERE client_order_id = ?",
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
            UPDATE order_attempt
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

    def _start_run(self, business_key: str, task_type: str, business_date: str) -> None:
        """用途：新增或重置任务为运行中；输入：业务键、任务类型和日期；输出：无。"""
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

    def _finish_run(
        self,
        business_key: str,
        status: str,
        error: str | None,
    ) -> None:
        """用途：结束任务并记录状态和错误；输入：业务键、结束状态和错误；输出：无。"""
        with self._connect() as connection:
            connection.execute(
                """
                UPDATE task_run SET status = ?, finished_at = ?, error_message = ?
                WHERE business_key = ?
                """,
                (
                    status,
                    _now(),
                    error,
                    business_key,
                ),
            )

    @staticmethod
    def _upsert_model(
        connection: sqlite3.Connection,
        model_dir: Path,
        metrics: dict[str, Any],
        status: str | None = None,
        *,
        artifact_hash: str | None = None,
    ) -> int:
        """用途：按制品哈希登记或更新模型；输入：连接、模型目录和指标；输出：模型主键。"""
        model_path = model_dir / "model.pkl"
        if artifact_hash is None:
            artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
        configured_model_id = str(metrics["modelId"])
        default_status = "READY"
        existing = connection.execute(
            "SELECT id, status FROM model_version WHERE artifact_hash = ?",
            (artifact_hash,),
        ).fetchone()
        if existing is not None:
            next_status = status or default_status
            connection.execute(
                """
                UPDATE model_version
                SET model_id = ?, status = ?, artifact_path = ?, metrics_json = ?
                WHERE id = ?
                """,
                (
                    configured_model_id,
                    next_status,
                    str(model_path),
                    json.dumps(metrics, ensure_ascii=False),
                    existing["id"],
                ),
            )
            return int(existing["id"])
        model_status = status or default_status
        connection.execute(
            """
            INSERT INTO model_version(
                model_id, artifact_hash, model_name, feature_set, status,
                artifact_path, metrics_json, created_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                configured_model_id,
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
        connection: sqlite3.Connection, model_id: int, rankings: pd.DataFrame
    ) -> int:
        """用途：按模型、日期和证券写入预测排名；输入：连接、模型主键和预测表；输出：处理条数。"""
        import pandas as pd

        rows = []
        ordered = rankings.sort_values(
            ["datetime", "score", "instrument"], ascending=[True, False, True]
        )
        for prediction_date, group in ordered.groupby("datetime", sort=True):
            for rank, row in enumerate(group.itertuples(index=False), start=1):
                label = None if pd.isna(row.label) else float(row.label)
                rows.append(
                    (
                        model_id,
                        pd.Timestamp(prediction_date).date().isoformat(),
                        getattr(row, "execution_date", None),
                        getattr(row, "generated_at", None),
                        getattr(row, "contract_version", None),
                        row.instrument,
                        rank,
                        float(row.score),
                        label,
                        _now(),
                    )
                )
        connection.executemany(
            """
            INSERT INTO prediction_rank(
                model_version_id, prediction_date, execution_date, generated_at,
                contract_version, instrument, rank, score, label, created_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(model_version_id, prediction_date, instrument) DO UPDATE SET
                execution_date = COALESCE(excluded.execution_date, prediction_rank.execution_date),
                generated_at = COALESCE(excluded.generated_at, prediction_rank.generated_at),
                contract_version = COALESCE(excluded.contract_version, prediction_rank.contract_version),
                rank = excluded.rank,
                score = excluded.score,
                label = excluded.label,
                created_at = excluded.created_at
            WHERE excluded.execution_date IS NOT NULL
               OR prediction_rank.execution_date IS NULL
            """,
            rows,
        )
        return len(rows)

def _now() -> str:
    """用途：生成统一 UTC 时间；输入：无；输出：ISO-8601 时间字符串。"""
    return datetime.now(UTC).isoformat()


def _artifact_fingerprint(*paths: Path) -> str:
    """用途：计算一组制品路径和内容的联合指纹；输入：文件路径；输出：SHA-256 十六进制字符串。"""
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path).encode("utf-8"))
        digest.update(path.read_bytes() if path.exists() else b"MISSING")
    return digest.hexdigest()


def _broker_order_matches_intent(order: BrokerOrder, intent: sqlite3.Row) -> bool:
    """用途：比较券商订单与本地委托关键参数；输入：券商订单和意图；输出：是否一致。"""
    instrument = str(intent["instrument"])
    return (
        order.order_time is not None
        and order.order_time.date().isoformat() == str(intent["business_date"])
        and order.stock_code == instrument[-6:]
        and order.direction == intent["side"]
        and order.order_quantity == int(intent["quantity"])
        and order.order_price == Decimal(str(intent["price"]))
    )
# AI_GENERATE_END----------- ------------------------------------------------------------------------------
