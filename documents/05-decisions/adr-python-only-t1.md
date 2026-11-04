---
id: adr-python-only-t1
title: ADR：Python 交易核心与 Java 高级能力分层
sidebar_position: 10
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-04
slug: /decisions/python-only-t1/
source_paths:
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/agent/AgentReadTools.java
  - stock-server/src/main/java/com/stock/console/agent/DevelopmentTaskService.java
  - stock-server/src/main/java/com/stock/console/config/ConsoleProperties.java
---

# ADR：Python T+1 为交易权威

## 背景

历史系统经历过纯 Java 四层、多模块 Maven、模型服务、Agent 平台和前后端一体化演进。功能逐步增加后，训练、排名、调度、交易、数据治理和页面形成多条并行权威路径，验证成本过高。

## 决策

Python 收敛为最核心的交易执行层：负责市场数据和模型产物落盘、候选生成、交易前最终校验、风险控制、幂等和券商执行。Java 负责高级能力：运营问答、确定性统计工具、模型与任务分析、只读交易决策证据、文档增强和受限 GitHub 自动修复。Java 通过 Parquet、SQLite、日志和 Markdown 读取事实，不调用 Python CLI，不写 `trading.db`，不持有券商凭据。

## 保留原则

- 模型排名、订单状态和真实成交必须可追溯。
- 原始市场数据按不可变分区保存并进行质量审计。
- 重跑必须幂等，订单尝试必须有唯一业务键。
- 真实收益只按成交事实计算。
- 自动化失败时保留可解释状态，不伪造成交或成功。
- Java Agent 的决策输出只具有咨询性质，不能新增 Python 候选、生成委托字段或绕过 Python 风控。
- GitHub 开发 Agent 是唯一 Java 写能力，且其写入目标仅限配置的代码仓库和目标分支。

## 被替代方案

七模块 Java 交易后台、Quartz 交易调度、MySQL/MongoDB 主链、独立模型服务、旧策略模式、Python 独立 Agent/Wheel 和可写交易 Agent 工具均不再是当前实现。它们的设计经验只在历史文档中保留。

## 后果

高级能力可以在 Java 中快速演进，同时核心交易路径仍保持单一权威。代价是 Java 依赖上游 SQLite 与 Parquet 契约稳定；新增分析工具时必须先核对上游数据结构，不能在控制台中创建第二套交易状态，也不能把咨询结果直接当作订单。
