---
id: agent-knowledge
title: Knowledge 能力
sidebar_position: 1
owner: agent-knowledge
type: capability
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /agent/knowledge/
source_paths:
  - stock-agent/src/main/java/com/stock/agent/knowledge/README.md
  - stock-agent/src/main/java/com/stock/agent/knowledge/rag/service/AgentRagService.java
  - stock-agent/src/main/java/com/stock/agent/knowledge/memory/service/AgentMemoryService.java
  - stock-agent/src/main/java/com/stock/agent/knowledge/rag/embedding/LangChainEmbeddingService.java
---

<DocMetadata />

**IDEA 源码入口**：[RAG 服务 `AgentRagService.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/knowledge/rag/service/AgentRagService.java) · [长期记忆 `AgentMemoryService.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/knowledge/memory/service/AgentMemoryService.java) · [向量生成 `LangChainEmbeddingService.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/knowledge/rag/embedding/LangChainEmbeddingService.java)

# knowledge 业务包

## 模块定位

`knowledge` 管理 Agent 的 RAG、Embedding 和长期 Memory，为当前任务提供可追溯的知识证据与受控连续性。它不是行情、持仓、候选、任务或交易状态的权威来源。

## 能力划分

| 能力 | 保存内容 | 用途 |
|---|---|---|
| RAG | 项目知识、业务规则和可检索文档片段 | 为当前任务补充外部知识证据 |
| Memory | 用户确认偏好、纠正、系统事实和历史经验 | 在后续会话中保持受控连续性 |
| Chat Memory | 当前会话最近消息 | 由 `runtime.memory` 维护短期上下文 |

RAG 文档不会自动成为用户偏好，模型推断也不能未经确认写成长期事实。当前实时事实必须通过只读工具重新查询。

## 目录结构

```text
knowledge/
├── controller                 # RAG 与 Memory 接口
├── memory
│   ├── service/repository/entity/dto/model
└── rag
    ├── service/embedding/entity/dto
```

## RAG 流程与 Embedding 契约

```text
文档导入
  → 文本分块和来源元数据
  → 本地 Embedding
  → MongoDB 向量文档
  → 查询向量和相似片段检索
  → 带来源的受控上下文
```

Embedding 使用 `model-files/embedding/bge-base-zh-v1.5` 的本地 DJL 模型。向量维度、归一化、Pooling、最大序列长度和模型版本共同构成向量契约；模型版本或维度变化后必须按版本重建旧向量，不能混合比较。

## Memory 生命周期

Memory 来源区分用户确认、系统事实、模型推断和业务结果。长期 Memory 必须绑定用户范围，记录来源、创建时间和有效状态，并支持纠正、遗忘和过期。用户纠正通过标记旧记录并建立新事实实现，不直接覆盖历史记录。

密码、Token、Cookie、完整账户信息、券商凭据及其他敏感数据不得进入 Memory、RAG 文档或向量元数据。

## 依赖与降级边界

- 可依赖 `shared` 的认证、响应和配置基础能力。
- Embedding 与 MongoDB 持久化属于本包内部实现。
- 不依赖 `integration`，不调用 Backend 事实 API。
- `interaction` 和 `runtime` 通过明确 Service 使用本包，不直接操作 Repository。
- Embedding 未就绪时，RAG 和向量 Memory 返回明确不可用状态，不使用哈希向量或空结果伪装成功。
- 不依赖检索的普通聊天和其他 Agent 用例仍可继续。

## 扩展约束

新增文档来源时应定义来源标识、用户范围、切分规则、保留策略和重建方案。新增 Memory 类型时应明确写入授权、纠正方式、有效期和敏感字段规则。

## 重点能力详细设计

### RAG 摄取

`AgentRagService.ingest` 要求 `agent.ragEnabled=true` 且内容非空。一次摄取流程如下：

1. `sourceId` 为空时生成 UUID，`version` 默认 `v1`。
2. 按固定 1200 字符切分，当前实现不做语义段落重叠。
3. Chunk ID 使用 `{sourceId}-{version}-{index}`，相同标识保存时覆盖对应文档。
4. 计算 SHA-256 `contentHash`。
5. 写入模型版本、Provider、维度、Embedding 状态和向量。
6. 默认 `sourceType=DOCUMENT`、`visibility=PRIVATE`、`active=true`。
7. 保存股票标签、普通标签、`asOf` 和创建时间。

当前设计适合中小规模知识库。由于检索阶段在应用层计算相似度，扩大数据量前需要引入 MongoDB Vector Search 或其他向量索引，并保持现有过滤和权限语义。

### RAG 检索

检索数量默认 5，限制为 1 至 20。MongoDB 候选过滤条件包括：

- `active=true`。
- 可选 `sourceType`、`version` 和 `asOf` 时间范围。
- `embeddingModelVersion`、`embeddingProvider` 和 `embeddingDimension` 必须与当前运行配置一致。
- `embeddingStatus=READY`。

候选取回后在应用层计算余弦相似度，按分数降序截取。`PRIVATE` 文档只允许 `userScope` 完全一致的用户访问，其他 visibility 当前视为共享可见。检索结果返回 Chunk、来源、版本、内容、相似度和事实时间。

### Embedding 契约与状态

`LangChainEmbeddingService` 支持 DJL、本地或 OpenAI-compatible Provider。向量维度必须与 `agent.embedding.dimension` 一致，否则抛出 `EMBEDDING_DIMENSION_MISMATCH`。

生产默认 `allowHashFallback=false`，模型未就绪时抛出 `EMBEDDING_NOT_READY`。若开发环境显式开启 Hash fallback，会生成 32 维向量并报告 `HASH_FALLBACK`；当前 RAG 搜索只读取 `READY` 向量，因此 fallback 结果不会伪装成正式可检索知识。

状态值包括 `DISABLED`、`UNAVAILABLE`、`READY` 和 `HASH_FALLBACK`。DJL 模型通过 `EmbeddingArtifactService` 解析本地已发布制品，Pooling、最大序列长度和 L2 归一化均属于向量契约。

### Memory 创建与敏感信息治理

创建 Memory 时必须提供 `memoryType`、`sourceType` 和内容。规则包括：

| 项目 | 约束 |
|---|---|
| 内容长度 | 最大 4000 字符 |
| TTL | 默认配置值，最终限制为 1 至 3650 天 |
| Confidence | 归一到 0 至 1，默认 0.5 |
| 用户范围 | 必须来自服务端认证上下文 |
| 禁止关键字 | password、token、cookie、私钥、密码 |
| 二次脱敏 | `password/token/cookie/secret=...` 替换为 `[REDACTED_SECRET]` |

Memory 类型包括 `SESSION`、`PREFERENCE`、`SEMANTIC`、`EPISODIC`、`TRADING`；来源包括 `USER_CONFIRMED`、`SYSTEM_FACT`、`MODEL_INFERRED`、`TRADE_RESULT`。状态包括 `ACTIVE`、`CORRECTED`、`FORGOTTEN`、`EXPIRED`。

### Memory 与向量一致性

创建 Memory 后同步创建 `agent_memory_vectors` 文档。忘记操作将主记录标记为 `FORGOTTEN`，同时把向量 `active=false`。纠正操作把来源改为 `USER_CONFIRMED`，重新生成向量；向量不存在时重新创建。

查询有效 Memory 前会扫描并把过期的 `ACTIVE` 记录更新为 `EXPIRED`。当前实现只更新主记录状态，后续如依赖 Memory 向量检索，应补充过期向量同步停用机制并设计批量过期任务。

## 接口与集合

| API | 功能 |
|---|---|
| `POST /api/agent/memories` | 创建 Memory |
| `GET /api/agent/memories` | 查询当前用户有效 Memory |
| `DELETE /api/agent/memories/{memoryId}` | 忘记 Memory |
| `POST /api/agent/memories/correct/{memoryId}` | 纠正 Memory |
| `POST /api/agent/rag/documents` | 摄取 RAG 文档 |
| `POST /api/agent/rag/search` | 检索 RAG |

集合包括 `agent_memory`、`agent_memory_vectors` 和配置指定的 `agent_rag_chunks`。

## 扩展设计要求

变更 Embedding 模型、维度、Pooling 或归一化方式时必须生成新模型版本并重建向量。新增 visibility 时必须显式实现授权规则，不能依赖“非 PRIVATE 即公开”的默认判断。大规模文档导入需要增加批次、幂等、删除旧版本和失败恢复设计。
