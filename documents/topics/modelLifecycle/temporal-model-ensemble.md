---
id: temporal-model-ensemble
title: Ranking LSTM 与 MASTER 连续权重融合
sidebar_position: 2
owner: model
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-26
slug: /topics/modelLifecycle/temporal-model-ensemble/
source_paths:
  - stock-server/stock-model/src/main/java/com/stock/model/service/TemporalModelEnsembleService.java
  - stock-server/stock-model/src/main/java/com/stock/model/algorithm/TemporalModelOutputCalculator.java
  - stock-server/stock-model/src/main/java/com/stock/model/algorithm/TemporalModelWeightOptimizer.java
  - stock-server/stock-model/src/main/java/com/stock/model/service/MasterShadowPredictionService.java
---

<DocMetadata />

# Ranking LSTM 与 MASTER 连续权重融合

:::warning 验证边界
MASTER 训练、影子预测和融合收益均未完成运行验证。
:::

### 架构与边界

Ranking LSTM 保持生产基线，MASTER 使用相同标签、成本模型和四输出契约训练和影子预测。两个模型的横截面输出统一转换为 `[0,1]` Rank Score，再由滚动指标驱动权重融合。MASTER 不是强制替换，任一模型长期无增益时权重允许降为 `0`。

`MASTER_TRANSFORMER` 使用时序特征投影与时间注意力提取单股表示，在同一交易日 Batch 内使用横截面自注意力建模股票关系，并通过市场状态门控控制横截面贡献。输出仍为 `rankScore`、`expectedExcessReturn`、`upProbability` 和 `downsideQuantile`。这是适配 DJL 四任务框架的 MASTER-inspired 架构，不宣称逐层复刻论文。

### 代码能力核对

| 能力 | 当前实现 | 源码状态 |
|---|---|---|
| 数据准备 | 独立 Quartz 任务按交易日一次获取 `TUSHARE_DAILY/TUSHARE_DAILY_V1` 全市场日线并写入统一 `stock_prices`，同时准备新闻快照；训练只检查契约所需数据。`RankingLstmDataPreprocessor` 构建 V6.1 18D 或 V6.2 26D 样本并执行全局日期切分、标准化、Purge、Embargo 和 Walk-Forward；`TrainingDatasetSnapshotService` 将不可变快照保存到 GridFS | 源码已调整；数据有缺口或已过期时进入 `WAITING_DATA` |
| 模型训练 | `LstmTrainerService` 按目标交易日分组并按横截面块训练 `MasterTransformerModel`，联合优化排序、收益、方向和下行分位，支持早停、独立测试、Walk-Forward 和严格隔离评估 | 已具备 |
| 模型预测 | `LstmModelRuntimeService` 根据候选元数据重建 MASTER，从 GridFS 加载参数，按训练时 `crossSectionBatchSize` 执行同日股票池推理；`MasterShadowPredictionService` 保存独立影子结果 | 已具备 |
| 模型评估 | 训练流水线执行一次性独立测试和 Walk-Forward 离线评估；候选登记后，由独立影子任务使用真实目标日收益持续比较 Ranking LSTM 与 MASTER，并自动调权或退役 | 已具备 |
| 制品加载校验 | 候选保存后重新读取 GridFS 参数，执行 DJL 参数反序列化和固定横截面前向冒烟，校验输出形状、有限值和概率范围 | 已补全 |
| 样本外边界 | 影子任务仅处理候选创建后的非重算生产预测批次，并有界补跑最近窗口内漏执行批次 | 已补全 |

### 新闻特征版本

事件事实按股票和交易日生成 `temporal-news-v2-8d` 快照，只读取截止时点之前发布的 `TRADE_SIGNAL`。8 维依次为 `signedImpact`、`effectiveConfidence`、`materiality`、`novelty`、`stockSpecificity`、`recency`、`sourceQuality` 和 `evidenceCoverage`。没有有效事件时使用合法零向量；新闻源、情感模型或治理数据不可用时阻断，不把不可用伪装成无事件。

`news_feature_snapshots` 以股票、交易日和特征版本唯一。V6.1 完全不读取新闻；V6.2 批量读取 8 维快照。模型输入解释以制品中的 `ModelFeatureMetadata` 为准，Ranking LSTM 与 MASTER 只有在完整特征元数据一致时才能融合，不能仅凭维度相同视为兼容。

### 双模型连续权重与自动选择

本节是长期模型生命周期，不是训练运行的后置等待阶段。训练任务在 MASTER 候选完成一次性离线评估并登记后即结束；后续 `masterShadowPrediction` 按日积累样本、刷新权重和执行回退，训练中断恢复与长期评估互不耦合。

#### 生产数据流

1. `dailyModelPrediction` 在工作日 `06:15` 生成 Ranking LSTM 基础批次。该批次只承担股票池、目标交易日和追溯载体职责，不代表 LSTM 永久占据生产权重。
2. `masterShadowPrediction` 在 `06:20` 对同一股票池执行 MASTER 影子预测，保存独立四任务输出，并用已经到齐的真实目标日收益回填历史影子样本。
3. `TemporalModelWeightOptimizer` 读取最近 20 个已评估交易日，只保留双模型同版本、有效样本数不少于 500、有效交易日不少于 10 且每日完整配对覆盖率不低于 95% 的样本外窗口；完整配对要求 Ranking LSTM、MASTER 四任务输出和实际收益同时有效，每日至少 3 只股票。
4. `masterShadowPrediction` 将优化结果写入全局融合状态，不回写 `06:15` 已持久化的 Ranking LSTM 原始预测。交易任务查询当日排名时，`PredictionCandidateQueryService` 调用 `TemporalModelEnsembleService.apply()`，读取最新融合状态并构造 `PredictionCandidateBatchDto`，因此 `06:20` 刷新的权重可以用于当天交易。
5. `PredictionCandidateBatchDto` 在本次查询结果中固定双模型 ID、实际权重、权重版本和状态；DTO 构造完成后，单次交易候选读取期间不再刷新全局权重。Trading 只读取融合后的 `effectiveModelScore` 排名，不直接读取任一原始模型分数。

两个模型每日持续预测，任一生产权重均允许为 `0`。权重为 `0` 的模型不参与当前批次的排名、收益过滤、方向评分或风险评分，但继续积累影子证据以支持自动恢复。

#### 四任务融合与最终评分

`TemporalModelOutputCalculator` 是生产排名和权重回放共用的唯一计算入口。融合输入的 rank 是两个模型各自在当日横截面内归一化后的 percentile；rank、上涨概率和下行风险必须位于 `[0,1]`，预期超额收益必须为有限数值，且 `wl + wm = 1`：

```text
effectiveRankScore             = wl * lstmRank + wm * masterRank
effectiveExpectedExcessReturn  = wl * lstmReturn + wm * masterReturn
effectiveUpProbability         = wl * lstmProbability + wm * masterProbability
effectiveDownsideQuantile      = wl * lstmDownside + wm * masterDownside
```

融合后按当日横截面重新计算 `effectiveUpProbabilityPercentile`，再生成唯一生产评分：

```text
returnScore    = 0.7 * effectiveRankScore
               + 0.3 * sigmoid(20 * effectiveExpectedExcessReturn)
directionScore = 0.7 * effectiveUpProbabilityPercentile
               + 0.3 * effectiveUpProbability
riskScore       = 1 - effectiveDownsideQuantile
effectiveModelScore = 0.55 * returnScore + 0.30 * directionScore + 0.15 * riskScore
```

候选最低净收益使用 `effectiveExpectedExcessReturn - 双边佣金 - 双边过户费 - 印花税 - 双边滑点 - 安全边际`，权重为 `0` 的模型不会通过其他字段继续影响候选。

#### 回放指标和组合门禁

MASTER 权重按 `0.00、0.05、...、1.00` 全量回放，Ranking LSTM 权重自动取 `1 - masterWeight`。每个组合使用以下统一口径：

| 指标 | 计算口径 | 用途 |
|---|---|---|
| Rank IC | 每个交易日对 `effectiveModelScore` 与实际收益做横截面 Spearman 相关，再取窗口均值 | 综合质量评分 |
| ICIR | 每日 Rank IC 均值除以样本标准差；标准差为零时记为 `0` | 运维观察，不设独立硬门禁 |
| Top 10% 扣成本收益 | 每日选择评分前 10% 股票，平均实际收益扣统一往返成本，再取窗口均值 | 必须严格为正并参与质量评分 |
| 最大回撤 | 按每日 Top 10% 扣成本收益复利形成净值曲线后计算 | 不得超过统一门禁并参与质量评分 |
| 输出标准差 | 窗口内全部 `effectiveModelScore` 的样本标准差 | 低于阈值视为输出塌缩 |

组合只有在数值全部有限、输出未塌缩、扣成本收益为正且最大回撤未越界时才进入 `eligibleCombinations`。综合质量分固定为 `0.45 × Rank IC 分 + 0.35 × 收益分 + 0.20 × 回撤分`。ICIR 只用于判断稳定性和排障，避免增加重复的一票否决门禁。

最优组合选择按以下确定性顺序执行：质量分更高、质量分差在 `1e-9` 内时距离当前权重更近、仍同分时 MASTER 权重更低。最优组合相对当前组合提升不足 `0.05` 时保持原权重。

#### 安全步进算法

```text
搜索全部 0.05 网格组合
过滤未通过硬门禁的组合
选择确定性的最优目标组合

if 没有任何合格组合:
    状态 = DEGRADED
    无限沿用旧权重并继续每日评估
else if 最优质量提升 < 0.05:
    状态 = ACTIVE
    保持旧权重
else:
    只在 eligibleCombinations 中选择：
      1. 与当前权重同方向
      2. 不越过目标权重
      3. 单次变化不超过 0.10
      4. 距目标最近
    if 不存在合格步进组合:
        状态 = DEGRADED
        保持旧权重并记录目标和阻断原因
    else:
        应用该合格组合，权重版本加 1
```

该算法不会先按数值移动 `0.10` 再补做检查，因此不合格的中间权重不会短暂进入生产。端点合法，系统可自动进入 `LSTM=1/MASTER=0` 或 `LSTM=0/MASTER=1`。

#### 状态转换

| 当前条件 | 状态 | 生产行为 |
|---|---|---|
| 样本交易日、样本数或覆盖率不足 | `OBSERVING` | 保持当前权重；新版本初始化时为 `LSTM=1/MASTER=0` |
| 当前组合有效且无需调权，或成功移动到合格组合 | `ACTIVE` | 后续批次使用当前固定权重 |
| 全部组合未通过绩效门禁，或目标方向没有可达的合格步进 | `DEGRADED` | 无限沿用旧权重，继续评估，不增加权重版本 |
| 单个模型制品、覆盖率或输出临时不可用 | `DEGRADED` | 本批次临时使用完整可用模型，不改持久化权重 |
| 两个模型都无法形成合法输出 | `UNAVAILABLE` | 停止生成交易候选，不使用旧权重掩盖运行失败 |
| 降级原因消失并重新形成合格组合 | `ACTIVE` | 恢复自动选择；实际调权时写审计 |

绩效降级与运行失败必须区分：绩效降级允许沿用上一版经过验证的权重；制品损坏、契约不一致、数值非法或双模型均无输出属于运行失败，不能因历史权重存在而继续生成候选。

任一模型 ID 或特征契约变化时清空旧组合窗口，重置为 `LSTM=1/MASTER=0/OBSERVING`。MASTER 权重降为 `0` 不退役模型；只有制品无效、契约变化或被新版本替代时才退役。

#### 状态持久化与审计

Mongo 集合 `temporal_model_ensemble_states` 只保存一条全局状态，主键为 `GLOBAL_TEMPORAL_ENSEMBLE`：

| 字段组 | 字段 |
|---|---|
| 结构与模型 | `schemaVersion`、`rankingLstmModelId`、`masterModelId` |
| 当前与目标 | `rankingLstmWeight`、`masterWeight`、`targetRankingLstmWeight`、`targetMasterWeight` |
| 决策结果 | `currentCombinationQualityScore`、`bestCombinationQualityScore`、`status`、`decisionReason` |
| 版本与时间 | `weightVersion`、`effectiveTradeDate`、`updatedAt` |
| 单模型指标 | 双模型各自的 `rankIc`、`icIr`、`netReturn`、`maxDrawdown`、`outputStd`、`qualityScore` |
| 窗口规模 | `evaluatedTradingDays`、`evaluatedSamples` |

状态结构使用 `v2-dual-weight`，检测到旧 `schemaVersion` 时直接重新初始化，不增加兼容迁移代码。仅在权重实际变化、首次进入降级、恢复正常、模型版本重置或进入不可用时写操作审计；无变化的每日评估不重复写审计。

配置在 Spring 绑定完成后集中校验：窗口、最低交易日和样本数必须为正；最低交易日不得大于窗口；搜索步长必须完整覆盖 `0` 和 `1`；最大单次变化不得小于搜索步长；最小改善不得为负；覆盖率和各权重参数必须位于合法范围。非法配置直接阻止服务启动，避免运行期生成不完整权重网格。

#### 源码实现映射

| 实现 | 职责 | 关键约束 |
|---|---|---|
| `MasterShadowPredictionService` | 查找候选创建后的生产批次，补齐 MASTER 影子结果并回填实际收益 | 跳过历史重算批次；同一 `baselineBatchId + modelId` 已存在时不重复生成 |
| `TemporalModelOutputCalculator` | 融合四任务输出、重算上涨概率百分位、生成最终模型评分 | rank、概率和风险必须在 `[0,1]`；权重和必须为 `1` |
| `TemporalModelWeightOptimizer` | 回放全部权重网格，计算 Rank IC、ICIR、净收益、回撤和输出标准差 | 只返回通过硬门禁的组合；近似同分优先当前附近和较低 MASTER 权重 |
| `TemporalModelEnsembleService` | 构造完整配对窗口、选择安全步进、处理运行降级、固化批次权重 | 状态刷新和批次应用在单 JVM 内串行；双模型不可用时拒绝候选 |
| `TemporalModelEnsembleStateDocument` | 保存全局模型 ID、当前/目标权重、指标、状态和权重版本 | 固定主键；旧 `schemaVersion` 直接重建，不做兼容迁移 |
| `PredictionCandidateBatchDto` | 将本批次模型 ID、实际权重、权重版本和状态传给决策层 | 权重只保存一次，单股票明细不重复保存 |
| `PredictionCandidateItemDto` | 提供六个 `effective*` 生产字段 | 决策层不得回退读取原始 LSTM 或 MASTER 分数字段 |
| `PredictionCandidateQueryService` | 构造融合后的 `effectiveModelScore` 排名，供 Trading 读取前三名 | 不参与权重计算，不改变模型状态 |
| `ModelOperationsQueryService` | 把 Mongo 状态组装到模型运维概览 | 只读，不提供人工调权命令 |

#### 一致性与并发

- 当前部署目标是单实例 Backend。`runShadowCycle()`、`refreshState()` 和批次融合入口使用单 JVM 串行保护，Quartz 任务统一由调度中心触发。
- 权重状态更新只影响之后调用 `getLatestUsableBatch()` / `apply()` 构造的融合 DTO，不修改 Mongo 中已经持久化的 Ranking LSTM 原始预测。已经构造完成的 `PredictionCandidateBatchDto` 所携带的 `weightVersion` 和双权重在本次交易候选读取期间保持不变。
- 状态保存和操作审计是先保存状态、再写审计的两个动作，不属于跨集合原子事务。运行核查必须同时检查状态文档和审计记录；如果未来要求多实例或强原子审计，再引入乐观锁、事务或 Outbox，当前不预先设计。
- MASTER 权重为 `0` 时影子链仍运行；停掉影子任务会使系统失去自动恢复证据，但不会修改已有生产批次。

#### 实施与验收顺序

| 阶段 | 操作 | 通过条件 | 失败处理 |
|---|---|---|---|
| 1. 源码契约 | 检查 DTO、配置、状态字段、调用方和旧字段残留 | 四任务字段一致，旧单向权重语义无残留 | 修复源码，不生成新状态 |
| 2. 编译与单测 | 编译 Backend，执行计算器、优化器、配置和服务测试 | 端点、同分、步进、降级、版本重置全部通过 | 保持 `SOURCE_IMPLEMENTED` |
| 3. 制品加载 | 加载 Ranking LSTM 与 MASTER GridFS 参数并执行固定输入前向 | 架构、形状、有限值、概率范围和特征版本一致 | 退役无效候选，不复用随机参数 |
| 4. 影子数据 | 连续生成 MASTER 影子预测并回填真实收益 | 每日完整配对覆盖率不低于 95%，至少 10 日和 500 样本 | 保持 `OBSERVING`，补数据后自动重评 |
| 5. 自动选择 | 回放 21 个权重组合并观察多次状态刷新 | 目标、实际步进、权重版本和审计一致 | 无合格组合进入 `DEGRADED` |
| 6. Trading 联调 | 核对交易候选只消费 `effective*` 字段 | 权重为 0 的模型对排序和净收益无影响 | 停止买入任务并修复契约 |
| 7. 生产观察 | 覆盖多个市场阶段并纳入滑点、成交失败和仓位相互作用 | 收益、回撤、换手和状态恢复均有可复核证据 | 自动降权或回退，不人工修改权重 |

#### 尚未完成的运行证明

- 尚未执行本次源码对应的 Maven 编译和自动化测试。
- 尚未证明真实 DJL 环境能够同时加载当前 Ranking LSTM 与 MASTER 制品。
- 尚未形成满足完整配对条件的连续影子评估窗口，也没有证明最优权重在不同市场阶段稳定。
- 当前回放使用股票收益和统一成本近似，不包含真实成交失败、资金占用、持仓交互和组合换手约束，不能替代组合级回测。
- 当前模型运维页展示组合核心状态，单模型 Rank IC、ICIR 和质量分已进入接口契约，但尚未作为独立图表展示。

两个模型使用新闻事件特征后，不再额外叠加普通 `newsImpactScore`；重大风险事件继续走独立 Java 门禁。

当前状态仍为 `SOURCE_IMPLEMENTED`。源码只证明训练/制品/运行时工厂接入、横截面输入构造、候选参数重新加载与前向冒烟校验、按制品块大小推理、候选创建后样本外影子预测、有界补跑、后验回填、滚动指标、双模型门禁、动态权重、候选融合、前端控制和新闻特征契约基础设施存在，不代表 Maven 编译、DJL 实际初始化、训练、GridFS 保存加载、权重学习或扣成本收益已经运行通过。

### 新闻标签质量与 Walk-Forward 消融

`SentimentDataPreprocessor` 的关键词结果固定记录为 `WEAK + RULE_KEYWORD`，置信度上限为 0.70，不得称为人工金标，也不得单独作为正式模型晋级证据。后续自动银标必须由事件类型、生产模型与候选模型一致性、以及事件后收益方向共同形成，并保留独立固定审计集；审计集允许用于质量验证，不进入训练扩充。

新闻增益评估直接比较 V6.1 与 V6.2，两者必须具有相同股票池、`sampleSetHash`、训练/验证/测试日期、Purge/Embargo、随机种子和成本模型。比较指标固定为每日横截面 Rank IC、ICIR、Top 10% 扣成本收益、最大回撤、覆盖率和输出标准差。只有 V6.2 相对 V6.1 取得稳定且可重复的净增益，新闻特征才允许进入生产排名；否则继续使用 V6.1，新闻风险门禁可独立保留。
