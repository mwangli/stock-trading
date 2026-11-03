---
id: adr-python-only-t1
title: ADR：Python T+1 为交易权威
sidebar_position: 10
owner: decisions
type: decision
status: ACCEPTED
last_verified: 2026-11-03
slug: /decisions/python-only-t1/
source_paths:
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/config/ConsoleProperties.java
---

# ADR：Python T+1 为交易权威

## 背景

历史系统经历过纯 Java 四层、多模块 Maven、模型服务、Agent 平台和前后端一体化演进。功能逐步增加后，训练、排名、调度、交易、数据治理和页面形成多条并行权威路径，验证成本过高。

## 决策

交易权威收敛到外部 Python T+1 系统：Parquet 保存市场事实，SQLite 保存任务、模型、预测、订单、成交、持仓推导所需事实和绩效；操作系统定时器负责触发。当前 Java/React 仓库只提供只读运营能力。

## 保留原则

- 模型排名、订单状态和真实成交必须可追溯。
- 原始市场数据按不可变分区保存并进行质量审计。
- 重跑必须幂等，订单尝试必须有唯一业务键。
- 真实收益只按成交事实计算。
- 自动化失败时保留可解释状态，不伪造成交或成功。

## 被替代方案

七模块 Java 交易后台、Quartz 交易调度、MySQL/MongoDB 主链、独立模型服务、旧策略模式和可写 Agent 工具均不再是当前实现。它们的设计经验只在历史文档中保留。

## 后果

控制台更容易证明只读边界，但依赖上游 SQLite 与 Parquet 契约稳定。新增业务展示时必须先核对上游数据结构，不能在控制台中临时创建另一套状态。
