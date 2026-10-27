---
id: ranking-lstm-v6
title: Ranking LSTM v6
sidebar_position: 1
owner: model
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-26
slug: /topics/modelLifecycle/ranking-lstm/
source_paths:
  - stock-server/stock-model/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/ModelTrainingPipelineService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/LstmTrainerService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/RankingLstmTrainingDataService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/RankingLstmDataPreprocessor.java
  - stock-server/stock-data/src/main/java/com/stock/data/service/TushareFreeDailySyncService.java
---

<DocMetadata />

# Ranking LSTM v6

:::warning 验证边界
尚未完成 Maven 编译、DJL 运行、训练、GridFS 写入和实际预测评估。
:::

### 目标与边界

本专项定义唯一基础模型 Ranking LSTM 的数据、标签、时间切分、模型结构、联合损失、制品契约和候选门禁。`v6-ranking-master-baseline` 固化唯一基础参数结构，不兼容旧 Ranking 制品；实现保持 Java 17、DJL API 与 PyTorch Engine，不引入 Python、ONNX 或独立模型服务。

本方案不实现对抗训练、市场状态多专家路由或由模型直接发起真实交易。候选版本的自动晋级和回退由确定性 Java 生命周期规则完成；历史模型方向 AUC 接近随机、Rank IC 偏弱、概率分布狭窄、多任务输出冲突和横截面方差过低等证据只用于提出优化方向，不代表新架构已改善指标。

### 候选架构

| 架构标识 | 设计 |
|---|---|
| `RANKING_LSTM` | 使用 LSTM 最后时间步编码时序特征，以横截面排序为主任务和唯一生产基础模型 |

计划训练只生成 Ranking LSTM 基础候选；候选通过自动晋级后，再串行生成 MASTER 挑战者。两者使用同一数据、标签和评估口径，但承担稳定基线与复杂增强的不同职责。

### 数据与标签契约

正式 V6 训练只把固定来源版本的 `model_stock_daily_bars` 和 `temporal-news-v1` 新闻快照作为外部硬依赖。模型日线来源固定为 `TUSHARE_FREE_DAILY/V6`，`stock_prices` 不作为静默回退；缺少官方复权因子时使用当日 `pre_close` 与前一日原始收盘价递推 `NORMALIZED_PRE_CLOSE_V1` 连续价格。快照前置检查发现股票覆盖率、最新交易日或新闻快照审计不达标时，运行状态进入 `WAITING_DATA`，不把数据缺口伪装成算法训练失败，也不生成候选。

模型日线按请求股票统计来源覆盖率，默认低于 95% 时中止；`temporal-news-v1` 快照必须使用特征交易日内的截止时间，固定 25 维、所有值有限且符合列取值范围。缺失快照由既有点时新闻流水线生成，不得在训练样本循环中使用未来新闻、推测截止时间或零向量代替。历史股票池、官方复权因子、行业参考日线和精确开盘可交易状态不再是免费版 V6 的训练硬门禁。

统一训练流水线先用 Tushare 免费 `daily` 按交易日回填模型专用日线，再由 `newsFeatureSnapshotBackfill` 按持久化 `_id` 游标分批生成新闻快照。新闻任务默认每批 200、单轮最多 2,000 个股票日，截止时刻固定为交易日 `23:59:59`；完成态必须同时达到 99.5% 覆盖率、25 维契约无异常、`latestInputPublishTime <= cutoffTime`，且有事件的快照具备管线和情感模型版本。流水线每轮回填后重新检查，只有实际写入日线或生成新闻快照才继续，最多执行 `dataPreparationMaxRounds` 配置轮数，避免无进展空转。

### 训练流水线与评估边界

手动和定时触发共用以下单一路径：

```text
数据盘点 → Tushare 免费日线回填 → 新闻快照回填 → 重新检查
→ 必要时继续下一轮 → 准备或复用 READY 快照
→ 基于 snapshotId 训练 → 独立测试和 Walk-Forward 离线评估
→ 候选登记 → 流水线结束
```

模型日线由已配置的 Tushare 免费接口自动准备；新闻快照由现有新闻事实和情感流水线自动回填。官方复权因子、历史行业归属、行业参考序列和精确开盘可交易状态在当前 53 维契约中不参与硬门禁，也不使用 Legacy 行情或默认值伪造。`READY` 快照是训练恢复边界，训练中断后无需重新提取；未完成的 `BUILDING` 快照在应用重启时标记失败并清理临时工件。

上述独立测试和 Walk-Forward 是一次性离线评估，属于训练流水线。候选创建后的真实市场影子评估、滚动窗口比较、融合调权和自动回退是长期生命周期动作，不延长本次训练运行，也不要求训练线程常驻。

样本主键为 `(targetTradeDate, stockCode)`，特征只使用截止日 `t` 及以前的数据：

```text
目标交易日 = t + 1
建仓价格 = open(t + 1)
平仓价格 = open(t + 2)
rawReturn = open(t + 2) / open(t + 1) - 1
excessReturn = rawReturn - benchmarkReturn
neutralReturn = excessReturn
```

方向标签使用包含双边佣金、卖出印花税和滑点的成本中性区间。排序标签使用未缩放 `neutralReturn`，回归标签按快照收益缩放参数转换，下行风险标签表达中性收益的下行幅度。

输入固定为 53 维：25 个免费行情和股票池市场连续特征、25 个 `temporal-news-v1` 新闻连续特征，以及股票、行业、市场 3 个类别索引。免费行情特征覆盖连续化量价、多周期收益和波动率、技术指标、个股相对等权市场收益、市场宽度、市场趋势和流动性有效标记；市场基准由训练股票池按日等权构造。股票和市场使用稳定字典，行业类别因缺少可靠历史归属统一为 `UNKNOWN`，索引 `0` 固定为 `UNKNOWN`。标准化统计、缺失值策略和类别字典只能由训练窗口拟合并随制品保存。

模型元数据固定记录 `modelGeneration=V6`、`featureVersion=tushare-free-temporal-news-v6-53d`、`capabilityLevel=V6_FREE_DATA_PRODUCTION` 及三项限制。后续购买可用的官方复权、历史行业和精确可交易数据后，可在同一 V6 代际升级为 `V6_PAID_DATA_ENHANCEMENT_56D`：恢复换手率、行业日收益和个股相对行业收益三项特征，总输入提升到 56 维；该增强必须重新训练并生成新制品，不能修改现有 53 维元数据伪装兼容。

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
