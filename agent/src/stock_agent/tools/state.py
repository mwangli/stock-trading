# AI_GENERATE_START ----
from __future__ import annotations

import re
import sqlite3
from datetime import date
from time import monotonic
from typing import Any

from stock_agent.config import AgentConfig
from stock_agent.memory import MemoryStore
from stock_agent.tools.registry import ToolDefinition, ToolRegistry


ALLOWED_TABLES = {
    "task_run", "model_version", "prediction", "model_evaluation",
    "training_request", "broker_order", "broker_order_fee", "realized_trade",
    "order_attempt", "broker_permission",
}
DISALLOWED_SQL = re.compile(
    r"\b(attach|detach|pragma|insert|update|delete|replace|create|alter|drop|"
    r"vacuum|reindex|analyze|load_extension)\b",
    re.IGNORECASE,
)


class StateTools:
    """Deterministic read-only access to the trading SQLite database."""

    def __init__(self, config: AgentConfig, memory: MemoryStore) -> None:
        self._config = config
        self._memory = memory

    def register(self, registry: ToolRegistry) -> None:
        self._add(registry, "state_database_summary", "汇总交易 SQLite 各业务表数量、最近任务和最近模型。", {}, self.state_database_summary)
        self._add(registry, "state_schema_catalog", "列出允许查询的 SQLite 表和字段。", {}, self.state_schema_catalog)
        self._add(
            registry, "performance_summary", "按总计、日、月、证券或模型汇总已完成闭环收益。",
            {
                "start_date": {"type": "string", "description": "YYYY-MM-DD"},
                "end_date": {"type": "string", "description": "YYYY-MM-DD"},
                "group_by": {"type": "string", "enum": ["total", "day", "month", "instrument", "model"]},
            }, self.performance_summary, required=["group_by"],
        )
        self._add(
            registry, "realized_trade_detail", "查询已完成买入卖出闭环的交易明细。",
            {
                "start_date": {"type": "string"}, "end_date": {"type": "string"},
                "instrument": {"type": "string"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 200},
            }, self.realized_trade_detail,
        )
        self._add(
            registry, "model_snapshot", "查询模型版本、滚动评估和重训请求。",
            {"limit": {"type": "integer", "minimum": 1, "maximum": 50}},
            self.model_snapshot,
        )
        self._add(
            registry, "prediction_ranking", "查询指定预测日的股票排名，省略日期时读取最新排名。",
            {
                "prediction_date": {"type": "string"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 200},
            }, self.prediction_ranking,
        )
        self._add(
            registry, "task_failures", "查询最近失败的业务任务及脱敏错误信息。",
            {"limit": {"type": "integer", "minimum": 1, "maximum": 100}},
            self.task_failures,
        )
        self._add(
            registry, "query_state_sql", "对交易 SQLite 执行单条只读 SELECT 查询。",
            {"sql": {"type": "string"}}, self.query_state_sql, required=["sql"],
        )
        self._add(
            registry, "search_agent_memory", "搜索以前会话和用户长期记忆。",
            {
                "query": {"type": "string"},
                "limit": {"type": "integer", "minimum": 1, "maximum": 20},
            }, self.search_agent_memory, required=["query"],
        )
        self._add(
            registry, "remember_user_fact", "仅在用户明确要求记住偏好或长期事实时保存本地记忆。",
            {"category": {"type": "string"}, "content": {"type": "string"}},
            self.remember_user_fact, required=["category", "content"],
        )

    def _add(
        self, registry: ToolRegistry, name: str, description: str,
        properties: dict[str, Any], handler: Any, required: list[str] | None = None,
    ) -> None:
        schema: dict[str, Any] = {
            "type": "object", "properties": properties, "additionalProperties": False,
        }
        if required:
            schema["required"] = required
        registry.register(ToolDefinition(name, description, schema, handler))

    def state_database_summary(self, _: dict[str, Any]) -> dict[str, Any]:
        with self._connect() as connection:
            existing = {
                row["name"] for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                ).fetchall()
            }
            counts = {
                table: connection.execute(f'SELECT COUNT(*) FROM "{table}"').fetchone()[0]
                for table in sorted(ALLOWED_TABLES & existing)
            }
            latest_run = _one(connection, """
                SELECT business_key, task_type, business_date, status,
                       started_at, finished_at, error_message
                FROM task_run ORDER BY id DESC LIMIT 1
            """) if "task_run" in existing else None
            latest_model = _one(connection, """
                SELECT id, model_name, feature_set, status, artifact_path, created_at
                FROM model_version ORDER BY id DESC LIMIT 1
            """) if "model_version" in existing else None
        if latest_run and latest_run.get("error_message"):
            latest_run["error_message"] = _redact_sensitive(latest_run["error_message"])
        return self._result({"counts": counts, "latestRun": latest_run, "latestModel": latest_model})

    def state_schema_catalog(self, _: dict[str, Any]) -> dict[str, Any]:
        with self._connect() as connection:
            existing = {
                row["name"] for row in connection.execute(
                    "SELECT name FROM sqlite_master WHERE type = 'table'"
                ).fetchall()
            }
            tables = {}
            for table in sorted(ALLOWED_TABLES & existing):
                rows = connection.execute(f'PRAGMA table_info("{table}")').fetchall()
                tables[table] = [
                    {
                        "name": row["name"], "type": row["type"],
                        "notNull": bool(row["notnull"]), "primaryKey": bool(row["pk"]),
                    }
                    for row in rows
                ]
        return self._result({"tables": tables})

    def performance_summary(self, arguments: dict[str, Any]) -> dict[str, Any]:
        start_date, end_date = _date_range(arguments)
        group_by = str(arguments.get("group_by", "total"))
        expressions = {
            "total": "'TOTAL'", "day": "sell_date",
            "month": "substr(sell_date, 1, 7)", "instrument": "instrument",
            "model": "COALESCE(CAST(model_version_id AS TEXT), 'NONE')",
        }
        if group_by not in expressions:
            raise ValueError("group_by 必须是 total/day/month/instrument/model")
        expression = expressions[group_by]
        group_clause = "" if group_by == "total" else f"GROUP BY {expression}"
        order_clause = "" if group_by == "total" else "ORDER BY period"
        with self._connect() as connection:
            rows = connection.execute(f"""
                SELECT {expression} AS period, COUNT(*) AS trade_count,
                       SUM(quantity) AS quantity, SUM(cost_basis) AS cost_basis,
                       SUM(sell_amount) AS sell_amount,
                       SUM(gross_profit) AS gross_profit, SUM(net_profit) AS net_profit,
                       SUM(buy_fee) + SUM(sell_fee) AS total_fees,
                       SUM(gross_profit) / NULLIF(SUM(cost_basis), 0) AS gross_return,
                       SUM(net_profit) / NULLIF(SUM(cost_basis) + SUM(buy_fee), 0) AS net_return,
                       SUM(CASE WHEN net_profit > 0 THEN 1 ELSE 0 END) * 1.0 /
                           NULLIF(COUNT(*), 0) AS win_rate
                FROM realized_trade WHERE sell_date BETWEEN ? AND ?
                {group_clause} {order_clause}
            """, (start_date, end_date)).fetchall()
            daily_rows = connection.execute("""
                SELECT sell_date,
                       SUM(gross_profit) / NULLIF(SUM(cost_basis), 0) AS gross_return,
                       SUM(net_profit) / NULLIF(SUM(cost_basis) + SUM(buy_fee), 0) AS net_return
                FROM realized_trade WHERE sell_date BETWEEN ? AND ?
                GROUP BY sell_date ORDER BY sell_date
            """, (start_date, end_date)).fetchall()
        gross_returns = [float(row["gross_return"]) for row in daily_rows if row["gross_return"] is not None]
        net_returns = [float(row["net_return"]) for row in daily_rows if row["net_return"] is not None]
        return self._result({
            "range": {"startDate": start_date, "endDate": end_date},
            "groupBy": group_by, "rows": [dict(row) for row in rows],
            "periodMetrics": {
                "compoundGrossReturn": _compound_return(gross_returns),
                "compoundNetReturn": _compound_return(net_returns),
                "grossMaxDrawdown": _max_drawdown(gross_returns),
                "netMaxDrawdown": _max_drawdown(net_returns),
                "observedDays": len(daily_rows),
            },
            "returnBasis": "只统计 realized_trade 已完成闭环；毛收益不扣费用，净收益扣已记录费用。",
        })

    def realized_trade_detail(self, arguments: dict[str, Any]) -> dict[str, Any]:
        clauses = ["sell_date BETWEEN ? AND ?"]
        start_date, end_date = _date_range(arguments)
        parameters: list[Any] = [start_date, end_date]
        instrument = str(arguments.get("instrument", "")).strip().upper()
        if instrument:
            clauses.append("instrument = ?")
            parameters.append(instrument)
        parameters.append(min(200, max(1, int(arguments.get("limit", 50)))))
        with self._connect() as connection:
            rows = connection.execute(f"""
                SELECT trade_key, model_version_id, instrument,
                       buy_order_id, sell_order_id, buy_date, sell_date,
                       quantity, buy_price, sell_price, buy_fee, sell_fee,
                       cost_basis, sell_amount, gross_profit, net_profit,
                       gross_return, net_return
                FROM realized_trade WHERE {' AND '.join(clauses)}
                ORDER BY sell_date DESC, sell_time DESC LIMIT ?
            """, parameters).fetchall()
        return self._result({"rows": [dict(row) for row in rows]})

    def model_snapshot(self, arguments: dict[str, Any]) -> dict[str, Any]:
        limit = min(50, max(1, int(arguments.get("limit", 10))))
        with self._connect() as connection:
            models = connection.execute("""
                SELECT id, artifact_hash, model_name, feature_set, status,
                       artifact_path, metrics_json, created_at
                FROM model_version ORDER BY id DESC LIMIT ?
            """, (limit,)).fetchall()
            evaluations = connection.execute("""
                SELECT business_key, model_version_id, business_date,
                       observations, window_observations, positive_period_ratio,
                       total_gross_return, total_net_return, max_drawdown,
                       decision, reason, created_at
                FROM model_evaluation ORDER BY business_date DESC LIMIT ?
            """, (limit,)).fetchall()
            requests = connection.execute("""
                SELECT id, evaluation_key, requested_model_version_id, status,
                       requested_at, completed_model_version_id, completed_at
                FROM training_request ORDER BY id DESC LIMIT ?
            """, (limit,)).fetchall()
        return self._result({
            "models": [dict(row) for row in models],
            "evaluations": [dict(row) for row in evaluations],
            "trainingRequests": [dict(row) for row in requests],
        })

    def prediction_ranking(self, arguments: dict[str, Any]) -> dict[str, Any]:
        prediction_date = str(arguments.get("prediction_date", "")).strip()
        limit = min(200, max(1, int(arguments.get("limit", 20))))
        with self._connect() as connection:
            if not prediction_date:
                row = connection.execute("SELECT MAX(prediction_date) AS value FROM prediction").fetchone()
                prediction_date = str(row["value"] or "")
            if not prediction_date:
                return self._result({"predictionDate": None, "rows": []})
            _date_or_default(prediction_date, prediction_date)
            rows = connection.execute("""
                SELECT p.prediction_date, p.instrument, p.rank, p.score, p.label,
                       p.model_version_id, m.model_name, m.status AS model_status
                FROM prediction p JOIN model_version m ON m.id = p.model_version_id
                WHERE p.prediction_date = ? ORDER BY p.rank LIMIT ?
            """, (prediction_date, limit)).fetchall()
        return self._result({"predictionDate": prediction_date, "rows": [dict(row) for row in rows]})

    def task_failures(self, arguments: dict[str, Any]) -> dict[str, Any]:
        limit = min(100, max(1, int(arguments.get("limit", 20))))
        with self._connect() as connection:
            rows = connection.execute("""
                SELECT business_key, task_type, business_date, status,
                       started_at, finished_at, error_message
                FROM task_run WHERE status = 'FAILED' ORDER BY id DESC LIMIT ?
            """, (limit,)).fetchall()
        visible = []
        for row in rows:
            item = dict(row)
            item["error_message"] = _redact_sensitive(item.get("error_message"))
            visible.append(item)
        return self._result({"rows": visible})

    def query_state_sql(self, arguments: dict[str, Any]) -> dict[str, Any]:
        sql = str(arguments.get("sql", "")).strip()
        _validate_read_sql(sql)
        deadline = monotonic() + 10.0
        with self._connect() as connection:
            connection.set_progress_handler(lambda: 1 if monotonic() > deadline else 0, 10_000)
            cursor = connection.execute(
                f"SELECT * FROM ({sql}) AS agent_query LIMIT ?",
                (self._config.max_query_rows + 1,),
            )
            rows = cursor.fetchall()
        truncated = len(rows) > self._config.max_query_rows
        return self._result({
            "rows": [dict(row) for row in rows[:self._config.max_query_rows]],
            "truncated": truncated,
        })

    def search_agent_memory(self, arguments: dict[str, Any]) -> dict[str, Any]:
        rows = self._memory.search(
            str(arguments.get("query", "")).strip(),
            min(20, max(1, int(arguments.get("limit", 10)))),
        )
        return {"status": "ok", "source": str(self._config.memory_db_path), "data": {"rows": rows}}

    def remember_user_fact(self, arguments: dict[str, Any]) -> dict[str, Any]:
        category = str(arguments.get("category", "preference")).strip() or "preference"
        content = str(arguments.get("content", "")).strip()
        if not content:
            raise ValueError("长期记忆内容不能为空")
        memory_id = self._memory.remember(category, content)
        return {"status": "ok", "source": str(self._config.memory_db_path), "data": {"memoryId": memory_id}}

    def _connect(self) -> sqlite3.Connection:
        path = self._config.state_db_path
        if not path.exists():
            raise FileNotFoundError(f"交易状态库不存在: {path}")
        connection = sqlite3.connect(f"file:{path.as_posix()}?mode=ro", uri=True)
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA query_only = ON")
        return connection

    def _result(self, data: dict[str, Any]) -> dict[str, Any]:
        return {"status": "ok", "source": str(self._config.state_db_path), "data": data}


def _date_range(arguments: dict[str, Any]) -> tuple[str, str]:
    start_date = _date_or_default(arguments.get("start_date"), "0001-01-01")
    end_date = _date_or_default(arguments.get("end_date"), "9999-12-31")
    if start_date > end_date:
        raise ValueError("开始日期不能晚于结束日期")
    return start_date, end_date


def _redact_sensitive(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    text = re.sub(
        r"(?i)(api[_-]?key|token|password|secret)\s*[:=]\s*[^\s,;]+",
        r"\1=***",
        text,
    )
    text = re.sub(r"\bgh[pousr]_[A-Za-z0-9_]{20,}\b", "***", text)
    text = re.sub(r"\bsk[-_][A-Za-z0-9_-]{16,}\b", "***", text)
    return text


def _one(connection: sqlite3.Connection, sql: str) -> dict[str, Any] | None:
    row = connection.execute(sql).fetchone()
    return dict(row) if row else None


def _date_or_default(value: Any, default: str) -> str:
    if value is None or not str(value).strip():
        return default
    normalized = str(value).strip()
    try:
        date.fromisoformat(normalized)
    except ValueError as error:
        raise ValueError(f"日期格式错误: {normalized}；请使用 YYYY-MM-DD") from error
    return normalized


def _validate_read_sql(sql: str) -> None:
    if not sql:
        raise ValueError("SQL 不能为空")
    if ";" in sql or "--" in sql or "/*" in sql:
        raise ValueError("只允许不含注释和分号的单条查询")
    if not re.match(r"^(select|with)\b", sql, re.IGNORECASE):
        raise ValueError("只允许 SELECT 或 WITH 查询")
    if DISALLOWED_SQL.search(sql):
        raise ValueError("查询包含禁止的 SQLite 操作")
    referenced = {
        match.group(1).lower() for match in re.finditer(
            r"\b(?:from|join)\s+[\"`\[]?([A-Za-z_][A-Za-z0-9_]*)", sql, re.IGNORECASE
        )
    }
    ctes = {
        match.group(1).lower() for match in re.finditer(
            r"(?:\bwith|,)\s*([A-Za-z_][A-Za-z0-9_]*)\s+AS\s*\(",
            sql,
            re.IGNORECASE,
        )
    }
    invalid = referenced - {name.lower() for name in ALLOWED_TABLES} - ctes
    if invalid:
        raise ValueError(f"查询引用了未授权表: {', '.join(sorted(invalid))}")


def _compound_return(returns: list[float]) -> float | None:
    if not returns:
        return None
    value = 1.0
    for item in returns:
        value *= 1.0 + item
    return value - 1.0


def _max_drawdown(returns: list[float]) -> float | None:
    if not returns:
        return None
    equity = 1.0
    peak = 1.0
    drawdown = 0.0
    for item in returns:
        equity *= 1.0 + item
        peak = max(peak, equity)
        drawdown = max(drawdown, 1.0 - equity / peak)
    return drawdown
# AI_GENERATE_END ----
