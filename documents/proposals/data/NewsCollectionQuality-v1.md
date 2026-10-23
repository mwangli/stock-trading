<!-- AI_GENERATE_START - -->
# NewsCollectionQuality-v1

- 状态：`PROPOSED`
- 领域：`stock-backend/com.stock.data`
- 最后静态核对日期：2026-10-23
- 运行证据：已完成只读数据库抽样；尚未执行新管线回填和质量 Gate

## 目标与非目标

本 Proposal 将新闻质量治理前移到采集层。证券数据源返回的每条文档必须在持久化阶段完成原始内容留存、规范化、文档类型识别、业务角色识别、内容去重和事件事实同步。情感模型、选股和后续时序模型只消费版本化事件事实，不再各自清洗原始新闻。

本方案不删除原始文档，不把证券平台菜单编号直接解释为真实文档类型，不在第一阶段引入 Mengzi、MASTER 或自动模型激活，也不承诺清洗一定改善收益。模型升级必须等待本 Proposal 的数据 Gate 取得运行证据。

## 当前证据

2026-10-23 对 `stock_news` 进行只读抽样时，集合约有 51.09 万条记录，其中约 97.67% 缺少完整事件元数据。来源高度集中，栏目“新闻”中大量记录实为交易所公告，栏目“公告”中又包含评级和行业研究内容。2,400 条标题样本的规范化重复率约为 16.08%。

历史 `stockRanking` 样本中的旧 `sentimentScore` 均值约为 0.99812，标准差约为 0.000861，不能证明新闻因子提供了有效横截面区分。当前源码虽然已经对 `actionable` 结果做过滤，但新逻辑尚无回填、训练或实际选股评估证据。

## 采集链路

```text
证券数据源
  -> 原始响应解析
  -> 标题和正文规范化
  -> 文档类型识别
  -> 业务角色识别
  -> 内容摘要与规范事件标识生成
  -> stock_news 原始文档写入
  -> stock_news_event_facts 幂等聚合
```

采集职责拆分如下：

- `StockNewsDocumentFactory` 只负责请求详情并构造原始文档。
- `NewsDocumentNormalizer` 负责 HTML、URL、重复股票前缀、页眉页脚、免责声明和空白规范化。
- `NewsDocumentClassifier` 根据来源、标题和正文推导文档类型，不依赖菜单显示名称。
- `NewsDocumentRoleResolver` 将文档划分为 `TRADE_SIGNAL`、`RISK_ONLY`、`CONTEXT_ONLY` 或 `IGNORE`。
- `NewsCanonicalEventResolver` 生成规范化内容摘要和跨来源事件标识。
- `NewsCollectionPipelineService` 统一编排规则并写回治理字段。
- `NewsEventFactService` 将非 `IGNORE` 文档幂等聚合到事件事实集合。

## 原始文档契约

`stock_news` 保留原有 `title` 和 `content`，同时增加：

| 字段 | 含义 |
|---|---|
| `rawMenuId` | 供应商菜单编号，仅用于来源追踪 |
| `rawTitle` / `rawContent` | 供应商原始标题和正文 |
| `normalizedTitle` / `normalizedContent` | 采集层规范化结果 |
| `sourceChannel` | 原始渠道名称，不承担业务类型语义 |
| `documentType` | 真实文档类型 |
| `documentRole` | 下游业务角色 |
| `contentHash` | 规范化标题和正文摘要 |
| `canonicalEventId` | 规范事件标识 |
| `noveltyScore` | 新颖度，范围 `[0,1]` |
| `materialityScore` | 重要性，范围 `[0,1]` |
| `stockSpecificity` | 股票相关度，范围 `[0,1]` |
| `pipelineVersion` | 采集治理规则版本 |
| `qualityStatus` / `qualityReason` | 质量状态和原因 |

菜单编号只写入 `rawMenuId`。既有 `category` 作为兼容展示字段保留，不再作为公告半衰期、模型权重或真实文档类型的依据。

## 文档类型与业务角色

文档类型固定为：

- `OFFICIAL_DISCLOSURE`
- `MEDIA_NEWS`
- `RESEARCH_REPORT`
- `RATING_CHANGE`
- `INVESTOR_QA`
- `UNKNOWN`

业务角色固定为：

- `TRADE_SIGNAL`：可进入情感推理和模型特征。
- `RISK_ONLY`：进入 Java 风险门禁，不与正向 Alpha 抵消。
- `CONTEXT_ONLY`：保留研究和审计，不参与选股评分。
- `IGNORE`：空内容、模板噪声或无法形成有效语义的文档，不生成事件事实。

董事会和监事会例行决议、制度修订、独立董事声明等默认属于 `CONTEXT_ONLY`。只有包含明确增减持、回购、控制权变化、重大合同、业绩变化、监管处罚或诉讼事实时，才能提升为可交易角色。

## 事件事实契约

`stock_news_event_facts` 以 `(stockCode, canonicalEventId)` 唯一。每个事实记录规范化文本、文档类型、业务角色、事件类型、发布时间、来源集合、外部文档标识集合、重要性和采集管线版本。

重复文档只通过集合字段补充来源和外部标识，不重复形成情感权重。相同 `canonicalEventId` 可以关联不同股票，但每只股票保留独立事实，以便表达不同的股票相关度和风险作用。

下游选股和情感训练只查询 `TRADE_SIGNAL` 与 `RISK_ONLY` 事件事实。没有事件事实与存在中性事件必须分别表达，不能统一编码为固定的 `0.5` 新闻分数。

## 历史回填

历史回填必须复用 `NewsCollectionPipelineService`，通过 `_id` 游标顺序处理并持久化 `pipelineVersion`、最后游标、扫描数、更新数、事实同步数、忽略数和失败数。只有一批原始文档和对应事件事实都持久化成功后才能推进游标。

回填任务通过 `JobConfig + JobSchedulerService` 注册，默认暂停，防止未完成容量验证时在 2C4G 节点自动扫描约 51 万条文档。任务启用后可以中断和续跑，不新增 `@Scheduled`。

## 数据 Gate

进入 Mengzi Proposal 前必须取得以下运行证据：

- 新采集数据治理字段完整率不低于 99.5%。
- 固定标注集文档类型准确率不低于 90%。
- 重大风险事件召回率不低于 95%，误报率不高于 5%。
- 规范化后的未合并重复事件率不高于 5%。
- 模型输入可以追溯到原始文档、事件事实和 `pipelineVersion`。
- 点时查询不会读取选股截止时刻之后发布或入库的事实。
- 完成无新闻、原始新闻和事件事实三组 Walk-Forward 消融比较。
- 事件事实方案在多数测试窗口产生正向 Rank IC，且扣成本 Top 10% 收益不低于无新闻基线。

若收益类 Gate 未通过，新闻排名权重必须保持为 `0`；风险事件门禁可以在风险召回和误报 Gate 独立通过后继续使用。

## 影响边界

- 数据域负责采集治理、原始文档和事件事实。
- 模型域只消费已治理事实，不维护第二套文本清洗规则。
- 策略域只消费点时有效的事实聚合和风险门禁结果。
- Java 交易域继续负责权限、资金、持仓、幂等、风控和券商执行。

## 验收与退出条件

源码验收覆盖规范化、文档分类、角色分类、规范事件标识、重复事实合并、回填游标、任务注册和下游事实读取。源码实现完成后状态更新为 `SOURCE_IMPLEMENTED`，但不得表述为数据 Gate 已通过。

完成数据库回填、固定标注集评估和三组消融实验后，状态才能更新为 `RUNTIME_VERIFIED`。只有此时才允许新增 `MengziShadowDeployment-v1` Proposal；否则继续迭代采集规则或将本方案标记为 `SUPERSEDED`。
<!-- AI_GENERATE_END - -->
