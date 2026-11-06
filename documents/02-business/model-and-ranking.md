---
id: business-model-ranking
title: 模型、预测与完整排名
sidebar_position: 20
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /business/model-ranking/
source_paths:
  - stock-trading@src/stock_trading/research/model.py
  - stock-trading@src/stock_trading/research/batched_model.py
  - stock-trading@src/stock_trading/research/training.py
  - stock-trading@src/stock_trading/research/backtest.py
  - stock-trading@src/stock_trading/research/artifacts.py
---

# 模型、预测与完整排名

## 当前模型基线

当前只维护一条可验证基线：`Qlib Alpha158 + LightGBM Regression`。模型输出每日全市场横截面分数；Python 按分数降序生成从 1 开始、连续且唯一的完整排名。

历史 Ranking LSTM、MASTER、DJL、PyTorch、Mengzi、情绪融合和多模型路由不再属于当前运行链路。

## 训练流程

`stock-train` 先检查原始 Parquet 与 Qlib 覆盖率，再按交易日进行默认 `70:15:15` 切分。已有完整模型制品时默认复用并激活；只有 `--force` 才重新训练。

训练完成后会：

1. 保存模型、特征定义、数据区间和指标。
2. 导入 SQLite 并激活新模型版本。
3. 在 `1000`、`2000`、`3000`、`5000`、`10000` 元资金档位运行回测。
4. 保存评估结果用于比较，但不把回测指标作为模型激活门禁。

物理内存低于 16 GiB 时，系统使用分批 Alpha158 特征、磁盘检查点和内存映射；其他环境使用单次加载路径。分批大小默认 60 个交易日，可通过 `MODEL_FEATURE_BATCH_DAYS` 调整。

## 排名契约

`stock-rank` 要求同一业务日 `stock-collect` 已成功、Qlib 覆盖目标日期且存在可用模型。输出文件为 `ranking-YYYY-MM-DD.parquet`，并把完整排名导入 SQLite 的 `prediction_rank` 表。

Top5 只用于交易入口和摘要展示，不是预测制品的全部内容：

- 无持仓时，从排名 1-5 依次筛选首个满足前缀、权限、价格和资金条件的股票。
- 已有持仓时不再买入。
- 上午持仓排名在 15 以内继续持有；跌出 Rank15 或不在排名中时进入卖出目标。

## 标签与防泄漏

预测日为 `D`，买入窗口为下一个交易日 `D+1` 下午，卖出窗口为再下一个交易日 `D+2` 上午：

```text
label(D, stock) = sell_vwap(D+2) / buy_vwap(D+1) - 1
```

时间切分、特征生成和标签窗口均按交易日历处理，未来窗口不能进入训练特征。

## 展示边界

控制台可展示模型版本、预测、评估和真实交易表现，但必须把模型分数、回测结果和真实已实现收益分开。高排名不表示已经成交，新模型被激活也不表示生产收益已经改善。
