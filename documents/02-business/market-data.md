---
id: business-market-data
title: 市场数据与股票池
sidebar_position: 10
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-03
slug: /business/market-data/
source_paths:
  - stock-server/src/main/java/com/stock/console/market/MarketRepository.java
  - stock-server/src/main/java/com/stock/console/market/MarketController.java
---

# 市场数据与股票池

## 业务目的

市场数据用于回答三个问题：当前有哪些可交易股票、最近一个交易日的全市场表现如何、单只股票的历史日线或 5 分钟走势是什么。控制台只展示已经由上游采集并写入 Parquet 的事实，不在请求期间访问外部行情供应商。

## 当前文件契约

| 路径 | 内容 | 页面用途 |
|---|---|---|
| `market/instruments/stocks.parquet` | 股票代码、名称、交易状态、证券类型 | 股票资料与检索 |
| `market/daily/trade_date=YYYY-MM-DD/bars.parquet` | 当日全市场行情 | 行情列表、市场统计、涨幅排名 |
| `market/daily_history/code=sh.600000/bars.parquet` | 单股历史日线 | K 线与交易日历 |
| `market/minute_5m/code=sh.600000/bars.parquet` | 单股 5 分钟行情 | 分钟图和 T+1 窗口研究 |

## 股票池规则

当前查询只选择 `security_type='1'`、状态正常且 `trade_status='1'` 的股票。代码格式必须为 `sh.######` 或 `sz.######`。股票资料与最新日线以内连接组合，缺少最新行情的股票不会出现在活跃股票分页中。

## 历史设计保留

历史 Python 重建确定 BaoStock 是首版研究数据源，原始日线和分钟线按不可变分区保存，并对 5 分钟数据执行根数、时间边界、聚合偏差和重复请求一致性审计。这些规则解释了当前 Parquet 目录的来源，但采集与审计实现不在本仓库中。

## 质量边界

- 最新交易日由 `market/daily/` 下最大的 `trade_date=` 分区确定。
- 5 分钟线不能被视为官方日线的精确拆分，研究时必须保留来源版本和审计结果。
- 控制台读取失败时返回空集合，不伪造最新价格或交易日。
