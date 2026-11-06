---
id: code-java-console
title: Java 只读控制台
sidebar_position: 10
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /code/java-readonly-console/
source_paths:
  - stock-trading@src/stock_trading/state/store.py
  - stock-server/src/main/java/com/stock/console/config/ConsoleProperties.java
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/market/MarketRepository.java
  - stock-server/src/main/java/com/stock/console/web/ApiResponse.java
---

# Java 只读控制台

## 包结构

```text
com.stock.console
├── auth        # 登录、会话与拦截器
├── dashboard   # 收益总览聚合
├── market      # Parquet 行情查询
├── trading     # 委托、成交、交易与推导持仓
├── model       # 模型、预测和评估
├── operations  # 数据源状态与日志/SSE
├── documents   # Markdown 索引和搜索
├── agent       # 只读问答与受限 GitHub 开发
├── facts       # SQLite 事实查询与聚合
└── web         # 统一响应与异常映射
```

## 数据访问

`TradingFacts` 在内存 DuckDB 中以 `ATTACH ... (TYPE sqlite, READ_ONLY)` 连接 Python 的 `data/state/trading.db`。当前查询已对齐 Python Schema v4，预测来源为 `prediction_rank`，账户资金来源为 `account_snapshot`。`MarketRepository` 使用参数化文件路径调用 `read_parquet`。代码不建立 JPA 实体、同步数据库或本地缓存副本。

容器通过 `STOCK_TRADING_VAR_DIR=/data/stock-trading` 对齐 Python `data/`。本地开发必须显式指向实际数据目录；控制台默认路径仍是历史值，不能依赖默认值判断 Python 文件是否缺失。

## 错误边界

- 文件不存在时返回 `available=false` 或空集合。
- SQL 查询异常记录告警并返回安全结果，避免整个控制台崩溃。
- 非法股票代码、文档 ID 和分页参数在边界处拒绝。
- 日志和文档中不可读文件被跳过，不暴露宿主机异常细节。

这些降级只保证观察层可用，不会修复上游数据，也不能把查询失败解释为真实业务为空。

## 维护规则

- 新增展示字段前先核对 `stock-trading` 的 SQLite Schema 或 Parquet 列。
- 同步修改 SQL、Controller 响应、前端类型、页面列和文档。
- 不在 Java 层增加交易写入、状态迁移、任务触发或券商调用。
- 上游 Schema 不兼容时显式失败并升级查询，不通过删除或修改 `trading.db` 兼容。
- 对外响应继续使用统一 `ApiResponse`。
