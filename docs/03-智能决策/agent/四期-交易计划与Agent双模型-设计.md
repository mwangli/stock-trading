<!-- AI_GENERATE_START -- -->
# AGENT 四期交易计划与双模型接入设计

## 1. 总体链路

```text
Agent 页面 / 策略页面上下文
  -> AgentController
  -> AgentApplicationService
  -> EvidenceAssembler
       -> TradingCalendarQueryTool
       -> PredictionCandidateQueryTool
       -> StrategyStateQueryTool
       -> BrokerFactQueryTool
  -> AgentRuntimeService
  -> LangChain4jAgentModelProvider
       -> DeepSeek ChatModel
       -> Ollama ChatModel
  -> TypedResultValidator
  -> AgentTask 持久化
  -> TradePlanDraft 校验
  -> PENDING_REVALIDATION
```

模型只接收应用服务生成的脱敏证据和上下文。模型不能改变证据来源、事实时间、用户范围和交易门禁。

## 2. 双 Provider 设计

### 2.1 配置对象

`AgentProperties` 保留公共运行参数，并增加两个独立的 Provider 配置对象：

| 配置对象 | 关键字段 |
|---|---|
| `DeepSeekProperties` | enabled、baseUrl、apiKey、model |
| `OllamaProperties` | enabled、baseUrl、apiKey、model |
|

公共 `agent.provider` 只负责选择策略，不再承担两个 Provider 的密钥存储职责。DeepSeek 配置优先读取 `DEEPSEEK_API_KEY`，同时兼容项目现有 `.env` 中的 `DS_API_KEY`；旧的通用 `agent.api-key` 不再作为新配置入口，避免多个密钥字段含义冲突。

### 2.2 Bean 初始化

- DeepSeek 使用 LangChain4j `OpenAiChatModel`，base URL、API Key 和 model 从 DeepSeek 配置读取。
- Ollama 使用同一 OpenAI-compatible Adapter，默认地址为 `/v1`，API Key 只作为协议兼容字段。
- `LangChain4jAgentModelProvider` 按 Provider 配置懒加载并缓存模型实例，避免两个 `ChatModel` Bean 注入歧义。
- 两个 Provider 可以同时配置；`auto` 模式按 DeepSeek、Ollama 顺序尝试，调用失败才进入下一个已配置 Provider。

### 2.3 Provider Router

Router 根据 `provider` 和 Bean 可用性选择模型：

- `deepseek` 只选择 DeepSeek；
- `ollama` 只选择 Ollama；
- `auto` 先选择 DeepSeek，调用失败且允许本地降级时选择 Ollama；
- `disabled` 直接返回 `AGENT_DISABLED`；
- 无可选模型返回 `NO_AGENT_PROVIDER_ENABLED`，不发送空请求。

Router 对外只返回 Provider 名称、模型名、可用状态和脱敏错误信息，不返回 API Key。

## 3. 事实和证据

### 3.1 EvidenceItem

| 字段 | 说明 |
|---|---|
| evidenceId | 应用服务生成的稳定证据编号 |
| sourceType | TOOL、STRATEGY、PREDICTION、BROKER_READ、RAG、MEMORY |
| sourceId | 批次、版本、工具或文档标识 |
| sourceVersion | 事实版本或工具版本 |
| asOf | 事实采集时间 |
| status | AVAILABLE、UNKNOWN、FAILED、EXPIRED |
| summary | 脱敏、截断后的事实摘要 |
| errorCode | 失败或未知时的标准错误码 |

### 3.2 固定工具

| 工具 | 数据来源 | 只读输出 |
|---|---|---|
| `queryTradingCalendar` | `TradingCalendarService` | 日期、开闭市状态、确认状态 |
| `queryPredictionCandidate` | `PredictionCandidateQueryService`、`RankingRepository` | 候选批次、预测批次、模型版本、候选摘要 |
| `queryStrategyState` | `StrategyStateManager`、`StrategyConfigService` | 策略开关、模式、配置版本、指标状态 |
| `queryBrokerFacts` | `BrokerAdapter` | 账户、持仓、当日订单和成交的脱敏摘要 |

工具输入使用专用 Request DTO，禁止字符串化查询和任意对象透传。

### 3.3 EvidenceAssembler

`EvidenceAssembler` 负责：

1. 按任务类型选择工具集合；
2. 调用 `ToolGateway` 并保留成功、未知、失败和超时结果；
3. 为证据生成稳定编号；
4. 对结果脱敏、限长和限量；
5. 将证据引用白名单传给模型；
6. 检查模型引用的证据编号必须属于本次任务集合，交易计划遇到不可用事实时直接阻断。

## 4. TradePlanDraft 和持久化

`TradePlanDraft` 通过单独的结构化解析器处理，不复用 Research、Diagnostic、Report 的结果类型。

Draft 校验必须包括：

- 方向只能是 `BUY` 或 `SELL`；
- 所有方向都必须有正数计划金额，SELL 还必须有有效数量；
- 股票代码、候选批次、预测批次不能为空；
- 目标交易日、预测批次和策略版本必须与候选批次事实一致；
- 过期时间不能早于当前时间，且不能超过系统允许窗口；
- 证据引用全部存在并且状态允许；
- 幂等键不能为空且长度受限。

`AgentTradePlanEntity` 增加预测批次、目标交易日、策略版本和 Draft JSON 时，需要同步 Flyway 与 Response DTO。

## 5. Agent 页面设计

前端新增 `pages/agent/index.tsx`，负责：

- 创建会话；
- 查询 Runtime 状态；
- 发送 Research 请求；
- 解析并展示任务状态、结构化结果、证据引用和不确定性；
- 根据错误码显示 DeepSeek/Ollama 配置建议；
- 支持从 URL 查询参数接收 `candidateBatchId`、`strategyVersion` 和 `stockCode` 上下文。

`strategyConfig` 页面不再维护一套独立的 Agent 消息渲染逻辑，而是通过导航参数进入统一 Agent 页面。迁移完成后保留单一 Agent 对话入口。

## 6. 执行边界

本期只完成 TradePlanDraft 创建和 `PENDING_REVALIDATION` 落库。执行服务仍需在后续阶段补齐：

- 当前账户、持仓、行情和订单的完整复核；
- 数据库条件抢占；
- 风控拒绝和券商异常的状态映射；
- 执行失败恢复与最终状态确认。

Agent 不得注入或调用 `TradeExecutor`、`RiskController`、`BrokerAdapter` 和 `ZXBrokerAdapter`。

## 7. 安全设计

- API Key 仅通过环境变量或安全配置注入；
- 配置状态接口只返回 `configured`、`enabled`、`model` 和 Provider 状态；
- 日志只记录 Provider、模型名、错误码和 traceId；
- 不记录完整 Prompt、Authorization Header、Cookie、Token 或 API Key；
- Ollama 的兼容 API Key 不视为真实凭据，也不能被前端编辑或展示。
<!-- AI_GENERATE_END -->
## 8. 本地启动与调试约定

### 8.1 启动前检查

- Flyway 迁移版本必须唯一；新增交易计划字段使用 `V31__extend_agent_trade_plan_phase4.sql`，不得与既有 `V30__create_sys_user_preference.sql` 重号。
- 修改迁移文件名后必须清理 `target/classes`，避免旧资源残留导致启动时仍发现重复版本。

### 8.2 Provider 验收矩阵

| 模式 | 关键配置 | 预期结果 |
|---|---|---|
| DeepSeek | `AGENT_PROVIDER=deepseek`、`AGENT_DEEPSEEK_ENABLED=true` | 状态为已配置，Research 返回 `COMPLETED` 或明确的远程 API 错误 |
| Ollama | `AGENT_PROVIDER=ollama`、`AGENT_OLLAMA_ENABLED=true` | 状态为已配置，Research 返回 `COMPLETED` 或明确的本地连接/模型错误 |
| 自动降级 | `AGENT_PROVIDER=auto`，两个 Provider 均开启 | 优先 DeepSeek，调用失败后尝试 Ollama |
| 无 Provider | 两个 Provider 均关闭 | 返回 `NO_AGENT_PROVIDER_ENABLED` |
<!-- AI_GENERATE_END -->
