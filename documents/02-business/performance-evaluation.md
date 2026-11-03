---
id: business-performance
title: 收益与模型评估
sidebar_position: 40
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /business/performance-evaluation/
source_paths:
  - stock-server/src/main/java/com/stock/console/facts/TradingFacts.java
  - stock-server/src/main/java/com/stock/console/dashboard/DashboardController.java
  - stock-client/src/desktop/pages/dashboard/index.tsx
---

# 收益与模型评估

## 页面目标

收益总览把账户持仓、已实现交易绩效和交易排名集中展示。它用于回答“当前还有什么持仓、哪些交易已经闭环、实际收益如何”，而不是预测未来收益。

## 当前收益口径

`realized_trade` 保存买卖价格、数量、费用、成本、卖出金额、毛利、净利、毛收益率和净收益率。控制台把这些事实映射为绩效序列与收益排名，并按模型版本关联真实交易表现。

```text
毛利润 = 卖出金额 - 成本
净利润 = 毛利润 - 买入费用 - 卖出费用
净收益率 = 净利润 / 含费用的实际成本口径
```

具体计算由上游写入，控制台不重新改写历史结果；Java 查询只负责字段映射和聚合。

## 历史评估设计

阶段 09 曾定义滚动评估的四类决策：样本不足继续观察、保持当前模型、模型退化请求重训、没有生产模型时请求首次训练。有效的设计原则仍然保留：

- 模型切换前后的收益不能混入同一评估窗口。
- 入金或出金属于外部现金流，不能计为模型利润。
- 毛收益用于观察策略边际，净收益用于判断真实业务收益。
- 最大回撤、正收益日比例和窗口收益应同时解释，不能只看单个指标。

历史真实交易开关和复杂准入门禁在阶段 12 被删除，文档不再把它们描述为当前控制条件。

## 展示边界

控制台当前持仓价格使用已知成交成本作为保守占位，不代表实时市值；页面必须结合字段时间和市场行情判断时效。没有真实已实现交易时，模型评估不能被解释为生产盈利证明。
