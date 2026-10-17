<!-- AI_GENERATE_START - -->
# AGENT 二期 LangChain4j 实施计划

## 1. 当前阶段目标

在已有 LangChain4j 依赖、配置、`AgentModelProvider`、`ToolGateway`、RAG 和 Memory 骨架的基础上，完成一条可审计的受控 Agent Runtime 编排链路。

本阶段只处理 Research、Diagnostic 和 Report 的模型调用编排，不改变 `tradingExecutor` 的执行权责，不生成可直接提交给券商的订单对象。

## 2. 实施顺序

### 步骤一：运行时契约

- 增加明确的 Runtime 请求、响应和错误码 DTO。
- 统一输入长度、输出长度、模型提供方、会话标识和任务标识。
- 区分 `COMPLETED`、`DEGRADED`、`FAILED` 和 `CANCELLED`。

### 步骤二：模型适配

- 保留 LangChain4j `ChatModel` 作为唯一模型调用入口。
- 统一处理模型未配置、模型 Bean 不存在、超时和空响应。
- 短期 `ChatMemory` 只保存受限会话消息，不保存密码、Token、Cookie、券商会话和完整账户快照。

### 步骤三：工具网关编排

- Runtime 不接受任意工具名、SQL、Mongo 查询、URL 或反射信息。
- 只通过显式注册的 `ToolGateway` 工具获取结构化证据。
- 工具执行结果必须携带状态、来源、时间和错误码，并写入脱敏审计。

### 步骤四：应用服务接入

- `AgentApplicationService` 使用 Runtime 请求/响应，而不是直接拼接模型结果。
- 任务失败时落库为 `FAILED`；模型不可用但事实摘要可返回时落库为 `DEGRADED`。
- 研究、诊断和报告共用同一套调用边界，保留各自的业务系统提示词。

### 步骤五：静态核查

- 检查所有新增或修改的 Java 文件拥有 AI 生成边界标记。
- 检查 Agent 代码中不存在 `@Scheduled`、`/approve`、`/reject`、券商写接口和任意 SQL/Mongo 输入。
- 检查响应均使用 `ResponseDTO<T>` 和明确 DTO。
- 检查 Maven 依赖版本只有一个 `langchain4j.version` 来源。
- 执行 UTF-8、控制字符、`git diff --check` 和定向符号扫描。

## 3. 文件范围

| 文件 | 目的 |
|---|---|
| `runtime/AgentRuntimeRequest.java` | 运行时请求契约 |
| `runtime/AgentRuntimeResponse.java` | 运行时响应契约 |
| `runtime/AgentRuntimeStatus.java` | 运行时状态枚举 |
| `runtime/AgentRuntimeService.java` | 统一模型调用、降级和输出限制 |
| `runtime/AgentStructuredOutput.java` | Research、Diagnostic、Report 统一结构化结果 |
| `runtime/AgentStructuredOutputParser.java` | 严格 JSON 解析和结构校验 |
| `service/AgentApplicationService.java` | 将 Research、Diagnostic、Report 接入 Runtime |
| `api/AgentDtos.java` | 对外任务响应补充降级状态和错误码 |
| `docs/03-智能决策/agent/二期-LangChain4j-设计.md` | 登记阶段边界与状态规则 |

## 4. 静态验收标准

| 编号 | 标准 |
|---|---|
| LC4J-IMPL-001 | 业务服务不直接调用 `ChatModel`，统一经过 Runtime Service。 |
| LC4J-IMPL-002 | 模型未配置、Bean 不存在或模型异常时不会伪造成功状态。 |
| LC4J-IMPL-003 | 模型输出受到最大长度限制，输入受到最大长度限制。 |
| LC4J-IMPL-004 | Runtime 不接受任意工具名、SQL、Mongo 查询、URL 或反射参数。 |
| LC4J-IMPL-005 | Tool Gateway 超时、未授权和执行异常使用不同错误码。 |
| LC4J-IMPL-006 | TradePlan 仍停留在 Java 二次校验边界，不由 Runtime 直接执行。 |
| LC4J-IMPL-007 | 本阶段不新增 `@Scheduled`、人工审批接口或券商写调用。 |
| LC4J-IMPL-008 | 模型成功输出必须解析为结构化结果，解析失败进入 `FAILED`。 |
| LC4J-IMPL-009 | Runtime 只保留一个 `AgentRuntimeRequest` 入口，不保留旧自由文本 Provider 方法。 |

## 5. 验证边界

本阶段只做源码和文件级检查。未授权时不执行 Maven 编译、测试、启动、模型调用、MongoDB 查询、数据库迁移、浏览器验证或线上验证。源码检查通过不等于编译通过。

## 6. 新项目重构约束

- Agent Runtime 只保留一个权威入口，不再增加旧 Provider 的兼容 Facade 或重复调用方法。
- 结构化输出直接使用当前 DTO 契约；旧的自由文本结果只作为过渡实现，完成迁移后删除冗余路径。
- Tool Calling 只允许显式注册工具；不为历史工具名、旧参数或旧 URL 增加兼容映射。
- RAG、Memory 和 TradePlan 的实现以当前设计为准，允许直接替换现有骨架，不保留无调用方的旧实现。
<!-- AI_GENERATE_END - -->
