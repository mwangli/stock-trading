<!-- AI_GENERATE_START - -->
# Python 控制台数据契约

## SQLite

Java 以 SQLite URI `mode=ro` 打开 `var/state/trading.db`，并设置 `PRAGMA query_only=ON` 和 `busy_timeout`。契约版本由 `console_contract_metadata.console_contract_version` 提供。

版本 `1` 的稳定视图：

- `console_v_account_latest`
- `console_v_positions_latest`
- `console_v_orders`
- `console_v_fills`
- `console_v_performance`
- `console_v_model_versions`
- `console_v_predictions`
- `console_v_model_evaluations`
- `console_v_training_requests`
- `console_v_task_runs`

视图属于 Python 迁移职责。Java 不执行建表、迁移、补数据或状态修复。

## Parquet

`var/market/console/contract.json` 是行情文件入口，包含：

```json
{
  "contractVersion": 1,
  "asOfDate": "YYYY-MM-DD",
  "instruments": "console/instruments.parquet",
  "dailyHistoryGlob": "daily_history/code=*/bars.parquet"
}
```

股票目录至少提供 `as_of_date`、`code`、`name`、`market`、`trade_status`。历史日线至少提供 `trade_date`、`open`、`high`、`low`、`close`、`preclose`、`volume`、`amount`、`tradestatus`、`is_st`。

## 降级规则

- 契约版本不一致：相关页面显示版本不兼容。
- 文件缺失或不可读：页面显示不可用，其他功能继续服务。
- SQLite 查询失败：返回空数据，不尝试修复。
- Python WAL 写入期间：Java 只读等待 `busy_timeout`，不得阻塞或改变 Python 事务。
<!-- AI_GENERATE_END - -->
