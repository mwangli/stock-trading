---
id: business-market-data
title: 市场数据与质量
sidebar_position: 10
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /business/market-data/
source_paths:
  - stock-trading@src/stock_trading/market/baostock_client.py
  - stock-trading@src/stock_trading/market/storage.py
  - stock-trading@src/stock_trading/market/sync.py
  - stock-trading@src/stock_trading/market/quality.py
  - stock-trading@src/stock_trading/market/minute_summary.py
---

# 市场数据与质量

## 数据用途

市场数据同时服务研究和交易：日线与复权因子用于 Qlib 特征，5 分钟线用于 T+1 标签与回测窗口，股票资料用于确定研究范围。真实交易报价不复用历史 Parquet，而是在每次委托前重新读取券商五档行情。

## 采集与存储

stock-collect 通过 BaoStock 检查股票资料和指定日期范围，只补齐缺失数据；它不会准备 Qlib。股票列表和基础资料的有效证券数都必须达到 100，落盘快照还要求至少 100 只处于可交易状态；异常空响应会直接失败，不覆盖已有有效快照。主要文件包括：

| 路径 | 内容 |
|---|---|
| `data/market/instruments/stocks.parquet` | 股票代码、名称、上市/退市和交易状态 |
| `data/market/daily_history/code=<code>/bars.parquet` | 单股历史日线 |
| `data/market/adjustment_factor/code=<code>/factors.parquet` | 复权因子 |
| `data/market/minute_5m/code=<code>/bars.parquet` | 原始 5 分钟行情 |
| `data/market/minute_summary/code=<code>/windows.parquet` | 每日买入/卖出窗口聚合 |

股票代码在外部数据、Qlib、模型和券商协议间进行统一转换，不能直接混用 `sh.600000`、`SH600000` 等不同格式。

## T+1 窗口

BaoStock 5 分钟时间戳按 K 线结束时间解释。只有完整落入窗口且总成交量大于 0 时才生成有效 VWAP：

- 下午买入窗口：`13:05-14:54`，预期 21 根 K 线。
- 上午卖出窗口：`09:35-11:25`，预期 22 根 K 线。

窗口不完整时不能用于标签、回测成交或收益判断。

## 质量与保留

统一覆盖率由 `STOCK_TRADING_DATA_QUALITY_MIN_RATIO` 控制，默认 `0.8`。审计按股票生命周期检查日线、复权和分钟数据；达到阈值但仍有缺失时记录警告，低于阈值时终止当前阶段。

- 原始 5 分钟行情保留 90 个自然日。
- T+1 窗口聚合保留约 5 年。
- 默认历史范围为 3 年，可通过 `STOCK_TRADING_HISTORY_DAYS` 调整，但上限为 5 年、下限为 1 年。

## 控制台展示边界

`stock-web` 只读取已落盘的 Parquet，不在页面请求期间调用 BaoStock。页面空数据可能表示文件缺失、分区未生成或真实业务为空，必须结合可用性字段、文件更新时间和 Python 日志判断。
