---
id: business-model-ranking
title: 模型、预测与排名
sidebar_position: 20
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /business/model-ranking/
source_paths:
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/model/ModelController.java
  - stock-client/src/desktop/pages/modelInfo/index.tsx
---

# 模型、预测与排名

## 业务目的

模型链路把全市场特征转换为每日横截面分数和排名，供 T+1 买入候选选择。控制台不训练模型，只展示模型版本、预测、评估、训练请求和真实交易表现。

## 当前事实表

| 表 | 解释 |
|---|---|
| `model_version` | 模型名称、特征集、状态、制品路径与指标 |
| `prediction` | 预测日期、股票、排名、分数、标签和模型版本 |
| `model_evaluation` | 滚动窗口、收益、回撤、决策和原因 |
| `training_request` | 因模型评估产生的训练请求与状态 |
| `realized_trade` | 归属于模型版本的真实已实现交易结果 |

## 当前展示口径

模型总览把预测数量、已实现交易数量、毛利、净利、净收益率和最后卖出时间聚合到模型版本。预测按日期倒序、排名正序展示；评估按业务日期倒序展示。页面应把“预测排名”和“真实收益”分开，不能用高分直接表达已获利。

## 历史设计取舍

历史提交曾实现 Ranking LSTM、MASTER Transformer、连续权重融合、Mengzi 情绪模型、新闻质量门禁和候选自动晋级。Python 最小化重建最终选择 `Qlib Alpha158 + LightGBM Regression` 作为可验证基线，核心目标从“维护多模型平台”收敛为“验证 Top 5 是否能稳定指导小资金 T+1”。

这些历史方案保留的原则是：

- 数据按时间切分，禁止未来信息泄漏。
- 预测批次必须绑定模型版本和预测日期。
- 模型评估以样本外与真实成交证据为准。
- 新模型不能只凭训练指标自动替换生产模型。

已删除模型的类名、接口和存储结构不再作为当前代码说明。
