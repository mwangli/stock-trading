<!-- AI_GENERATE_START --- -->
# AGENT 二期 LangChain4j 设计

## 1. 设计原则

1. LangChain4j 负责模型、工具调用、结构化输出、短期会话记忆和 Embedding 抽象。
2. `strategyAgent` 负责业务安全边界、工具白名单、权限、审计、证据、任务状态和 TradePlan。
3. `tradingExecutor` 负责实时事实重读、交易日、账户、持仓、风控、幂等和券商写入。
4. RAG 与 Memory 不能替代结构化交易事实。
5. 外部模型不可用时必须安全降级，不能伪造成功。

## 2. 目标架构

```text
AgentController
      |
      v
AgentApplicationService
      |
      +--> LangChainAgentRuntime
      |       +--> ChatModel
      |       +--> AiServices / Structured Output
      |       +--> ChatMemoryProvider
      |       +--> ToolGateway
      |
      +--> ContextAssembler
      +--> EvidenceValidator
      +--> AgentTask / Audit Repository
      +--> MemoryService
      +--> LangChainMongoEmbeddingAdapter
      |
      v
MongoDB: RAG Chunk / Embedding / Memory Vector
MySQL: Session / Task / Tool Audit / TradePlan

TradePlan
      |
      v
tradingExecutor
      +--> TradingCalendarService
      +--> BrokerReadService / current facts
      +--> RiskController
      +--> TradeExecutor
      +--> ZXBrokerAdapter
```

LangChain4j 不直接注入 `TradeExecutor`、`BrokerAdapter` 或 `ZXBrokerAdapter` 到模型工具集合。模型只能看到 Tool Gateway 注册的只读工具和受控的 TradePlan 输出工具。

## 3. Maven 依赖设计

在父 POM 或 `stock-backend/pom.xml` 统一定义版本属性：

```xml
<langchain4j.version>1.22.0</langchain4j.version>
```

第一阶段实现建议引入：

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

说明：

- `langchain4j` 提供 AI Services、ChatMemory、TextSegment、Embedding、ToolSpecification 等核心 API。
- `langchain4j-open-ai` 用于 DeepSeek OpenAI-compatible ChatModel 和 EmbeddingModel 适配。
- 暂不引入 `langchain4j-spring-boot-starter`，因为当前项目 Spring Boot 为 3.2.10，而官方 Spring Boot 3 Starter 支持线要求 3.5+。
- 暂不直接使用 `langchain4j-mongodb-atlas` 作为现有单节点 MongoDB 的向量存储。该集成面向 MongoDB Atlas Vector Search；本项目先实现基于 `EmbeddingStore` 契约的 MongoTemplate 适配器。

## 4. 包结构

沿用当前 `com.stock.strategyAgent`：

```text
com.stock.strategyAgent
├── agent
│   ├── LangChainAgentRuntime
│   ├── AgentServiceInterface
│   ├── AgentOutputSchemas
│   └── AgentChatMemoryProvider
├── tool
│   ├── ToolDefinition
│   ├── ToolGateway
│   ├── ToolExecutionContext
│   ├── ToolExecutionResult
│   └── read
├── rag
│   ├── LangChainMongoEmbeddingStore
│   ├── LangChainRagService
│   └── RerankPolicy
├── memory
│   ├── LangChainMemoryAdapter
│   └── LongTermMemoryRetriever
├── audit
│   ├── AgentAuditEntity
│   ├── AgentToolCallEntity
│   └── AgentAuditRepository
└── config
    ├── AgentLangChainProperties
    └── LangChain4jConfiguration
```

已有 `service`、`runtime`、`rag`、`domain` 和 `persistence` 类逐步迁移到以上职责边界，迁移过程中保留兼容 Facade，避免一次性修改 Controller 契约。

## 5. LangChain4j Runtime

### 5.1 ChatModel Bean

`LangChain4jConfiguration` 根据配置创建 `ChatModel`：

```text
agent.enabled=false -> 不创建可调用外部模型的运行实例，使用 DisabledChatModel
provider=deepseek   -> 创建 OpenAiChatModel，baseUrl 指向 DeepSeek-compatible API
provider=ollama     -> 预留扩展，不作为本轮必需实现
```

模型调用参数必须来自 `AgentProperties`：

- `apiUrl`
- `apiKey`
- `model`
- `timeoutMs`
- `maxOutputChars`
- `maxModelRounds`
- `maxSteps`

不得在业务方法内创建 `HttpClient`、组装 `messages` JSON 或拼接 Authorization Header。

### 5.2 AI Service

使用 LangChain4j AI Services 或等价低层 `ChatModel` 调用实现以下结构化能力：

```text
ResearchAgentService -> ResearchAnswerDto
DiagnosticAgentService -> DiagnosticAnswerDto
ReportAgentService -> ReportAnswerDto
TradePlanAgentService -> TradePlanDraftDto
```

AI Service 的工具列表必须显式提供，不能从 Spring 容器自动收集所有 Bean。TradePlan Agent 只允许返回草稿 DTO，不允许返回券商订单 DTO。

### 5.3 输出校验

模型输出返回后必须经过：

```text
LangChain4j structured output
 -> Bean Validation
 -> EvidenceValidator
 -> SensitiveDataRedactor
 -> TradePlanValidator
 -> 任务状态落库
```

结构化输出失败时进入 `FAILED` 或 `NEEDS_REVIEW`，不进行隐式 JSON 修复后继续下单。

## 6. Tool Gateway

### 6.1 ToolDefinition

```java
name
version
description
requiredPermission
readOnly
maxResultSize
timeoutMs
redactionPolicy
```

每个工具使用明确的 Request DTO 和 Response DTO。LangChain4j 工具方法可以使用 `@Tool` 暴露描述，但真正执行必须委托 `ToolGateway.execute(...)`，由网关完成二次校验。

### 6.2 工具执行流程

```text
模型 Tool Call
 -> 解析工具名
 -> 白名单查找
 -> Request DTO 反序列化
 -> 参数边界校验
 -> 用户范围校验
 -> 权限校验
 -> 超时执行
 -> 领域查询服务
 -> 结果大小限制
 -> 敏感字段脱敏
 -> ToolExecutionAudit
 -> 返回 ToolExecutionResult
```

禁止：

- 任意 SQL
- 任意 Mongo Query
- 任意 URL
- 任意 Bean 或方法反射
- Shell、文件、网络写入
- `BrokerAdapter.submitOrder`
- 修改系统配置

### 6.3 一期工具注册

先注册以下只读工具适配器：

- `PredictionTool`
- `StrategyTool`
- `NewsSentimentTool`
- `JobDiagnosticTool`
- `TradingCalendarTool`
- `RiskReadTool`
- `BrokerSnapshotTool`

工具实现通过项目已有查询服务或新增 Query Port 接口获取数据，不让 LangChain4j 工具直接访问 Repository。

## 7. ChatMemory 与长期 Memory

### 7.1 短期会话记忆

使用 LangChain4j `ChatMemory` 保存当前会话有限消息：

- 按 `sessionId` 隔离。
- 限制消息数量和字符数。
- 不持久化密码、Token、Cookie、券商会话和完整账户信息。
- 任务结束或会话过期后清理。

`ChatMemoryProvider` 必须由项目实现，根据用户范围和会话状态返回记忆实例，不能使用全局共享 ChatMemory。

### 7.2 长期 Memory

长期 Memory 仍由 MySQL `agent_memory` 和 MongoDB `agent_memory_vectors` 管理：

```text
MemoryService
 -> 类型/来源/敏感字段/TTL 校验
 -> MySQL 元数据
 -> LangChain4j EmbeddingModel
 -> Mongo EmbeddingStore 适配器
```

LangChain4j 只负责 Embedding、TextSegment 和召回抽象，不改变已有 Memory 生命周期规则。

## 8. MongoDB RAG 适配器

### 8.1 适配目标

实现项目自己的 `LangChainMongoEmbeddingStore`，对接 LangChain4j `EmbeddingStore<TextSegment>` 能力：

- `add`
- `addAll`
- `search`
- `remove`
- `removeAll`

底层使用 `MongoTemplate` 读写现有 RAG 文档集合，保留：

- `chunkId`
- `content`
- `embedding`
- `metadata`
- `sourceType`
- `sourceId`
- `version`
- `asOf`
- `visibility`
- `contentHash`
- `embeddingModelVersion`

### 8.2 检索策略

```text
EmbeddingModel.embed(query)
 -> MongoDB active/visibility/source/version/time 过滤
 -> 应用层余弦相似度
 -> topK
 -> 来源去重
 -> EvidenceReference
```

当部署环境升级到支持 MongoDB Vector Search 的 Atlas 或明确可用的 MongoDB 向量索引时，再增加 `MongoDbEmbeddingStore` 实现和索引切换，不修改上层 RAG 契约。

### 8.3 失败降级

- Embedding 失败：RAG 检索失败并记录审计。
- MongoDB 不可用：Research 降级为结构化工具；TradePlan 拒绝生成或执行。
- 权限过滤异常：拒绝返回结果，不降级为全量查询。
- 来源版本冲突：返回不确定状态，不由模型自动选择事实。

## 9. TradePlan 集成

```text
TradePlanAgentService
 -> TradePlanDraftDto
 -> EvidenceValidator
 -> TradePlanValidator
 -> AgentTradePlanEntity(PENDING_REVALIDATION)
 -> TradePlanExecutionService
 -> TradingCalendarService
 -> Broker current facts
 -> RiskController
 -> TradeExecutor
 -> ZXBrokerAdapter
```

TradePlan Agent 不允许直接注入以下类型：

- `TradeExecutor`
- `RiskController`
- `BrokerAdapter`
- `ZXBrokerAdapter`
- `TradingProperties` 写配置服务

执行服务只能由 `tradingExecutor.job` 或 `tradingExecutor` 内部应用服务触发。

执行前必须重新读取：

- 当前交易日和确认状态
- 当前实时价格
- 账户资金
- 当前持仓
- 当日订单和成交
- 可交易性事实
- 交易时间
- 风险配置和真实写入门禁

## 10. 审计和持久化

二期新增 MySQL 表：

```text
agent_audit
agent_tool_call
agent_evidence
```

审计字段至少包括：

- `traceId`
- `sessionId`
- `taskId`
- `toolName`
- `toolVersion`
- `modelProvider`
- `modelName`
- `modelVersion`
- 参数摘要
- 结果摘要
- 开始/结束时间
- 状态
- 错误码
- 脱敏策略版本

禁止持久化原始 API Key、Token、Cookie、券商会话、完整账户快照和完整敏感 Prompt。

## 11. 配置设计

```yaml
agent:
  enabled: ${AGENT_ENABLED:false}
  provider: ${AGENT_PROVIDER:disabled}
  api-url: ${AGENT_API_URL:}
  api-key: ${AGENT_API_KEY:}
  model: ${AGENT_MODEL:}
  timeout-ms: ${AGENT_TIMEOUT_MS:30000}
  max-steps: ${AGENT_MAX_STEPS:6}
  max-model-rounds: ${AGENT_MAX_MODEL_ROUNDS:3}
  max-output-chars: ${AGENT_MAX_OUTPUT_CHARS:12000}
  chat-memory-max-messages: ${AGENT_CHAT_MEMORY_MAX_MESSAGES:20}
  tool-timeout-ms: ${AGENT_TOOL_TIMEOUT_MS:5000}
  audit-enabled: ${AGENT_AUDIT_ENABLED:true}
  rag-enabled: ${AGENT_RAG_ENABLED:false}
  memory-enabled: ${AGENT_MEMORY_ENABLED:true}
  trade-plan-enabled: ${AGENT_TRADE_PLAN_ENABLED:false}
  vector-store: ${AGENT_VECTOR_STORE:mongodb}
  embedding:
    enabled: ${AGENT_EMBEDDING_ENABLED:false}
    provider: ${AGENT_EMBEDDING_PROVIDER:disabled}
    base-url: ${AGENT_EMBEDDING_BASE_URL:http://localhost:11434}
    api-key: ${AGENT_EMBEDDING_API_KEY:ollama}
    model: ${AGENT_EMBEDDING_MODEL:nomic-embed-text}
    model-version: ${AGENT_EMBEDDING_MODEL_VERSION:nomic-embed-text-v1}
    dimension: ${AGENT_EMBEDDING_DIMENSION:768}
    allow-hash-fallback: ${AGENT_EMBEDDING_ALLOW_HASH_FALLBACK:false}
```

配置变更必须继续通过现有运行配置管理和日志审计机制完成，不由模型修改。

## 12. 实施顺序

1. 本文档和二期需求文档落库。
2. 在 `stock-backend/pom.xml` 引入 LangChain4j 核心模块和 OpenAI-compatible 模块，并统一使用版本属性。
3. 增加 LangChain4j 配置属性和 `ChatModel` Bean。
4. 将现有 `AgentModelProvider` 改造为 LangChain4j Adapter。
5. 实现 ToolDefinition、ToolGateway、工具审计和第一个只读工具。
6. 实现 Research、Diagnostic、Report 的结构化输出适配。
7. 实现 LangChain4j `ChatMemory` 与现有 Session/Memory 对接。
8. 实现 LangChain4j EmbeddingModel 和 Mongo EmbeddingStore 适配器。
9. 将 RAG 查询迁移到 LangChain4j Embedding 抽象，保留 Mongo 权限过滤。
10. 接入 TradePlanDraft、证据校验和既有 TradePlan 执行链路。
11. 增加审计表、审计服务和脱敏策略。
12. 完成源码静态检查、依赖一致性检查和文档契约检查。

## 13. 验证边界

本设计不宣称编译、测试、模型调用、MongoDB 向量检索或真实交易已经验证。按照项目 `AGENTS.md` 约束，后续只进行源码和文件级检查，编译、测试、启动和运行验证由用户自行执行。

## 14. 下一阶段实现边界

下一阶段只实现 Agent 的受控编排闭环，不直接实现券商执行：

```text
AgentApplicationService
  -> AgentRuntimeRequest
  -> LangChain4j ChatModel
  -> AgentRuntimeResponse
  -> AgentTask 状态与审计
```

### 14.1 输入与输出契约

- `AgentRuntimeRequest` 只允许任务类型、会话标识、用户范围、系统约束和用户问题；不允许 SQL、Mongo 查询、URL、Bean 名称、方法名或任意工具名称。
- `AgentRuntimeResponse` 必须包含状态、提供方、模型标识、受限文本结果、证据摘要和错误码；模型不可用时返回 `DEGRADED`，不能伪造 `COMPLETED`。
- 单次模型调用继续使用 `AgentProperties` 的超时、最大输入、最大输出和会话消息上限；不能在业务方法中重新组装 HTTP 协议。

### 14.2 Tool Gateway 接入边界

- 当前阶段只把固定只读工具接入显式的运行时协调器，不允许 LangChain4j 自动扫描 Spring Bean。
- 工具调用必须先经过 `ToolGateway`，再进入具体查询服务；失败、超时和未授权分别记录标准错误码。
- 工具返回结果只作为证据，不允许直接转换为交易订单或修改业务事实。

### 14.3 任务状态规则

```text
CREATED -> RUNNING -> COMPLETED
                  \\-> DEGRADED
                  \\-> FAILED
                  \\-> CANCELLED
```

- `DEGRADED` 仅表示模型不可用但结构化事实摘要仍可返回。
- `FAILED` 表示工具、参数、超时或结构化结果不可用，不能继续执行交易计划。
- 交易计划仍保持 `PENDING_REVALIDATION`，必须由 `tradingExecutor` 重新读取事实并完成风险门禁。

### 14.4 本阶段明确不做

- 不新增人工审批接口。
- 不把自由文本解析为券商订单。
- 不引入自动工具循环、子 Agent、外部向量数据库或新的定时调度注解。
- 不执行编译、测试、启动、模型调用、数据库迁移或线上验证。

详细文件级任务和静态验收项见《二期-LangChain4j-实施计划》。

## 15. 结构化输出与显式工具阶段

下一阶段采用单一 Runtime 入口：

```text
业务任务
  -> AgentRuntimeService
  -> LangChain4j ChatModel
  -> StructuredOutputParser
  -> AgentStructuredResult
```

- Research、Diagnostic、Report 分别使用明确的结果 DTO，不再以任意自由文本作为业务结果契约。
- Tool Gateway 只向 Runtime 暴露显式注册的只读工具；工具参数和返回值必须是当前 DTO/Record，不接受字符串化 SQL、Mongo 查询或反射信息。
- 工具结果先进入证据集合，再进入结构化输出提示；模型不得直接修改任务状态、交易计划或配置。
- 结构化解析失败直接进入 `FAILED`，不做多套 JSON 修复器或旧格式兼容。
- 交易计划继续只允许生成 `TradePlanDraft`，实际交易由 `tradingExecutor` 重新取数并执行风险门禁。

本阶段完成后，删除旧的直接自由文本编排路径，避免同一任务存在两套结果入口。
<!-- AI_GENERATE_END --- -->
