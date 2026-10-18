<!-- AI_GENERATE_START - -->
# AGENT 五期 EmbeddingModel 配置与 RAG/Memory 边界设计

## 1. 设计结论

ChatModel 与 EmbeddingModel 必须独立配置、独立健康检查、独立版本管理。EmbeddingModel 不复用 DeepSeek Chat 的地址、API Key 或模型名称。

第五阶段只完成 EmbeddingModel 配置解耦、Provider 状态和向量元数据；RAG 与长期 Memory 的正式能力归入第六阶段。

## 2. Provider 配置

```yaml
agent:
  embedding:
    enabled: false
    provider: disabled
    base-url: http://localhost:11434
    api-key: ollama
    model: nomic-embed-text
    model-version: nomic-embed-text-v1
    dimension: 768
    timeout-ms: 30000
    allow-hash-fallback: false
```

支持：

- `disabled`：不创建 EmbeddingModel；RAG/Memory 语义检索不可用；
- `ollama`：使用本地 Ollama EmbeddingModel；
- `openaiCompatible`：使用独立的 OpenAI-compatible Embedding 服务。

禁止直接把 DeepSeek Chat 配置当作 Embedding 配置。远程 Embedding 服务必须使用独立的 URL、Key、模型和版本。

## 3. 向量一致性

RAG Chunk 和 Memory Vector 必须保存：

- `embeddingProvider`；
- `embeddingModelVersion`；
- `embeddingDimension`；
- `embeddingStatus`。

检索时只允许查询向量维度和模型版本一致的文档。更换模型或维度后必须重建向量，不能混合检索。

`hash-32-v1` 只允许作为明确开启的开发环境降级方案，生产环境默认关闭。

## 4. RAG/Memory 阶段边界

第五阶段：

- 完成 EmbeddingModel 配置解耦；
- 完成 Provider 状态和降级状态展示；
- 不让 RAG/Memory 覆盖实时行情、账户、持仓、交易日和风控事实；
- 交易计划只使用结构化工具事实作为执行依据。

第六阶段：

- 完成真实 Embedding Provider 和批量重建；
- 完成 RAG 文档版本、权限、过期和可见性治理；
- 完成长期 Memory 的确认、纠错、遗忘和审计；
- 将 RAG/Memory 结果转换为 `EvidenceItem`，经过状态和权限校验后才允许进入 Agent 上下文。

## 5. 安全约束

- API Key 只存在后端环境变量或安全配置；
- 前端只展示 Provider、模型版本、维度和脱敏状态；
- RAG/Memory 不得直接决定交易方向、金额、数量或执行状态；
- Embedding 不可用时禁止伪造 `READY`。
<!-- AI_GENERATE_END - -->
