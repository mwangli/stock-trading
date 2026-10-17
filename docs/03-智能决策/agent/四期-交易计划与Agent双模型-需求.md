<!-- AI_GENERATE_START -- -->
# AGENT 四期交易计划与双模型接入需求

## 1. 阶段定位

第四阶段承接三期结构化编排，建立 TradePlanDraft、业务事实证据和前端 Agent 工作台。本期优先完成只读事实复核闭环，同时接入 DeepSeek API 与本地 Ollama DeepSeek R1 7B 两种模型来源。

本期不允许 Agent 直接调用券商写接口，不改变 `TRADING_REAL_WRITE_ENABLED` 的真实交易门禁，不新增人工审批接口和新的定时注解。

## 2. 建设目标

1. Agent 只能输出经过严格校验的 `TradePlanDraft`，不能输出券商订单对象。
2. 候选批次、预测批次、策略版本、交易日和证据时间必须可追溯且一致。
3. 通过固定只读工具获取交易日历、预测候选、策略状态和券商事实摘要。
4. Agent 模型支持 DeepSeek API、Ollama 本地模型和自动选择模式。
5. DeepSeek 与 Ollama 可以同时配置和初始化；选择模式决定实际调用的 Provider。
6. 两个 Provider 都关闭、配置不完整或均不可用时，接口返回明确错误码和用户可理解的处理建议。
7. 前端提供独立 Agent 页面，并保留策略配置页的上下文入口。

## 3. 模型配置需求

### 3.1 Provider 模式

`agent.provider` 允许：

- `deepseek`：强制使用 DeepSeek API。
- `ollama`：强制使用本地 Ollama OpenAI-compatible API。
- `auto`：优先使用 DeepSeek，失败后使用已配置的 Ollama；两个 Provider 都可同时配置并由运行时懒加载。
- `disabled`：Agent 不调用模型，只返回禁用状态。

### 3.2 DeepSeek 配置

- `agent.deepseek.enabled`：是否启用 DeepSeek。
- `agent.deepseek.base-url`：默认使用 DeepSeek OpenAI-compatible 地址。
- `agent.deepseek.api-key`：从 `DEEPSEEK_API_KEY` 或现有 `.env` 的 `DS_API_KEY` 注入，禁止写入前端或日志。
- `agent.deepseek.model`：模型名称。
- API Key 不得写入日志、接口响应、任务结果、审计摘要或前端代码。

### 3.3 Ollama 配置

- `agent.ollama.enabled`：是否启用 Ollama。
- `agent.ollama.base-url`：默认 `http://localhost:11434/v1`。
- `agent.ollama.api-key`：可选兼容字段，默认使用非敏感占位值，不作为安全凭据。
- `agent.ollama.model`：默认 `deepseek-r1:7b`。
- Ollama 不可连接时必须返回 `OLLAMA_UNAVAILABLE`，不能伪造模型完成。

### 3.4 配置错误

- Provider 为 `deepseek` 但 DeepSeek 未启用或缺少 API Key：返回 `DEEPSEEK_NOT_CONFIGURED`。
- Provider 为 `ollama` 但 Ollama 未启用或模型名为空：返回 `OLLAMA_NOT_CONFIGURED`。
- Provider 为 `auto` 且两个 Provider 都未启用：返回 `NO_AGENT_PROVIDER_ENABLED`。
- Provider 为未知值：返回 `INVALID_AGENT_PROVIDER`。
- 两个 Provider 同时配置但全部调用失败：返回 `ALL_AGENT_PROVIDERS_UNAVAILABLE`。

## 4. TradePlanDraft 需求

Draft 至少包含：

- `candidateBatchId`
- `predictionBatchId`
- `stockCode`
- `direction`
- `plannedAmount`
- `plannedQuantity`
- `priceConstraint`
- `targetTradeDate`
- `strategyVersion`
- `expiresAt`
- `reason`
- `riskWarnings`
- `evidenceRefs`
- `uncertainty`
- `idempotencyKey`

Draft 只表示策略建议，不表示已通过风险校验，不表示已提交委托，不表示已经成交。

## 5. 前端 Agent 工作台

新增 `/agent` 页面，至少展示：

- 当前 Agent 总开关和 Provider 状态；
- DeepSeek、Ollama 的启用、配置和可用性状态；
- 会话消息列表、发送状态、错误提示和重试入口；
- 结构化结果类型、任务状态、证据引用和不确定性；
- 交易策略页面的候选批次和当前策略版本上下文入口。

策略配置页继续保留 Agent 对话按钮，点击后跳转或打开统一 Agent 页面，并携带当前候选批次和策略版本上下文。

## 6. 非功能约束

- 所有后端接口使用 `ResponseDTO<T>` 和明确 DTO。
- 所有工具必须只读并通过 `ToolGateway` 注册。
- 不允许模型传入 SQL、Mongo 查询、URL、Bean 名称或反射方法名。
- 不新增 `@Scheduled`。
- 不执行真实交易写入。
- 前端不保存 API Key，不显示完整 API Key，只显示是否已配置和脱敏状态。

## 7. 验收标准

| 编号 | 验收标准 |
|---|---|
| AGENT4-AC-001 | Agent 输出只能通过 `TradePlanDraft` 结构化校验。 |
| AGENT4-AC-002 | 候选批次与预测批次、策略版本和目标交易日能够完成一致性校验。 |
| AGENT4-AC-003 | 证据包含来源、版本、事实时间、状态和错误码。 |
| AGENT4-AC-004 | DeepSeek、Ollama 可独立开关，`auto` 模式可识别两个 Provider。 |
| AGENT4-AC-005 | 两个 Provider 都关闭时返回 `NO_AGENT_PROVIDER_ENABLED`，前端展示明确错误。 |
| AGENT4-AC-006 | API Key 不进入日志、任务结果、审计结果和前端资源。 |
| AGENT4-AC-007 | `/agent` 页面可创建会话、发送 Research 请求并展示结构化结果。 |
| AGENT4-AC-008 | 策略页面可以将候选批次和策略版本作为 Agent 上下文传递。 |
| AGENT4-AC-009 | 本期不新增券商写入、人工审批和定时注解。 |

## 8. 验证边界

本期默认只执行文档、源码、UTF-8、控制字符、AI 标记、契约和差异检查。未获得额外授权时，不执行 Maven 编译、前端构建、测试、应用启动、模型调用、数据库迁移或线上验证。

源码检查通过不等于编译通过；Provider 配置完成不等于模型网络调用成功；TradePlanDraft 落库不等于交易执行成功。
<!-- AI_GENERATE_END -->
