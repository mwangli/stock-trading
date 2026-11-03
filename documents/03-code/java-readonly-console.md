---
id: code-java-console
title: Java 只读控制台
sidebar_position: 10
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /code/java-readonly-console/
source_paths:
  - stock-server/src/main/java/com/stock/console/config/ConsoleProperties.java
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/market/MarketRepository.java
  - stock-server/src/main/java/com/stock/console/web/ApiResponse.java
---

# Java 只读控制台

## 包结构

```text
com.stock.console
├── auth        # 登录、当前用户、退出与拦截器
├── dashboard   # 收益总览聚合接口
├── market      # Parquet 行情查询
├── trading     # 委托、成交、交易与持仓接口
├── model       # 模型、预测、评估与训练请求
├── operations  # 数据源状态和日志读取/SSE
├── documents   # Markdown 索引、内容和搜索
├── agent       # 会话、只读事实与文档增强回答
├── facts       # SQLite 事实查询与聚合
└── web         # 统一响应与异常映射
```

## 核心实现

`TradingFacts` 是 SQLite 只读事实的集中入口。它通过内存 DuckDB 附加 `state/trading.db`，使用 SQL 直接查询上游表，不建立第二套 JPA 实体或同步数据库。`MarketRepository` 使用同一 DuckDB JDBC 驱动读取 Parquet。

这种实现的优点是代码短、字段映射透明、不会产生缓存副本；约束是上游表结构变化会直接影响查询 SQL，因此文档和接口契约必须随表结构同步更新。

## 错误处理

- 数据文件不存在时返回 `available=false` 或空集合。
- SQL 查询异常记录告警并返回空结果，避免整个控制台崩溃。
- 非法股票代码和不存在文档通过 `IllegalArgumentException` 进入统一异常响应。
- 日志与文档中不可读文件被跳过，不暴露宿主机文件异常细节。

## 代码维护规则

- 新增展示字段时，先确认上游表或 Parquet 列，再更新 SQL、Controller 契约和页面类型。
- 不在 Java 层新增交易写入、状态修复或任务触发接口。
- 需要业务聚合时优先保持单一查询入口，避免每个页面重复解释同一事实。
- 对外响应保持 `ApiResponse` 统一结构。
