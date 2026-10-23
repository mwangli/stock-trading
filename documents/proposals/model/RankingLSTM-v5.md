<!-- AI_GENERATE_START - -->
# RankingLSTM-v5

- 状态：`SOURCE_IMPLEMENTED`
- 领域：`stock-backend/com.stock.model`
- 最后静态核对日期：2026-10-23
- 运行证据：无

## 目标与非目标

本 Proposal 定义横截面 Ranking LSTM 与双注意力 Ranking LSTM 的数据、标签、时间切分、模型结构、联合损失、制品契约和候选门禁。实现保持 Java 17、DJL API 与 PyTorch Engine，不引入 Python、ONNX 或独立模型服务。

本方案不实现对抗训练、市场状态多专家路由、自动真实交易或模型自动激活。通用模型生命周期见[模型领域](../../backend/模型领域.md)。

## 当前证据

历史评估记录显示，既有激活模型存在方向 AUC 接近随机、Rank IC 偏弱、概率分布狭窄、多任务输出冲突和横截面方差过低等问题。该证据用于提出优化方向，不代表新架构已经改善指标。

当前工作区源码包含 Ranking 数据管线、模型结构、联合损失、评估指标、制品元数据和兼容加载相关实现，但尚未执行 Maven 编译、DJL 运行、模型训练、MongoDB/GridFS 写入或实际预测评估。

## 候选架构

| 架构标识 | 设计 |
|---|---|
| `RANKING_LSTM` | 使用 LSTM 编码时序特征，以横截面排序为主任务 |
| `DUAL_ATTENTION_RANKING_LSTM` | 在 Ranking LSTM 基础上增加输入特征注意力和时间注意力 |

同一训练快照分别训练两个候选。注意力模型只有在多个测试窗口稳定优于基础 Ranking LSTM 时才能成为优先候选，模型复杂度本身不能作为激活理由。

## 数据与标签契约

正式训练使用版本化模型数据：

- `model_stock_daily_bars`
- `model_reference_daily_bars`
- `stock_adjustment_factors`
- `historical_stock_universe`
- `stock_daily_tradability_facts`

`stock_prices` 只服务旧模型和既有行情业务，不作为本方案的静默回退。任一关键数据源不满足训练、验证和测试窗口要求时，训练记录明确失败，不生成候选。

样本主键为 `(targetTradeDate, stockCode)`，特征只使用截止日 `t` 及以前的数据：

```text
目标交易日 = t + 1
建仓价格 = open(t + 1)
平仓价格 = open(t + 2)
rawReturn = open(t + 2) / open(t + 1) - 1
excessReturn = rawReturn - benchmarkReturn
neutralReturn = excessReturn - industryMedianExcessReturn
```

方向标签使用包含双边佣金、卖出印花税和滑点的成本中性区间。排序标签使用未缩放 `neutralReturn`，回归标签按快照中的收益缩放参数转换，下行风险标签表达中性收益的下行幅度。

连续特征覆盖复权量价、多周期收益和波动率、成交活跃度、技术指标、相对基准、行业相对收益、市场宽度、基准趋势、流动性与可交易状态。股票、行业和市场类别使用快照内稳定字典，索引 `0` 固定为 `UNKNOWN`。

标准化统计、缺失值策略和类别字典只能由训练窗口拟合并随制品保存，推理不得使用最新全量数据重新拟合。

## 时间切分

所有股票使用全市场统一日期边界：

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

快照记录日期边界、交易日列表和各集合样本数。60 日隔离只用于敏感性评估，不替代主 Walk-Forward 配置。

## 模型结构与输出

输入包括连续时序矩阵及股票、行业和市场类别索引，类别索引不进入 LSTM。输出固定为：

```text
0 rankScore
1 expectedExcessReturn
2 upProbability
3 downsideQuantile
```

`rankScore` 不限制区间；预期超额收益使用 `tanh` 约束缩放结果；上涨概率使用 `sigmoid`；下行分位使用非负激活。

基础版本将 LSTM 最终时序表示与类别 Embedding 拼接后进入共享投影层和四个 Head。双注意力版本依次增加特征注意力和时间注意力。注意力权重只用于诊断，不直接改变 Java 风控、仓位或交易执行。

## 联合损失与候选选择

默认设计权重：

```text
0.45 * PairwiseRankingLoss
+ 0.25 * HuberExcessReturnLoss
+ 0.15 * CostAwareDirectionLoss
+ 0.10 * DownsideQuantileLoss
+ 0.05 * OutputConsistencyLoss
```

训练 Batch 只包含同一目标交易日样本；收益差不超过成本阈值的股票对不参与排序损失；中性样本降低方向损失权重；预测收益与上涨概率冲突时增加一致性惩罚。

验证阶段记录输出均值、标准差、正负比例和唯一值数量。全正、全负或近似常量输出标记为塌缩候选。最佳 Epoch 使用验证集 Rank IC、成本后 Top 10% 收益和归一化损失组合选择，而不是只比较训练损失。

## 制品与兼容

制品元数据保存架构、输入输出、特征、标签、切分和成本版本，训练、验证及测试窗口，类别字典、标准化统计、Walk-Forward 指标、概率校准参数和注意力参数。

运行时按 `architectureType` 与 `outputSchemaVersion` 创建模型。旧三输出制品继续由旧模型加载，四输出制品由 Ranking 模型加载，不能修改旧元数据伪装兼容。

预测明细增加排序、横截面百分位、预期超额收益、校准概率、下行分位、架构和特征版本字段；旧字段继续保留用于历史批次查询。

## 候选门禁

进入影子预测的设计门槛：

- Walk-Forward 平均 Rank IC 大于 `0.02`。
- Rank ICIR 大于 `0.30`。
- 至少 60% 测试窗口 Rank IC 为正。
- Top 10% 组合扣除成本后超额收益为正。
- Brier Score 不差于实际上涨比例基线。
- 不存在输出塌缩。
- 最大回撤不超过基准组合的 1.2 倍。
- 至少一个关键指标优于当前激活模型。

这些数值是待验证门禁，不是当前已达到的结果。

## 发布、回滚与验收

候选通过离线门禁后进入不少于 20 个交易日的影子预测，只写预测和评估记录。人工激活后，权限、资金、持仓、风险、幂等和券商执行仍由 Java 交易领域负责。

出现连续表现恶化、输出再次塌缩或特征契约不兼容时，停止使用候选并回滚上一激活版本。

源码验收需要覆盖全市场日期切分、训练窗口统计契约、成本中性标签、同日 Batch、联合损失、注意力输出、四输出制品、旧模型兼容和塌缩检测。

## 未验证事项与退出条件

尚未验证：

- Maven 编译与 DJL API 兼容性。
- 模型初始化、训练、保存和加载。
- MongoDB 与 GridFS 读写。
- Walk-Forward 指标和影子预测效果。
- 候选激活、回滚和历史批次兼容。

完成源码编译后状态可更新为 `COMPILED`；完成训练、制品读写、预测和影子评估后，稳定事实归并到[模型领域](../../backend/模型领域.md)，Proposal 状态更新为 `RUNTIME_VERIFIED` 或 `SUPERSEDED`。
<!-- AI_GENERATE_END - -->