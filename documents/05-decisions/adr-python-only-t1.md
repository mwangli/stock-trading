---
id: adr-python-only-t1
title: ADR：Python 交易主线为唯一业务权威
sidebar_position: 10
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-06
slug: /decisions/python-only-t1/
source_paths:
  - stock-trading@src/stock_trading
  - stock-trading@pyproject.toml
  - stock-trading@.github/config/stock-trading.cron
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/agent/AgentReadTools.java
---

# ADR：Python 交易主线为唯一业务权威

## 背景

历史系统先后存在 Java 多模块交易后台、MySQL/MongoDB、Quartz、多个模型实现、Agent 平台和前后端一体化方案。多条路径同时管理数据、任务、模型和交易状态，导致同一业务事实可能有多个解释，部署与验证成本持续增加。

## 决策

`stock-trading` 分支的 Python 应用是当前唯一业务权威：

- BaoStock 和 Parquet 管理市场原始数据。
- Qlib、Alpha158 和 LightGBM 管理研究与完整排名。
- SQLite 管理任务、模型、预测排名、账户快照、委托、券商事实和复盘状态。
- Python 确定性逻辑管理 T+1 时序、Rank5/Rank15、资金、报价、撤单、幂等与恢复。
- 券商管理实时账户、持仓、委托和成交终态。

`stock-web` 分支只提供可选观察与辅助能力。Java 可以读取 Python 产生的 SQLite、Parquet、日志和文档，但不得写交易库、调用 Python CLI、持有券商凭据或建立第二套交易状态。

## 保留原则

- 原始数据、模型制品、运行状态和券商事实分层保存。
- 模型排名、委托状态和真实成交必须可追溯。
- 重跑通过业务键、事务和唯一约束保持幂等。
- 未确认结果必须显式记录，不能伪造成交或成功。
- 真实收益只使用完整成交闭环计算。
- 调度放在操作系统层，应用命令保持独立和可人工复核。
- Agent 只提供说明或受限代码写入，不进入交易执行链。

## 被替代方案

以下内容不再用于指导当前开发：七模块 Maven 交易后台、Java 交易写接口、Quartz 交易任务、MySQL/MongoDB 主链、Redis Pub/Sub、DJL/PyTorch 推理、Ranking LSTM、MASTER、Mengzi、独立 RAG 服务和可写交易 Agent。

## 后果

业务事实和故障边界更清晰，Python 可以独立部署和验证，Web 控制台可以独立演进。代价是两个分支之间必须维护稳定的数据契约和路径约定；控制台发现字段不一致时，应先核对 Python 源码和实际数据，不能通过写入或迁移上游数据库来兼容。
