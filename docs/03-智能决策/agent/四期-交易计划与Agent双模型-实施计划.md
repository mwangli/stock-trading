<!-- AI_GENERATE_START -- -->
# AGENT 四期交易计划与双模型实施计划

## 1. 实施原则

先文档和契约，再代码；先只读事实和模型路由，再 TradePlanDraft；本期不打开真实交易写入。

工作区中与本需求无关的既有修改必须保留，不进行回滚、格式化或顺带重构。

## 2. 实施波次

### 波次一：文档和契约

- 固化四期需求、设计和实施计划。
- 定义 Provider 配置、错误码、TradePlanDraft、EvidenceItem 和前端页面边界。
- 明确旧 `agent.api-key` 不作为新的双 Provider 配置入口。

### 波次二：双 Provider Runtime

- 扩展 `AgentProperties` 为公共配置、DeepSeek 配置和 Ollama 配置。
- 配置 DeepSeek 和 Ollama 两个 OpenAI-compatible Provider，并由运行时按需创建和缓存 ChatModel。
- 由 `LangChain4jAgentModelProvider` 实现双 Provider 路由和状态 DTO。
- 更新 Runtime 状态接口，区分启用、配置完整、Provider 选择和调用失败。
- 两个 Provider 都关闭时返回 `NO_AGENT_PROVIDER_ENABLED`。

### 波次三：只读事实工具和证据编排

- 实现交易日历、预测候选、策略状态和券商事实工具。
- 增加 `EvidenceItem`、`EvidenceCollection` 和 `EvidenceAssembler`。
- 将固定工具结果统一编号、脱敏、截断并传给 Agent Runtime。
- 保留工具失败证据，不以空值或零值代替。

### 波次四：TradePlanDraft

- 新增 Draft 请求、响应和 `AgentStructuredOutputParser` 严格解析分支。
- Agent 只生成 Draft，不生成券商订单。
- 校验候选批次、预测批次、策略版本、目标交易日、证据引用白名单、有效期和幂等键。
- 扩展 `agent_trade_plan` 持久化字段和查询 DTO。
- 默认保持 `AGENT_TRADE_PLAN_ENABLED=false`。

### 波次五：前端 Agent 工作台

- 新增 `/agent` 路由、菜单和中英文文案。
- 展示 Runtime Provider 状态、模型错误、会话、任务状态、结构化结果和证据。
- 策略配置页 Agent 按钮改为进入统一 Agent 页面并传递候选上下文。
- 不在前端保存、编辑或展示 API Key。

### 波次六：启动与双模型验收

- 检查所有新增和修改文件的 UTF-8、控制字符、AI 标记和行尾空白。
- 检查 `ResponseDTO<T>`、DTO 字段注释、类和 public 方法 Javadoc。
- 检查没有新增 `@Scheduled`、审批接口、券商写入或任意查询入口。
- 检查配置错误码与前端提示文案一致。
- 检查前后端接口路径、请求字段和响应字段一致。
- 清理构建产物后启动后端，确认 Flyway 迁移版本唯一且应用进入可服务状态。
- DeepSeek 使用 `DEEPSEEK_API_KEY` 或现有 `.env` 的 `DS_API_KEY` 注入，验证状态查询和一次 Research 请求。
- Ollama 使用本地 OpenAI-compatible 地址和 `deepseek-r1:7b`（或本机实际模型名），验证状态查询和一次 Research 请求。
- `AGENT_PROVIDER=auto` 时验证 DeepSeek 优先、失败后 Ollama 降级；两个 Provider 均关闭时验证 `NO_AGENT_PROVIDER_ENABLED`。

## 3. 计划文件

### 后端

- `config/AgentProperties.java`
- `config/LangChain4jConfiguration.java`
- `runtime/AgentModelProvider.java`
- `runtime/LangChain4jAgentModelProvider.java`
- `tool/read/TradingCalendarQueryTool.java`
- `tool/read/PredictionCandidateQueryTool.java`
- `tool/read/StrategyStateQueryTool.java`
- `tool/read/BrokerFactQueryTool.java`
- `evidence/EvidenceItem.java`
- `evidence/EvidenceAssembler.java`
- `runtime/TradePlanDraft.java`
- `runtime/AgentStructuredOutputParser.java`
- `service/TradePlanAgentService.java`
- `service/TradePlanValidator.java`
- `service/TradePlanService.java`
- `api/AgentController.java`
- `api/AgentDtos.java`
- `domain/entity/AgentTradePlanEntity.java`
- `db/migration/V31__extend_agent_trade_plan_phase4.sql`
- `application.yml`
- `pom.xml`（仅在 Ollama 专用依赖确实必要时修改）

### 前端

- `src/pages/agent/index.tsx`
- `src/App.tsx`
- `src/layouts/DashboardLayout.tsx`
- `src/locales/zh.json`
- `src/locales/en.json`
- `src/pages/strategyConfig/index.tsx`
- `src/pages/agent/styles.css`（仅页面专属样式）

## 4. 静态验收标准

| 编号 | 标准 |
|---|---|
| AGENT4-IMPL-001 | 双 Provider 配置字段与环境变量名称一致，API Key 不出现在响应和日志。 |
| AGENT4-IMPL-002 | `deepseek`、`ollama`、`auto`、`disabled` 路由状态明确。 |
| AGENT4-IMPL-003 | 两个 Provider 同时配置不会产生 Spring Bean 歧义。 |
| AGENT4-IMPL-004 | 两个 Provider 都关闭时返回标准错误码并被前端展示。 |
| AGENT4-IMPL-005 | 只读工具均通过 `ToolGateway` 注册，输入输出为专用 DTO。 |
| AGENT4-IMPL-006 | EvidenceAssembler 保留失败、未知和过期证据。 |
| AGENT4-IMPL-007 | TradePlanDraft 不能携带券商订单号、凭据或直接执行字段。 |
| AGENT4-IMPL-008 | `/agent` 页面和策略页入口使用同一套 Agent API 和结构化结果展示。 |
| AGENT4-IMPL-009 | 本期不新增定时注解、审批接口和真实交易写路径。 |

## 5. 本次调试约束

- 本次用户已明确授权后端启动、Maven 构建、Ollama 调用和 DeepSeek API 调用；这些操作仅限本地环境，不包含线上部署或数据库写入。
- API Key 只从运行环境读取，状态接口和日志不得返回或打印密钥值。
- DeepSeek 与 Ollama 同时配置时允许 `auto` 路由；两者同时关闭时必须返回 `NO_AGENT_PROVIDER_ENABLED`，不得发送空模型请求。
- 验收报告必须区分源码检查、编译结果、应用启动、DeepSeek 实际调用和 Ollama 实际调用。
<!-- AI_GENERATE_END -->
