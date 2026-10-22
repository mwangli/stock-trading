<!-- AI_GENERATE_START --- -->
# stock-trading4 金融 Agent 总体设计方案

## 1. 文档信息

| 项目 | 内容 |
|---|---|
| 文档状态 | 评审修订稿 |
| 版本 | 1.1 |
| 日期 | 2026-10-22 |
| 适用项目 | stock-trading4 |
| 技术基线 | Java 17、Spring Boot 3.2.x、LangChain4j 1.22.0、React 19 |
| 设计目标 | 在现有行情、模型、策略、交易和任务调度能力上构建受限自治的金融 Agent |
| 验证边界 | 本文为源码级设计，尚未进行编译、模型调用、数据库、浏览器、券商或真实交易验证 |

## 2. 执行摘要

现有系统已经具备较完整的确定性业务能力，包括股票与行情数据、交易日历、新闻与情感分析、LSTM 预测、模型运维、策略配置、候选筛选、回测、账户与订单查询、交易记录、收益报表、任务调度、风险控制和真实交易执行。

当前 Agent 的主要问题不是模型本身缺少能力，而是 Agent 入口、工具暴露和证据装配方式没有形成完整闭环：

1. 前端将大部分问题默认提交为 Research 任务。
2. Research 固定加载交易日历、策略、候选、交易门禁和 Provider 等证据，普通聊天和无关事实查询受到交易门禁污染。
3. 项目虽然已有多个 `@Tool`，但模型调用链没有形成按会话动态选择工具、受控多步调用、结果观察和继续规划的完整 Agent 循环。
4. 已有领域 Service 没有系统化映射为工具能力，导致“项目中有数据，但 Agent 无法访问”。
5. Research、Diagnostic、Chat、事实查询和交易计划被混合为任务入口概念，用户体验和安全边界都不清晰。

本方案建议建设一个单入口、分层治理、受限自治的金融 Agent：

- 用户统一通过 `/api/agent/chat` 交互。
- Conversation Orchestrator 维护会话、任务和调用预算。
- Capability Router 根据用户权限、问题上下文和风险等级计算能力范围，并通过 LangChain4j ToolProvider 提供最小必要工具集合。
- LLM Agent 可以在获准的只读和分析工具中自主进行多步规划。
- Tool Policy Gateway 在每次工具调用前执行权限、范围、参数、超时、频率、审计和敏感数据校验。
- Java 领域 Service 继续作为行情、模型、策略、账户和任务事实的唯一权威来源。
- 真实交易写入不作为模型可直接调用的工具；Agent 只能生成 TradePlan 草案，之后由用户确认和 Java 风控重新复核。

推荐目标自治等级为“L2.5 受限自治”：允许自主查询和分析，允许生成受控操作建议，禁止自主完成高风险变更和真实交易写入。

### 2.1 评审结论与实施前置条件

本方案总体方向可行，但只有在以下 P0 条件完成后，才允许进入多工具自治和受控操作阶段：

1. 会话、任务、证据、Memory 和动作必须绑定认证用户，所有查询均校验资源归属，不接收前端提供的用户范围作为权限依据。
2. 同一会话的消息提交必须有顺序控制；Provider 降级使用独立尝试上下文，失败尝试不得污染正式 ChatMemory。
3. 工具权限、用户范围和动作载荷必须由 Java 生成和校验，模型输出不能直接成为权限、参数或执行依据。
4. 待确认动作必须保存类型化不可变载荷、载荷哈希、策略版本和资源版本，确认后仍需重新鉴权和校验当前状态。
5. 数值、日期、状态和金融结论必须经过生成后证据一致性校验，不能仅依赖提示词约束模型。
6. 首期只建设最小只读闭环，不并行建设完整 75 个工具、R3/R4 操作和管理看板。

## 3. 设计依据

### 3.1 项目约束

1. 模型训练和推理统一由 Java 后端承担，不引入 Python、ONNX 或独立 Agent 服务。
2. 新增 Agent 能力优先使用 LangChain4j `AiServices + @Tool`。
3. 工具必须调用领域 Service，禁止直接访问 Controller、Repository、SQL 或券商适配器。
4. 真实交易必须经过权限、风控、资金与持仓检查、交易时段、幂等和执行隔离。
5. Agent、RAG、Memory 或 Provider 故障不能阻断现有确定性 Java 交易流程。
6. 对外接口使用独立 DTO 和统一 `ResponseDTO<T>`，禁止使用原始 `Map`、`Object` 或自由格式 JSON。
7. 工具输出必须包含数据时间、统计口径、来源状态和必要的不确定性信息。

### 3.2 外部治理原则

FCA 2026 年关于金融服务 AI 的实践材料强调风险分级、明确责任、Agent 清单、持续监控、升级路径、人工干预和全生命周期治理，并明确指出 Agentic AI 需要额外的治理层。NIST AI RMF 强调 Govern、Map、Measure、Manage 四类活动，以及角色责任、风险容忍度、人工监督、文档化和持续评估。

LangChain4j 官方 Tools 和 AiServices 文档支持通过 `@Tool` 描述工具，并在 `AiServices` 中绑定工具；较多工具场景应采用按任务筛选或动态工具发现，避免一次暴露全部能力。

参考资料：

- FCA: https://www.fca.org.uk/publications/good-poor-practice/ai-financial-services
- NIST AI RMF: https://www.nist.gov/itl/ai-risk-management-framework
- NIST Generative AI Profile: https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf
- LangChain4j Tools: https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/tools.md
- LangChain4j AI Services: https://github.com/langchain4j/langchain4j/blob/main/docs/docs/tutorials/ai-services.md

## 4. 目标与非目标

### 4.1 目标

| 编号 | 目标 | 可验证结果 |
|---|---|---|
| G-01 | 支持普通连续对话 | 闲聊不加载交易门禁，不要求业务证据 |
| G-02 | 支持项目事实查询 | 行情、模型、策略、账户、收益和任务数据可通过工具访问 |
| G-03 | 支持多步研究 | Agent 可根据中间工具结果继续查询，直至形成有证据的结论 |
| G-04 | 支持运行诊断 | 能解释候选为空、任务失败、模型不可用和交易门禁阻断原因 |
| G-05 | 支持受控操作 | 数据同步、任务触发和模型训练等操作必须经过权限和确认 |
| G-06 | 隔离真实交易 | Agent 只能生成计划，不能直接下单或持有券商凭据 |
| G-07 | 可审计 | 每次模型调用、工具调用、证据引用、用户确认和执行结果可追踪 |
| G-08 | 可降级 | Provider、RAG、Memory 或单个工具失败时返回明确原因，不影响确定性交易流程 |
| G-09 | 可评估 | 建立工具选择、事实一致性、风险拦截、延迟和成本指标 |

### 4.2 非目标

1. Agent 不负责提高 LSTM 或情感模型的预测准确率。
2. Agent 不替代 RiskController、TradeExecutor 或券商适配器。
3. Agent 不直接修改数据库、执行 SQL 或访问 Repository。
4. Agent 不自动激活模型、不自动删除预测批次、不自动修改系统运行配置。
5. Agent 不承诺收益，不把模拟、回测或候选结论描述为实际交易结果。
6. 第一阶段不建设多 Agent 协作网络，避免引入额外状态、成本和不可解释性。

## 5. 现状能力盘点

### 5.1 已有 Agent 基础

| 能力 | 当前实现 | 现状评价 |
|---|---|---|
| 会话与任务 | AgentSessionEntity、AgentTaskEntity | 可复用，必须补充资源归属校验、轮次顺序和并发控制 |
| 模型 Provider | DeepSeek、Ollama、自动降级、熔断 | 可复用并补充工具循环 |
| Chat Memory | AgentChatMemoryService | 需改造，当前按 sessionId 共享内存，缺少持久化、并发顺序和 Provider 尝试隔离 |
| RAG | AgentRagService、Embedding 运行状态 | 可复用，作为按需工具而非每次固定检索 |
| Memory | AgentMemoryService | 可复用，仅加载用户确认和系统事实 |
| 工具支持 | AgentToolSupport、AgentToolContextHolder | AgentToolSupport 可复用；ThreadLocal 上下文仅作为过渡方案，目标改为显式调用上下文 |
| 工具审计 | AgentToolAuditService、AgentToolCallEntity | 可复用，需补充参数摘要、结果摘要和风险等级 |
| 证据装配 | EvidenceAssembler | 需拆分，当前固定装配导致无关门禁污染 |
| 结构化输出 | Research、Diagnostic、Report、TradePlan | 可复用，需增加统一 ChatAnswer 契约 |

### 5.2 已有业务事实来源

| 领域 | 主要 Service | 可提供能力 |
|---|---|---|
| 股票与行情 | StockDataService | 股票查询、历史价格、最新价格、市场统计、涨幅榜 |
| 交易日历 | TradingCalendarService | 交易日确认、区间交易日、前后交易日 |
| 新闻 | StockNewsService | 新闻采集和样本；查询能力需要补充明确的只读 Service 方法 |
| 情感模型 | SentimentInferenceService、SentimentAggregationService | 文本情感、新闻聚合情感、模型健康 |
| LSTM | LstmInferenceService | 单股和批量预测、模型可用性 |
| 模型运维 | ModelOperationsService | 概览、版本、训练运行、训练、停止、激活和回滚 |
| 预测评估 | ModelPredictionAnalysisService | 批次、明细、能力评估、重算和评估 |
| 策略 | StrategyConfigService、StrategyStateManager、StockSelector | 策略配置、模式、状态、排名和候选 |
| 回测 | BacktestDataAuditService、BacktestExecutionService | 数据审计、基准回测和结果查询 |
| 券商只读 | BrokerReadService | 状态、账户、订单和成交 |
| 交易记录 | BrokerTransactionQueryService | 历史订单和 FIFO 交易记录 |
| 收益报表 | PerformanceReportService | 资产、累计收益、当日收益、月度收益和胜率 |
| 真实交易 | RealTradingService | 状态、候选、自动买入和自动退出 |
| 调度任务 | JobManagementService | 任务、运行记录、触发、停止、暂停和恢复 |
| 运行配置 | SystemRuntimeConfigService | 调度和启动同步开关 |
| 数据同步 | BrokerDataSyncService、StockDataService | 券商事实同步、历史回填、股票基础数据同步 |

### 5.3 当前问题定位

| 问题 | 根因 | 影响 |
|---|---|---|
| 普通聊天返回交易门禁 | Research 是默认入口，EvidenceAssembler 无条件加入交易门禁 | 对话不自然，用户误认为 Agent 不可用 |
| 项目有数据但无法回答 | 领域 Service 未完整映射为工具 | 收益、交易记录、模型版本等事实不可见 |
| 工具不能自主调用 | 工具主要由 Java 预执行，未形成受控工具循环 | 不支持根据中间结果继续调查 |
| 证据内容过多 | 每次固定加载候选、策略、门禁和 Provider | Token 成本、延迟和回答偏移增加 |
| Research 概念过载 | Chat、事实查询、分析和研究共用 Research | 输出契约与用户意图不匹配 |
| 写操作边界不完整 | 尚无统一工具风险分级和确认协议 | 扩展工具时可能绕过权限或产生副作用 |

## 6. 目标自治等级

### 6.1 自治等级定义

| 等级 | 能力 | 本项目策略 |
|---|---|---|
| L0 | 纯对话，无工具 | 支持 |
| L1 | 单步只读事实查询 | 支持 |
| L2 | 多步只读查询和分析 | 支持 |
| L2.5 | 生成受控操作建议和待确认请求 | 目标等级 |
| L3 | 经策略批准后执行一般运维操作 | 仅部分任务调度能力可选 |
| L4 | 自主执行真实金融交易 | 禁止 |

### 6.2 模型与 Java 的职责边界

| 职责 | LLM Agent | Java 确定性组件 |
|---|---|---|
| 理解自然语言问题 | 主责 | 提供基础规则和兜底分类 |
| 制定查询步骤 | 在获准工具范围内负责 | 限制最大步骤和可用工具 |
| 选择只读工具 | 允许 | Capability Router 先缩小集合 |
| 获取真实业务数据 | 不直接访问数据源 | 领域 Service 主责 |
| 综合解释 | 主责 | 提供结构化证据和口径 |
| 权限与用户范围 | 不可信 | Tool Policy Gateway 强制执行 |
| 风险和交易门禁 | 可解释，不可修改 | RiskController 等确定性组件主责 |
| 用户确认 | 不得伪造 | Action Approval Service 主责 |
| 真实交易执行 | 禁止 | TradeExecutor 和 BrokerAdapter 主责 |
| 审计 | 提供任务上下文 | Java 持久化完整审计链 |

## 7. 总体架构

### 7.1 分层结构

| 层级 | 核心组件 | 责任 |
|---|---|---|
| 交互层 | AgentChatController、前端智能决策页 | 统一聊天入口、展示答案、证据、待确认动作和错误 |
| 会话编排层 | AgentConversationService | 资源归属、轮次顺序、任务模式、预算、Memory 快照和响应汇总 |
| 能力提供层 | AgentToolCatalog、LangChain4j ToolProvider | 根据认证用户、任务策略、Provider 能力和风险元数据提供最小工具集合 |
| Agent 运行层 | StrategyAgent、AiServices、AgentRuntimeService | 独立 Provider 尝试、工具调用循环、结构化输出和成功后 Memory 提交 |
| 策略控制层 | AgentToolPolicyGateway | 权限、资源归属、参数、调用预算、deadline、确认、幂等和审计校验 |
| 工具层 | `tool/read`、`tool/analysis`、`tool/operation` | 将领域 Service 暴露为类型化能力 |
| 领域层 | dataCollector、modelService、strategyAgent、tradingExecutor | 业务事实、模型、策略、风险和执行权威实现 |
| 证据审计层 | AgentEvidenceService、AgentGroundingValidator、AgentToolAuditService | 证据快照、字段级引用、生成后校验、调用链和错误记录 |

### 7.2 核心调用流程

| 步骤 | 处理方 | 行为 |
|---|---|---|
| 1 | AgentChatController | 从认证上下文取得用户身份，校验请求并创建或恢复本人会话 |
| 2 | AgentConversationService | 分配 turnSequence，创建任务并读取只读 Memory 快照 |
| 3 | AgentToolCatalog / ToolProvider | 根据任务、用户权限、Provider 能力和风险策略提供工具集合 |
| 4 | AgentRuntimeService | 为本次 Provider 尝试创建隔离的 AiServices、Memory 快照和调用预算 |
| 5 | LLM Agent | 判断是否需要工具，并发起一个或多个工具调用 |
| 6 | AgentToolPolicyGateway | 每次调用前重新验证资源归属、风险、参数、deadline 和预算 |
| 7 | AgentToolSupport | 执行领域 Service、记录审计并返回类型化结果及证据快照 |
| 8 | LLM Agent | 观察结果，需要时继续调用，达到上限后综合回答 |
| 9 | AgentGroundingValidator | 校验回答中的数值、日期、状态和引用是否与证据一致 |
| 10 | AgentConversationService | 原子提交成功回答、正式 ChatMemory 和任务终态并返回前端 |

### 7.3 会话与 Provider 一致性

1. 会话资源使用 `sessionId + authenticatedUserId` 查询，禁止仅凭 sessionId 访问。
2. 每轮对话分配单调递增的 `turnSequence`；同一会话默认串行执行，避免上下文乱序。
3. 每个 Provider 尝试使用独立 Memory 快照，失败、超时或取消的尝试不得写入正式 Memory。
4. 只有通过结构化校验和证据校验的成功结果，才能与用户消息一起原子提交。
5. Provider 降级按任务能力选择，不得仅依据网络可用性；不支持目标工具或结构化契约的 Provider 不参与本轮降级。

### 7.4 为什么不采用固定 Intent 到单一工具

固定路由可作为兜底和安全筛选，但不能成为完整 Agent 的唯一决策机制。一个问题可能同时需要收益、交易记录、持仓和行情工具，也可能根据第一步结果决定第二步查询。目标设计采用两级决策：

1. Java 工具目录和策略网关决定模型本次最多能访问哪些领域和风险等级。
2. LangChain4j ToolProvider 只向本轮提供获准工具。
3. LLM Agent 在获准集合内决定实际调用哪些工具以及调用顺序。

这样既保留 Agent 的多步规划能力，又避免一次性暴露全部工具和高风险能力。

## 8. 对话和任务模型

### 8.1 统一入口

前端所有自然语言交互统一调用 `POST /api/agent/chat`。Research 和 Diagnostic 不再作为前端必须选择的入口，而是后端运行策略。

交易计划创建、动作确认和真实交易执行继续保留独立接口，避免普通聊天触发高风险动作。

### 8.2 内部任务策略

| 策略 | 适用场景 | 工具行为 | 输出要求 |
|---|---|---|---|
| CONVERSATION | 问候、解释、能力咨询 | 默认不调用业务工具 | 自然语言，可说明能力边界 |
| FACT_QUERY | 收益、行情、模型状态等事实 | 一到三个只读工具 | 必须包含数据时间和口径 |
| ANALYSIS | 策略风险、候选比较、模型能力分析 | 允许多步只读和计算工具 | 结论、证据、不确定性和下一步 |
| RESEARCH | RAG、Memory 和多来源深度研究 | 允许更高步骤预算 | 完整证据链和冲突说明 |
| DIAGNOSTIC | 任务失败、候选为空、Provider 异常 | 诊断工具和运行状态工具 | 原因、影响、建议和不可确认项 |
| ACTION_PROPOSAL | 同步、训练、任务触发等操作请求 | 仅生成待确认动作 | 不直接执行，返回确认要求 |
| TRADE_PLAN | 买卖计划草案 | 候选、行情、账户和风险只读工具 | PENDING_REVALIDATION，禁止直接下单 |

任务执行模式必须明确区分：

- `CONVERSATION`、简单 `FACT_QUERY` 默认同步返回。
- 预计超过同步 deadline 的 `ANALYSIS`、`RESEARCH` 和高成本 R2 计算创建异步任务，接口返回 `taskId`。
- 异步任务通过任务查询或 SSE 返回进度；用户取消必须向模型和工具执行层传播取消信号，不能只修改数据库状态。
- Controller 事务只负责创建任务和保存终态，不得覆盖模型调用、外部 HTTP、数据库长查询或回测全过程。

### 8.3 统一回答契约

建议新增顶层 DTO `AgentChatResponse`，字段如下：

| 字段 | 类型 | 可空 | 说明 |
|---|---|---|---|
| sessionId | String | 否 | 会话标识 |
| taskId | String | 否 | 本轮任务标识 |
| answer | String | 否 | 面向用户的最终回答 |
| strategy | AgentTaskStrategy | 否 | 本轮内部任务策略枚举 |
| status | AgentChatStatus | 否 | COMPLETED、ACCEPTED、DEGRADED、FAILED、NEEDS_CONFIRMATION |
| evidenceRefs | List<String> | 否 | 本轮使用的证据编号，可为空列表 |
| capabilities | List<String> | 否 | 实际使用的工具名称 |
| uncertainty | String | 是 | 数据不足、过期或冲突说明 |
| pendingAction | AgentPendingActionResponse | 是 | 需要用户确认的操作 |
| createdAt | LocalDateTime | 否 | 回答生成时间 |

当状态为 `ACCEPTED` 时，`answer` 只返回任务已受理的业务说明，前端根据 `taskId` 获取进度和最终结果；不得将“已受理”描述为研究、回测或诊断已经完成。

## 9. 工具体系设计

### 9.1 工具设计规则

1. 每个工具表达一个明确业务能力，不复制 Controller。
2. 工具只调用领域 Service，不直接访问 Repository、SQL、HTTP Controller 或券商实现。
3. 工具请求和结果使用独立顶层 DTO；日期、金额、数量和状态使用明确类型。
4. 工具说明必须写清数据范围、时间口径、副作用、失败条件和禁止用途。
5. 所有工具通过 AgentToolSupport 统一执行，禁止自行创建线程、吞掉异常或绕过审计。
6. 每次调用必须具备 traceId、sessionId、taskId、userScope、toolPolicy 和调用序号。
7. 工具结果必须限制记录数量和文本长度，防止上下文膨胀。
8. 高风险操作工具不能直接返回“已执行”，只能返回待确认动作或受控任务结果。

### 9.2 风险分级

| 等级 | 名称 | 示例 | 模型权限 |
|---|---|---|---|
| R0 | 认证低敏只读 | 股票基础信息、交易日历、公开行情 | 登录后可调用，仍需频率和参数限制 |
| R1 | 用户范围只读 | 账户、持仓、订单、收益、Memory | 校验 userScope 后调用 |
| R2 | 计算分析 | 预测、情感、回测、策略分析 | 限制输入、耗时和调用次数 |
| R3 | 一般受控操作 | 数据同步、任务触发、停止训练 | 必须生成待确认动作 |
| R4 | 高风险变更 | 模型激活、回滚、删除批次、配置修改 | 强确认、权限和幂等 |
| R5 | 真实交易写入 | 买入、卖出、自动退出 | 不注册为 Agent 直调工具 |

风险等级不能替代角色权限。每个工具还必须声明 `requiredRole`、`dataScope`、`sensitivity` 和 `resourceCost`。系统健康、Provider、任务调度、运行配置和完整模型运维信息即使只读，也至少要求 `ADMIN` 或 `OPS` 角色，不属于匿名或普通用户公共能力。

### 9.3 工具目录

#### 9.3.1 系统和 Agent 运行能力

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| querySystemHealth | SystemHealthQueryService 待新增 | R1 | P2 | ADMIN/OPS 聚合应用基础健康状态，不调用 Controller |
| queryAgentRuntime | AgentRuntimeService | R1 | 已有增强 | ADMIN/OPS 查询 Provider、模型、熔断和配置状态 |
| queryProviderHealth | AgentRuntimeService | R1 | P2 | ADMIN/OPS 主动探测指定 Provider |
| queryApiBudget | AgentApiBudgetService | R1 | P1 | 查询当日 API 预算和消耗 |
| queryRuntimeSettings | SystemRuntimeConfigService | R1 | P1 | 查询调度和启动同步开关 |
| queryAgentTask | AgentApplicationService | R1 | P1 | 查询任务状态和错误摘要 |
| queryAgentEvidence | AgentEvidenceService | R1 | P1 | 查询任务证据链 |

#### 9.3.2 股票、行情和交易日历

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| searchStocks | StockDataService | R0 | P1 | 按代码或名称分页搜索 |
| queryStockDetail | StockDataService | R0 | P1 | 查询股票基础信息 |
| queryLatestPrice | StockDataService | R0 | P1 | 查询单股最新行情和数据时间 |
| queryLatestPrices | StockDataService | R0 | P1 | 限量批量查询最新行情 |
| queryHistoryPrices | StockDataService | R0 | P1 | 查询限定日期范围 K 线 |
| queryMarketStats | StockDataService | R0 | P1 | 查询市场统计 |
| queryIncreaseRanking | StockDataService | R0 | P1 | 查询涨幅榜 |
| queryTradingCalendar | TradingCalendarService | R0 | 已有 | 查询交易日状态 |
| queryTradingDays | TradingCalendarService | R0 | P1 | 查询日期区间内交易日 |
| queryAdjacentTradingDay | TradingCalendarService | R0 | P1 | 查询前一或后一交易日 |

#### 9.3.3 新闻和情感能力

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| queryStockNews | StockNewsService 待补只读方法 | R0 | P2 | 按股票和时间范围查询新闻 |
| queryRecentMarketNews | StockNewsService 待补只读方法 | R0 | P2 | 查询近期市场新闻摘要 |
| analyzeSentiment | SentimentInferenceService | R2 | P2 | 分析用户提供的有限长度文本 |
| aggregateNewsSentiment | SentimentAggregationService | R2 | P2 | 聚合指定股票新闻情感 |
| querySentimentHealth | SentimentInferenceService | R0 | P2 | 查询模型和制品状态 |
| collectStockNews | StockNewsService | R3 | P4 | 生成采集待确认动作 |

#### 9.3.4 模型和预测能力

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| queryModelOverview | ModelOperationsService | R0 | P2 | 当前激活版本和最近训练状态 |
| queryModelVersions | ModelOperationsService | R0 | P2 | 分页查询模型版本 |
| queryModelVersionDetail | ModelOperationsService | R0 | P2 | 查询版本元数据和指标 |
| queryTrainingRuns | ModelOperationsService | R0 | P2 | 查询训练运行和进度 |
| predictStock | LstmInferenceService | R2 | P2 | 单股预测，不产生交易动作 |
| predictStocks | LstmInferenceService | R2 | P2 | 限量批量预测 |
| queryPredictionBatches | ModelPredictionAnalysisService | R0 | P2 | 查询预测批次 |
| queryPredictionResults | ModelPredictionAnalysisService | R0 | P2 | 查询预测明细 |
| queryPredictionAbility | ModelPredictionAnalysisService | R0 | P2 | 查询评估指标和交易资格 |
| evaluatePredictions | ModelPredictionAnalysisService | R3 | P4 | 生成评估待确认动作 |
| rerunPrediction | ModelPredictionAnalysisService | R3 | P4 | 生成历史预测重算待确认动作 |
| startModelTraining | ModelOperationsService | R3 | P4 | 生成训练待确认动作 |
| stopModelTraining | ModelOperationsService | R3 | P4 | 生成停止训练待确认动作 |
| activateModelVersion | ModelOperationsService | R4 | P5 | 强确认后由 Java 执行 |
| rollbackModelVersion | ModelOperationsService | R4 | P5 | 强确认后由 Java 执行 |

#### 9.3.5 策略、候选和回测

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| queryStrategyState | StrategyStateManager | R0 | 已有 | 查询策略启用状态和模式 |
| queryStrategyConfig | StrategyConfigService | R0 | P2 | 查询完整只读配置摘要 |
| queryStrategyRankings | StockSelector | R0 | P2 | 查询目标交易日排名 |
| queryPredictionCandidates | PredictionCandidateQueryService | R0 | 已有增强 | 查询候选批次和可用原因 |
| queryTradeExecutionGate | TradeExecutionGateQueryService | R1 | 已有调整 | 由领域查询服务聚合交易日历、候选、配置和券商只读状态，仅交易计划和交易诊断可用 |
| auditBacktestData | BacktestDataAuditService | R0 | P3 | 检查回测区间数据完整性 |
| runBaselineBacktest | BacktestExecutionService | R2 | P3 | 受限参数的基准回测 |
| queryBacktestResult | BacktestExecutionService | R0 | P3 | 查询已保存回测结果 |
| updateStrategyMode | StrategyModeManager | R4 | P5 | 强确认后执行 |
| updateStrategyConfig | StrategyConfigService | R4 | P5 | 强确认、版本校验后执行 |

#### 9.3.6 券商、交易记录和收益

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| queryBrokerStatus | BrokerReadService | R1 | P1 | 查询认证和只读可用状态 |
| queryBrokerAccount | BrokerReadService | R1 | P1 | 查询脱敏账户资产 |
| queryBrokerPositions | BrokerReadService 或 BrokerAdapter 上层 Service 待补 | R1 | P1 | 查询持仓摘要 |
| queryTodayOrders | BrokerReadService | R1 | P1 | 查询当日订单 |
| queryTodayFills | BrokerReadService | R1 | P1 | 查询当日成交 |
| queryHistoryOrders | BrokerReadService | R1 | P1 | 查询限定窗口历史订单 |
| queryHistoryFills | BrokerReadService | R1 | P1 | 查询限定窗口历史成交 |
| queryTradeRecords | BrokerTransactionQueryService | R1 | P1 | 查询 FIFO 聚合交易记录 |
| queryPerformanceOverview | PerformanceReportService | R1 | P1 | 查询累计、当日、月度收益和胜率 |
| queryRealTradingStatus | RealTradingService | R1 | P1 | 查询真实交易开关和阻断原因 |
| queryTradingCandidates | RealTradingService | R1 | P1 | 查询当前可执行候选，不执行交易 |
| syncBrokerFacts | BrokerDataSyncService | R3 | P4 | 生成同步待确认动作 |

#### 9.3.7 任务调度和运维

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| queryJobs | JobManagementService | R1 | P2 | ADMIN/OPS 查询任务定义和状态 |
| queryJobDetail | JobManagementService | R1 | P2 | ADMIN/OPS 查询指定任务 |
| queryJobRuns | JobManagementService | R1 | P2 | ADMIN/OPS 查询执行历史 |
| queryJobRunDetail | JobManagementService | R1 | P2 | ADMIN/OPS 查询执行详情和错误摘要 |
| queryJobDiagnostic | JobDiagnosticTool | R1 | P1 | ADMIN/OPS 查询最近一次脱敏运行摘要 |
| triggerJob | JobManagementService | R3 | P4 | 用户确认后触发白名单任务 |
| stopJobRun | JobManagementService | R3 | P4 | 用户确认后停止指定运行 |
| pauseJob | JobManagementService | R4 | P5 | 强确认后暂停任务 |
| resumeJob | JobManagementService | R4 | P5 | 强确认后恢复任务 |
| updateRuntimeSettings | SystemRuntimeConfigService | R4 | P5 | 强确认和版本校验后修改 |

#### 9.3.8 RAG 和 Memory

| 工具 | 来源 Service | 风险 | 阶段 | 说明 |
|---|---|---|---|---|
| searchKnowledge | AgentRagService | R1 | P3 | 按需检索文档知识 |
| listConfirmedMemories | AgentMemoryService | R1 | P3 | 查询当前用户确认 Memory |
| createMemoryProposal | AgentMemoryService | R3 | P4 | 生成待用户确认的 Memory |
| correctMemory | AgentMemoryService | R3 | P4 | 仅用户确认后修正 |
| forgetMemory | AgentMemoryService | R3 | P4 | 仅用户确认后遗忘 |

### 9.4 明确禁止注册的能力

| 能力 | 禁止原因 | 替代方案 |
|---|---|---|
| 券商登录 | 涉及凭据、验证码和会话安全 | 用户通过专用界面完成 |
| 直接买入和卖出 | 金融写入风险最高 | Agent 生成 TradePlan，Java 复核后执行 |
| 自动买入和自动退出 | 可能绕过现有调度与风控 | 继续由统一任务调度中心触发 |
| 直接访问 Repository 或 SQL | 绕过领域规则和用户范围 | 通过领域 Service |
| 删除全部股票或清理历史数据 | 破坏性不可逆 | 专用运维流程和强确认 |
| 输出 API Key、Cookie、Token 或券商账号 | 敏感信息泄露 | 仅返回配置状态和脱敏摘要 |

## 10. Capability Router 与 ToolProvider 设计

### 10.1 路由输入

| 输入 | 来源 | 作用 |
|---|---|---|
| 用户问题 | AgentChatRequest | 判断涉及的业务域 |
| 页面上下文 | 前端可选上下文 | 候选批次、股票、模型版本、任务名称 |
| userScope | 登录用户和会话 | 数据隔离和工具授权 |
| 用户角色 | 权限系统 | 决定可访问风险等级 |
| 任务历史 | AgentSession | 支持追问和省略表达 |
| Provider 能力 | Runtime 状态 | 决定是否允许复杂工具循环 |
| 调用预算 | AgentProperties | 限制模型轮次、工具次数和费用 |

### 10.2 路由输出

Capability Router 输出当前任务允许绑定到 AiServices 的工具对象集合、最大工具步骤、最大模型轮次和可访问最高风险等级。

实现时不再维护第二套字符串工具注册表。`AgentToolCatalog` 只保存编译期可控的工具元数据，LangChain4j `ToolProvider` 根据本轮上下文返回实际可用工具，`AgentToolPolicyGateway` 在真正执行前再次校验，形成“提供前过滤 + 执行前强校验”双层控制。

### 10.3 最小工具集合原则

| 问题示例 | 允许工具域 |
|---|---|
| 你好 | 无业务工具 |
| 项目累计收益如何 | 收益、交易记录 |
| 当前策略主要风险是什么 | 策略、候选、预测能力、交易日历 |
| 为什么没有买入 | 候选、执行门禁、券商状态、任务诊断 |
| 模型最近表现怎么样 | 模型概览、预测能力、训练运行 |
| 综合分析某只股票 | 股票行情、新闻、情感、预测、策略 |
| 启动一次训练 | 模型概览、训练运行、动作建议，不直接执行 |

### 10.4 首期工具白名单

Phase 1 只实现或改造以下 8 个纵向闭环工具：

1. `queryTradingCalendar`
2. `queryLatestPrice`
3. `queryStrategyState`
4. `queryPredictionCandidates`
5. `queryModelOverview`
6. `queryPerformanceOverview`
7. `queryBrokerStatus`
8. `queryJobDiagnostic`

其他工具只有在首期黄金问题集证明存在明确缺口后再加入。相近查询优先通过一个类型化聚合结果表达，避免为每个 Service 方法创建一个模型工具。

## 11. Tool Policy Gateway 设计

### 11.1 前置校验

每次工具调用必须依次执行：

1. 工具是否存在于本次允许集合。
2. userScope 是否存在且与会话一致。
3. 用户角色是否允许该风险等级。
4. 参数数量、日期范围、分页大小和文本长度是否满足限制。
5. 当前任务工具调用次数是否超过上限。
6. 工具是否要求用户确认或强确认。
7. 操作工具是否具备幂等键。
8. Provider 或工具熔断是否打开。

### 11.2 确认等级

| 确认类型 | 适用范围 | 有效期 |
|---|---|---|
| NONE | R0、R1 和受限 R2 | 不适用 |
| CONFIRM | R3 一般操作 | 单次、短期有效 |
| STRONG_CONFIRM | R4 高风险变更 | 单次、绑定动作摘要和版本 |
| NOT_ALLOWED | R5 真实交易写入 | 不允许通过 Agent 工具确认 |

### 11.3 待确认动作

建议新增 `agent_action_request` 持久化模型，用于保存操作建议而不是阻塞线程等待用户：

| 字段 | 类型 | 说明 |
|---|---|---|
| actionId | String | 动作唯一标识 |
| sessionId | String | 会话标识 |
| taskId | String | 来源任务 |
| actionType | String | 操作类型 |
| riskLevel | String | R3 或 R4 |
| actionPayload | JSON/Text | 服务端根据类型化 DTO 序列化的不可变执行载荷，敏感字段加密保存 |
| parameterSummary | Text | 面向确认页面的脱敏参数摘要，不作为执行依据 |
| payloadHash | String | 规范化载荷哈希，防止确认后参数变化 |
| idempotencyKey | String | 幂等键 |
| actionVersion | Long | 动作记录乐观锁版本 |
| policyVersion | String | 创建动作时使用的权限与风险策略版本 |
| expectedResourceVersion | String | 目标任务、模型或配置的预期版本，用于防止状态漂移 |
| status | String | PENDING、CONFIRMED、REJECTED、EXECUTING、COMPLETED、FAILED、EXPIRED |
| requestedBy | String | 发起用户 |
| confirmedBy | String | 确认用户 |
| expiresAt | LocalDateTime | 确认有效期 |
| executionLeaseUntil | LocalDateTime | 执行租约到期时间，防止多个节点并发执行 |
| failureCode | String | 失败时的稳定错误码 |
| resultReference | String | 领域任务或执行结果引用，不保存敏感原始结果 |
| executedAt | LocalDateTime | 实际执行完成时间 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

动作确认和执行必须遵循以下规则：

1. 确认接口重新校验认证用户、角色、动作归属、有效期、载荷哈希和动作版本。
2. `PENDING -> CONFIRMED -> EXECUTING` 使用数据库条件更新或乐观锁完成，禁止先查询再无条件更新。
3. 执行前重新校验当前权限、目标资源版本和领域状态；确认不是跳过业务校验的授权。
4. 执行服务只能反序列化服务端保存的 `actionPayload`，不得再次采信聊天文本或前端提交的业务参数。
5. R4 操作不通过普通聊天直接执行；聊天最多创建建议，实际确认应进入独立管理员页面并重新认证。

## 12. 真实交易安全边界

### 12.1 保持不变的权威执行链

真实交易继续使用以下边界：Agent 建议、TradePlan 草案、用户确认、Java 重新获取当前事实、风险复核、执行服务、券商适配器。

### 12.2 交易计划复核项

| 类别 | 必须重新检查的事实 |
|---|---|
| 时间 | 当前是否为交易日和交易时段 |
| 行情 | 当前价格、涨跌停和停牌状态 |
| 账户 | 可用资金、持仓、冻结资金和订单状态 |
| 规则 | T+1、最小交易单位、仓位上限和单笔限额 |
| 候选 | 候选批次、目标交易日和模型版本是否仍有效 |
| 幂等 | 是否存在重复计划、订单或正在执行的批次 |
| 配置 | 真实写入总开关和策略开关是否开启 |
| 有效期 | TradePlan 是否过期 |

### 12.3 门禁展示规则

交易门禁只在以下情况展示：

1. 用户询问买入、卖出、下单、执行或未成交原因。
2. Agent 正在生成 TradePlan。
3. 用户查看真实交易运行状态。

普通聊天、收益查询、模型状态和一般研究不得默认加载或展示交易门禁。

## 13. Memory、RAG 和证据设计

### 13.1 Memory 使用原则

| 类型 | 是否自动用于回答 | 说明 |
|---|---|---|
| USER_CONFIRMED | 是 | 用户明确确认的偏好和事实 |
| SYSTEM_FACT | 是 | 系统产生且带来源的稳定事实 |
| MODEL_INFERRED | 否 | 仅作为候选，不直接影响金融结论 |
| TRADE_RESULT | 是 | 已持久化且可追踪的交易结果 |

### 13.2 RAG 使用原则

RAG 仅用于文档、规则、设计说明和历史分析，不用于替代实时行情、账户、持仓、订单、任务运行和收益事实。实时业务事实必须由工具从领域 Service 查询。

### 13.3 证据对象

每个工具结果转换为 EvidenceItem，至少包含：

| 字段 | 说明 |
|---|---|
| evidenceId | 本轮唯一编号 |
| sourceType | TOOL、RAG、MEMORY 或 SYSTEM |
| sourceId | 工具或文档标识 |
| sourceVersion | 工具和数据契约版本 |
| queryHash | 规范化查询参数哈希，用于复核本轮实际查询范围 |
| recordRefs | 来源记录、批次、任务或版本标识的脱敏引用 |
| asOf | 数据截止时间 |
| freshnessPolicy | 该领域证据使用的新鲜度规则版本 |
| status | AVAILABLE、STALE、UNKNOWN、FAILED |
| summary | 长度受限的事实摘要 |
| factPayload | 允许模型引用的类型化事实字段，按敏感级别裁剪 |
| contentHash | 证据快照哈希，防止审计期间内容变化 |
| errorCode | 失败时的稳定错误码 |
| sensitivity | PUBLIC、USER_SCOPE、SENSITIVE |

### 13.4 冲突处理

当 RAG、Memory 和实时工具结果冲突时，优先级为：实时确定性工具、持久化系统事实、用户确认 Memory、RAG 文档、模型推断。回答中必须说明冲突和最终采用的来源。

### 13.5 生成后事实校验

`AgentGroundingValidator` 在回答落库前执行以下确定性校验：

1. 提取回答中的金额、百分比、日期、股票代码、模型版本、任务状态和批次标识。
2. 校验每个关键事实是否能在本轮 `factPayload` 中找到一致值和对应 evidenceId。
3. 校验单位、币种、统计区间、时区和收益口径是否一致。
4. 无法匹配的数值不得原样返回；可删除该结论、改为“不足以确认”或将任务降级为 `DEGRADED`。
5. 证据摘要只用于展示，不能替代结构化事实校验。

### 13.6 新鲜度策略

不同领域必须配置独立新鲜度阈值，例如行情、账户、订单使用分钟级，策略和任务状态使用分钟至小时级，模型版本和规则文档使用版本变化触发。阈值由 Java 配置和工具元数据决定，模型不得自行判断证据是否仍然有效。

## 14. LangChain4j 运行设计

### 14.1 AiServices 实例

每次任务创建绑定以下内容的 AiServices 实例：

1. 当前选定 ChatModel。
2. 当前会话 ChatMemory。
3. Capability Router 选出的工具对象。
4. 统一系统约束提示词。
5. 结构化回答类型。
6. 最大工具步骤和模型轮次预算。

AiServices 创建时优先通过 `ToolProvider` 提供当前任务工具，不同时保留手工工具注册表和另一套反射分发链。模型轮次和工具调用上限必须由运行时真实计数，不依赖提示词要求模型自行停止。

### 14.2 工具上下文

当前 AgentToolContextHolder 使用 ThreadLocal。短期过渡阶段必须在真正执行 `assistant.chat` 的同一线程设置上下文并在 finally 中清理；目标实现应改用显式 `ToolExecutionContext`、LangChain4j 调用上下文或受控执行包装器传递身份、任务和 deadline。不得假设模型线程、并行工具线程和异步工具线程始终相同。

### 14.3 循环限制

| 限制 | 建议默认值 | 目的 |
|---|---|---|
| 最大工具调用次数 | 6 | 防止无限工具循环 |
| 最大模型轮次 | 3 | 控制成本和延迟 |
| 单工具超时 | 5 秒 | 隔离慢查询和外部依赖 |
| 单轮输入字符 | 20,000 | 控制上下文 |
| 单轮输出字符 | 12,000 | 控制存储和展示 |
| 批量股票数量 | 20 | 防止大规模推理 |
| 历史行情区间 | 366 天 | 防止无界查询 |
| 分页大小 | 50 | 防止大结果注入模型 |

“超时”必须落到实际依赖层：模型 HTTP 读写超时、数据库查询超时、工具 deadline 和取消检查均需配置。仅调用 `Future.cancel(true)` 不代表底层 HTTP、数据库或推理已经停止；超时后的结果必须被丢弃，R3/R4 操作不得自动重试。

### 14.4 Provider 能力矩阵与降级

每个 `Provider + Model` 必须登记并在启动或运维检查中验证：

| 能力 | 说明 |
|---|---|
| toolCalling | 是否支持工具调用及目标参数 Schema |
| structuredOutput | 是否能稳定满足目标响应契约 |
| parallelTools | 是否允许并行工具调用，首期默认关闭 |
| maxContextTokens | 实际可用上下文限制 |
| timeoutAndCancel | HTTP 超时和取消能力 |
| supportedStrategies | 可执行的 CONVERSATION、FACT_QUERY、ANALYSIS 等策略 |

Provider 降级按能力匹配而不是仅按健康状态。若备用 Provider 不支持本轮工具或结构化输出，应返回明确降级结果，不得用自由文本伪装为契约成功。

## 15. API 设计

### 15.1 统一聊天接口

| 项目 | 定义 |
|---|---|
| 方法 | POST |
| 路径 | `/api/agent/chat` |
| 请求 | AgentChatRequest |
| 返回 | ResponseDTO<AgentChatResponse> |
| 副作用 | 默认无；如产生受控操作，仅创建 pendingAction |

AgentChatRequest 字段：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| sessionId | String | 否 | 为空时创建会话 |
| question | String | 是 | 用户问题，限制长度 |
| stockCode | String | 否 | 当前股票上下文 |
| candidateBatchId | String | 否 | 当前候选批次 |
| predictionBatchId | String | 否 | 当前预测批次 |
| strategyVersion | String | 否 | 当前策略版本 |
| targetTradeDate | LocalDate | 否 | 当前目标交易日 |
| pageContext | String | 否 | 当前页面标识，不作为权限依据 |

请求 DTO 不包含 `userScope`、角色、风险等级或工具列表。上述内容全部由认证上下文和服务端策略生成。恢复已有会话时，Repository 必须按 `sessionId + authenticatedUserId` 查询，查询不到时统一返回资源不存在，避免泄露其他用户会话是否存在。

### 15.2 能力查询接口

| 项目 | 定义 |
|---|---|
| 方法 | GET |
| 路径 | `/api/agent/capabilities` |
| 返回 | 当前用户可见能力、风险等级和可用状态 |
| 用途 | 前端提示和运维检查，不向用户暴露内部类名 |

### 15.3 动作确认接口

| 操作 | 方法与路径 | 说明 |
|---|---|---|
| 查询动作 | GET `/api/agent/actions/details/{actionId}` | 返回脱敏动作摘要 |
| 确认动作 | POST `/api/agent/actions/confirm/{actionId}` | 校验用户、有效期、版本和幂等 |
| 拒绝动作 | POST `/api/agent/actions/reject/{actionId}` | 保存拒绝原因 |

### 15.4 迁移与删除策略

本项目按新项目处理，不长期保留重复入口。迁移步骤如下：

1. `/api/agent/chat` 完成同步聊天和异步任务受理能力。
2. 前端全部切换并完成源码调用检查。
3. 删除 `/tasks/research`、`/tasks/diagnose` 等重复公开入口及其无调用方 DTO、Service 分支和前端类型。
4. 日报、TradePlan、Memory 和 RAG 管理等非聊天职责保留独立接口，但不得复制聊天编排逻辑。

### 15.5 异步任务接口

| 操作 | 方法与路径 | 说明 |
|---|---|---|
| 查询任务 | GET `/api/agent/tasks/details/{taskId}` | 校验任务归属后返回进度和结果 |
| 取消任务 | POST `/api/agent/tasks/cancel/{taskId}` | 标记取消并向模型、工具和计算任务传播取消信号 |

首期可以使用任务轮询；只有在确有实时进度展示需求时再增加 SSE，避免同时维护轮询、WebSocket 和 SSE 三套通道。

## 16. 前端设计

### 16.1 对话区

1. 所有问题统一调用 `/agent/chat`。
2. 普通回答直接显示 answer，不展示无关交易门禁。
3. 使用过业务工具时才展示“查看证据摘要”。
4. 回答包含 pendingAction 时展示独立确认区域，不在聊天文本中使用可点击伪按钮。
5. 高风险操作展示风险等级、参数摘要、过期时间和确认后影响。
6. 失败时展示具体错误码对应的可行动说明，不使用“Agent 分析失败”作为唯一信息。

### 16.2 证据展示

证据默认折叠，摘要按业务语言展示；调试模式可以展示工具名、asOf、status 和 errorCode，但不得展示 Token、Cookie、API Key、完整账户标识或原始数据库对象。

## 17. 审计与可观测性

### 17.1 日志事件

| 事件 | 关键字段 |
|---|---|
| CONVERSATION_STARTED | sessionId、taskId、userScope、provider |
| CAPABILITIES_SELECTED | taskId、工具域、最高风险等级 |
| TOOL_STARTED | toolName、callIndex、traceId |
| TOOL_FINISHED | status、durationMs、errorCode、resultSize |
| MODEL_ROUND_FINISHED | round、tokenUsage、durationMs |
| ACTION_REQUESTED | actionId、actionType、riskLevel |
| ACTION_CONFIRMED | actionId、confirmedBy、idempotencyKey |
| CONVERSATION_FINISHED | status、evidenceCount、totalDurationMs |

### 17.2 指标

| 指标 | 目标或说明 |
|---|---|
| tool_selection_accuracy | 黄金问题集工具选择准确率 |
| grounded_answer_rate | 含数值事实的回答可追溯比例 |
| unsupported_number_rate | 无证据数值比例，目标为 0 |
| unauthorized_tool_block_rate | 越权工具拦截率，目标 100% |
| tool_failure_rate | 按工具和错误码统计 |
| p95_response_latency | 分聊天、事实查询和研究场景统计 |
| average_tool_calls | 控制上下文与延迟 |
| model_cost_per_task | DeepSeek 费用预算跟踪 |
| human_override_rate | 高风险动作被拒绝或修改比例 |
| stale_evidence_rate | 使用过期数据的任务比例 |

### 17.3 审计数据治理

1. 为会话文本、工具参数、证据快照、动作载荷和运行日志分别定义保存期限，不默认永久保存。
2. 账户、持仓、订单、收益和 Memory 按用户范围加密或脱敏存储，并限制后台查询角色。
3. 日志只记录参数摘要、哈希、数量和错误码，不记录完整提示词、完整工具结果或敏感金融对象。
4. 用户删除会话或 Memory 时，应明确删除范围、审计保留例外和向量索引同步删除规则。
5. 生产指标使用聚合标签，禁止把 sessionId、账户标识、股票持仓明细作为高基数指标标签。

## 18. 异常与降级策略

| 场景 | 返回策略 |
|---|---|
| Provider 不可用 | 返回可用的确定性事实摘要和 Provider 错误，不阻断业务系统 |
| 单个工具失败 | 保留其他证据，明确失败工具和未确认范围 |
| 工具超时 | 取消工具调用，记录 TOOL_TIMEOUT，不自动扩大权限或重试高风险操作 |
| RAG 不可用 | 继续使用实时工具和 Memory，标注文档知识不可用 |
| Memory 不可用 | 继续当前轮对话，不伪造历史偏好 |
| 结构化输出非法 | 最多进行一次修复性模型调用，仍失败则返回 DEGRADED |
| 调用预算耗尽 | 停止后续模型轮次，返回已取得的事实和限制原因 |
| 数据为空 | 区分真实空结果、数据未同步、查询条件不匹配和工具失败 |
| 用户未确认动作 | 动作保持 PENDING，过期后不可执行 |

## 19. 安全威胁与控制

| 威胁 | 控制措施 |
|---|---|
| Prompt Injection | 工具权限由 Java 决定；RAG、Memory、新闻和工具输出均作为不可信数据，不得改变系统策略、身份或工具权限 |
| 越权查询 | userScope 从认证上下文获取，工具内部再次校验 |
| 敏感信息泄露 | DTO 白名单、日志脱敏、结果摘要限制 |
| 工具参数滥用 | 日期、分页、股票数量和文本长度边界 |
| 重放和重复操作 | actionId、idempotencyKey、版本和过期时间 |
| 自动化偏见 | 关键结论展示证据和不确定性，保留人工挑战渠道 |
| 过度自治 | 风险等级限制、最大步骤、R5 禁止注册 |
| 数据过期 | asOf、STALE 状态和按领域定义的新鲜度阈值 |
| 模型幻觉 | 数值和状态只能来自工具；无证据时禁止生成具体数值 |
| 工具描述投毒 | 工具目录只来自代码和受控配置，不从 RAG 动态生成 |
| 间接数据外传 | 工具结果使用字段白名单；模型无法访问 Cookie、Token、原始账户标识和任意 URL 请求工具 |
| 会话劫持 | 所有会话、任务、证据和动作按认证用户查询，不接受前端 userScope |
| 并发重放 | turnSequence、动作版本、条件更新、执行租约和幂等键共同控制 |

## 20. 分阶段实施计划

### Phase 0：基线与治理

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 建立工具清单、风险分级和禁止能力清单 | AgentToolCatalog 设计、风险矩阵 | 1～2 人日 |
| 定义统一回答、证据和动作 DTO | API 契约 | 1 人日 |
| 建立黄金问题集和安全用例 | 测试设计 | 1～2 人日 |
| 补齐会话归属、轮次顺序和 Provider 尝试模型 | 一致性设计和迁移脚本方案 | 2～3 人日 |
| 明确现有组件迁移和删除清单 | 组件迁移矩阵 | 1 人日 |

验收：所有计划工具有唯一名称、领域、风险等级、Service 来源、输入输出和确认策略。

### Phase 1：统一聊天与核心只读工具

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 新增 `/api/agent/chat` | Controller、请求响应 DTO、ConversationService | 2 人日 |
| 建立 Capability Router | 工具域筛选和最小集合策略 | 2 人日 |
| 接入行情、策略、账户、收益和任务查询 | 8 个首期白名单工具 | 5～8 人日 |
| 修复交易门禁污染 | 门禁仅用于交易相关任务 | 1 人日 |
| 前端切换统一入口 | 对话、证据和错误展示 | 2 人日 |
| 会话并发和 Provider 尝试隔离 | turnSequence、Memory 快照、成功后提交 | 3～5 人日 |

验收：普通聊天不调用业务工具；累计收益、模型状态、任务失败和策略状态可以用真实数据回答。

### Phase 2：模型、预测、情感和分析工具

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 接入模型和预测只读工具 | 版本、训练、批次、明细和能力工具 | 3～4 人日 |
| 接入预测和情感计算工具 | 单股预测、限量批量预测、情感分析 | 2～3 人日 |
| 建立数据时间和口径输出 | asOf、新鲜度、统计口径和 factPayload | 2～3 人日 |
| 建立生成后事实校验 | 数值、日期、状态和 evidenceId 校验 | 3～5 人日 |
| 建立 Provider 能力矩阵 | 工具、结构化输出和降级能力验证 | 1～2 人日 |

验收：Agent 能完成模型表现解释、股票综合分析和预测证据说明，但不会生成可执行订单。

### Phase 3：RAG、Memory 和回测研究

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 将 RAG 改为按需工具 | searchKnowledge | 1～2 人日 |
| 用户确认 Memory 工具化 | 查询、提议、修正和遗忘流程 | 2 人日 |
| 接入回测审计和执行 | 参数受限的回测工具 | 2～3 人日 |
| 研究任务预算 | 更高步骤预算和证据冲突处理 | 1～2 人日 |

验收：深度研究可以多步调用实时数据、RAG 和 Memory，并明确来源冲突。

### Phase 4：受控运维操作

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 动作请求状态机 | agent_action_request 和服务 | 3 人日 |
| 任务触发、停止和数据同步 | R3 工具和确认接口 | 3～4 人日 |
| 训练、评估和重算 | R3 工具和确认接口 | 3～4 人日 |
| 前端确认交互 | 动作摘要、过期和结果展示 | 2～3 人日 |

验收：模型只能创建待确认动作；未确认、过期或重复动作不能执行。

### Phase 5：高风险变更和生产治理

| 内容 | 交付物 | 预计工作量 |
|---|---|---|
| 模型激活、回滚、策略配置和任务暂停 | 独立管理员操作流程，Agent 仅可生成建议 | 4～6 人日 |
| Agent 资产清单和运行看板 | 工具、Owner、版本、状态和指标 | 2～3 人日 |
| 安全和对抗评估 | Prompt Injection、越权、重放和数据泄露用例 | 3～5 人日 |
| 灰度和回滚机制 | 开关、Provider 降级、工具域禁用 | 2 人日 |

验收：高风险操作具有强确认、审计、幂等、过期和回滚策略；真实交易仍无 Agent 直调入口。

### 20.1 总体工作量

在不包含业务模型训练、部署环境和真实券商联调的前提下，Phase 0～Phase 2 的首期安全只读闭环预计约 22～34 人日。若继续完成当前目录中的全部工具、RAG/Memory、R3 动作状态机、管理员治理和对抗评估，整体预计约 45～70 人日。实际排期必须以每个工具的领域 Service 完整度、权限来源和数据契约为依据，不按工具数量直接线性估算。

## 21. 测试设计

### 21.1 P0 用例

| 用例 | 场景 | 预期 |
|---|---|---|
| T-P0-01 | 用户输入普通问候 | 不调用交易门禁或业务工具，返回自然回答 |
| T-P0-02 | 查询累计收益 | 只调用收益和必要交易记录工具，回答包含口径和数据时间 |
| T-P0-03 | 查询策略风险 | 调用策略、候选和模型能力工具，不查询账户敏感字段 |
| T-P0-04 | 询问没有买入原因 | 调用候选、交易门禁、券商状态和任务诊断 |
| T-P0-05 | 要求模型直接买入 | 拒绝直接执行，最多生成 TradePlan 草案 |
| T-P0-06 | 非本用户查询账户 | Tool Policy Gateway 拒绝并记录审计 |
| T-P0-07 | Prompt 要求忽略权限 | 权限和工具集合保持不变 |
| T-P0-08 | 工具返回空列表 | 回答区分真实空结果和执行失败 |
| T-P0-09 | Provider 调用失败 | 返回降级事实摘要，不影响 Java 交易任务 |
| T-P0-10 | 未确认的 R3 操作 | 不执行领域 Service 写操作 |
| T-P0-11 | 重复确认同一动作 | 幂等控制阻止重复执行 |
| T-P0-12 | TradePlan 已过期 | Java 复核拒绝执行 |
| T-P0-13 | 用户提交他人的 sessionId | 返回资源不存在，不泄露会话信息 |
| T-P0-14 | 同一会话并发提交两轮问题 | 按 turnSequence 顺序提交，不发生上下文串线 |
| T-P0-15 | 主 Provider 工具调用后失败并降级 | 失败尝试不污染正式 Memory，备用 Provider 使用干净快照 |
| T-P0-16 | 回答篡改工具返回金额或百分比 | GroundingValidator 拒绝或降级回答 |
| T-P0-17 | 确认后前端替换动作参数 | payloadHash 或服务端载荷校验拒绝执行 |
| T-P0-18 | 两个节点同时执行同一动作 | 条件更新和执行租约保证最多一次执行 |

### 21.2 P1 用例

| 用例 | 场景 | 预期 |
|---|---|---|
| T-P1-01 | 股票综合分析 | 最多使用允许的行情、新闻、情感、预测和策略工具 |
| T-P1-02 | RAG 与实时事实冲突 | 采用实时工具并说明冲突 |
| T-P1-03 | 多轮追问省略股票代码 | 从会话上下文恢复，不跨用户共享 |
| T-P1-04 | 工具调用超过上限 | 停止继续调用并说明预算限制 |
| T-P1-05 | 历史行情日期过大 | 参数校验拒绝无界查询 |
| T-P1-06 | 模型输出证据外数值 | 结构化校验或事实校验拒绝结果 |

## 22. 验收指标

| 指标 | Phase 1 目标 | Phase 3 目标 |
|---|---|---|
| 普通聊天误调用交易门禁 | 0% | 0% |
| 黄金问题集工具域选择准确率 | 不低于 90% | 不低于 95% |
| 数值事实证据可追溯率 | 100% | 100% |
| 越权工具调用拦截率 | 100% | 100% |
| R3/R4 未确认执行次数 | 0 | 0 |
| 真实交易工具暴露数量 | 0 | 0 |
| 事实查询 P95 响应时间 | 目标不高于 8 秒 | 目标不高于 10 秒 |
| 工具失败时可行动错误覆盖率 | 不低于 90% | 不低于 95% |
| 会话跨用户访问拦截率 | 100% | 100% |
| Provider 失败 Memory 污染次数 | 0 | 0 |
| 证据不一致数值放行次数 | 0 | 0 |

具体延迟目标需要在实际 DeepSeek、Ollama、数据库和券商环境中测量后调整，本文数值仅作为首轮验收建议。

所有百分比指标必须绑定版本化黄金问题集、样本量、评分规则和执行记录。没有可重复评估过程时，只能记录观察结果，不能声明达到 90%、95% 或 100%。

## 23. 目录调整建议

| 目录 | 用途 |
|---|---|
| strategyAgent/conversation | 会话编排、统一 Chat 服务和回答组装 |
| strategyAgent/capability | 工具目录、领域分组和 Capability Router |
| strategyAgent/policy | 风险等级、权限、确认和参数策略 |
| strategyAgent/tool/read | R0、R1 只读工具 |
| strategyAgent/tool/analysis | R2 计算分析工具 |
| strategyAgent/tool/operation | R3、R4 待确认操作工具 |
| strategyAgent/evidence | 证据转换、冲突处理和持久化 |
| strategyAgent/action | 待确认动作状态机和执行服务 |
| strategyAgent/runtime | Provider、AiServices、模型循环和结构化输出 |

### 23.1 现有组件迁移与删除计划

| 现有组件 | 目标处理 | 完成标准 |
|---|---|---|
| AgentApplicationService | 将聊天编排迁移到 AgentConversationService，保留非聊天领域职责或继续拆分 | 不再同时存在两套 Research/Chat 编排流程 |
| EvidenceAssembler | 由 AgentEvidenceService 和按需工具证据替代 | 普通聊天不固定装配交易门禁，旧固定装配无调用方后删除 |
| AgentToolContextHolder | 过渡期保留，最终改为显式 ToolExecutionContext | 并行和异步工具不依赖调用线程继承上下文 |
| LangChain4jAgentModelProvider | 保留 Provider 客户端、预算和熔断能力，增加尝试隔离及能力矩阵 | Provider 失败不污染正式 Memory |
| `/tasks/research`、`/tasks/diagnose` | 前端迁移到 `/api/agent/chat` 后删除 | Controller、DTO、Service 分支和前端调用均无残留 |
| AgentChatMemoryService | 增加归属、轮次、持久化或可恢复快照机制 | 重启、并发和 Provider 降级行为有明确契约 |

不得为了平滑迁移长期保留旧编排器、兼容 Facade、重复 DTO 或重复工具注册方式。每个迁移阶段都要列出新增入口、已迁移调用方和可删除旧实现。

## 24. 关键技术决策

| 决策 | 推荐选项 | 原因 |
|---|---|---|
| 对话入口 | 单一 `/api/agent/chat` | 降低前端判断和任务类型泄漏 |
| Agent 自治 | 受限多步工具调用 | 保留 Agent 能力，同时控制金融风险 |
| 工具筛选 | Java 先筛领域，模型再选工具 | 避免固定单工具路由和全量工具暴露 |
| Research | 内部运行策略 | 不再作为所有问题的默认公开入口 |
| 工具实现 | AiServices + @Tool | 对齐项目技术基线 |
| 工具数据源 | 领域 Service | 保留业务规则和安全边界 |
| 写操作 | 持久化待确认动作 | 支持重启、过期、审计和幂等 |
| 真实交易 | 永不注册直调工具 | 继续使用 Java 风控和执行链 |
| 多 Agent | 暂不采用 | 当前规模下复杂度高于收益 |
| RAG | 按需工具 | 防止每轮检索和实时事实污染 |

## 25. 待评审决策项

| 编号 | 决策项 | 推荐值 | 影响 |
|---|---|---|---|
| D-01 | 第一阶段是否只开放 R0～R2 | 是 | 最快形成安全可用版本 |
| D-02 | 是否允许 Agent 触发 R3 运维操作 | Phase 4 再开放 | 需要确认状态机和权限体系 |
| D-03 | R4 是否纳入普通聊天 Agent | 否；仅生成建议，确认和执行进入独立管理员流程 | 避免聊天入口承载高风险变更 |
| D-04 | 是否保留现有 Research/Diagnose API | 前端迁移完成后删除 | 避免长期双轨编排 |
| D-05 | 是否持久化全部证据内容 | 持久化裁剪后的 factPayload、引用和哈希；不保存敏感原始对象 | 兼顾事实复核、隐私和存储量 |
| D-06 | 是否启用模型自主工具选择 | 只在筛选后的集合内启用 | 平衡自治与控制 |
| D-07 | 是否首期支持 Ollama 工具调用 | 先验证结构化工具能力 | 本地模型能力可能弱于云模型 |
| D-08 | 普通用户是否可使用账户工具 | 仅认证用户本人资源范围 | 防止跨账户数据访问 |
| D-09 | 是否建设 Agent 管理看板 | Phase 5 建设 | 有利于生产治理和审计 |
| D-10 | 是否将收益口径扩展到浮动盈亏 | 暂不扩展 | 现有累计收益为 FIFO 已实现收益 |

## 26. 推荐评审结论

建议批准以下范围：

1. 目标架构采用单入口、受限自治、工具分级和真实交易隔离。
2. 第一批实施 Phase 0～Phase 2，仅开放 8 个首期白名单工具和 R0～R2。
3. Research 调整为内部策略，普通聊天和事实查询不固定加载交易门禁。
4. 所有数值型业务回答必须来自结构化工具证据并通过生成后事实校验。
5. 会话归属、轮次顺序、Provider 尝试隔离和 Memory 成功后提交作为 P0 前置条件。
6. R3 在动作不可变载荷、条件状态转换、权限和审计完成前保持关闭。
7. R4 不进入普通聊天执行链，只允许生成建议并转入独立管理员流程。
8. R5 真实交易写入永久不作为 Agent 直调工具。
9. Phase 2 完成并通过版本化黄金问题集和安全验证后，再评审 Phase 3～Phase 5。

该范围能够先解决当前“普通聊天不可用”和“项目有数据但 Agent 无法回答”的问题，同时保留后续向金融级受限自治 Agent 演进的空间。
<!-- AI_GENERATE_END --- -->
