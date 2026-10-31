<!-- AI_GENERATE_START -- -->
# 只读数据源

## SQLite

Java 使用 DuckDB 的 SQLite 扩展，以 `READ_ONLY` 方式挂载 `var/state/trading.db`。所有 SQL 均由后端固定定义并且仅允许 `SELECT`，Web 请求不能提交任意 SQL、表名或文件路径。

控制台直接读取现有业务表：

- `broker_account_snapshot`
- `broker_position_snapshot`
- `broker_order_fact`
- `broker_fill_fact`
- `live_performance_snapshot`、`performance_snapshot`
- `model_version`、`prediction`、`model_evaluation`
- `training_request`、`task_run`

表结构仍属于 Python 工程职责。Java 不执行建表、迁移、补数据、状态修复或任何 `INSERT`、`UPDATE`、`DELETE`、`DDL`。

## Parquet

DuckDB 直接读取现有文件路径，不维护额外清单文件：

- 股票目录：`var/market/instruments/stocks.parquet`。
- 历史日线：`var/market/daily_history/code=<code>/bars.parquet`。
- 5 分钟行情：`var/market/minute_5m/code=<code>/bars.parquet`。

股票目录使用现有 `snapshot_date`、`code`、`code_name`、`trade_status` 等列。历史行情使用现有 `trade_date`、`open`、`high`、`low`、`close`、`preclose`、`volume`、`amount`、`tradestatus`、`is_st` 等列。

## 降级规则

- 文件缺失、表缺失或不可读：页面显示具体数据源不可用原因，其他功能继续服务。
- 业务查询暂时无记录：返回空列表，不伪造样例数据。
- SQLite 查询失败：返回空数据或明确状态错误，不尝试修复。
- Python 写入期间：Java 保持只读，不改变 Python 事务和业务状态。
<!-- AI_GENERATE_END -- -->
