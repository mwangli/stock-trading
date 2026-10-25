---
id: ranking-lstm-v6
title: Ranking LSTM v6
sidebar_position: 1
owner: model
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
source_paths:
  - stock-backend/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java
  - stock-backend/src/main/java/com/stock/model/service/LstmTrainerService.java
  - stock-backend/src/main/java/com/stock/model/service/RankingLstmTrainingDataService.java
---

<DocMetadata />

# Ranking LSTM v6

:::warning 验证边界
尚未完成 Maven 编译、DJL 运行、训练、GridFS 写入和实际预测评估。
:::

**IDEA 源码入口**：[模型结构 `RankingLSTMModel.java`](pathname://../../../../stock-backend/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java) · [训练编排 `LstmTrainerService.java`](pathname://../../../../stock-backend/src/main/java/com/stock/model/service/LstmTrainerService.java) · [训练数据 `RankingLstmTrainingDataService.java`](pathname://../../../../stock-backend/src/main/java/com/stock/model/service/RankingLstmTrainingDataService.java)

### 目标与边界

本专项定义唯一基础模型 Ranking LSTM 的数据、标签、时间切分、模型结构、联合损失、制品契约和候选门禁。`v6-ranking-master-baseline` 固化唯一基础参数结构，不兼容旧 Ranking 制品；实现保持 Java 17、DJL API 与 PyTorch Engine，不引入 Python、ONNX 或独立模型服务。

本方案不实现对抗训练、市场状态多专家路由、自动真实交易或模型自动激活。历史模型方向 AUC 接近随机、Rank IC 偏弱、概率分布狭窄、多任务输出冲突和横截面方差过低等证据只用于提出优化方向，不代表新架构已改善指标。

### 候选架构

| 架构标识 | 设计 |
|---|---|
| `RANKING_LSTM` | 使用 LSTM 最后时间步编码时序特征，以横截面排序为主任务和唯一生产基础模型 |

计划训练只生成 Ranking LSTM 基础候选；候选通过自动晋级后，再串行生成 MASTER 挑战者。两者使用同一数据、标签和评估口径，但承担稳定基线与复杂增强的不同职责。

### 数据与标签契约

正式训练使用 `model_stock_daily_bars`、`model_reference_daily_bars`、`stock_adjustment_factors`、`historical_stock_universe` 和 `stock_daily_tradability_facts`。`stock_prices` 只服务旧模型和既有行情业务，不作为静默回退。关键数据源不满足训练、验证和测试窗口要求时明确失败，不生成候选。

数据准备额外执行两类成熟度门禁：关键行情、复权、历史股票池、行业参考序列和可交易事实按标识统计覆盖率，预处理后有效股票占请求股票的比例也必须达到同一阈值，默认低于 95% 时中止；`temporal-news-v1` 快照必须使用特征交易日内的截止时间，固定 25 维、所有值有限且符合列取值范围。缺失快照不得在训练时使用推测截止时间自动补造。

历史新闻快照由暂停态任务 `newsFeatureSnapshotBackfill` 分批生成。任务以当前 `model_stock_daily_bars` 来源版本为权威股票日范围，按 `_id` 游标在 2C4G 节点顺序处理，默认每批 200、单次最多 2,000 个股票日，截止时刻固定为交易日 `23:59:59`。完成态必须同时达到 99.5% 覆盖率、25 维契约无异常、`latestInputPublishTime <= cutoffTime` 且有事件的快照具备管线和情感模型版本；训练链仍保持缺失即失败。

样本主键为 `(targetTradeDate, stockCode)`，特征只使用截止日 `t` 及以前的数据：

```text
目标交易日 = t + 1
建仓价格 = open(t + 1)
平仓价格 = open(t + 2)
rawReturn = open(t + 2) / open(t + 1) - 1
excessReturn = rawReturn - benchmarkReturn
neutralReturn = excessReturn - industryMedianExcessReturn
```

方向标签使用包含双边佣金、卖出印花税和滑点的成本中性区间。排序标签使用未缩放 `neutralReturn`，回归标签按快照收益缩放参数转换，下行风险标签表达中性收益的下行幅度。

连续特征覆盖复权量价、多周期收益和波动率、成交活跃度、技术指标、相对基准、行业相对收益、市场宽度、基准趋势、流动性与可交易状态。股票、行业和市场类别使用快照内稳定字典，索引 `0` 固定为 `UNKNOWN`。标准化统计、缺失值策略和类别字典只能由训练窗口拟合并随制品保存。

### 时间切分

| 配置 | 默认设计值 |
|---|---:|
| 序列长度 | 60 个交易日 |
| 训练窗口 | 3 年 |
| 验证窗口 | 6 个月 |
| 独立测试窗口 | 3 个月 |
| 滚动步长 | 3 个月 |
| 标签跨度 purge | 2 个交易日 |
| embargo | 20 个交易日 |
| 严格敏感性隔离 | 60 个交易日 |

所有股票使用全市场统一日期边界。快照记录日期边界、交易日列表和各集合样本数。60 日隔离只用于敏感性评估，不替代主 Walk-Forward 配置。

### 四输出与联合损失

输出固定为 `rankScore`、`expectedExcessReturn`、`upProbability`、`downsideQuantile`。`rankScore` 不限制区间，预期超额收益使用 `tanh`，上涨概率使用 `sigmoid`，下行分位使用非负激活。类别索引不进入 LSTM，最后时间步的时序表示与类别 Embedding 拼接后进入共享输出层。

```text
0.45 * PairwiseRankingLoss
+ 0.25 * HuberExcessReturnLoss
+ 0.15 * CostAwareDirectionLoss
+ 0.10 * DownsideQuantileLoss
+ 0.05 * OutputConsistencyLoss
```

训练 Batch 只包含同一目标交易日样本；收益差不超过成本阈值的股票对不参与排序损失；中性样本降低方向损失权重；预测收益与上涨概率冲突时增加一致性惩罚。验证阶段检测全正、全负或近似常量的输出塌缩，最佳 Epoch 由 Rank IC、成本后 Top 10% 收益和归一化损失组合选择。

### 制品兼容与候选 Gate

制品保存架构、输入输出、特征、标签、切分和成本版本，类别字典、标准化统计、Walk-Forward 指标和概率校准参数。运行时按 `architectureType` 与 `outputSchemaVersion` 创建模型；旧三输出与新四输出制品由各自模型加载，不修改旧元数据伪装兼容。

候选评估只保留制品和契约完整、样本数量有效、数值有限、输出未塌缩、扣成本收益为正和最大回撤不超过 20% 的硬门禁。Rank IC、扣成本收益和最大回撤进入统一综合评分，不再为 ICIR、正窗口比例、Brier Score 和严格隔离指标设置独立一票否决。

这些数值是待验证门禁。完成源码编译后状态可更新为 `COMPILED`；完成训练、制品读写、预测和影子评估后更新为 `RUNTIME_VERIFIED` 或 `SUPERSEDED`。
