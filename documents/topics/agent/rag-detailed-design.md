---
id: agent-rag-detailed-design
title: RAG 详细设计与实施说明
sidebar_position: 2
owner: agent-knowledge
type: design
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /topics/agent/rag-detailed-design/
slug: /agent/knowledge/rag-detailed-design/
source_paths:
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/AgentRagService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/RagBusinessInitializationListener.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/RagBusinessInitializationService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/AgentKnowledgeContextService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/enums/KnowledgeMode.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/AgentApplicationService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/tool/KnowledgeSearchTool.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/EmbeddingArtifactService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/client/DjlEmbeddingModel.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/EmbeddingModelWarmup.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/LangChainEmbeddingService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/service/ProjectDocumentSearchService.java
  - stock-server/stock-agent/src/main/java/com/stock/agent/config/AgentProperties.java
  - stock-server/Dockerfile
  - .github/workflows/ci-cd.yml
---

<DocMetadata />

# RAG 详细设计与实施说明

## 1. 文档目标

本文档定义 `stock-agent` 中 RAG 与本地 Embedding 的实际实现、运行边界、数据契约、就绪判定、失败降级和部署验证方式。文档以当前源码为准，不把配置存在、端口可访问或前端显示为绿色等同于功能已经完成运行验证。

当前证据等级为 `SOURCE_IMPLEMENTED`：源码和制品结构已完成静态检查，本次未执行 Maven 编译、应用启动、真实 DJL 推理、MongoDB 摄取检索或浏览器验证。

## 2. 设计目标

| 目标 | 设计要求 |
|---|---|
| 本地可控 | 使用仓库发布的 BGE 固定制品，不在运行时下载、转换或覆盖模型 |
| 单一向量契约 | Provider、模型版本、维度、Pooling、归一化和最大长度共同确定向量兼容性 |
| 真实就绪 | 只有模型完成加载并可执行推理时才报告 `READY` |
| 失败隔离 | Embedding 失败只降级 RAG 和向量 Memory，不阻止 Agent 普通对话和确定性工具 |
| 用户隔离 | 私有文档只能由相同服务端认证 `userScope` 检索 |
| 可追溯 | 每个 Chunk 保存来源、版本、内容哈希、模型元数据和事实时间 |
| 交易安全 | RAG 只提供背景知识，不能替代 Java 实时事实、风控、幂等或券商执行 |

## 3. 非目标

- 不把 RAG 作为行情、持仓、账户、候选批次或交易状态的权威来源。
- 不允许 Agent 根据 RAG 内容直接生成或提交券商订单。
- 不在运行时自动下载、转换、修复或改写模型制品。
- 不为旧模型向量提供隐式兼容；向量契约变化后必须重建。
- 当前不引入独立向量数据库、在线学习平台或多套并行检索框架。

## 4. 当前问题与根因

### 4.1 表现

前端能力状态中 Agent 与 Memory 可用，但 RAG 和 Embedding 长期显示“未就绪”。模型文件实际存在，权重文件也未被修改。

### 4.2 根因一：懒加载状态无法自行转为 READY

`DjlEmbeddingModel` 仅在首次调用 `embed` 时加载模型。原运行状态只读取 `isReady()`，状态接口本身不会触发加载，导致没有摄取或检索请求前始终返回未就绪。

修复后由 `EmbeddingModelWarmup` 监听 `ApplicationReadyEvent`，在 HTTP 服务启动完成后主动调用 `ensureReady()`。加载成功后状态变为 `READY`；失败时保留错误摘要并继续提供其他 Agent 能力。

### 4.3 根因二：模型制品治理过度

原实现同时维护 manifest、文件大小、SHA-256、README 和多份 Tokenizer 元数据，CI 与运行时又重复检查同一批文件。这些检查不能使损坏模型恢复可用，只增加构建耗时、启动耗时和维护分支。

当前实现只保留权重、`config.json`、`tokenizer.json` 以及情感模型加载所需的 `serving.properties`。发布流程只负责把模型文件放入不可变基础镜像，运行时解析配置版本目录后直接交给 DJL；文件缺失、LFS 指针未展开或模型不可加载时，由 DJL 自然失败并记录错误。

## 5. 总体架构

| 层次 | 组件 | 职责 |
|---|---|---|
| HTTP | `AgentKnowledgeController` | 接收 RAG 摄取与检索请求，从认证上下文解析用户范围 |
| 业务 | `AgentRagService` | 文本切分、元数据治理、候选过滤、权限过滤和相似度排序 |
| 向量适配 | `LangChainEmbeddingService` | 统一生成向量，校验维度，暴露模型版本、Provider 和运行状态 |
| 本地模型 | `DjlEmbeddingModel` | 使用 DJL、PyTorch 和 HuggingFace Tokenizer 加载 BGE 并执行推理 |
| 目录解析 | `EmbeddingArtifactService` | 根据配置解析固定版本目录，不重复读取或校验模型内容 |
| 启动预热 | `EmbeddingModelWarmup` | 应用就绪后主动加载本地模型，失败时降级而不阻断 Agent |
| 业务来源 | `BusinessRagSource` 实现 | 从受控业务接口或业务集合生成标准知识文档，不直接写向量库 |
| 初始化编排 | `RagBusinessInitializationListener`、`RagBusinessInitializationService` | 在模型预热后采集各来源并执行幂等同步，单来源失败相互隔离 |
| 持久化 | MongoDB `agent_rag_chunks` | 保存 Chunk、向量、来源、权限和向量契约元数据 |
| 状态汇总 | `AgentRuntimeManagementService` | 向前端返回 Embedding 配置、状态、模型版本和错误摘要 |

## 6. 模型制品设计

### 6.1 目录

| 项目 | 当前值 |
|---|---|
| 根目录 | `model-files/embedding` |
| 版本目录 | `bge-base-zh-v1.5` |
| 权重文件 | `bge-base-zh-v1.5.pt` |
| 引擎 | PyTorch |
| 预期维度 | 768 |
| 最大序列长度 | 512 |
| Pooling | CLS |
| 归一化 | L2 |

Docker Profile 使用 `/app/model-files/embedding`。生产服务器的 `model-files` 目录通过 Compose 只读挂载到 `/app/model-files`，Backend 镜像不包含固定模型文件。

### 6.2 运行时加载

运行时只确认模型版本已配置且目录不能逃逸模型根目录，随后由 DJL 加载权重和 Tokenizer。加载失败时不修改文件、不尝试修复，也不生成额外的“制品检查”状态，只记录底层错误并保持 Embedding 不可用。

## 7. 启动与就绪状态机

| 状态 | 条件 | RAG 行为 |
|---|---|---|
| `DISABLED` | Agent 或 Embedding 明确关闭，或 Provider 为 disabled | 拒绝向量能力 |
| `INITIALIZING` | 配置完整，Bean 已创建，但模型尚未完成加载且暂无失败原因 | 暂不接收正式摄取或检索 |
| `READY` | DJL 模型已加载；远程 Provider 则 Embedding Bean 可用 | 允许摄取和检索 |
| `UNAVAILABLE` | 模型加载或状态读取失败 | 返回明确不可用，不伪造空结果 |
| `HASH_FALLBACK` | 仅开发环境显式允许 Hash fallback | 可生成临时向量，但不作为正式 RAG READY 数据 |

RAG 页面就绪条件为 `agent.ragEnabled=true` 且 Embedding 状态为 `READY`。MongoDB 的最终可写可读性仍需通过一次认证后的摄取和检索进行运行验证。

## 8. 摄取详细设计

### 8.1 输入契约

| 字段 | 必填 | 含义 |
|---|---|---|
| `sourceType` | 否 | 来源类型，默认 `DOCUMENT` |
| `sourceId` | 否 | 来源标识，缺省时生成 UUID |
| `version` | 否 | 来源版本，默认 `v1` |
| `content` | 是 | 待切分正文 |
| `stockCodes` | 否 | 关联股票代码 |
| `tags` | 否 | 业务标签 |
| `visibility` | 否 | 可见范围，默认 `PRIVATE` |

服务端用户范围来自认证会话，不接受客户端自行指定。

### 8.2 处理流程

1. 检查 RAG 开关和正文非空。
2. 解析服务端 `userScope`。
3. 按最多 1200 字符切分正文。
4. 计算每个 Chunk 的 SHA-256 内容哈希。
5. 调用当前 READY Embedding 模型生成向量。
6. 校验向量维度等于配置维度。
7. 写入来源、版本、权限、向量契约和事实时间。
8. 使用 `{sourceId}-{version}-{index}` 作为 Chunk ID，相同来源版本重复写入时覆盖对应 Chunk。

当前摄取不是跨 Chunk 事务。任一中间 Chunk 失败时，之前已写入的 Chunk 可能保留；批量导入前应增加批次状态与失败恢复，而不是让模型自行推断导入完整性。

### 8.3 业务系统自动初始化

Agent 在 `ApplicationReadyEvent` 后按以下顺序执行一次初始化：

```text
EmbeddingModelWarmup，Order=100
  → Embedding READY
  → RagBusinessInitializationListener，Order=200
  → 遍历 BusinessRagSource
  → 生成 BusinessRagSourceDocument
  → AgentRagService.synchronizeBusinessSource
  → 内容哈希幂等判断
  → 全部 Chunk 向量生成成功
  → 写入未激活新版本
  → 激活新版本
  → 停用同来源旧版本
```

配置 `agent.ragAutoInitializationEnabled` 控制该流程，当前 `application.yml` 默认通过环境变量 `AGENT_RAG_AUTO_INITIALIZATION_ENABLED=true` 开启。RAG、Agent 或 Embedding 关闭，以及 Embedding 未达到 `READY` 时，初始化保守跳过，不阻止普通 Agent 能力启动。

首批自动来源如下：

| 数据分类 | 实际来源 | RAG 来源类型 | 权限 | 说明 |
|---|---|---|---|---|
| 研究资料 | Backend `/api/internal/agentFacts/modelOperations?mode=VERSIONS` | `RESEARCH_MATERIAL` | `SHARED` | 保存最近模型版本、离线 Rank IC、Top10% 收益等已治理评估摘要 |
| 复盘结论 | Agent MongoDB `agent_trade_plan.reviewComment` | `REVIEW_CONCLUSION` | `PRIVATE` | 按交易计划所属 `userScope` 隔离用户批准或拒绝意见 |
| 用户原始文档 | `POST /api/agent/rag/documents` | `DOCUMENT` | 默认 `PRIVATE` | 只能由用户上传、粘贴或由明确的数据接入功能导入，不从文件系统和浏览器隐式采集 |

下列数据明确不进入自动初始化：

- 实时行情、账户、持仓、委托、成交和候选批次，它们必须由 Java 工具实时查询。
- 密码、Token、Cookie、券商凭据和完整账户明细。
- 未经确认的模型自由推断和普通聊天内容。
- 仓库 `documents/`，避免和项目文档索引形成双份事实源。

### 8.4 自动来源幂等和版本切换

业务来源同步不直接复用客户端指定版本，而是计算：

```text
sourceContentHash = SHA-256(来源全文)
vectorContract = provider + modelVersion + dimension
version = SHA-256(sourceContentHash + vectorContract) 的前 16 位
```

同一 `sourceType + sourceId + userScope` 已存在相同内容和向量契约的活动 Chunk 时直接跳过。内容变化或向量契约变化时，先在内存完成所有向量生成，再批量写入未激活新版本；新版本整体激活后才停用旧版本。写入失败会删除本次未完成版本，旧活动版本继续可检索。

当前自动流程是“每次 Agent 启动幂等重跑”，用于初始化和重启后的增量补齐，不是日内轮询任务。若后续需要周期刷新，必须由 Backend 的 `JobConfig + JobSchedulerService` 统一调度受控同步入口，禁止在 Agent 新增 `@Scheduled`。

## 9. 检索详细设计

### 9.1 输入契约

| 字段 | 必填 | 约束 |
|---|---|---|
| `query` | 是 | 空值返回空列表，调用方不得把空列表解释为事实不存在 |
| `limit` | 否 | 默认 5，范围 1 至 20 |
| `sourceType` | 否 | 精确过滤来源类型 |
| `version` | 否 | 精确过滤来源版本 |
| `asOfFrom` | 否 | 最早事实时间 |
| `asOfTo` | 否 | 最晚事实时间，不得早于起始时间 |

### 9.2 候选过滤

MongoDB 候选必须同时满足：

- `active=true`。
- 模型版本等于当前 `modelVersion`。
- Provider 等于当前 `provider`。
- 向量维度等于当前 `dimension`。
- `embeddingStatus=READY`。
- 可选来源、版本和时间范围匹配。

取回候选后再执行权限判断。`PRIVATE` 文档仅允许相同 `userScope`；当前其他 visibility 被视为共享，新增可见范围前必须补充明确枚举与授权规则。

### 9.3 排序

当前实现使用应用层余弦相似度排序并截取前 N 条，适合中小规模知识库。候选量扩大后应迁移到 MongoDB Vector Search，但必须保留现有模型契约过滤、用户隔离和事实时间语义。

## 10. MongoDB 文档模型

| 字段 | 类型 | 说明 |
|---|---|---|
| `chunkId` | String | `sourceId-version-index`，MongoDB 主键 |
| `sourceType` | String | 来源类型 |
| `userScope` | String | 服务端认证用户范围 |
| `sourceId` | String | 来源标识 |
| `version` | String | 来源版本 |
| `content` | String | Chunk 正文 |
| `stockCodes` | List | 股票标签 |
| `tags` | List | 普通业务标签 |
| `visibility` | String | 可见范围 |
| `contentHash` | String | 正文 SHA-256 |
| `sourceContentHash` | String | 来源全文 SHA-256，用于自动来源幂等和版本替换 |
| `embeddingModelVersion` | String | 向量模型版本 |
| `embeddingProvider` | String | 向量 Provider |
| `embeddingDimension` | Integer | 向量维度 |
| `embeddingStatus` | String | 生成时模型状态 |
| `embedding` | `List<Double>` | 归一化向量 |
| `active` | boolean | 是否可检索 |
| `asOf` | LocalDateTime | 文档事实时间 |
| `createdAt` | LocalDateTime | 创建时间 |

现有复合索引用于来源版本和活动可见性筛选。当前未声明 MongoDB 原生向量索引。

## 11. HTTP API

| 方法与路径 | 请求 | 响应 | 权限 |
|---|---|---|---|
| `POST /api/agent/rag/documents` | `RagIngestRequest` | `ResponseDTO<Integer>`，返回 Chunk 数 | 登录用户，用户范围由服务端解析 |
| `POST /api/agent/rag/search` | `RagSearchRequest` | `ResponseDTO<List<RagResult>>` | 登录用户，私有数据按用户隔离 |
| `POST /api/agent/chat` | `AgentChatRequest`，包含 `knowledgeMode` | `ResponseDTO<AgentChatResponse>` | 登录用户，按三态契约绑定或执行 RAG |
| `GET /api/agent/runtime/status` | 无 | `RuntimeStatus` | 返回 Embedding 配置、状态、模型版本和失败摘要 |

RAG 接口路径当前包含固定子路径且无路径参数，不涉及路径参数末尾约束。

## 12. 与 Agent 对话的集成边界

普通 `/api/agent/chat` 和内部 Research 请求统一携带 `knowledgeMode`。API 决定本轮采用哪一种知识策略，Java 不再通过关键词表猜测用户意图；默认 `AUTO` 时，由模型根据工具说明和问题语义决定是否调用受控 `searchKnowledge`。

### 12.1 三态 API 契约

| 模式 | API 语义 | 模型调用前 | 模型可见工具 | 失败行为 |
|---|---|---|---|---|
| `NONE` | 调用方明确禁止访问业务知识库 | 不检索 | 不绑定 `searchKnowledge` | 不产生 RAG 依赖 |
| `AUTO` | 默认，由模型按语义判断是否需要历史知识 | 不预检索 | 绑定 `searchKnowledge` | 工具返回 `available=false`，普通回答和其他工具继续 |
| `REQUIRED` | 调用方要求本轮必须先查知识库 | Java 强制执行一次检索并注入 Evidence | 不绑定 `searchKnowledge`，避免重复检索 | 未就绪、失败或无匹配时注入 `RAG000/UNKNOWN`，回答必须显式说明 |

请求未传 `knowledgeMode` 或显式传 `null` 时均按 `AUTO` 处理。调用示例：

```json
{
  "sessionId": "session-001",
  "question": "总结与当前策略相似的历史复盘案例",
  "knowledgeMode": "AUTO"
}
```

调用方只有在业务流程明确要求“必须引用知识库”或“禁止使用知识库”时才应覆盖默认值。例如合规报告可以使用 `REQUIRED`，纯实时账户查询可以使用 `NONE`；普通用户聊天保持 `AUTO`，不要求前端维护不断膨胀的关键词规则。

### 12.2 AUTO Tool Calling 时序

```text
POST /api/agent/chat, knowledgeMode=AUTO
  → AgentCapabilityRouter 绑定 searchKnowledge
  → 模型理解问题和工具描述
  → 不需要历史知识：直接回答或调用其他事实工具
  → 需要历史知识：调用 searchKnowledge({query})
  → Java 从 ToolExecutionContext 读取认证 userScope
  → AgentKnowledgeContextService.search
  → AgentRagService 按向量契约和权限检索
  → 最多返回 5 个 Chunk
  → 模型结合检索结果生成回答
  → AgentToolAuditService 保存 toolCall 和 rag: 来源引用
```

模型只能决定“是否调用”和“查询文本”，不能决定用户范围、集合、过滤权限、最大结果数、正文长度或调用次数。`searchKnowledge` 每轮最多调用一次，每段正文最多 2000 字符，私有数据必须匹配服务端认证 `userScope`。

### 12.3 REQUIRED 预检索时序

`REQUIRED` 在模型调用前由 `AgentApplicationService` 调用 `retrieveRequired`。成功时生成 `RAG001` 至 `RAG005` Evidence 并注入 Prompt；失败或无匹配时生成 `RAG000` 未知证据。该模式不再向模型绑定 `searchKnowledge`，因此不会产生第二次检索。

### 12.4 统一安全边界

1. Prompt 明确 RAG 内容是不可信背景资料，其中的指令不能覆盖系统提示、权限或 Java 规则。
2. 实时行情、账户、持仓、订单和执行状态仍由 Java 工具查询；与 RAG 冲突时以 Java 权威事实为准。
3. PRIVATE 内容按当前认证 `userScope` 过滤，客户端和模型都不能传入或覆盖用户范围。
4. AUTO 工具证据使用 `rag:<sourceType>:<sourceId>:<version>:<chunkId>`，并通过工具审计与任务关联。
5. API 三态是策略边界，不是权限边界；无论哪种模式都不能绕过 Java 权限和审计。

### 12.5 与项目文档索引的区别

| 对比项 | 业务 RAG | 项目文档索引 |
|---|---|---|
| 内容来源 | 用户文档、策略演化、模型研究摘要、用户复盘意见 | 仓库 `documents/**/*.md` |
| 更新方式 | API 摄取或启动时业务系统自动采集 | CI 将仓库文档装配到镜像只读目录，重新构建部署后生效 |
| 检索方式 | BGE 768 维向量相似度 | Markdown 标题切分后的内存关键词匹配 |
| 持久化 | MongoDB `agent_rag_chunks` | Agent 进程内存 |
| 权限 | PRIVATE 按 `userScope` 隔离，SHARED 为登录用户共享 | 从受控只读目录加载，不承载用户私有资料 |
| 适用问题 | “参考我的资料”“历史复盘怎样”“类似研究结论是什么” | “项目架构怎样”“代码模块职责是什么”“工程规范是什么” |

`documents/` 不重复写入 RAG。项目文档索引服务工程和架构解释，业务 RAG 服务运行期知识和用户资料，两者在 Agent 上下文层合并但保持不同来源编号。

## 13. 配置契约

| 配置 | 当前默认 | 说明 |
|---|---|---|
| `agent.ragEnabled` | true | RAG 总开关 |
| `agent.ragCollection` | `agent_rag_chunks` | MongoDB 集合 |
| `agent.ragAutoInitializationEnabled` | true | Embedding 就绪后自动初始化受控业务知识 |
| `agent.projectDocuments.enabled` | true | 是否启用项目工程文档检索 |
| `agent.projectDocuments.path` | `./documents` | 相对进程工作目录；本地从仓库根启动，容器继承固定的 `WORKDIR /app` |
| `agent.embedding.enabled` | true | Embedding 开关 |
| `agent.embedding.provider` | `djl` | 本地 Provider |
| `agent.embedding.model` | `bge-base-zh-v1.5` | 模型名称 |
| `agent.embedding.modelPath` | `./model-files/embedding` | 本地制品根目录，可由环境变量覆盖 |
| `agent.embedding.modelVersion` | `bge-base-zh-v1.5` | 向量契约版本 |
| `agent.embedding.dimension` | 768 | 预期向量维度 |
| `agent.embedding.maxSequenceLength` | 512 | Tokenizer 最大长度 |
| `agent.embedding.poolingMode` | `cls` | Pooling 模式 |
| `agent.embedding.normalize` | true | 是否 L2 归一化 |
| `agent.embedding.allowHashFallback` | false | 生产禁止伪向量降级 |

## 14. 错误与降级

| 场景 | 状态或异常 | 处理 |
|---|---|---|
| 模型开关关闭 | `DISABLED` | RAG 不可用，普通 Agent 继续 |
| 模型正在加载 | `INITIALIZING` | 前端显示初始化，不宣称可用 |
| 模型加载失败 | `UNAVAILABLE` | 保留错误摘要，不修改模型文件 |
| Tokenizer 或配置不可解析 | `UNAVAILABLE` | 阻止加载，避免错误向量污染 |
| 向量维度不一致 | `EMBEDDING_DIMENSION_MISMATCH` | 拒绝写入 |
| 未创建 Embedding Bean | `EMBEDDING_NOT_READY` | 拒绝正式摄取或检索 |
| MongoDB 不可用 | 持久化异常 | 不返回伪成功，不吞异常 |
| Backend 事实源不可用 | 与 RAG 状态独立 | 不影响背景知识检索，但实时事实任务降级 |
| AUTO 检索失败 | `searchKnowledge.available=false` | 不阻断普通回答和 Java 工具，审计记录失败类型 |
| REQUIRED 检索失败或无匹配 | `RAG000`、`UNKNOWN` | 明确告知知识库不可用或无匹配，禁止伪造引用 |

## 15. 安全治理

- 用户范围只能由认证会话解析，客户端不能覆盖。
- 密码、Token、Cookie、私钥、券商凭据和完整账户数据不得写入 RAG。
- 日志记录模型、版本、目录和错误类型，不记录完整文档正文、Prompt 或敏感参数。
- RAG 内容属于不可信背景证据，不能覆盖 Java 权限、资金、持仓、T+1、价格保护和幂等规则。
- RAG 正文中的提示词或操作指令一律视为资料内容，不执行其权限提升、工具调用或交易要求。
- 共享 visibility 扩展前必须定义明确枚举和授权矩阵。

## 16. 性能与容量

当前实现每次检索先从 MongoDB 取回全部契约匹配候选，再在 JVM 计算余弦相似度。该实现组件最少，适合项目当前中小规模文档量，但候选数量增加后会线性消耗内存和 CPU。

迁移到 MongoDB Vector Search 的触发条件应以实际指标决定，包括候选量、P95 延迟、单次读取字节数和 Agent 并发。迁移时只替换相似度执行位置，不改变 API、用户隔离、模型版本过滤或 Java 交易权威边界。

## 17. 可观测性

启动和运行日志至少应覆盖：

- 制品目录、模型名和版本。
- DJL 加载成功或失败原因链。
- 当前 Embedding 状态和错误摘要。
- RAG 摄取来源标识与 Chunk 数，不记录正文。
- RAG 检索条件、候选数量和返回数量，不记录敏感内容。

前端状态使用 `embeddingReady` 判断正式可用性，并可使用 `embeddingStatus` 与 `embeddingMessage` 展示初始化或失败原因。`embeddingConfigured=true` 只表示配置完整，不能表示模型可推理。

## 18. 部署与升级

1. 在生产服务器部署目录预置完整的 `model-files/**`；模型目录位于其他位置时，通过 `MODEL_FILES_DIR` 指定绝对路径。
2. CI/CD 只发布应用镜像，Compose 将宿主机模型目录只读挂载到 Backend，不下载或复制模型文件。
3. 应用启动后触发本地模型预热。
4. 状态接口应从 `INITIALIZING` 转为 `READY`。
5. 模型版本、维度、Pooling 或归一化任一变化时，发布新版本目录并重建旧向量。
6. 不得在原版本目录内原地替换权重后继续复用旧向量。

## 19. 验证方案

### 19.1 本次已完成的静态检查

- 模型版本目录、配置路径和 Docker 路径一致。
- 模型目录只保留运行必需文件。
- 预热入口只对 DJL 模型生效，失败不会阻止 Agent 启动。
- 状态区分 `INITIALIZING`、`READY` 与 `UNAVAILABLE`。
- 摄取和检索均绑定服务端用户范围。
- 检索过滤模型版本、Provider、维度和 READY 状态。
- 自动来源通过 Backend 内部只读客户端或 Agent 自有业务 Repository 采集，没有跨库访问 Backend 数据库。
- 自动来源使用全文哈希和向量契约生成不可变版本，写入失败前不会停用旧活动版本。
- 通用对话和 Research 已接入 `NONE`、`AUTO`、`REQUIRED` 三态 API 契约，不再依赖关键词触发策略。
- `AUTO` 只绑定一次受控 `searchKnowledge` Tool Calling；`REQUIRED` 只执行一次 Java 预检索；`NONE` 不绑定也不预检索。
- RAG 结果限制为最多 5 个 Chunk、单段 2000 字符；REQUIRED Evidence 总摘要最多 8000 字符。

### 19.2 需要用户执行的编译与运行验证

| 验证项 | 通过标准 |
|---|---|
| Maven 编译 | `stock-agent` 编译成功 |
| 应用启动 | Agent 启动后无 DJL 加载异常 |
| 状态接口 | `embeddingStatus=READY` 且 `embeddingReady=true` |
| 首次推理 | 生成 768 维向量，无维度异常 |
| RAG 摄取 | 认证用户写入文档后返回正数 Chunk 数 |
| RAG 检索 | 同一用户可检索，其他用户不能读取 PRIVATE 文档 |
| 自动初始化 | 启动日志出现来源汇总，MongoDB 存在 `BUSINESS_KNOWLEDGE`、`RESEARCH_MATERIAL`；存在复核意见时生成 `REVIEW_CONCLUSION` |
| 初始化幂等 | 无业务变化时重启记录为 unchanged，活动 Chunk 数不增长 |
| 来源更新 | 修改复核意见或工程文档后重启，新版本活动、旧版本 `active=false` |
| AUTO Tool Calling | 历史复盘问题调用 `searchKnowledge` 并返回 `rag:` 引用；普通闲聊不调用该工具 |
| REQUIRED 预检索 | 请求传 `REQUIRED` 时模型调用前产生 `RAGxxx` Evidence，且不再调用 `searchKnowledge` |
| NONE 禁用 | 请求传 `NONE` 时不产生 RAG 检索和 `rag:` 工具审计记录 |
| 路由隔离 | 项目源码问题调用项目文档工具；当前持仓和订单问题调用 Backend 工具而非 RAG |
| 来源失败隔离 | 暂停 Backend 时 Backend 来源失败，但本地复核来源和普通 Agent 启动不被阻断 |
| 重启一致性 | 重启后仍能检索已有同版本向量 |
| 降级验证 | 临时使用错误路径时仅 RAG/向量 Memory 降级，普通 Agent 仍可用 |

未经上述运行验证，不能把本文档状态提升为 `RUNTIME_VERIFIED`。

## 20. 验收标准

1. 固定模型权重未损坏时，应用就绪后 Embedding 能主动加载并最终报告 `READY`。
2. Windows 文本换行差异不再阻止本地模型加载。
3. 权重缺失、截断或内容不可加载时，DJL 会阻止模型进入 READY。
4. 模型失败原因可通过状态或日志定位，不再只显示笼统“未就绪”。
5. RAG 摄取和检索使用相同模型版本、Provider 和维度。
6. PRIVATE 文档严格按服务端用户范围隔离。
7. Embedding 失败不影响普通对话、Provider 和确定性工具。
8. RAG 不参与真实交易最终决策，Java 风控与执行保持最终权威。
9. Agent 启动且 Embedding READY 后自动执行一次业务知识初始化，无变化时不重复生成向量。
10. API 使用 `REQUIRED` 时失败和无匹配必须可见；`AUTO` 工具失败不能阻断普通回答。
11. 用户复核意见只允许相同 `userScope` 检索，共享策略和模型研究资料不包含账户或订单数据。
12. 项目文档保持关键词索引单一事实源，不重复写入业务 RAG。
13. `AUTO` 是否检索由模型 Tool Calling 决定，但用户隔离、一次调用上限、结果数量和正文长度仍由 Java 强制控制。

## 21. 风险与回退

| 风险 | 规避或回退 |
|---|---|
| 预热增加启动后的 CPU 与内存峰值 | 在应用就绪后加载；失败降级，不阻断其他能力 |
| 删除 manifest 和重复完整性检查 | 模型内容由 DJL 实际加载结果判定 |
| 旧向量与新模型混用 | 检索强制过滤版本、Provider、维度和 READY 状态 |
| 应用层全量候选排序变慢 | 指标达到阈值后替换为 MongoDB Vector Search |
| 将 RAG 背景误作实时事实 | Prompt 和 Evidence 明确来源，实时事实只走 Java 工具 |

如需回退主动预热，可恢复懒加载，但状态会在首次向量请求前保持未就绪，不建议作为长期方案。
