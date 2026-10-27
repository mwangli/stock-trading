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

正式训练始终依赖固定来源版本的 `model_stock_daily_bars`。V6.1 不读取新闻；V6.2 额外依赖 `temporal-news-v2-8d` 新闻快照。模型日线来源固定为 `TUSHARE_FREE_DAILY/V6`，`stock_prices` 不作为静默回退；缺少官方复权因子时使用当日 `pre_close` 与前一日原始收盘价递推 `NORMALIZED_PRE_CLOSE_V1` 连续价格。前置检查发现对应契约的数据不达标时，运行状态进入 `WAITING_DATA`，不把数据缺口伪装成算法训练失败，也不生成候选。

模型日线按请求股票统计来源覆盖率，默认低于 95% 时中止。V6.2 的 `temporal-news-v2-8d` 快照必须使用特征交易日内的截止时间，固定 8 维、所有值有限且符合列取值范围；`NO_EVENT` 使用合法零向量，数据或模型不可用的 `UNAVAILABLE` 状态不得静默补零。历史股票池、官方复权因子、行业参考日线和精确开盘可交易状态不作为当前免费数据契约的训练硬门禁。

日线同步和新闻快照生成由独立 Quartz 数据任务完成。训练流水线只检查，不在训练线程中循环回填；数据不足时直接进入 `WAITING_DATA`，待独立任务补齐后重新提交。这样可避免训练任务同时承担网络同步、情感推理和样本构造，缩短锁占用时间并使失败原因可追踪。

### 训练流水线与评估边界

手动和定时触发共用以下单一路径：

```text
数据盘点 → 不满足则 WAITING_DATA
→ 满足后选择样本集合 → 构建行情特征 → V6.2 关联紧凑新闻
→ 分片写入并生成清单 → 准备或复用 READY 快照
→ 基于 snapshotId 训练 → 独立测试和 Walk-Forward 离线评估
→ 候选登记 → 流水线结束
```

`READY` 快照是训练恢复边界。快照契约哈希包含完整 `ModelFeatureMetadata`，因此不同维度或特征顺序不能互相复用；未完成的 `BUILDING` 快照在应用重启时标记失败并清理临时工件。首版仍使用 GridFS 与 JSON GZIP，后续只优化分片和样本引用，不引入新的二进制格式。

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

V6.1 输入为 18D：15 个行情连续特征加 3 个类别索引，不读取新闻。V6.2 输入为 26D：相同 15 个行情连续特征、8 个紧凑新闻特征和相同 3 个类别索引。15 个行情特征依次为隔夜跳空、日内收益、日内振幅、收盘位置、相对 20 日平均成交额、RSI14、5/20/60 日收益、5/20/60 日波动率、当日个股相对市场超额收益、等权市场 20 日收益和市场宽度。股票和市场使用稳定字典，行业类别因缺少可靠历史归属统一为 `UNKNOWN`，索引 `0` 固定为 `UNKNOWN`。

模型通过 `ModelFeatureMetadata` 固化代际、业务版本、完整特征版本、新闻版本、三类特征名称及顺序、标签、输出、切分、成本、价格和基准口径。维度由特征列表计算，运行时从模型元数据读取，不使用代码中的固定 18/26/53 常量解释制品。V6.1 为 `v6.1-price-ranking-18d`，V6.2 为 `v6.2-compact-news-ranking-26d`；任何特征增删或重排都必须生成新元数据和新模型制品。

### 快照简化设计

快照按 `CHECK_FOUNDATION → SELECT_SAMPLE_SET → BUILD_MARKET_FEATURES → JOIN_COMPACT_NEWS（仅 V6.2）→ WRITE_CHUNKS → WRITE_MANIFEST → READY` 推进。先确定最多 50,000 个 `(stockCode, targetDate)` 样本键，再为 V6.2 查询入选键对应的新闻，避免扫描全市场新闻。

样本只保存一次，`sampleSetHash` 使用排序后的 `stockCode|targetDate` 计算 SHA-256。V6.1 与 V6.2 对比训练必须使用相同股票池、样本键、日期切分和随机种子，并校验相同 `sampleSetHash`。Walk-Forward 清单只保存日期边界，不复制 `float[][]`；加载时只生成最新主训练折叠，其余普通和严格隔离折叠在评估时逐折生成并释放，每折只用自身训练窗口拟合标准化参数。

当前固定每 2,500 个样本写一个 `json.gz` 分片，清单保存特征元数据、字典、样本哈希、日期边界、分片摘要和数量。首版不实现断点续传；进程重启后将遗留 `BUILDING` 标记为 `FAILED` 并重建，优先保持状态机简单和结果可审计。

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
