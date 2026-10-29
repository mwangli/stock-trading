---
id: ranking-lstm-v6
title: Ranking LSTM v6
sidebar_position: 1
owner: model
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-29
slug: /topics/modelLifecycle/ranking-lstm/
source_paths:
  - stock-server/stock-model/src/main/java/com/stock/model/algorithm/RankingLSTMModel.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/ModelTrainingPipelineService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/LstmTrainerService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/RankingValidationMetricsCalculator.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/LstmModelArtifactService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/RankingLstmTrainingDataService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/RankingLstmDataPreprocessor.java
  - stock-server/stock-model/src/main/java/com/stock/model/enums/LstmFeatureContract.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/TrainingDatasetSnapshotService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/NewsFeatureSnapshotBackfillService.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/NewsFeatureCompletenessService.java
  - stock-server/stock-model/src/main/java/com/stock/model/entity/NewsFeatureCompletenessDocument.java
  - stock-server/stock-data/src/main/java/com/stock/data/service/TushareFreeDailySyncService.java
---

<DocMetadata />

# Ranking LSTM v6

:::warning 验证边界
V3、V6.1 的训练运行、模型记录和数据快照来自 2026-10-28 的本地运行数据库核验；V4 仅有源码兼容契约，没有可追溯运行记录；V6.2 已完成新闻历史治理、增量采集、日期水位和最终审计，但尚未生成训练快照和候选模型。新闻最早日期晚于现有模型行情截止日期，当前不能评估新闻增益。本次文档收尾未再次执行 Maven 编译、DJL 训练、预测或影子评估。
:::

### 模型版本与快照台账

| 版本 | 制品/业务版本 | 输入契约 | 运行证据 | 当前结论 |
|---|---|---|---|---|
| V3 | `v3-embedding-multitask-1791775430969` | 16D，`panel-embedding-v3`；收益、方向概率、下行风险三输出 | 模型 `6acc52c6643b6156a8d1783a`，状态 `ACTIVE`；成功运行 `6acc4e8f643b6156a8d17838` | 已有可追溯模型、训练指标和 READY 快照 |
| V4 | `v4-balanced-direction` | 16D，沿用 `panel-embedding-v3` 三输出，在 V3 上增加方向类别平衡权重 | 运行数据库未发现模型、训练运行或快照 | 仅能确认源码兼容契约，不记录虚构指标 |
| V6.1 | `v6.1-price-ranking-18d` / `v6.1-price-ranking-18d-ranking_lstm-1793128131770` | 18D：15 个行情连续特征 + 股票、行业、市场 3 个类别索引；不读取新闻 | 模型 `040b1600-8ef6-4d08-8fdb-6e525d6510b3`，状态 `RETIRED`；运行 `52ac2c39-a167-4de3-b55e-10b8aee80c3b` | 已完成训练和离线评估，因旧质量规则淘汰；需要按毛收益与月度稳定性重评 |
| V6.2 | `v6.2-compact-news-ranking-26d` | V6.1 的 18D + `temporal-news-v2-8d` 8 个新闻特征，共 26D | 运行 `b7844125-8622-4a6f-8494-04ccbd1fd928`，状态 `WAITING_DATA`；新闻准备最终运行 `db14d55f-2c4c-4af8-a4f8-c030960916cc` | 日期水位和审计已通过，但新闻与现有行情无日期交集，暂不能训练有意义的新闻增强模型 |

| 版本 | 快照 ID / 状态 | 样本与切分 | 制品信息 | 证据边界 |
|---|---|---|---|---|
| V3 | `6acc490f643b6156a8d17834` / `READY` | 请求 4,619 只，有效 4,596 只；训练 73,516，验证 13,786，无独立测试；数据截止 2026-10-09 | `CHUNKED_JSON_GZIP_V1`，47 分片，51,015,664 bytes | 另有较早 READY 快照 `6acc4082643b6156a8d17831` |
| V4 | 无 | 无 | 无 | 运行证据缺失 |
| V6.1 | `2e39117d-d094-433a-a58a-dc6e2f9c7562` / `READY` | 请求 4,426 只，有效 1,483 只；训练 39,434，验证 28,155，测试 20,113；3 个 Walk-Forward 折和 3 个严格隔离折 | `CHUNKED_JSON_GZIP_V4`，40 分片，400,473,138 bytes；训练 2022-03-15 至 2022-11-16，验证 2022-12-19 至 2023-05-17，测试 2023-06-19 至 2023-09-18 | 可用于 V6.1 复盘；与未来 V6.2 比较时必须固定相同 `sampleSetHash` |
| V6.2 | 无 | 旧实现曾把 1,739,777 个行情股票日作为物化目标；1,721 条旧式全零 `NO_EVENT` 已删除，既有 V6.1 快照区间有事件稀疏快照为 0 | 无训练快照 | 432/432 日期水位完成且最终审计通过；新闻始于 2023-10-09，既有 V6.1 不可变快照止于 2023-09-18，区间不相交；该日期不代表统一日线库的当前边界 |

### 目标与边界

本专项定义唯一基础模型 Ranking LSTM 的数据、标签、时间切分、模型结构、联合损失、制品契约和候选门禁。`v6-ranking-master-baseline` 固化唯一基础参数结构，不兼容旧 Ranking 制品；实现保持 Java 17、DJL API 与 PyTorch Engine，不引入 Python、ONNX 或独立模型服务。

本方案不实现对抗训练、市场状态多专家路由或由模型直接发起真实交易。候选版本的自动晋级和回退由确定性 Java 生命周期规则完成；历史模型方向 AUC 接近随机、Rank IC 偏弱、概率分布狭窄、多任务输出冲突和横截面方差过低等证据只用于提出优化方向，不代表新架构已改善指标。

### 候选架构

| 架构标识 | 设计 |
|---|---|
| `RANKING_LSTM` | 使用 LSTM 最后时间步编码时序特征，以横截面排序为主任务和唯一生产基础模型 |

计划训练只生成 Ranking LSTM 基础候选；候选通过自动晋级后，再串行生成 MASTER 挑战者。两者使用同一数据、标签和评估口径，但承担稳定基线与复杂增强的不同职责。

### 数据与标签契约

正式训练始终依赖统一日线集合 `stock_prices` 中固定来源版本的数据。V6.1 不读取新闻；V6.2 额外依赖 `temporal-news-v2-8d` 新闻快照。模型日线来源固定为 `TUSHARE_DAILY/TUSHARE_DAILY_V1`；缺少官方复权因子时使用当日 `pre_close` 与前一日原始收盘价递推 `NORMALIZED_PRE_CLOSE_V1` 连续价格。前置检查会同时验证来源版本、覆盖率和最新交易日时效，数据不达标时运行状态进入 `WAITING_DATA`，不把数据缺口伪装成算法训练失败，也不生成候选。

模型日线按请求股票统计来源覆盖率，默认低于 95% 时中止。V6.2 的 `temporal-news-v2-8d` 必须在新闻清洗阶段就达到训练快照可用标准：特征交易日和截止时间明确、固定 8 维、所有值有限、列取值范围合法，并携带来源、流水线版本、情感模型版本、输入事件数和最晚发布时间。`NO_EVENT` 只有在对应来源日期已经完成采集和清洗时才是合法零向量；数据源、采集任务、模型或治理信息不可用时必须标记 `UNAVAILABLE`，不得静默补零。历史股票池、官方复权因子、行业参考日线和精确开盘可交易状态不作为当前免费数据契约的训练硬门禁。

日线同步和新闻特征准备由独立 Quartz 数据任务完成。新闻任务把采集、正文清洗、去重、数量过滤、内容质量校验、股票关联、情感推理、时间归属和股票日 8D 聚合合并为一条流水线，并在阶段结束时写入可审计的来源日期完备性水位。训练流水线只批量读取已经准备好的股票日特征，不逐股票日查询原始新闻、不重复执行情感推理，也不在训练线程中回填。数据不足时直接进入 `WAITING_DATA`，待独立任务补齐后重新提交。

### 训练流水线与评估边界

手动和定时触发共用以下单一路径：

```text
行情基础数据盘点 → 不满足则 WAITING_DATA
→ 满足后选择样本集合 → V6.2 按入选样本键校验日期水位并批量关联股票日 8D 新闻特征
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

训练快照按 `CHECK_FOUNDATION → SELECT_SAMPLE_SET → BUILD_MARKET_FEATURES → BULK_JOIN_PREPARED_NEWS（仅 V6.2）→ WRITE_CHUNKS → WRITE_MANIFEST → READY` 推进。`RankingLstmDataPreprocessor.planSampleSelection()` 先按 `maxPanelSamples` 和 `maxSamplesPerStock` 固定目标样本，再仅展开每个目标样本实际使用的 60 日窗口唯一键；正式 `process()` 复用同一个 `RankingSampleSelectionPlan`，不重复选择。新闻侧只对这些键执行范围批量读取和内存关联，禁止对 173 万个股票日逐条查询原始新闻或逐条调用情感模型。

新闻侧在进入训练快照前已经完成数据准备。持久化分为两类事实：`news_feature_snapshots` 只保存 5 日点时窗口内存在合格事件的股票日标准 8D 聚合记录；`news_feature_completeness` 按 `featureVersion + marketDataSource + marketDataSourceVersion + featureTradeDate` 保存 `COMPLETE` 水位、最大发布时间、去重事件数、有事件股票数、生成数、复用数、失败数和流水线版本，无法从历史数据可靠恢复的原始数与过滤数允许为空。样本键没有聚合记录且对应日期水位为 `COMPLETE` 时，训练快照只在内存中生成 `NO_EVENT` 零向量；水位缺失或非完成状态判定为 `UNAVAILABLE` 并阻断，不持久化无新闻股票日。

样本只保存一次，`sampleSetHash` 使用排序后的 `stockCode|targetDate` 计算 SHA-256。V6.1 与 V6.2 对比训练必须使用相同股票池、样本键、日期切分和随机种子，并校验相同 `sampleSetHash`。V6.2 新闻门禁检查的是入选样本键的可判定率 100%、来源日期水位完整、无前视、8D 契约合法和追溯字段齐全，不再要求预先存在 1,739,777 条股票日文档，也不要求全部历史交易日先达到 `COMPLETE`。全量日期完成率和稀疏快照审计只用于独立回填任务与运维观察，不参与训练前置放行。Walk-Forward 清单只保存日期边界，不复制 `float[][]`；加载时只生成最新主训练折叠，其余普通和严格隔离折叠在评估时逐折生成并释放，每折只用自身训练窗口拟合标准化参数。

当前固定每 2,500 个样本写一个 `json.gz` 分片，清单保存特征元数据、字典、样本哈希、日期边界、分片摘要和数量。首版不实现训练快照断点续传；进程重启后将遗留 `BUILDING` 标记为 `FAILED` 并重建，优先保持状态机简单和结果可审计。新闻清洗流水线自身必须按来源和发布时间游标增量执行，避免重复清洗和重复推理。

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

训练 Batch 只包含同一目标交易日样本；收益差不超过成本阈值的股票对不参与排序损失；中性样本降低方向损失权重；预测收益与上涨概率冲突时增加一致性惩罚。验证阶段检测全正、全负或近似常量的输出塌缩。最佳 Epoch 先通过输出方差、样本覆盖和数值健康门禁，再按 Top 10% 平均日毛收益排序；当日毛收益的统计置信区间重叠时，以月度正收益比例、月度标准差、最差月份和毛收益最大回撤决定稳定性优先级，Rank IC 作为排序诊断指标。

### 制品兼容与候选 Gate

制品保存架构、输入输出、特征、标签、切分和成本版本，类别字典、标准化统计、Walk-Forward 指标和概率校准参数。运行时按 `architectureType` 与 `outputSchemaVersion` 创建模型；旧三输出与新四输出制品由各自模型加载，不修改旧元数据伪装兼容。

候选评估只保留制品和契约完整、样本数量有效、数值有限、输出未塌缩、日毛收益为正、月度样本覆盖有效和毛收益最大回撤不超过 20% 的硬门禁。模型能力采用分层排序，不把不同数量级指标直接线性相加：第一目标是 Top 10% 组合平均日毛收益最大化；当候选之间的日毛收益差异缺乏统计显著性时，再按月毛收益正月份比例、月度标准差、最差月份和毛收益曲线最大回撤选择更稳定者。Rank IC 只作为横截面排序诊断和退化预警。

交易成本不再混入模型能力评分。成本后收益按实际换手率、持仓重叠和买卖侧费率单独形成执行经济性报告，用于策略持有期、换仓缓冲区和实盘资金启用判断；固定单次成本 0.2% 只能作为压力情景，不能据此直接淘汰具有正毛收益和稳定横截面排序能力的模型。

### V6.1 运行结果与问题分析

V6.1 最佳 Epoch 为 14，`trainLoss=0.4152911603`，`valLoss=0.4128260016`。独立测试 Rank IC 为 `0.1138328795`，Top 10% 成本后收益为 `-0.0008815124`；Walk-Forward 平均 Rank IC 为 `0.0326355239`，ICIR 为 `0.5648026160`，正 Rank IC 窗口为 1/3，成本后收益为 `-0.0015210641`，最大回撤为 `0.0045571996`。按当前固定成本阈值 `0.002` 反推，Walk-Forward 平均日毛收益约为 `0.000478936`，即约 `0.0479%`；独立测试日毛收益约为 `0.001118488`，即约 `0.1118%`。

成本后为负的直接原因是固定单次成本 `0.2%` 高于毛收益，不应被解释为模型能力必然为负。V6.1 的真实模型问题是跨窗口稳定性不足和至少一个样本外折的 `rankScore` 标准差低于 `1e-5`。这里的低方差是模型在该测试窗口输出的排序分数近似常量，不是输入特征标准差为零：只有 1/3 窗口 Rank IC 为正，说明排序关系对时段敏感；输出近常量说明某些窗口未形成足够横截面区分度。18D 中行业索引固定为 `UNKNOWN`、市场索引基本固定，同日市场收益和市场宽度对所有股票相同，多周期收益与波动率又高度相关，名义 18D 并不等于 18 个有效横截面自由度。股票 Embedding 在 1,483 只股票和 39,434 个训练样本上还可能记忆训练窗口。

#### 样本外低方差可观测性方案

低方差门禁必须从布尔结果升级为可定位证据。每个 Walk-Forward 和严格隔离测试折都持久化以下字段：折号、训练/验证/测试日期边界、三段样本数、有效横截面交易日数、Rank IC、Top 10% 毛收益与成本后收益、`rankScore` 均值/标准差/最小值/最大值、标签均值/标准差、Brier 指标和 `collapsed`。聚合层额外保存最低 `rankScore` 标准差与折叠折数。

逐折明细写入不可变候选模型的 `metricsJson.walkForwardFoldDetails` 和 `metricsJson.strictIsolationFoldDetails`。候选即使被质量门禁拒绝，模型版本记录仍保留，因此可以通过现有模型详情接口回看是哪一折低于 `1e-5`、该折标签是否也缺少离散度、样本量是否骤降以及毛收益是否同步退化。验证集和独立测试集同时保存输出标准差，便于区分“训练后立即塌缩”和“仅在特定样本外窗口塌缩”。

诊断顺序固定为：先检查测试折样本数和横截面日数，再比较标签标准差，随后检查 `rankScore` 标准差与范围，最后结合 Rank IC 和毛收益判断。标签有正常离散度但输出标准差低，优先排查模型、损失权重和 Embedding；标签本身低方差或样本骤减，优先排查时间窗口和数据覆盖。禁止用输入标准化统计直接解释样本外输出塌缩。

#### V6.1 优化顺序

1. 先补齐逐折和逐月指标：每日毛收益、月复合毛收益、月度正收益比例、月度标准差、最差月份、毛收益最大回撤、Rank IC、`rankScore` 标准差、标签标准差、横截面规模和实际换手率。
2. Epoch 选择改为分层规则：先通过输出方差、样本覆盖和日毛收益为正的健康门禁，再最大化平均日毛收益；当候选 Epoch 的日毛收益置信区间重叠时，以月度正收益比例、月度标准差、最差月份和毛收益最大回撤选择更稳定者。Rank IC 和 loss 只用于诊断，不与收益原值直接线性相加。
3. 做固定样本集消融：15 个连续特征基线、去股票 Embedding、仅保留股票 Embedding、去同日市场常量、去高度相关周期特征，定位低方差由数据、Embedding 还是联合损失引起。
4. 分别比较 1、3、5 日持有期。模型选择仍以毛收益和月度稳定性为准，成本报告使用实际换手率和持仓重叠，避免“每日全换仓”假设掩盖持有期优化空间。
5. 扩充有效股票和日期覆盖，并提高 Pairwise Ranking Loss 相对权重；在完成上述诊断前，不优先扩网络或放宽低方差阈值。

### V6.2 新闻质量门禁与流水线判断

旧运行生成的 1,721 条记录全部是显式全零 `NO_EVENT`，并不是有事件且可用于验证新闻增益的 8D 特征；契约升级后审计将其识别为非法，最终运行已全部删除。此前 `NewsFeatureSnapshotBackfillService` 按全部模型行情股票日游标逐条检查、逐条生成，并以 1,739,777 个行情股票日作为覆盖率分母；该路径现已替换为交易日游标，每个日期只查询 5 日窗口内的有事件股票并生成稀疏快照。

#### V6.2 推荐方案：清洗、质量门禁和快照准备一体化

1. 新闻采集按来源和发布时间游标批量拉取，事件只清洗一次；正文为空、重复、时间无效、无法关联股票、来源质量不足或证据不足的事件在同一流水线内过滤并记录原因。
2. 通过质量过滤的事件只执行一次情感推理，随后按 `stockCode + featureTradeDate + cutoffTime` 聚合为标准 `temporal-news-v2-8d`，写入前完成维度、范围、有限性、前视和追溯校验。
3. 每个来源日期完成后写完备性水位。只有所有要求来源均为 `COMPLETE`，该日期缺少股票日聚合记录才解释为 `NO_EVENT`；采集未完成、模型失败或治理信息缺失统一解释为 `UNAVAILABLE`。
4. 训练快照先固定 V6.1/V6.2 共用的 `sampleSetHash`，再按股票集合和日期范围一次性批量读取预聚合新闻特征，在内存中对样本键关联；不逐键查 Mongo，不回查原始新闻，不在训练阶段做清洗或推理。
5. 已删除“全市场股票日文档覆盖率 99.5%”通过门禁、全部历史交易日预完成门禁和按模型日线 `_id` 回填路径。全量审计改为独立运维指标：交易日 `COMPLETE` 水位完成率、稀疏快照数量、契约错误、前视和追溯缺失；训练阶段唯一新闻硬门禁是“入选样本键可判定率 100%”：每个样本键必须命中有效 8D 特征，或由同来源版本的 `COMPLETE` 水位确定为 `NO_EVENT`。
6. 首轮只在 20 至 50 个交易日、300 至 500 只股票的固定窗口运行，核对事件数、过滤率、`NO_EVENT` 比例、处理吞吐和 V6.1/V6.2 相同样本集增益；通过后再扩大日期范围。

#### 2026-10-28 至 2026-10-29 数据准备运行结果

1. 历史治理运行 `2f0de02a-4799-4f53-ae66-b122b3172e51` 处理 257,768 条文档，新增 28,248 条事件事实，重复 1,910 条，忽略 8,689 条，失败 0 条。
2. 每日增量采集覆盖 4,426 只股票，新增或修复 35,363 条；17 只部分失败股票经定向重采成功补充 125 条。最终原始新闻 522,467 条，事件事实 56,708 条，其中 `TRADE_SIGNAL` 55,989 条。
3. 日期准备覆盖 432 个预期日期，432 个均为 `COMPLETE`。最终运行 `db14d55f-2c4c-4af8-a4f8-c030960916cc` 删除 1,721 条旧式全零文档后，审计结果为有事件稀疏快照 0、非法契约 0、前视 0、来源缺失 0、`passed=true`。
4. 新闻最早日期为 2023-10-09，V6.1 快照行情截止日期为 2023-09-18。当前 432 个行情日期只能由 `COMPLETE` 水位判定为 `NO_EVENT`，无法形成 V6.1 与 V6.2 的新闻消融对比。
5. `newsFeatureSnapshotBackfill` 已恢复为默认暂停状态，`enabled=false`、`taskStatus=STOPPED`。后续只有在模型行情扩展到 2023-10-09 之后，或取得更早的合格新闻历史后，才重新执行样本键准备与 V6.2 训练。

本文当前证据等级为 `SOURCE_IMPLEMENTED` 加数据准备运行核验。V3 和 V6.1 的历史训练与快照已核验；V4 缺少运行证据；V6.2 的采集、治理、日期水位和审计已运行通过，但因新闻与行情日期不相交，尚无训练、候选模型或新闻增益证据。本次文档收尾未执行编译、测试、训练、制品读写、预测和影子评估，不能据此把 V6.2 标记为 `COMPILED`、训练完成或模型 `RUNTIME_VERIFIED`。
