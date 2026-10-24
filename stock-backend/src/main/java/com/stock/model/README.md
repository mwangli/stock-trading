<!-- AI_GENERATE_START ---------- -->
# Model 模型模块

## 模块定位

`com.stock.model` 负责财经情感、LSTM、训练快照、模型训练与推理、预测批次、效果评估、候选版本、激活和回滚。模型输出是策略输入和研究事实，不直接产生真实委托。

## 核心能力

| 能力 | 主要职责 |
|---|---|
| 数据准备 | 从 `market.service` 获取版本化市场 DTO，构建训练和推理输入 |
| 算法实现 | Ranking LSTM、双注意力、MASTER Transformer、损失函数和指标 |
| 训练快照 | 保存数据范围、特征/标签版本、切分策略、样本数和校验信息 |
| 训练与推理 | 受控训练、批量预测、队列执行和失败记录 |
| 情感分析 | 固定制品加载、情感推理、事件特征和影子评估 |
| 生命周期 | 候选、评估、激活、回滚、审计和运行状态 |

## 重点能力详细设计

### 1. 不可变训练快照

**设计目标**：训练使用可复现的数据工件，而不是每次从变化中的行情库临时拼装数据。

`TrainingDatasetSnapshotService` 根据模型名称、股票范围、日期窗口和数据契约生成 `contractHash`。相同契约存在 `READY` 快照时可以复用；契约变化必须生成新快照，不能修改旧快照元数据伪装兼容。

快照创建流程：

1. 从 `MarketDataQueryService` 读取明确时间范围的市场 DTO。
2. 固化股票集合、特征版本、标签版本、样本范围、数据切分和统计摘要。
3. 将大数据按分片写入临时工件，计算每个分片和清单摘要。
4. 通过 `TrainingSnapshotArtifactStore` 保存到 MongoDB GridFS。
5. 校验文件大小、分片摘要、清单摘要和汇总样本数后，将快照标记为 `READY`。

读取时再次校验 GridFS 文件是否存在、大小和摘要是否一致。任一工件缺失或摘要不符都必须失败，不能退回实时查询后继续训练。

### 2. LSTM 数据预处理与防泄漏

`LstmDataPreprocessor` 和 `RankingLstmDataPreprocessor` 负责把市场序列转换为 DJL 可消费的张量。流程遵循以下约束：

- 训练、验证、测试按时间顺序切分，禁止随机打散跨越未来数据。
- 标准化参数只由训练窗口拟合，再应用到验证、测试和推理窗口。
- 特征时间必须早于预测目标时间，标签和目标日真实行情不能进入特征。
- 缺失值、停牌、上市不足、样本长度不足和无效价格需要明确过滤并计数。
- 排序模型同时保留股票截面和时间序列关系，训练样本必须绑定目标交易日与股票池版本。

预处理输出应携带特征版本、标签版本、序列长度、切分范围和有效样本数，作为模型制品契约的一部分。

### 3. Attention 机制

本模块存在两类 Attention，作用维度和业务含义不同：

| Attention | 所属架构 | 归一化维度 | 解决的问题 |
|---|---|---|---|
| 特征注意力 | `DUAL_ATTENTION_RANKING_LSTM` | 同一时间步的连续特征维 | 动态判断量价、波动率、流动性等特征的重要程度 |
| 时间注意力 | `DUAL_ATTENTION_RANKING_LSTM`、`MASTER_TRANSFORMER` | 单只股票的历史时间维 | 从完整观察窗口选择更有贡献的交易日表示 |
| 横截面自注意力 | `MASTER_TRANSFORMER` | 同一目标交易日的股票维 | 建模股票之间的相对关系和市场联动 |

#### 3.1 双注意力 Ranking LSTM

`RankingLSTMModel` 在 `DUAL_ATTENTION_RANKING_LSTM` 模式下执行以下流程：

```text
连续特征序列 [B, T, F]
  → 特征注意力 Linear(F → F)
  → 在 F 维执行 softmax
  → 原始特征逐元素加权
  → 单向 LSTM + LayerNorm
  → 时间注意力 Linear(H → 1)
  → 在 T 维执行 softmax
  → LSTM 隐状态加权求和
  → 拼接股票、行业、市场 Embedding
  → 共享投影和四任务输出
```

符号说明：

- `B`：同一批次的股票样本数。
- `T`：历史序列长度。
- `F`：连续特征数量，不包含三列类别索引。
- `H`：LSTM 隐藏维度。

特征注意力的计算语义为：

```text
featureScores  = Linear(X)
featureWeights = softmax(featureScores, featureDimension)
weightedInput  = X * featureWeights
```

每个交易日、每只股票都会产生一组特征权重，因此权重不是全局固定的“特征重要性”。它只表示当前样本和当前时间步下模型分配的相对权重。

时间注意力在 LSTM 编码之后执行：

```text
timeScores          = Linear(encodedSequence)
timeWeights         = softmax(timeScores, timeDimension)
temporalRepresentation = sum(encodedSequence * timeWeights, timeDimension)
```

基础 `RANKING_LSTM` 不启用双注意力：它直接使用 LSTM 最后一个时间步的隐藏表示。这样可以在相同数据、标签和损失契约下比较注意力是否带来稳定增益。

#### 3.2 Attention 的约束与解释边界

- softmax 权重只在各自维度内归一化，不能跨股票、跨日期直接比较绝对值。
- Attention 权重表示模型内部的信息分配，不等价于因果贡献，也不能直接解释为买卖理由。
- 当前 `RankingLSTMModel` 的对外输出只有四任务结果，没有持久化特征权重或时间权重；如需可解释性分析，必须新增明确的诊断输出契约，不能从日志推测。
- 类别字段不进入特征注意力。股票、行业和市场索引使用最后时间步取值查询 Embedding，并在时序表示形成后拼接。
- 注意力模型必须与基础 Ranking LSTM 使用同一训练快照、时间切分、损失和评估口径比较；参数更多不能作为激活理由。
- 特征缺失掩码、标准化版本和序列长度属于注意力制品契约，推理时必须与训练版本一致。

### 4. MASTER Transformer 机制

`MasterTransformerModel` 用于 `MASTER_TRANSFORMER` 候选架构。其目标不是只判断单只股票的历史走势，而是在同一目标交易日内同时学习“单股时序表示”和“股票之间的横截面关系”。

当前实现是适配现有 DJL 四任务框架的 **MASTER-inspired 单头 Transformer**，并非完整复刻论文，也不是标准多层 Transformer Encoder。

#### 4.1 单股时序表示

输入首先恢复为 `[B, T, inputSize]`，末尾三列类别索引被排除，当前 MASTER 只使用连续特征：

```text
continuous [B, T, F]
  → Linear(F → H)
  → ReLU
  → LayerNorm
  → temporal attention: Linear(H → H/2) → tanh → Linear(H/2 → 1)
  → 在 T 维 softmax
  → 加权求和得到 [B, H]
```

这一阶段没有使用 LSTM。时间维通过可学习的注意力评分聚合，但当前实现没有显式位置编码、因果 Mask 或时序卷积。因此它依赖输入特征本身表达时间状态，不能将其描述为完整的时序 Transformer 编码器。

#### 4.2 横截面 Q/K/V 自注意力

得到每只股票的时序表示 `R [B, H]` 后，模型分别生成：

```text
Q = Wq * R
K = Wk * R
V = Wv * R
```

股票关系分数使用缩放点积：

```text
A = softmax((Q * K^T) / sqrt(H), stockDimension)
C = A * V
```

其中 `A` 的形状为 `[B, B]`。每一行表示一只股票对当前 Batch 内其他股票的关系权重，`C [B, H]` 是聚合后的横截面关系表示。

这要求 Batch 具有明确业务语义：

- 训练数据由 `LstmTrainerService.createDateGroupedDatasets` 先按 `targetTradeDate` 分组，禁止跨日期股票进入同一个横截面 Attention。
- 同一日期股票数超过最大 Batch 时会被拆成多个 Chunk，Attention 只能看到当前 Chunk；因此训练和推理的 Batch 大小、股票覆盖与分块策略会影响关系上下文。
- 推理必须保持候选股票集合完整，并使用与训练兼容的特征版本；不能把单股票推理结果当作完整横截面结果。

#### 4.3 市场状态门控

模型使用同一 Batch 股票时序表示的均值构造市场状态：

```text
marketState = mean(R, stockDimension)
marketGate  = sigmoid(Wg * marketState)
fused       = R + marketGate * C
```

`marketGate [1, H]` 控制横截面关系表示在各隐藏维度上的贡献。残差项保留单股自身表示，避免关系信息完全覆盖个股信号。

该门控依赖当前 Batch 的股票构成，因此股票池缺失、分块变化或覆盖不完整都会改变市场状态。影子预测必须校验股票覆盖，生产融合条件不满足时回退到 Ranking LSTM。

#### 4.4 输出契约

融合表示通过两层输出 Head 生成与 Ranking LSTM 相同的四个结果：

```text
0 rankScore               # 不限制区间，用于横截面排序
1 expectedExcessReturn    # tanh 约束后的预期超额收益
2 upProbability           # sigmoid 上涨概率
3 downsideQuantile        # sigmoid 下行风险分位
```

统一输出契约允许两个架构复用损失、Walk-Forward 指标、影子评估和候选查询，但不代表两个模型制品可以互换。运行时必须通过 `architectureType` 重建正确模型。

#### 4.5 与标准 Transformer 的差异

| 能力 | 当前 `MasterTransformerModel` | 标准 Transformer Encoder 常见实现 |
|---|---|---|
| Attention Head | 单头 Q/K/V | 多头注意力 |
| 编码层数 | 单次横截面关系计算 | 多层堆叠 |
| 时间位置编码 | 无显式位置编码 | 位置编码或相对位置偏置 |
| 时间因果 Mask | 无 | 按任务选择 Causal Mask |
| 残差 | 融合阶段保留单股表示 | 每个 Attention/FFN 子层残差 |
| Feed Forward | 输出 Head | 每层独立 FFN |
| 类别 Embedding | 当前未使用 | 可按架构设计融合 |

因此文档和接口统一称其为 `MASTER_TRANSFORMER` 架构标识，但能力描述必须使用“MASTER-inspired 横截面自注意力模型”，不能宣称已经实现完整通用 Transformer。

#### 4.6 影子运行和生产边界

`ModelActivationService` 明确禁止将 `MASTER_TRANSFORMER` 直接激活为生产基础模型。当前安全路径为：

1. 训练并保存 MASTER 候选制品。
2. `MasterShadowPredictionService` 使用生产 Ranking LSTM 批次的相同股票集合和基础交易日执行影子预测。
3. 校验 MASTER 与基线模型的特征、标签、时间切分、成本和输出契约一致，并要求影子结果至少覆盖基线股票的 95%。
4. 真实目标日行情到齐后回填实际收益并计算滚动指标。
5. 推理先一次性构造完整股票池输入，再按制品中的 `crossSectionBatchSize` 分块前向传播；该字段只对 MASTER 强制，既有 Ranking LSTM v5 制品保持可加载。
6. `TemporalModelEnsembleService` 只保留有限值、输出塌缩、扣成本收益和最大回撤硬门禁，Rank IC、收益和回撤统一进入综合评分。
7. 至少积累 10 个已评估交易日和 500 个有效股票样本；MASTER 综合评分领先 Ranking LSTM 至少 `0.05` 时自动启用融合。
8. MASTER 从 `0.10` 权重起步，按 `0.10 + 2 × 评分优势` 自动调权，默认最高不超过 `0.50`。
9. 只有生产融合开关、滚动门槛、特征版本和股票覆盖同时满足时，才写入 `ensembleRankScore`；任一条件不满足时保持 Ranking LSTM 原结果。

Transformer 输出仍然只是模型事实，不直接改变策略状态、仓位或真实交易权限。

### 5. 训练、候选、自动晋级和退役

`LstmTrainerService` 负责训练编排，算法结构、损失函数和指标位于 `algorithm`。计划训练先生成基础时序模型候选，自动评估完成后再串行生成 MASTER 候选，不需要人工激活。

生命周期分为：

| 阶段 | 含义 | 允许动作 |
|---|---|---|
| 训练中 | 正在读取快照并优化参数 | 记录进度，可安全中断 |
| 候选 `CANDIDATE` | 制品完整，等待综合评分或 MASTER 影子样本 | 自动评估、比较 |
| 激活 `ACTIVE` | 当前基础生产版本或已参与融合的 MASTER | 推理、滚动监控、自动调权 |
| 退役 `RETIRED` | 未晋级、被替换或越过硬门禁 | 保留元数据和审计证据 |

`LstmModelArtifactService` 将参数写入 GridFS，并把参数文件 ID、结构配置、输入输出契约、指标和状态写入模型元数据。`ModelPromotionPolicy` 使用 `0.45 × Rank IC 分 + 0.35 × 收益分 + 0.20 × 回撤分` 形成唯一综合评分；首个基础模型达到 `0.20` 自动晋级，后续候选至少领先生产模型 `0.05` 才自动切换。

`LstmModelRuntimeService` 和 `LstmInferenceService` 只从激活元数据与对应 GridFS 参数重建模型。参数文件缺失、模型结构不匹配或特征契约不一致时强制失败，不使用随机参数或旧文件代替。

模型运维前端允许在受控训练弹窗选择 `RANKING_LSTM`、`DUAL_ATTENTION_RANKING_LSTM` 或 `MASTER_TRANSFORMER`，版本列表展示实际架构。前端不再提供人工激活或普通回滚按钮，只展示自动评分、影子样本、当前权重和决策原因。

### 6. 预测批次持久化队列

**设计目标**：让全市场预测可追踪、可恢复、可重试，并避免并发 Worker 重复消费同一批次。

`PredictionExecutionQueueService` 的主要流程：

1. 预测请求先创建持久化批次并进入 `QUEUED`。
2. `PredictionBatchClaimService` 通过事务认领下一批任务并转为 `RUNNING`。
3. 单 JVM 使用运行标志限制同类 Worker 并发；持久化 Claim 负责批次级互斥。
4. 按固定股票批次执行推理，持续更新总数、已处理数、成功数、失败数和进度说明。
5. 完成后记录覆盖率和批次状态，并衔接评估队列。

应用重启后，遗留 `RUNNING` 批次恢复为 `QUEUED`。线程中断时当前批次重新排队。预测失败标记 `FAILED`；人工重试只接受失败批次，先删除残留预测明细并重置进度、评估状态和错误信息，再重新入队。

历史重算通过来源批次确定股票集合，来源批次没有有效明细时直接失败，不能悄悄改用当前全市场股票池。

### 7. 预测评估和交易资格门禁

`PredictionEvaluationQueueService` 将预测完成与真实行情到齐解耦。批次只有在以下条件满足后才能进入评估：

- 预测状态属于可评估状态。
- 存在待评估预测明细。
- 目标交易日已经到达。
- 目标日行情覆盖达到要求。

否则记录 `TARGET_DATE_NOT_REACHED`、`MARKET_DATA_COVERAGE_LOW` 或 `PENDING_MARKET_DATA` 等等待原因。行情同步完成后可以扫描并唤醒等待批次。

评估分别计算方向正确率、收益误差、排序能力、覆盖率和交易相关指标。`PredictionMetricsCalculator` 与 `RankingValidationMetricsCalculator` 负责纯计算，Service 负责数据读取和持久化。不同模型契约、数据范围和费用口径的结果不能直接横向比较。

`PredictionCandidateQueryService` 对外提供候选和交易资格判断。预测完成不等于允许交易；覆盖不足、评估未完成或门禁指标不合格时，`execution` 必须拒绝自动交易。

### 8. Sentiment 固定制品与推理

`SentimentArtifactService` 管理随 Backend 不可变镜像发布的情感模型工件，启动和首次使用时校验目录、必要文件和契约。固定制品不打入可执行 JAR，而是在 CI 从 Git LFS 下载并校验后写入镜像独立层；不允许缺失时自动创建空模型。

`SentimentInferenceService` 负责文本预处理、模型推理和结果标准化；`SentimentAggregationService` 按股票、事件和时间窗口聚合。推理失败、输入为空、制品缺失和输出结构异常分别记录，不能统一返回中性情感掩盖故障。

### 9. Mengzi 影子部署与路由

`SentimentShadowDeploymentService` 让候选情感模型先以影子方式运行：

1. 主模型继续提供生产结果。
2. 候选模型处理相同输入，但结果只用于对比和审计。
3. 累积覆盖、稳定性和质量指标。
4. 达到门槛后由受控操作切换路由状态为 `ACTIVE`。
5. 出现质量或运行问题时切换到 `ROLLBACK` 或 `BLOCKED`。

`SentimentModelRouterService` 读取持久化路由状态决定当前模型。影子运行不会自动扩大真实交易权限，模型切换也不能绕过决策与执行门禁。

### 10. MASTER 影子预测与时序集成

`MasterShadowPredictionService` 生成不影响生产候选的 MASTER 影子结果，用于评估截面关系和新模型价值。`TemporalModelEnsembleService` 基于历史评估结果组合多个模型输出，权重必须来自已完成的历史窗口，不能使用目标日未来数据。

影子模型和集成结果需要保存模型版本、来源批次、目标交易日、信息截止时间和权重版本。只有明确完成评估和激活流程的版本才能成为正式决策输入。

## 模型制品与存储

| 制品 | 存储 | 生命周期 |
|---|---|---|
| Sentiment 固定模型 | Backend 镜像 `/app/model-files/sentiment` | Git LFS 校验后随镜像发布 |
| Mengzi 等候选情感模型 | Backend 镜像内对应版本目录 | 影子推理、评估后人工切换 |
| LSTM 参数和训练快照 | MongoDB GridFS | 训练、候选、激活和回滚 |
| 模型元数据与运行记录 | MySQL/MongoDB | 版本、批次、审计和评估 |

固定制品不打入可执行 JAR，但必须与源码提交一起形成不可变容器镜像。生产服务器只拉取镜像，不通过 SCP 接收或挂载宿主机模型目录。旧制品与新制品按各自元数据和输入输出契约加载，不得修改旧元数据伪装兼容。

## 依赖与安全边界

- 只允许调用 `market` 的公开 Service 和 DTO，不直接访问市场 Repository 或 Entity。
- `decision` 通过模型 Service/DTO 消费预测和评估结果。
- 模型不得调用 `execution` 发起交易，也不得改变真实交易权限。
- 训练和预测任务由 `schedule` 触发，但业务算法保留在本模块 Service 中。
- 模型异常必须表现为失败、等待或不可用，不能通过默认预测结果降级为“正常”。

## 职责目录

```text
com/stock/model/
├── algorithm/    # DJL 模型、损失函数、指标和纯计算实现
├── config/       # 模型、训练、队列和影子能力配置
├── controller/   # 模型运维与预测接口
├── dto/          # 请求、响应和内部结果契约
├── entity/       # 模型、批次、快照和评估持久化对象
├── repository/   # 模型生命周期数据访问
├── service/      # 训练、推理、评估、激活和制品编排
└── vo/           # 模型内部值对象
```

## 维护规则

- 算法纯计算放入 `algorithm`，流程、事务和外部存储编排放入 `service`。
- 训练、制品写入、激活和生产生效是不同状态，不得合并表述。
- 新模型必须定义输入输出、特征、标签、切分、成本和制品版本。
- 修改模型 DTO 或批次字段时，检查决策、调度、平台内部事实 API 和前端调用方。

## 专项设计：RankingLSTM-v5

- 状态：`SOURCE_IMPLEMENTED`
- 最后静态核对日期：2026-10-23
- 运行证据：无；尚未完成 Maven 编译、DJL 运行、训练、GridFS 写入和实际预测评估

### 目标与边界

本专项定义横截面 Ranking LSTM 与双注意力 Ranking LSTM 的数据、标签、时间切分、模型结构、联合损失、制品契约和候选门禁。实现保持 Java 17、DJL API 与 PyTorch Engine，不引入 Python、ONNX 或独立模型服务。

本方案不实现对抗训练、市场状态多专家路由、自动真实交易或模型自动激活。历史模型方向 AUC 接近随机、Rank IC 偏弱、概率分布狭窄、多任务输出冲突和横截面方差过低等证据只用于提出优化方向，不代表新架构已改善指标。

### 候选架构

| 架构标识 | 设计 |
|---|---|
| `RANKING_LSTM` | 使用 LSTM 编码时序特征，以横截面排序为主任务 |
| `DUAL_ATTENTION_RANKING_LSTM` | 增加输入特征注意力和时间注意力 |

同一训练快照分别训练两个候选。注意力模型只有在多个测试窗口稳定优于基础模型时才能成为优先候选，复杂度本身不能作为激活理由。

### 数据与标签契约

正式训练使用 `model_stock_daily_bars`、`model_reference_daily_bars`、`stock_adjustment_factors`、`historical_stock_universe` 和 `stock_daily_tradability_facts`。`stock_prices` 只服务旧模型和既有行情业务，不作为静默回退。关键数据源不满足训练、验证和测试窗口要求时明确失败，不生成候选。

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

输出固定为 `rankScore`、`expectedExcessReturn`、`upProbability`、`downsideQuantile`。`rankScore` 不限制区间，预期超额收益使用 `tanh`，上涨概率使用 `sigmoid`，下行分位使用非负激活。类别索引不进入 LSTM，基础版本将时序表示与类别 Embedding 拼接，双注意力版本增加特征和时间注意力。

```text
0.45 * PairwiseRankingLoss
+ 0.25 * HuberExcessReturnLoss
+ 0.15 * CostAwareDirectionLoss
+ 0.10 * DownsideQuantileLoss
+ 0.05 * OutputConsistencyLoss
```

训练 Batch 只包含同一目标交易日样本；收益差不超过成本阈值的股票对不参与排序损失；中性样本降低方向损失权重；预测收益与上涨概率冲突时增加一致性惩罚。验证阶段检测全正、全负或近似常量的输出塌缩，最佳 Epoch 由 Rank IC、成本后 Top 10% 收益和归一化损失组合选择。

### 制品兼容与候选 Gate

制品保存架构、输入输出、特征、标签、切分和成本版本，类别字典、标准化统计、Walk-Forward 指标、概率校准参数和注意力参数。运行时按 `architectureType` 与 `outputSchemaVersion` 创建模型；旧三输出与新四输出制品由各自模型加载，不修改旧元数据伪装兼容。

候选评估只保留制品和契约完整、样本数量有效、数值有限、输出未塌缩、扣成本收益为正和最大回撤不超过 20% 的硬门禁。Rank IC、扣成本收益和最大回撤进入统一综合评分，不再为 ICIR、正窗口比例、Brier Score 和严格隔离指标设置独立一票否决。

这些数值是待验证门禁。完成源码编译后状态可更新为 `COMPILED`；完成训练、制品读写、预测和影子评估后更新为 `RUNTIME_VERIFIED` 或 `SUPERSEDED`。

## 专项设计：MasterRankingLstmEnsemble-v1

- 状态：`SOURCE_IMPLEMENTED`
- 最后静态核对日期：2026-10-24
- 运行证据：无；MASTER 训练、影子预测和融合收益均未验证

### 架构与边界

Ranking LSTM 保持生产基线，MASTER 使用相同标签、成本模型和四输出契约训练和影子预测。两个模型的横截面输出统一转换为 `[0,1]` Rank Score，再由滚动指标驱动权重融合。MASTER 不是强制替换，任一模型长期无增益时权重允许降为 `0`。

`MASTER_TRANSFORMER` 使用时序特征投影与时间注意力提取单股表示，在同一交易日 Batch 内使用横截面自注意力建模股票关系，并通过市场状态门控控制横截面贡献。输出仍为 `rankScore`、`expectedExcessReturn`、`upProbability` 和 `downsideQuantile`。这是适配 DJL 四任务框架的 MASTER-inspired 架构，不宣称逐层复刻论文。

### 新闻特征版本

事件事实按股票和交易日生成 `temporal-news-v1` 快照，只读取选股截止时点之前发布的 `TRADE_SIGNAL`。固定 25 维依次包含三类概率、连续情感分数、校准置信度、归一化熵、概率间隔、弃权率、事件覆盖、影响幅度、重要性、新颖度、个股相关度、时间衰减、事件年龄、事件数量、来源质量、七类事件占比和缺失状态。

`news_feature_snapshots` 以股票、交易日和特征版本唯一，训练和推理批量读取快照，不在样本循环内查询 MongoDB 或重新执行情感模型。任一必需股票交易日缺少完整 25 维快照时直接阻断。Ranking LSTM 与 MASTER 的新契约为 `cross-sectional-temporal-news-v6`：28 个价格/技术/横截面连续特征加 25 个新闻特征，再加 3 个类别索引，共 56 维。旧 31 维制品禁止兼容加载，两个模型必须使用同一快照版本重新训练后才能融合。

### 影子与动态融合

- MASTER 写入独立影子预测集合，不替换生产批次。
- 未晋级时权重固定为 Ranking LSTM `1.0`、MASTER `0`。
- 动态权重依据 Rank IC、扣成本收益和最大回撤综合评分计算。
- 滚动窗口至少包含 10 个交易日和 500 个有效样本，MASTER 还必须满足 95% 覆盖率。
- MASTER 权重上限默认是 `0.50`，即使相对质量更高也不能越过该边界。
- 影子和融合配置默认开启，没有 MASTER 候选时任务安静跳过。
- 同日结果缺失或版本不匹配时回到 Ranking LSTM 原结果。
- 生产融合开关、滚动状态、股票覆盖率和特征版本全部通过时才写入 `ensembleRankScore`。

两个模型使用新闻事件特征后，不再额外叠加普通 `newsImpactScore`；重大风险事件继续走独立 Java 门禁。默认任务 `masterShadowPrediction` 在工作日 `06:20` 执行，位于生产预测之后、选股之前，任务和影子配置默认开启。

当前状态仍为 `SOURCE_IMPLEMENTED`。源码只证明训练/制品/运行时工厂接入、横截面输入构造、按制品块大小推理、影子预测、后验回填、滚动指标、双模型门禁、动态权重、候选融合、前端控制和新闻特征契约基础设施存在，不代表 DJL 初始化、训练、制品保存加载、权重学习或扣成本收益通过。

## 专项设计：MengziShadowDeployment-v1

- 状态：`SOURCE_IMPLEMENTED`
- 最后静态核对日期：2026-10-24
- 运行证据：无；第一阶段数据 Gate、模型推理和影子评估均未执行

### 目标与资源边界

Mengzi 与 FinBERT 读取同一批 `stock_news_event_facts`，并通过 `SentimentInputAssembler` 使用相同的标题优先、关键句和最多三块输入。FinBERT 保持生产基线，Mengzi 只写影子预测；通过门槛后状态仅进入 `ELIGIBLE`，必须人工确认才可激活。运行故障允许自动回滚 FinBERT，Java 继续负责事实筛选、版本追踪、风险门禁和最终交易执行。

官方 `Langboat/mengzi-bert-base-fin` 是 `BertForMaskedLM` 基础编码器，不是三分类情感模型。基础制品使用 `encoder-artifact-v1` 独立保存；候选分类器必须采用冻结编码器加 `CLS 768 -> Linear 256 -> GELU -> Dropout 0.1 -> Linear 3`，并在 `sentiment-artifact-v1` 中记录 `FINE_TUNED_CLASSIFIER`、基础 revision、金标样本数、标签映射和校准参数。没有合格人工金标时，只允许校验编码器和开发训练原型，禁止配置为生产或影子分类制品。

FinBERT 当前缺陷包括上游约八千条分析师报告句子带来的领域偏差、私有评估集不可复核、负类召回率和 ECE 缺失、128 token 长度较短以及最大 Softmax 概率未校准。Mengzi 的主要风险是仅有金融编码器而没有本项目三分类头和金标质量证据。两者都必须使用人工金标评估集报告 Macro-F1、各类 Precision/Recall、ECE、弃权覆盖率、否定/反转和长文本分组结果；弱标注只能用于预训练或样本扩充，不能替代金标验收。

### 成对结果契约

输入仅包含 `TRADE_SIGNAL` 事件事实，使用 `canonicalEventId` 幂等。输出统一保存三类概率、原始和校准置信度、熵、概率间隔、弃权状态、分块数量和文本覆盖率。`RISK_ONLY` 不进入 Alpha 新闻特征，也不由 `TRADE_SIGNAL` 情感分数代理，继续由独立 Java 风险门禁消费。

### 影子候选与回滚门槛

- 影子期不少于 20 个交易日。
- 有效评估样本不少于配置阈值。
- 按股票和目标交易日聚合事件，以目标日开盘到下一交易日开盘收益评估。
- Rank IC 必须按每日横截面计算后再跨日平均，不能把所有事件混在一个 Spearman 样本中。
- Rank IC、方向准确率和扣成本 Top 10% 收益不低于 FinBERT 同事件基线。
- 推理失败率、输出稳定性、非弃权覆盖率和长文本覆盖率达到门槛。

任一门槛未满足时保持 `SHADOW`；全部通过后进入 `ELIGIBLE`，不能自动激活。人工确认激活后进入回滚窗口，运行故障或滚动指标跌破门槛时恢复 FinBERT。状态迁移只改变情感模型路由，不绕过交易权限、资金、持仓和券商执行。

影子任务通过 `JobConfig + JobSchedulerService` 注册，默认任务名 `sentimentMengziShadow`，默认 Cron 为工作日 `01:00`。`MENGZI_SHADOW_ENABLED` 与任务状态必须同时开启，单批按配置数量幂等处理，且禁止在交易时段执行大批量推理。

当前源码和本地制品最多证明统一输入输出、官方编码器校验、三分类头结构、日级快照契约、后验评估、`ELIGIBLE` 门槛和自动回滚路径存在。没有人工金标训练、校准和完整影子期证据前，交付状态最高为“编码器制品完整、分类器待训练”，不能描述为 Mengzi 预测效果通过。
<!-- AI_GENERATE_END ---------- -->
