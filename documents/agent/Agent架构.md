<!-- AI_GENERATE_START - -->
# Agent 架构

## 模块定位

`stock-agent` 是独立 Spring Boot 服务，默认端口 8081，负责自然语言交互、研究诊断、RAG、Memory、交易计划草案和独立交易建议。它不参与 Backend 的确定性自动交易执行链。

Agent 不依赖 Backend Java 类、Repository 或数据库实体。核心事实统一通过受保护的 `/api/internal/agentFacts/**` 获取，公开接口使用 `/api/agent/**`。

## 运行流程

```text
用户请求或 Agent 任务
  → 服务端身份与任务类型解析
  → 能力路由和工具白名单
  → 上下文、RAG 与 Memory 组装
  → AiServices 调用 Provider
  → 结构化输出解析与校验
  → 结果、证据和审计持久化
```

当前任务类型包括研究、诊断、报告和交易计划。任务状态区分创建、运行、等待模型、等待工具、完成、降级、失败、需复核、取消和过期。

## Provider 与结构化输出

系统支持云端 Provider 和本地 Ollama。Provider 配置包含启用状态、模型、超时、最大输出、并发、预算和熔断参数。失败达到阈值后进入冷却状态，不能把空文本或默认回答伪装为成功。

研究、诊断、报告和交易计划分别使用明确结果类型。解析失败时记录调用状态和错误原因，自由文本不能绕过结构校验成为业务结果。

## 工具体系

工具使用 LangChain4j `@Tool` 声明，由 `AiServices` 按任务绑定。工具覆盖行情与交易日历、模型与预测、策略状态、任务诊断、券商脱敏摘要、交易绩效和 Provider 状态。

- 每类任务只绑定必要工具。
- 工具参数和结果使用明确类型。
- 工具实现调用已有领域 Service 或 Backend 内部事实 API，不直接访问 Controller、SQL、Repository 或券商协议。
- 查询限制用户范围、数量、时间范围和敏感字段。
- 审计记录任务、工具名、输入摘要、结果状态、耗时和错误码。
- 完整提示词、数据库对象和敏感凭据不得写入日志。

## 会话、存储和预算

Agent 使用 MongoDB 保存会话、任务、Memory、RAG 文档块、交易计划、建议与工具审计，使用 Redis 共享 Backend 登录 Session。用户身份由服务端 Session 解析，不采信请求体提供的用户范围。

云端调用在执行前预留预算并在完成后记录实际使用。并发保持受控，避免与模型训练、Quartz 和交易任务争抢资源。

## 交易建议边界

Agent 可以基于冻结的 `TradeDecisionContext` 生成买入、持有、跳过或弃权建议，并在后续行情到达后进行反事实评估。上下文保存候选批次、策略版本、价格截止时间、预测、情感、新闻摘要、交易日和质量标记，不能在事后用新数据改写。

Agent 不能：

- 修改 Backend 候选集合、委托数量或风险参数。
- 控制、暂停或放行真实订单。
- 绕过交易日、资金、仓位、T+1、停牌或涨跌停校验。
- 调用券商写接口或持有券商凭据。

建议独立写入 Agent MongoDB 并用于展示和评估。真实交易只以 Backend 风控、执行批次和券商事实为准。

## 降级行为

Provider 超时、熔断、预算耗尽、解析失败或服务不可用时记录明确失败状态。Agent 故障只影响研究、建议和评估，不改变 Backend 确定性策略、硬风险与交易执行。

## 主要代码位置

```text
stock-agent/src/main/java/com/stock/agent/
├── client
├── controller
├── audit
├── capability
├── decision
├── dto
├── entity
├── rag
├── repository
├── runtime
├── service
└── tool
```
<!-- AI_GENERATE_END - -->