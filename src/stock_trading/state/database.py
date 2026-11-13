# AI_GENERATE_START --------
from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator


SCHEMA_VERSION = 11
_MODEL_ID_MIGRATION = """
ALTER TABLE model_version ADD COLUMN model_id TEXT NOT NULL DEFAULT 'legacy';
ALTER TABLE model_evaluation ADD COLUMN model_id TEXT NOT NULL DEFAULT 'legacy';
ALTER TABLE training_request ADD COLUMN model_id TEXT NOT NULL DEFAULT 'legacy';
CREATE INDEX IF NOT EXISTS idx_model_version_config
ON model_version(model_id, id);
CREATE INDEX IF NOT EXISTS idx_model_evaluation_config
ON model_evaluation(model_id, business_date);
CREATE INDEX IF NOT EXISTS idx_training_request_config
ON training_request(model_id, status, requested_at);
"""
SCHEMA_MIGRATIONS = {
    1: "ALTER TABLE prediction RENAME TO prediction_rank;" + _MODEL_ID_MIGRATION,
    2: "ALTER TABLE prediction RENAME TO prediction_rank;" + _MODEL_ID_MIGRATION,
    3: "ALTER TABLE prediction RENAME TO prediction_rank;" + _MODEL_ID_MIGRATION,
    4: _MODEL_ID_MIGRATION,
    5: "",
    6: "",
}

_PREDICTION_TIME_MIGRATION = """
ALTER TABLE prediction_rank ADD COLUMN execution_date TEXT;
ALTER TABLE prediction_rank ADD COLUMN generated_at TEXT;
ALTER TABLE prediction_rank ADD COLUMN contract_version TEXT;
"""
SCHEMA_MIGRATIONS = {
    version: migration + _PREDICTION_TIME_MIGRATION
    for version, migration in SCHEMA_MIGRATIONS.items()
}
SCHEMA_MIGRATIONS[7] = _PREDICTION_TIME_MIGRATION

_STOCK_PERMISSION_SCHEMA = """
CREATE TABLE IF NOT EXISTS broker_stock_permission (
    instrument TEXT PRIMARY KEY,
    stock_code TEXT NOT NULL,
    scope TEXT NOT NULL,
    status TEXT NOT NULL,
    evidence_type TEXT NOT NULL,
    error_no TEXT,
    evidence_detail TEXT,
    response_json TEXT,
    response_msg TEXT,
    observed_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_broker_stock_permission_status
ON broker_stock_permission(status, instrument);
"""
SCHEMA_MIGRATIONS[8] = _STOCK_PERMISSION_SCHEMA
SCHEMA_MIGRATIONS[9] = """
ALTER TABLE broker_stock_permission ADD COLUMN response_json TEXT;
ALTER TABLE broker_stock_permission ADD COLUMN response_msg TEXT;
"""
SCHEMA_MIGRATIONS[10] = ""

SCHEMA = """
-- 任务运行记录：保存可恢复、可幂等任务的执行状态。
CREATE TABLE IF NOT EXISTS task_run (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 本地自增主键，仅用于库内关联和排序。
    business_key TEXT NOT NULL UNIQUE, -- 任务幂等业务键，相同业务输入只能产生一条记录。
    task_type TEXT NOT NULL, -- 任务类型，例如模型导入、预测导入或券商同步。
    business_date TEXT NOT NULL, -- 任务归属业务日期，格式 YYYY-MM-DD。
    status TEXT NOT NULL, -- 任务状态，例如 RUNNING、SUCCESS、SKIPPED、FAILED。
    started_at TEXT, -- 任务开始时间，UTC ISO-8601 字符串。
    finished_at TEXT, -- 任务结束时间，未结束时为空。
    error_message TEXT -- 失败原因；成功或未结束时为空。
);

-- 模型版本：登记模型制品、特征集、评估指标和当前生命周期状态。
CREATE TABLE IF NOT EXISTS model_version (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 模型版本本地主键。
    model_id TEXT NOT NULL DEFAULT 'legacy', -- 稳定模型配置 ID，旧记录统一标记 legacy。
    artifact_hash TEXT NOT NULL UNIQUE, -- 模型文件 SHA-256，用于识别同一制品。
    model_name TEXT NOT NULL, -- 模型算法或训练方案名称。
    feature_set TEXT NOT NULL, -- 模型使用的特征集名称。
    status TEXT NOT NULL, -- 制品状态；新链路固定 READY，历史状态原样保留。
    artifact_path TEXT NOT NULL, -- 模型制品在本机的绝对或项目内路径。
    metrics_json TEXT NOT NULL, -- 训练和评估指标的完整 JSON 快照。
    created_at TEXT NOT NULL -- 模型首次登记时间，UTC ISO-8601 字符串。
);

CREATE INDEX IF NOT EXISTS idx_model_version_config
ON model_version(model_id, id);

-- 预测结果：保存模型在交易日维度产生的候选排名和后验标签。
CREATE TABLE IF NOT EXISTS prediction_rank (
    id INTEGER PRIMARY KEY AUTOINCREMENT, -- 预测记录本地主键。
    model_version_id INTEGER NOT NULL, -- 产生该预测的模型版本主键。
    prediction_date TEXT NOT NULL, -- 特征日期 D，格式 YYYY-MM-DD。
    execution_date TEXT, -- D 的下一交易日，历史研究记录为空。
    generated_at TEXT, -- 实际信号生成时间，UTC ISO-8601。
    contract_version TEXT, -- 交易时间合同版本，旧排名为空。
    instrument TEXT NOT NULL, -- Qlib 标准证券代码，例如 SH600000。
    rank INTEGER NOT NULL, -- 同一模型同一预测日内的候选排名，1 为最高。
    score REAL NOT NULL, -- 模型输出的排序分数。
    label REAL, -- 标签成熟后回填的真实收益标签，未成熟时为空。
    created_at TEXT NOT NULL, -- 预测写入时间，UTC ISO-8601 字符串。
    UNIQUE(model_version_id, prediction_date, instrument),
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE INDEX IF NOT EXISTS idx_prediction_latest
ON prediction_rank(prediction_date, model_version_id, rank);
CREATE INDEX IF NOT EXISTS idx_prediction_execution
ON prediction_rank(execution_date, contract_version, model_version_id, rank);

-- 模型滚动评估：保存固定已实现收益观测窗口内的指标和决策。
CREATE TABLE IF NOT EXISTS model_evaluation (
    business_key TEXT PRIMARY KEY, -- 模型与业务日期组合形成的评估幂等键。
    model_id TEXT NOT NULL DEFAULT 'legacy', -- 被评估的稳定模型配置 ID。
    model_version_id INTEGER, -- 被评估的模型版本；无法绑定时为空。
    business_date TEXT NOT NULL, -- 评估截止交易日，格式 YYYY-MM-DD。
    window_observations INTEGER NOT NULL, -- 配置要求的有效收益观测数量。
    observations INTEGER NOT NULL, -- 当前已取得的有效收益样本数。
    positive_period_ratio REAL, -- 窗口内毛收益为正的观测比例。
    total_gross_return REAL, -- 窗口内复合毛收益率。
    total_net_return REAL, -- 窗口内复合净收益率。
    max_drawdown REAL, -- 窗口净值序列的最大回撤。
    decision TEXT NOT NULL, -- 评估决策，例如 WARMUP、KEEP、RETRAIN。
    reason TEXT NOT NULL, -- 形成决策的业务原因。
    created_at TEXT NOT NULL, -- 评估记录创建时间，UTC ISO-8601 字符串。
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

-- 重训请求：由 RETRAIN 评估产生，同一模型 ID 的新制品登记后完成。
CREATE TABLE IF NOT EXISTS training_request (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id TEXT NOT NULL DEFAULT 'legacy',
    evaluation_key TEXT NOT NULL UNIQUE,
    requested_model_version_id INTEGER,
    status TEXT NOT NULL CHECK(status IN ('PENDING','COMPLETED','CANCELLED')),
    requested_at TEXT NOT NULL,
    completed_model_version_id INTEGER,
    completed_at TEXT,
    FOREIGN KEY(evaluation_key) REFERENCES model_evaluation(business_key),
    FOREIGN KEY(requested_model_version_id) REFERENCES model_version(id),
    FOREIGN KEY(completed_model_version_id) REFERENCES model_version(id)
);

CREATE INDEX IF NOT EXISTS idx_model_evaluation_config
ON model_evaluation(model_id, business_date);
CREATE INDEX IF NOT EXISTS idx_training_request_config
ON training_request(model_id, status, requested_at);

-- 回测实验摘要：逐笔交易和权益仍保存在实验目录的 Parquet 文件中。
CREATE TABLE IF NOT EXISTS backtest_run (
    backtest_id TEXT PRIMARY KEY,
    model_id TEXT NOT NULL,
    model_version_id INTEGER,
    model_artifact_hash TEXT NOT NULL,
    dataset_id TEXT NOT NULL,
    strategy_contract_hash TEXT NOT NULL,
    backtest_contract_hash TEXT NOT NULL,
    artifact_path TEXT NOT NULL,
    status TEXT NOT NULL,
    initial_cash REAL NOT NULL,
    test_start TEXT,
    test_end TEXT,
    closed_trades INTEGER,
    gross_profit REAL,
    gross_return REAL,
    net_profit REAL,
    max_drawdown REAL,
    win_rate REAL,
    average_holding_days REAL,
    turnover REAL,
    summary_json TEXT NOT NULL,
    created_at TEXT NOT NULL,
    finished_at TEXT,
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);
CREATE INDEX IF NOT EXISTS idx_backtest_run_model
ON backtest_run(model_id, created_at);

-- 券商成交事实：只保存券商已成交订单及其模型归因。
CREATE TABLE IF NOT EXISTS broker_order (
    trade_date TEXT NOT NULL,
    broker_order_id TEXT NOT NULL,
    instrument TEXT NOT NULL,
    stock_name TEXT,
    market TEXT,
    direction TEXT NOT NULL CHECK(direction IN ('BUY','SELL')),
    order_price REAL NOT NULL,
    order_quantity INTEGER NOT NULL,
    average_fill_price REAL NOT NULL,
    filled_quantity INTEGER NOT NULL,
    model_version_id INTEGER,
    prediction_date TEXT,
    raw_status TEXT,
    status TEXT NOT NULL CHECK(status = 'FILLED'),
    order_time TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    PRIMARY KEY(trade_date, broker_order_id),
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE INDEX IF NOT EXISTS idx_broker_order_fifo
ON broker_order(instrument, direction, order_time);
CREATE INDEX IF NOT EXISTS idx_broker_order_trade_date
ON broker_order(trade_date);

-- 券商费用事实：当前无真实字段时保存固定 5 元和零额外费用。
CREATE TABLE IF NOT EXISTS broker_order_fee (
    trade_date TEXT NOT NULL,
    broker_order_id TEXT NOT NULL,
    base_fee REAL NOT NULL,
    commission REAL NOT NULL,
    stamp_tax REAL NOT NULL,
    other_broker_fee REAL NOT NULL,
    aggregate_total REAL,
    total_fee REAL NOT NULL,
    fee_source TEXT NOT NULL CHECK(fee_source IN ('BUSINESS_ZERO','BROKER_ACTUAL')),
    updated_at TEXT NOT NULL,
    PRIMARY KEY(trade_date, broker_order_id),
    FOREIGN KEY(trade_date, broker_order_id)
        REFERENCES broker_order(trade_date, broker_order_id)
);

-- 已实现交易：一行对应一个买单与一个卖单形成的 FIFO 闭环。
CREATE TABLE IF NOT EXISTS realized_trade (
    trade_key TEXT PRIMARY KEY,
    model_version_id INTEGER,
    instrument TEXT NOT NULL,
    buy_order_id TEXT NOT NULL,
    sell_order_id TEXT NOT NULL,
    buy_date TEXT NOT NULL,
    sell_date TEXT NOT NULL,
    quantity INTEGER NOT NULL,
    buy_price REAL NOT NULL,
    sell_price REAL NOT NULL,
    buy_fee REAL NOT NULL,
    sell_fee REAL NOT NULL,
    cost_basis REAL NOT NULL,
    sell_amount REAL NOT NULL,
    gross_profit REAL NOT NULL,
    net_profit REAL NOT NULL,
    gross_return REAL NOT NULL,
    net_return REAL NOT NULL,
    buy_time TEXT NOT NULL,
    sell_time TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY(model_version_id) REFERENCES model_version(id),
    FOREIGN KEY(buy_date, buy_order_id)
        REFERENCES broker_order(trade_date, broker_order_id),
    FOREIGN KEY(sell_date, sell_order_id)
        REFERENCES broker_order(trade_date, broker_order_id)
);

CREATE INDEX IF NOT EXISTS idx_realized_trade_sell_date
ON realized_trade(sell_date);
CREATE INDEX IF NOT EXISTS idx_realized_trade_model_date
ON realized_trade(model_version_id, sell_date);

-- 委托尝试：记录实时委托状态和买入模型归因。
CREATE TABLE IF NOT EXISTS order_attempt (
    client_order_id TEXT PRIMARY KEY, -- 本地稳定尝试标识，与一次实时报价一一对应。
    business_date TEXT NOT NULL,
    instrument TEXT NOT NULL, -- Qlib 标准证券代码。
    side TEXT NOT NULL, -- 买卖方向 BUY 或 SELL。
    quantity INTEGER NOT NULL, -- 该次实时尝试提交的委托股数。
    price REAL NOT NULL, -- 该次实时尝试提交的委托限价。
    model_version_id INTEGER,
    prediction_date TEXT,
    status TEXT NOT NULL, -- 本地委托状态机当前状态。
    broker_order_id TEXT, -- 券商接受后返回的委托号。
    error_message TEXT, -- 明确拒绝或通信结果不确定时的原因。
    created_at TEXT NOT NULL, -- 委托意图首次创建时间。
    updated_at TEXT NOT NULL, -- 委托状态最后更新时间。
    FOREIGN KEY(model_version_id) REFERENCES model_version(id)
);

CREATE INDEX IF NOT EXISTS idx_order_attempt_broker
ON order_attempt(business_date, broker_order_id, created_at);

-- 券商交易权限事实：按市场板块保存已确认或未知的交易权限。
CREATE TABLE IF NOT EXISTS broker_permission (
    scope TEXT PRIMARY KEY, -- 权限范围，例如 SH_MAIN 或 SZ_MAIN。
    status TEXT NOT NULL, -- ALLOWED、DENIED 或 UNKNOWN。
    evidence_type TEXT NOT NULL, -- 权限结论来源，例如 HISTORICAL_ORDER。
    evidence_detail TEXT, -- 不含凭据的证据摘要。
    observed_at TEXT NOT NULL, -- 证据发生或采集时间。
    updated_at TEXT NOT NULL -- 本地最后更新时间。
);

-- 券商逐股交易权限事实：保存零价格、零数量真实买入接口的逐股响应结论。
CREATE TABLE IF NOT EXISTS broker_stock_permission (
    instrument TEXT PRIMARY KEY, -- Qlib 标准证券代码，例如 SH600000。
    stock_code TEXT NOT NULL, -- 券商六位证券代码。
    scope TEXT NOT NULL, -- 兼容板块范围，例如 SH_MAIN 或 SZ_MAIN。
    status TEXT NOT NULL, -- ALLOWED 或 DENIED。
    evidence_type TEXT NOT NULL, -- 固定为 HIGH_PRICE_PERMISSION_PROBE。
    error_no TEXT, -- 券商业务错误码；接口受理时可为空。
    evidence_detail TEXT, -- 不含账号、Token 和委托号的券商响应摘要。
    response_json TEXT, -- 券商买入接口返回的完整原始 JSON。
    response_msg TEXT, -- 券商原始中文消息，便于后续分析。
    observed_at TEXT NOT NULL, -- 权限探测发生时间。
    updated_at TEXT NOT NULL -- 本地最后更新时间。
);

CREATE INDEX IF NOT EXISTS idx_broker_stock_permission_status
ON broker_stock_permission(status, instrument);

-- 账户快照：固定主键保证全库只保留最近一次主动同步的券商账户资金事实。
CREATE TABLE IF NOT EXISTS account_snapshot (
    id INTEGER PRIMARY KEY CHECK(id = 1),
    available_cash REAL NOT NULL,
    total_assets REAL NOT NULL,
    total_position REAL NOT NULL,
    frozen_amount REAL NOT NULL,
    captured_at TEXT NOT NULL
);

-- PC 通知中心消息：Python 负责写入，Java 控制台只读查询。
CREATE TABLE IF NOT EXISTS notify_msg (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    category TEXT NOT NULL CHECK(category IN ('SYSTEM','TRADE')),
    level TEXT NOT NULL CHECK(level IN ('INFO','SUCCESS','WARNING','ERROR')),
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    source TEXT NOT NULL,
    event_key TEXT UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notify_msg_created
ON notify_msg(created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_notify_msg_category
ON notify_msg(category, id DESC);
CREATE INDEX IF NOT EXISTS idx_notify_msg_expires
ON notify_msg(expires_at);
"""


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

# AI_GENERATE_END --------
