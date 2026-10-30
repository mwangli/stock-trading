---
id: stage-02-qlib-alpha158-lightgbm
title: 阶段 02：Qlib Alpha158 与 LightGBM 基线报告
sidebar_position: 102
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-02-qlib-alpha158-lightgbm/
source_paths:
  - stock-trading/pyproject.toml
  - stock-trading/src/stock_trading/cli.py
  - stock-trading/src/stock_trading/market/baostock_client.py
  - stock-trading/src/stock_trading/market/storage.py
  - stock-trading/src/stock_trading/research/labels.py
  - stock-trading/src/stock_trading/research/qlib_store.py
  - stock-trading/src/stock_trading/research/model.py
---

<DocMetadata />

# 阶段 02：Qlib Alpha158 与 LightGBM 基线报告

## 阶段目标

建立从原始 Parquet 到 Qlib 的确定性转换，使用与真实 T+1 分阶段交易一致的窗口标签训练 `Alpha158 + LightGBM Regression`，输出每日 Top 5、IC 和 Rank IC，并验证该链路能否在 2 核配置下运行。

## 已完成

- 增加单股历史日线和 BaoStock 前复权因子采集。
- 增加固定股票池历史数据断点续传、单股独立连接、三次重试和日期范围完成清单。
- 原始数据仍保存为 Parquet；Qlib 数据目录由 Parquet 确定性重建，不反向修改原始数据。
- 按 `raw_price * foreAdjustFactor` 和 `raw_volume / foreAdjustFactor` 生成 Qlib 日线字段。
- 停牌、零价格和零成交量日线不进入 Alpha158，统一在 Qlib 日历中表现为缺失值。
- 标签固定为“预测日后一个交易日下午买入窗口 VWAP，到再后一个交易日上午卖出窗口 VWAP 的收益率”。
- Qlib 只负责 Alpha158 特征和数据切分；LightGBM 使用原生 Python API，避免引入 Qlib `LGBModel` 隐式依赖的 MLflow 运行状态。
- `pyqlib` 和 `lightgbm` 改为 `research` 可选依赖，生产交易基础环境无需安装完整研究依赖。
- 产出模型、全量测试评分、每日 Top 5 和完整指标元数据。

## 真实数据范围

研究池使用 2026-10-29 全市场快照中满足 5-20 元、正常交易、非 ST 条件且成交额靠前的 20 只股票。该选择只用于验证工程和模型基线，不代表最终生产股票池。

- 数据区间：2025-01-02 至 2026-10-29。
- 交易日历：440 个交易日。
- 日线：20 个股票文件，每只 440 行。
- 5 分钟线：8,790 个股票交易日，共 421,920 根。
- 分钟质量：所有分区均为 48 根，首根结束时间 `09:35`，末根结束时间 `15:00`，无重复结束时间。
- 自定义标签：8,749 条；停牌或前后窗口不完整时不生成标签。
- Alpha158：158 个特征名全部唯一。

## 时间切分

训练、验证和测试严格按时间分离，并在相邻分段之间预留两个交易日，避免标签的买入和卖出窗口跨段：

```text
训练：2025-01-02 至 2025-12-29
隔离：2025-12-30 至 2025-12-31
验证：2026-01-05 至 2026-04-28
隔离：2026-04-29 至 2026-04-30
测试：2026-05-06 至 2026-10-29
```

## 运行验证

- Qlib 加载固定为 `kernels=2`，符合 2C4G 节点的并发边界。
- Qlib 输出共 182 个文件；同一份 Parquet 连续重建后文件数量均为 182，SHA-256 差异为 0。
- Alpha158 特征加载和处理约 17 秒，完整训练命令约 28 秒。
- 测试段生成 2,400 条评分、2,360 条成熟标签、120 个预测日和 600 条 Top 5。
- 2026-10-29 最新 Top 5 已生成；由于未来买卖窗口尚未完成，其标签为空，符合预测与事后评估分离规则。
- Python 源码语法检查、CLI 入口、Parquet 回读、Qlib 重建、LightGBM 训练和制品回读均已执行。

## 最终指标

修正停牌日 0 价格污染后，真实测试结果为：

```text
有效 IC 日：118
常数分数日：0
IC Mean：-0.0136767
IC Std：0.3425723
Rank IC Mean：-0.0178742
Rank IC Std：0.2990870
```

结论：工程链路通过，但当前固定 20 只股票样本上的模型排序能力未通过业务门槛。该结果不支持直接进入真实交易，也不能用合成数据或未修正停牌数据的较高指标替代。

## 制品

运行时制品位于忽略版本控制的 `stock-trading/var/artifacts/alpha158-lightgbm/`：

- `model.pkl`：LightGBM 模型。
- `test_predictions.parquet`：测试段全部横截面评分和已成熟标签。
- `top5.parquet`：每日 Top 5；最新日期允许标签为空。
- `metrics.json`：特征集、模型、标签、时间切分、样本数和 IC 指标。

## 已知限制

- 股票池按 2026-10-29 快照选择，存在幸存者偏差，不能作为正式收益回测结论。
- 当前仅 20 只股票，横截面宽度不足以评价 Alpha158 的全市场能力。
- 尚未按历史每个交易日动态应用 5-20 元、ST、停牌、涨跌停、权限和成交能力过滤。
- 尚未计算组合换手、滑点、最低 5 元手续费、印花税、分阶段成交和无法成交。
- 分钟线按股票和交易日形成大量小文件，历史全市场回填前应评估按股票年度分片，降低磁盘 I/O。
- 未执行 Java Maven、React 构建、数据库或券商请求。

## 下一阶段

阶段 03 将建立独立组合回测：使用逐日可交易股票池、真实下午买入和次日上午卖出窗口 VWAP，分别输出毛收益、手续费前收益、手续费后收益、换手、最大回撤和无法成交原因。由于当前模型 IC 为负，阶段 03 的主要目的为验证交易与成本计算闭环，并确定扩大股票池或调整标签和特征后是否值得继续模型研究。
