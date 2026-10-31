# AI_GENERATE_START ------------------------------------------------------
from __future__ import annotations

import hashlib
import json
import logging
import sqlite3
from contextlib import contextmanager
from dataclasses import asdict
from datetime import UTC, date, datetime, timedelta
from decimal import Decimal
from pathlib import Path
from typing import Any, Iterable, Iterator

import pandas as pd

from stock_trading.broker.models import BrokerOrder, BrokerSyncSnapshot

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

SCHEMA = """
-- 任务运行记录：保存可恢复、可幂等任务的执行状态。
CREATE TABLE IF NOT EXISTS task_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 本地自增主键，仅用于库内关联和排序。
    business_key TEXT NOT NULL UNIQUE, -- 任务幂等业务键，相同业务输入只能产生一条记录。
    task_type TEXT NOT NULL, -- 任务类型，例如预测导入、券商同步或影子回测导入。
    business_date TEXT NOT NULL, -- 任务归属业务日期，格式 YYYY-MM-DD。
    status TEXT NOT NULL, -- 任务状态，例如 RUNNING、SUCCESS、SKIPPED、FAILED。
    started_at TEXT, -- 任务开始时间，UTC ISO-8601 字符串。
    finished_at TEXT, -- 任务结束时间，未结束时为空。
    error_message TEXT -- 失败原因；成功或未结束时为空。
);

-- 模型版本：登记模型制品、特征集、评估指标和当前生命周期状态。
CREATE TABLE IF NOT EXISTS model_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 模型版本本地主键。
    artifact_hash TEXT NOT NULL UNIQUE, -- 模型文件 SHA-256，用于识别同一制品。
    model_name TEXT NOT NULL, -- 模型算法或训练方案名称。
    feature_set TEXT NOT NULL, -- 模型使用的特征集名称。
    status TEXT NOT NULL, -- 模型状态，例如 CANDIDATE、ACTIVE、REJECTED、DEGRADED。
    artifact_path TEXT NOT NULL, -- 模型制品在本机的绝对或项目内路径。
    metrics_json TEXT NOT NULL, -- 训练和评估指标的完整 JSON 快照。
    created_at TEXT NOT NULL -- 模型首次登记时间，UTC ISO-8601 字符串。
);

-- 预测结果：保存模型在交易日维度产生的候选排名和后验标签。
CREATE TABLE IF NOT EXISTS prediction (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 预测记录本地主键。
    model_version_id INTEGER NOT NULL, -- 产生该预测的模型版本主键。
    prediction_date TEXT NOT NULL, -- 预测对应的数据日期，格式 YYYY-MM-DD。
    instrument TEXT NOT NULL, -- Qlib 标准证券代码，例如 SH600000。
    rank INTEGER NOT NULL, -- 同一模型同一预测日内的候选排名，1 为最高。
    score REAL NOT NULL, -- 模型输出的排序分数。
    label REAL, -- 标签成熟后回填的真实收益标签，未成熟时为空。
    created_at TEXT NOT NULL, -- 预测写入时间，UTC ISO-8601 字符串。
    UNIQUE(model_version_id, prediction_date, instrument),
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

-- 影子订单：仅保存回测导入和历史影子成交，不参与真实委托定价。
CREATE TABLE IF NOT EXISTS shadow_order (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 影子订单本地主键。
    client_order_id TEXT NOT NULL UNIQUE, -- 稳定客户端订单号，也是防重复提交的幂等键。
    business_date TEXT NOT NULL, -- 计划执行的业务日期，格式 YYYY-MM-DD。
    instrument TEXT NOT NULL, -- Qlib 标准证券代码。
    side TEXT NOT NULL, -- 买卖方向，仅允许 BUY 或 SELL。
    quantity INTEGER NOT NULL, -- 委托股数；买入按 A 股整手规则生成。
    price REAL NOT NULL, -- 计划限价，来源于生成计划时的五档行情。
    status TEXT NOT NULL, -- 计划/委托状态，例如 PLANNED、PREPARED、SUBMITTED、FILLED。
    candidate_rank INTEGER, -- 买入候选排名；卖出计划为空。
    model_version_id INTEGER, -- 买入计划关联的模型版本；卖出或历史数据可为空。
    prediction_date TEXT, -- 买入计划采用的预测日期；卖出计划为空。
    created_at TEXT NOT NULL -- 计划创建时间，UTC ISO-8601 字符串。
);

-- 影子成交：保存回测或影子链路生成的成交明细。
CREATE TABLE IF NOT EXISTS shadow_execution (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 影子成交本地主键。
    execution_id TEXT NOT NULL UNIQUE, -- 成交幂等键，防止同一成交重复导入。
    order_id INTEGER NOT NULL, -- 关联的影子订单主键。
    quantity INTEGER NOT NULL, -- 成交股数。
    price REAL NOT NULL, -- 成交价格。
    fee REAL NOT NULL, -- 该笔成交对应的费用。
    occurred_at TEXT NOT NULL, -- 成交发生时间，ISO-8601 字符串。
    FOREIGN KEY(order_id) REFERENCES shadow_order(id)
);

-- 研究持仓：保存回测导入后的期末持仓，不代表券商实时持仓。
CREATE TABLE IF NOT EXISTS position (
    instrument TEXT PRIMARY KEY, -- Qlib 标准证券代码。
    quantity INTEGER NOT NULL, -- 总持仓股数。
    available_quantity INTEGER NOT NULL, -- 按 T+1 规则当前可卖股数。
    average_cost REAL NOT NULL, -- 含买入费用的单位平均成本。
    updated_at TEXT NOT NULL -- 持仓更新时间，UTC ISO-8601 字符串。
);

-- 回测绩效快照：保存指定回测场景的每日账户权益。
CREATE TABLE IF NOT EXISTS performance_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 回测绩效快照本地主键。
    scenario TEXT NOT NULL, -- 回测场景名称，用于隔离不同参数组合。
    business_date TEXT NOT NULL, -- 权益归属交易日，格式 YYYY-MM-DD。
    cash REAL NOT NULL, -- 当日收盘后的可用现金。
    market_value REAL NOT NULL, -- 当日收盘后的持仓市值。
    equity REAL NOT NULL, -- 当日总权益，通常为现金加持仓市值。
    created_at TEXT NOT NULL, -- 快照写入时间，UTC ISO-8601 字符串。
    UNIQUE(scenario, business_date)
);

-- 实盘绩效快照：基于券商事实计算每日损益、收益和成本。
CREATE TABLE IF NOT EXISTS live_performance_snapshot (
    business_date TEXT PRIMARY KEY, -- 绩效归属交易日，格式 YYYY-MM-DD。
    model_version_id INTEGER, -- 当日评估关联的活动模型版本；无活动模型时为空。
    cash REAL NOT NULL, -- 券商账户可用资金。
    market_value REAL NOT NULL, -- 券商账户持仓总市值。
    equity REAL NOT NULL, -- 券商账户总资产。
    external_cash_flow REAL NOT NULL, -- 当日入金为正、出金为负的外部资金变动。
    turnover REAL NOT NULL, -- 当日成交金额合计。
    estimated_fees REAL NOT NULL, -- 按配置费率估算的佣金、过户费和印花税。
    gross_pnl REAL, -- 扣交易费用前损益；缺少上一日权益时为空。
    net_pnl REAL, -- 扣外部资金流后的净损益；缺少上一日权益时为空。
    gross_return REAL, -- 毛损益相对上一日权益的收益率。
    net_return REAL, -- 净损益相对上一日权益的收益率。
    created_at TEXT NOT NULL, -- 首次创建时间，UTC ISO-8601 字符串。
    updated_at TEXT NOT NULL, -- 最近重算时间，UTC ISO-8601 字符串。
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

-- 模型滚动评估：保存固定窗口内的收益、回撤和继续/重训决策。
CREATE TABLE IF NOT EXISTS model_evaluation (
    business_key TEXT PRIMARY KEY, -- 模型与业务日期组合形成的评估幂等键。
    model_version_id INTEGER, -- 被评估的模型版本；无法绑定时为空。
    business_date TEXT NOT NULL, -- 评估截止交易日，格式 YYYY-MM-DD。
    window_days INTEGER NOT NULL, -- 配置要求的滚动评估窗口天数。
    observations INTEGER NOT NULL, -- 当前已取得的有效收益样本数。
    gross_positive_ratio REAL, -- 窗口内毛收益为正的样本比例。
    total_gross_return REAL, -- 窗口内复合毛收益率。
    total_net_return REAL, -- 窗口内复合净收益率。
    max_drawdown REAL, -- 窗口净值序列的最大回撤。
    decision TEXT NOT NULL, -- 评估决策，例如 WARMUP、KEEP、RETRAIN。
    reason TEXT NOT NULL, -- 形成决策的业务原因。
    created_at TEXT NOT NULL, -- 评估记录创建时间，UTC ISO-8601 字符串。
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

-- 训练请求：在模型退化时登记确定性重训请求，不直接启动训练。
CREATE TABLE IF NOT EXISTS training_request (
    business_key TEXT PRIMARY KEY, -- 模型与业务日期组合形成的请求幂等键。
    model_version_id INTEGER, -- 触发重训的模型版本；无法绑定时为空。
    trigger_type TEXT NOT NULL, -- 触发类型，例如 PERFORMANCE_DEGRADED。
    status TEXT NOT NULL, -- 请求状态，例如 REQUESTED、RUNNING、COMPLETED。
    requested_at TEXT NOT NULL, -- 首次请求时间，UTC ISO-8601 字符串。
    updated_at TEXT NOT NULL, -- 请求状态最后更新时间。
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

-- 券商账户快照：保存一次只读同步取得的账户汇总事实。
CREATE TABLE IF NOT EXISTS broker_account_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 券商账户快照本地主键。
    business_key TEXT NOT NULL UNIQUE, -- 本次券商同步任务的幂等业务键。
    business_date TEXT NOT NULL, -- 快照归属业务日期，格式 YYYY-MM-DD。
    available_cash REAL NOT NULL, -- 券商返回的可用资金。
    total_assets REAL NOT NULL, -- 券商返回的账户总资产。
    total_position REAL NOT NULL, -- 券商返回的持仓总市值。
    frozen_amount REAL NOT NULL, -- 券商返回的冻结资金。
    captured_at TEXT NOT NULL -- 券商快照采集时间。
);

-- 券商持仓快照：保存一次同步中的逐证券持仓事实。
CREATE TABLE IF NOT EXISTS broker_position_snapshot (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 券商持仓快照本地主键。
    business_key TEXT NOT NULL, -- 本次券商同步任务的幂等业务键。
    instrument TEXT NOT NULL, -- 券商证券代码。
    stock_name TEXT, -- 券商返回的证券名称。
    quantity INTEGER NOT NULL, -- 总持仓股数。
    available_quantity INTEGER NOT NULL, -- 当前可卖股数。
    frozen_quantity INTEGER NOT NULL, -- 已冻结、暂不可卖股数。
    average_cost REAL, -- 券商返回的持仓平均成本。
    current_price REAL NOT NULL, -- 快照时点最新价格。
    market TEXT, -- 券商市场标识，例如 SH 或 SZ。
    captured_at TEXT NOT NULL, -- 券商快照采集时间。
    UNIQUE(business_key, instrument)
);

-- 券商委托事实：以券商委托号为权威键保存订单最新状态。
CREATE TABLE IF NOT EXISTS broker_order_fact (
    broker_order_id TEXT PRIMARY KEY, -- 券商返回的唯一委托号。
    instrument TEXT, -- 券商证券代码。
    stock_name TEXT, -- 券商返回的证券名称。
    direction TEXT NOT NULL, -- 归一化买卖方向 BUY 或 SELL。
    order_price REAL NOT NULL, -- 委托限价。
    order_quantity INTEGER NOT NULL, -- 委托股数。
    average_fill_price REAL, -- 已成交部分的平均成交价。
    filled_quantity INTEGER, -- 已成交股数。
    raw_status TEXT, -- 券商原始状态文本，便于协议问题追溯。
    status TEXT NOT NULL, -- 本地归一化状态，例如 SUBMITTED、PARTIAL、FILLED。
    market TEXT, -- 券商市场标识。
    order_time TEXT, -- 券商返回的委托时间。
    updated_at TEXT NOT NULL -- 本地最后同步时间。
);

-- 券商成交事实：保存逐笔成交并通过内容指纹去重。
CREATE TABLE IF NOT EXISTS broker_fill_fact (
    fill_key TEXT PRIMARY KEY, -- 成交关键字段计算出的 SHA-256 幂等键。
    broker_order_id TEXT, -- 关联券商委托号；券商未返回时可为空。
    instrument TEXT, -- 券商证券代码。
    stock_name TEXT, -- 券商返回的证券名称。
    direction TEXT NOT NULL, -- 归一化买卖方向 BUY 或 SELL。
    fill_price REAL NOT NULL, -- 成交价格。
    fill_quantity INTEGER NOT NULL, -- 成交股数。
    fill_amount REAL NOT NULL, -- 成交金额。
    fill_time TEXT, -- 券商返回的成交时间。
    created_at TEXT NOT NULL -- 本地首次写入时间。
);

-- 委托尝试：记录每次实时报价和券商委托事实，承担提交幂等和状态恢复。
CREATE TABLE IF NOT EXISTS broker_order_intent (
    client_order_id TEXT PRIMARY KEY, -- 本地稳定尝试标识，与一次实时报价一一对应。
    instrument TEXT NOT NULL, -- Qlib 标准证券代码。
    side TEXT NOT NULL, -- 买卖方向 BUY 或 SELL。
    quantity INTEGER NOT NULL, -- 该次实时尝试提交的委托股数。
    price REAL NOT NULL, -- 该次实时尝试提交的委托限价。
    status TEXT NOT NULL, -- 本地委托状态机当前状态。
    broker_order_id TEXT, -- 券商接受后返回的委托号。
    error_message TEXT, -- 明确拒绝或通信结果不确定时的原因。
    created_at TEXT NOT NULL, -- 委托意图首次创建时间。
    updated_at TEXT NOT NULL -- 委托状态最后更新时间。
);

-- 券商交易权限事实：按市场板块保存已确认或未知的交易权限。
CREATE TABLE IF NOT EXISTS broker_permission (
    scope TEXT PRIMARY KEY, -- 权限范围，例如 SH_MAIN 或 SZ_MAIN。
    status TEXT NOT NULL, -- ALLOWED、DENIED 或 UNKNOWN。
    evidence_type TEXT NOT NULL, -- 权限结论来源，例如 HISTORICAL_ORDER。
    evidence_detail TEXT, -- 不含凭据的证据摘要。
    observed_at TEXT NOT NULL, -- 证据发生或采集时间。
    updated_at TEXT NOT NULL -- 本地最后更新时间。
);
"""


class StateStore:
    """Transactional SQLite store for idempotent research and shadow-trading state."""

    def __init__(self, path: Path) -> None:
        """用途：绑定 SQLite 文件路径；输入：数据库路径；输出：无。"""
        self._path = path

    def initialize(self) -> None:
        """用途：创建目录、数据表和兼容字段；输入：无；输出：无。"""
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

    def cleanup(self, reference_date: date) -> dict[str, object]:
        """按数据价值分级清理 SQLite，并保留活动委托和长期基础事实。"""
        self.initialize()
        task_cutoff = (reference_date - timedelta(days=180)).isoformat()
        snapshot_cutoff = (reference_date - timedelta(days=366)).isoformat()
        history_cutoff = (reference_date - timedelta(days=366 * 5)).isoformat()
        deleted: dict[str, int] = {}
        terminal_statuses = "'FILLED','CANCELLED','REJECTED'"
        with self._connect() as connection:
            old_accounts = (
                "SELECT business_key FROM broker_account_snapshot "
                "WHERE business_date < ?"
            )
            deleted["broker_position_snapshot"] = connection.execute(
                f"DELETE FROM broker_position_snapshot WHERE business_key IN ({old_accounts})",
                (snapshot_cutoff,),
            ).rowcount
            deleted["broker_account_snapshot"] = connection.execute(
                "DELETE FROM broker_account_snapshot WHERE business_date < ?",
                (snapshot_cutoff,),
            ).rowcount

            old_shadow_orders = (
                "SELECT id FROM shadow_order WHERE business_date < ? "
                f"AND status IN ({terminal_statuses})"
            )
            deleted["shadow_execution"] = connection.execute(
                f"DELETE FROM shadow_execution WHERE order_id IN ({old_shadow_orders})",
                (history_cutoff,),
            ).rowcount
            deleted["shadow_order"] = connection.execute(
                "DELETE FROM shadow_order WHERE business_date < ? "
                f"AND status IN ({terminal_statuses})",
                (history_cutoff,),
            ).rowcount

            rules = (
                (
                    "task_run",
                    "business_date < ? AND status NOT IN ('QUEUED','RUNNING')",
                    task_cutoff,
                ),
                ("prediction", "prediction_date < ?", history_cutoff),
                ("performance_snapshot", "business_date < ?", history_cutoff),
                ("live_performance_snapshot", "business_date < ?", history_cutoff),
                ("model_evaluation", "business_date < ?", history_cutoff),
                (
                    "training_request",
                    "substr(updated_at, 1, 10) < ? AND status = 'COMPLETED'",
                    history_cutoff,
                ),
                (
                    "broker_order_intent",
                    f"substr(updated_at, 1, 10) < ? AND status IN ({terminal_statuses})",
                    history_cutoff,
                ),
                (
                    "broker_order_fact",
                    f"substr(COALESCE(order_time, updated_at), 1, 10) < ? "
                    f"AND status IN ({terminal_statuses})",
                    history_cutoff,
                ),
                (
                    "broker_fill_fact",
                    "substr(COALESCE(fill_time, created_at), 1, 10) < ?",
                    history_cutoff,
                ),
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
            "SQLite 分级清理完成：删除=%d；任务截止=%s；快照截止=%s；历史截止=%s",
            deleted_rows,
            task_cutoff,
            snapshot_cutoff,
            history_cutoff,
        )
        return {
            "deletedRows": deleted_rows,
            "deletedByTable": deleted,
            "taskCutoff": task_cutoff,
            "snapshotCutoff": snapshot_cutoff,
            "historyCutoff": history_cutoff,
        }

    def import_research_artifacts(
        self,
        model_dir: Path,
        backtest_dir: Path,
        scenario: str,
    ) -> dict[str, Any]:
        """用途：事务导入模型、预测、回测交易和绩效；输入：制品目录与场景；输出：导入摘要。"""
        self.initialize()
        LOGGER.info("研究制品导入阶段 1/5：读取模型指标和 TopK")
        metrics = json.loads((model_dir / "metrics.json").read_text(encoding="utf-8"))
        topk = pd.read_parquet(model_dir / "top5.parquet")
        business_date = pd.to_datetime(topk["datetime"]).max().date().isoformat()
        fingerprint = _artifact_fingerprint(
            model_dir / "model.pkl",
            model_dir / "top5.parquet",
            backtest_dir / "summary.json",
        )
        business_key = f"IMPORT_RESEARCH:{scenario}:{business_date}:{fingerprint[:16]}"
        self._start_run(business_key, "IMPORT_RESEARCH", business_date)
        try:
            with self._connect() as connection:
                LOGGER.info("研究制品导入阶段 2/5：写入模型和预测")
                model_id = self._upsert_model(connection, model_dir, metrics)
                prediction_rows = self._upsert_predictions(connection, model_id, topk)
                LOGGER.info("研究制品导入阶段 3/5：写入回测订单和执行")
                order_rows, execution_rows = self._upsert_shadow_trades(
                    connection, backtest_dir
                )
                LOGGER.info("研究制品导入阶段 4/5：写入绩效和持仓")
                performance_rows = self._upsert_performance(
                    connection, backtest_dir, scenario
                )
                position_rows = self._replace_position(connection, backtest_dir)
            LOGGER.info("研究制品导入阶段 5/5：提交事务并记录成功状态")
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        LOGGER.info(
            "研究制品导入完成：预测=%d；订单=%d；执行=%d；持仓=%d；绩效=%d",
            prediction_rows,
            order_rows,
            execution_rows,
            position_rows,
            performance_rows,
        )
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
        """用途：幂等保存账户、持仓、委托和成交事实；输入：券商快照与业务日期；输出：同步摘要。"""
        self.initialize()
        LOGGER.info(
            "券商事实落库准备：日期=%s；持仓=%d；委托=%d；成交=%d",
            business_date,
            len(snapshot.positions),
            len(snapshot.orders),
            len(snapshot.fills),
        )
        payload = json.dumps(asdict(snapshot), default=_json_default, sort_keys=True)
        fingerprint = hashlib.sha256(payload.encode("utf-8")).hexdigest()
        business_key = f"SYNC_BROKER:{business_date}:{fingerprint[:16]}"
        self._start_run(business_key, "SYNC_BROKER", business_date)
        try:
            with self._connect() as connection:
                self._insert_broker_account(connection, business_key, business_date, snapshot)
                connection.execute(
                    "DELETE FROM broker_position_snapshot WHERE business_key = ?",
                    (business_key,),
                )
                position_rows = self._insert_broker_positions(connection, business_key, snapshot)
                order_rows = self._upsert_broker_orders(connection, snapshot)
                fill_rows = self._upsert_broker_fills(connection, snapshot)
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        LOGGER.info(
            "券商事实落库完成：持仓=%d；委托=%d；成交=%d",
            position_rows,
            order_rows,
            fill_rows,
        )
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "positions": position_rows,
            "orders": order_rows,
            "fills": fill_rows,
        } | self.summary()

    def import_backtest_performance(
        self, backtest_dir: Path, scenario: str, business_date: str
    ) -> dict[str, Any]:
        """仅导入一个资金档位的回测权益，不覆盖影子订单和期末持仓。"""
        self.initialize()
        fingerprint = _artifact_fingerprint(
            backtest_dir / "summary.json", backtest_dir / "daily_equity.parquet"
        )
        business_key = f"IMPORT_BACKTEST:{scenario}:{business_date}:{fingerprint[:16]}"
        self._start_run(business_key, "IMPORT_BACKTEST", business_date)
        try:
            with self._connect() as connection:
                rows = self._upsert_performance(connection, backtest_dir, scenario)
            self._finish_run(business_key, "SUCCESS", None)
        except BaseException as error:
            self._finish_run(business_key, "FAILED", str(error))
            raise
        return {
            "businessKey": business_key,
            "status": "SUCCESS",
            "performanceSnapshots": rows,
        }

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

    def research_artifacts_registered(
        self, model_dir: Path, backtest_dirs: dict[str, Path]
    ) -> bool:
        """直接读取研究制品并检查模型、预测和资金场景是否已完整入库。"""
        self.initialize()
        model_path = model_dir / "model.pkl"
        if not model_path.is_file():
            return False
        try:
            artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
            topk = pd.read_parquet(model_dir / "top5.parquet", columns=["datetime"])
            prediction_date = pd.to_datetime(topk["datetime"]).max().date().isoformat()
            scenario_dates = {
                scenario: pd.to_datetime(
                    pd.read_parquet(
                        directory / "daily_equity.parquet", columns=["tradeDate"]
                    )["tradeDate"]
                ).max().date().isoformat()
                for scenario, directory in backtest_dirs.items()
            }
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
                SELECT COUNT(*) FROM prediction
                WHERE model_version_id = ? AND prediction_date = ?
                """,
                (model["id"], prediction_date),
            ).fetchone()[0])
            stored_dates = {
                str(row["scenario"]): str(row["latest_date"])
                for row in connection.execute(
                    """
                    SELECT scenario, MAX(business_date) AS latest_date
                    FROM performance_snapshot
                    GROUP BY scenario
                    """
                ).fetchall()
            }
        return prediction_count == 5 and all(
            stored_dates.get(scenario) == expected_date
            for scenario, expected_date in scenario_dates.items()
        )

    def prediction_registered(self, model_path: Path, prediction_date: str) -> bool:
        """检查指定模型的单日预测是否已经写入 SQLite。"""
        self.initialize()
        if not model_path.is_file():
            return False
        try:
            artifact_hash = hashlib.sha256(model_path.read_bytes()).hexdigest()
        except OSError:
            return False
        with self._connect() as connection:
            row = connection.execute(
                """
                SELECT 1
                FROM prediction p
                JOIN model_version mv ON mv.id = p.model_version_id
                WHERE mv.artifact_hash = ? AND p.prediction_date = ?
                LIMIT 1
                """,
                (artifact_hash, prediction_date),
            ).fetchone()
        return row is not None

    def import_prediction_artifact(
        self, model_dir: Path, prediction_path: Path
    ) -> dict[str, Any]:
        """用途：导入单日预测制品并绑定模型；输入：模型目录与预测文件；输出：导入摘要。"""
        self.initialize()
        LOGGER.info("预测制品导入阶段 1/3：读取文件=%s", prediction_path)
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
        self._start_run(business_key, "PREDICT", business_date)
        try:
            with self._connect() as connection:
                LOGGER.info("预测制品导入阶段 2/3：绑定模型版本")
                model_id = self._upsert_model(connection, model_dir, metrics)
                LOGGER.info("预测制品导入阶段 3/3：写入横截面排名；候选数=%d", len(predictions))
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
        } | self.summary()

    def latest_prediction_candidates(
        self,
        before_business_date: str,
        limit: int,
        model_version_id: int | None = None,
    ) -> list[dict[str, Any]]:
        """用途：读取目标日前最近一批候选；输入：截止日期、数量和可选模型；输出：排序候选列表。"""
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

    def prepare_real_order(
        self,
        client_order_id: str,
        instrument: str,
        side: str,
        quantity: int,
        price: Decimal,
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
                "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
                (client_order_id,),
            ).fetchone()
            if existing is not None:
                self._assert_same_order_intent(
                    existing, instrument, side, quantity, price
                )
                return dict(existing)

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

            return dict(
                connection.execute(
                    "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
                    (client_order_id,),
                ).fetchone()
            )

    def start_order_submission(self, client_order_id: str) -> None:
        """用途：原子地把预留订单切换为提交中；输入：客户端订单号；输出：无。"""
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
                "SELECT * FROM broker_order_intent WHERE client_order_id = ?",
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
        """用途：用券商订单事实推进本地状态机；输入：券商订单和未匹配处理策略；输出：更新后的意图列表。"""
        self.initialize()
        broker_orders = tuple(order for order in orders if order.order_id)
        LOGGER.info("委托状态匹配开始：有效券商委托=%d；未匹配转UNKNOWN=%s", len(broker_orders), mark_unmatched_unknown)
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
        LOGGER.info("委托状态匹配完成：活动意图=%d；成功匹配=%d；全部意图=%d", len(intents), len(matched_clients), len(rows))
        return [dict(row) for row in rows]

    def latest_live_performance_before(
        self, business_date: str
    ) -> dict[str, Any] | None:
        """用途：读取指定日期前最近一日实盘绩效；输入：业务日期；输出：绩效快照或空值。"""
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
        """用途：查询最新活动模型；输入：无；输出：模型主键或空值。"""
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
        """用途：按业务日期新增或更新实盘绩效；输入：已计算绩效字段；输出：持久化后的快照。"""
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
        """用途：读取模型最近有效收益序列；输入：模型主键和窗口长度；输出：按日期正序的绩效列表。"""
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
        """用途：保存滚动评估并按决策登记重训请求；输入：窗口指标、决策和原因；输出：评估及训练请求。"""
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
                LOGGER.info("模型评估已存在，返回历史结果：日期=%s；决策=%s", business_date, existing["decision"])
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
        LOGGER.info(
            "模型评估保存完成：日期=%s；模型版本=%s；样本=%d/%d；决策=%s；已生成训练请求=%s",
            business_date,
            model_id,
            observations,
            window_days,
            decision,
            training_request is not None,
        )
        return {
            "evaluation": dict(evaluation),
            "trainingRequest": training_request,
        }

    def summary(self) -> dict[str, Any]:
        """用途：汇总各表记录数和最新任务、模型；输入：无；输出：状态库摘要。"""
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
                    "broker_permission",
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
        """用途：写入一次券商账户快照；输入：连接、同步键、日期和快照；输出：无。"""
        account = snapshot.account
        connection.execute(
            """
            INSERT INTO broker_account_snapshot(
                business_key, business_date, available_cash, total_assets,
                total_position, frozen_amount, captured_at
            ) VALUES(?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(business_key) DO UPDATE SET
                business_date = excluded.business_date,
                available_cash = excluded.available_cash,
                total_assets = excluded.total_assets,
                total_position = excluded.total_position,
                frozen_amount = excluded.frozen_amount,
                captured_at = excluded.captured_at
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
        """用途：批量写入券商持仓快照；输入：连接、同步键和快照；输出：写入条数。"""
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
    def _upsert_broker_orders(
        connection: sqlite3.Connection, snapshot: BrokerSyncSnapshot
    ) -> int:
        """用途：按券商委托号更新订单事实；输入：连接和券商快照；输出：有效订单数。"""
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
        """用途：按成交指纹幂等写入成交事实；输入：连接和券商快照；输出：待写成交数。"""
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
        """用途：防止同一客户端订单号绑定不同参数；输入：已有意图和新参数；输出：无。"""
        same = (
            existing["instrument"] == instrument
            and existing["side"] == side
            and int(existing["quantity"]) == quantity
            and Decimal(str(existing["price"])) == price
        )
        if not same:
            raise ValueError("client_order_id 已绑定不同订单参数")

    @staticmethod
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


    @contextmanager
    def _connect(self) -> Iterator[sqlite3.Connection]:
        """用途：提供自动提交或回滚的 SQLite 连接；输入：无；输出：事务连接迭代器。"""
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

    @staticmethod
    def _ensure_column(
        connection: sqlite3.Connection,
        table_name: str,
        column_name: str,
        column_type: str,
    ) -> None:
        """用途：为旧数据库补充缺失字段；输入：连接、表名、字段名和类型；输出：无。"""
        columns = {
            str(row["name"])
            for row in connection.execute(f"PRAGMA table_info({table_name})").fetchall()
        }
        if column_name not in columns:
            connection.execute(
                f"ALTER TABLE {table_name} ADD COLUMN {column_name} {column_type}"
            )

    def _successful_run_exists(self, business_key: str) -> bool:
        """用途：判断幂等任务是否已成功；输入：业务键；输出：是否存在成功记录。"""
        with self._connect() as connection:
            row = connection.execute(
                "SELECT 1 FROM task_run WHERE business_key = ? AND status = 'SUCCESS'",
                (business_key,),
            ).fetchone()
            return row is not None

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

    def _finish_run(self, business_key: str, status: str, error: str | None) -> None:
        """用途：结束任务并记录状态和错误；输入：业务键、结束状态和错误；输出：无。"""
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
        """用途：按制品哈希登记或更新模型；输入：连接、模型目录和指标；输出：模型主键。"""
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
        """用途：按模型、日期和证券写入预测排名；输入：连接、模型主键和预测表；输出：处理条数。"""
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
        """用途：把回测交易转换为影子订单和成交；输入：连接和回测目录；输出：订单数与成交数。"""
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
        """用途：导入回测每日权益；输入：连接、回测目录和场景；输出：快照条数。"""
        equity = pd.read_parquet(backtest_dir / "daily_equity.parquet")
        connection.execute(
            "DELETE FROM performance_snapshot WHERE scenario = ?",
            (scenario,),
        )
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
        """用途：用回测期末持仓替换研究持仓；输入：连接和回测目录；输出：最终持仓条数。"""
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
    """用途：生成统一 UTC 时间；输入：无；输出：ISO-8601 时间字符串。"""
    return datetime.now(UTC).isoformat()


def _artifact_fingerprint(*paths: Path) -> str:
    """用途：计算一组制品路径和内容的联合指纹；输入：文件路径；输出：SHA-256 十六进制字符串。"""
    digest = hashlib.sha256()
    for path in paths:
        digest.update(str(path).encode("utf-8"))
        digest.update(path.read_bytes() if path.exists() else b"MISSING")
    return digest.hexdigest()


def _json_default(value: object) -> str:
    """用途：把 Decimal 和 datetime 转为 JSON 字符串；输入：待序列化值；输出：字符串表示。"""
    if isinstance(value, (Decimal, datetime)):
        return str(value)
    raise TypeError(f"unsupported JSON value: {type(value).__name__}")


def _optional_float(value: Decimal | None) -> float | None:
    """用途：转换可空 Decimal；输入：Decimal 或空值；输出：float 或空值。"""
    return float(value) if value is not None else None


def _broker_order_matches_intent(order: BrokerOrder, intent: sqlite3.Row) -> bool:
    """用途：比较券商订单与本地委托关键参数；输入：券商订单和意图；输出：是否一致。"""
    instrument = str(intent["instrument"])
    return (
        order.stock_code == instrument[-6:]
        and order.direction == intent["side"]
        and order.order_quantity == int(intent["quantity"])
        and order.order_price == Decimal(str(intent["price"]))
    )
# AI_GENERATE_END ------------------------------------------------------
