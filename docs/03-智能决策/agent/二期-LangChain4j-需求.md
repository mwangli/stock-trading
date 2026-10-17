<!-- AI_GENERATE_START -- -->
# AGENT 二期 LangChain4j 需求

## 1. 文档定位

本文档定义 AGENT 二期引入 LangChain4j 后的业务需求和验收标准，不描述具体 Java 类、数据库字段或实现代码。详细技术方案见《AGENT 二期 LangChain4j 设计》。

二期继续使用现有 `strategyAgent` 模块和 `com.stock.strategyAgent` 包名，不新建平行的 `com.stock.agent` 领域模块。

## 2. 建设目标

1. 使用 LangChain4j 统一模型调用、AI Service、Tool Calling、结构化输出、会话记忆和 Embedding 抽象。
2. 保留项目自己的 `ToolGateway`、权限控制、脱敏、审计和交易安全边界。
3. 让 Agent 能够基于受控工具完成 Research、Diagnostic、Report 和 TradePlan 决策。
4. 继续使用现有 MongoDB 保存 RAG 文档、Chunk、Embedding 和 Memory 语义索引。
5. 自动交易不设置人工审批，TradePlan 生成后自动进入 `tradingExecutor` 的事实重读、风控和执行链路。
6. LangChain4j 不得直接调用券商写接口、修改交易配置或覆盖结构化业务事实。

## 3. 技术约束

### 3.1 LangChain4j 版本和依赖策略

- LangChain4j 基线版本：`1.22.0`。
- 项目 Java 版本为 Java 17，满足 LangChain4j 基础模块要求。
- 当前项目 Spring Boot 为 `3.2.10`，不直接引入要求 Spring Boot 3.5+ 的 LangChain4j Spring Boot Starter。
- 先引入 LangChain4j 核心模块和 OpenAI-compatible 模型模块，由项目现有 Spring Bean 管理生命周期和配置。
- DeepSeek 通过 OpenAI-compatible API 接入，不在业务代码中继续手写 HTTP 请求协议。
- API Key、模型地址和模型名只能从环境变量或受控运行配置读取。

### 3.2 MongoDB 约束

- 不新增 Qdrant、Milvus、Redis Vector 或独立向量数据库。
- 继续使用现有 MongoDB 保存 RAG 和 Memory 语义文档。
- LangChain4j 的 Embedding 和 EmbeddingStore 抽象必须适配现有 Mongo 文档结构。
- 当前单节点 MongoDB 先使用应用层相似度检索；不得假设 MongoDB Atlas Vector Search 在本地单节点上可用。
- RAG 不能替代实时行情、账户、持仓、交易日历、风控和券商订单事实。

## 4. 功能需求

### 4.1 LangChain4j Agent Runtime

- 支持通过 LangChain4j `ChatModel` 执行模型调用。
- 支持结构化输出映射到明确 DTO。
- 支持最大模型轮次、最大工具调用次数、输入长度、输出长度和超时限制。
- 模型调用失败、超时、结构化输出失败时，任务必须进入明确失败状态。
- 不得使用模型自由文本直接驱动交易执行。

### 4.2 Tool Gateway

所有模型工具调用必须经过项目自己的 Tool Gateway。

Tool Gateway 必须提供：

- 工具名称白名单
- 工具版本
- 参数 DTO 校验
- 只读权限判断
- 用户范围校验
- 最大返回量
- 超时控制
- 敏感字段脱敏
- 调用审计
- 错误码归一化

一期只读工具不得直接暴露数据库、Mongo 查询表达式、SQL、URL、Bean 名称、方法名或 Shell。

### 4.3 Research

- 用户可以查询候选、预测、情感、策略、运行状态和历史证据。
- 回答必须带 Evidence Reference。
- RAG 内容只能作为背景证据，结构化工具事实优先。
- 来源缺失、时间过期、批次冲突时必须输出不确定状态。
- 不得输出收益保证或确定性交易承诺。

### 4.4 Diagnostic

- 使用固定诊断工具和检查器获取交易日、数据、模型、策略、任务和风控事实。
- LangChain4j 只负责组织诊断结论，不负责重新计算交易日或风控结果。
- 诊断结果必须区分 `PASS`、`WARN`、`BLOCKED` 和 `UNKNOWN`。

### 4.5 Report

日报必须使用固定结构化 Report DTO，至少包含：

- 市场概览
- 候选摘要
- 模型信号
- 情感信号
- 策略状态
- 风险提示
- 数据质量
- 异常任务
- 证据引用
- 不确定性

### 4.6 RAG

- 支持文档摄取、Chunk 切分、Embedding、版本管理、权限过滤、时间过滤、来源过滤和删除/禁用。
- Chunk 必须保留 `sourceType`、`sourceId`、`version`、`asOf`、`visibility`、`contentHash` 和 `embeddingModelVersion`。
- Embedding 由 LangChain4j `EmbeddingModel` 生成。
- MongoDB 继续保存 Embedding 和 payload。
- 检索结果必须带来源、版本、时间和相似度。
- RAG 不可用时，Research 可以降级为结构化工具；TradePlan 必须因证据不足停止生成或拒绝执行。

### 4.7 Memory

- 支持会话记忆、用户偏好、语义记忆、事件记忆和交易复盘记忆。
- LangChain4j `ChatMemory` 只负责短期会话上下文；长期 Memory 仍由项目 MemoryService 管理。
- 长期 Memory 必须经过类型、来源、置信度、敏感字段、TTL 和用户范围校验。
- Memory 删除、纠错和过期必须同步更新 MongoDB 语义索引。
- 未经用户确认的模型推断不得直接变成交易规则或用户偏好。

### 4.8 TradePlan 自动交易

- 不增加人工审批状态和人工审批接口。
- Agent 只能生成结构化 TradePlan，不得生成券商订单号、会话信息或直接下单参数。
- TradePlan 必须包含股票代码、方向、数量或金额建议、价格约束、有效期、理由、证据引用、风险提示、置信度、候选批次和幂等键。
- 计划生成后自动进入：

```text
Agent
 -> TradePlanValidator
 -> tradingExecutor 重新读取当前事实
 -> RiskController
 -> TradeExecutor
 -> ZXBrokerAdapter
```

- `TRADING_REAL_WRITE_ENABLED=false` 时，任何 Agent 路径不得调用券商写接口。
- 过期、批次不一致、资金不足、持仓不满足、T+1 不满足、交易时间不符、风控失败或幂等冲突必须拒绝并留痕。

## 5. 非功能需求

- 默认 Agent、RAG、TradePlan 和外部模型均关闭或受控启用。
- 同一会话低并发运行，默认并发数为 1。
- 模型调用必须有超时、最大输出长度和有限重试。
- Prompt、工具参数摘要、模型版本和响应摘要必须可审计，但禁止保存密钥、Token、Cookie、券商会话和完整敏感响应。
- 不影响数据采集、模型训练、策略计算和现有交易任务的主链路。
- 接口仍使用 `ResponseDTO<T>` 和明确 DTO。

## 6. 验收标准

| 编号 | 验收标准 |
|---|---|
| LC4J-AC-001 | 模型调用通过 LangChain4j `ChatModel` 或 AI Service 完成，不再由业务代码手写 OpenAI-compatible HTTP JSON 协议。 |
| LC4J-AC-002 | 所有模型工具调用经过项目 Tool Gateway 白名单和参数 DTO 校验。 |
| LC4J-AC-003 | Tool Gateway 不允许模型传入 SQL、Mongo 查询、URL、Bean 名称或方法名。 |
| LC4J-AC-004 | Research、Diagnostic、Report 输出映射为明确结构化 DTO，并保留证据引用。 |
| LC4J-AC-005 | RAG 使用 LangChain4j Embedding 抽象，MongoDB 继续保存 Chunk、Embedding 和 payload。 |
| LC4J-AC-006 | RAG 结果按来源、版本、时间和用户范围过滤，且不覆盖实时结构化事实。 |
| LC4J-AC-007 | Memory 支持会话上下文和长期记忆分层，删除、纠错、TTL 和脱敏规则继续生效。 |
| LC4J-AC-008 | TradePlan 不包含人工审批状态，生成后自动进入 Java 二次取数、风险复核和执行链路。 |
| LC4J-AC-009 | `TRADING_REAL_WRITE_ENABLED=false` 时，Agent 不调用任何券商写接口。 |
| LC4J-AC-010 | LangChain4j、RAG 或外部模型不可用时，Research 可安全降级，TradePlan 必须停止或拒绝。 |
| LC4J-AC-011 | 模型调用、工具调用、RAG 检索和 Memory 变更均有脱敏审计摘要。 |
| LC4J-AC-012 | 当前 Spring Boot 3.2.10 环境不依赖不兼容的 Spring Boot 3.5+ Starter。 |

## 7. 实施边界

本文档只定义第二阶段需求。后续实现必须先完成设计文档，再修改 Maven 依赖和 Java 源码。按照项目规范，本阶段不自动执行编译、测试、启动或真实交易验证。
<!-- AI_GENERATE_END --->
