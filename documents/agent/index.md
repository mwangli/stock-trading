---
id: agent-overview
title: Agent 总览
sidebar_position: 1
owner: agent
type: architecture
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /agent/
source_paths:
  - stock-agent/src/main/java/com/stock/agent/README.md
  - stock-agent/src/main/java/com/stock/agent/StockAgentApplication.java
  - stock-agent/src/main/java/com/stock/agent/interaction/service/AgentApplicationService.java
  - stock-agent/src/main/java/com/stock/agent/runtime/service/AgentRuntimeCoordinator.java
---

<DocMetadata />

**IDEA 源码入口**：[应用入口 `StockAgentApplication.java`](../../stock-agent/src/main/java/com/stock/agent/StockAgentApplication.java) · [业务编排 `AgentApplicationService.java`](../../stock-agent/src/main/java/com/stock/agent/interaction/service/AgentApplicationService.java) · [运行协调 `AgentRuntimeCoordinator.java`](../../stock-agent/src/main/java/com/stock/agent/runtime/service/AgentRuntimeCoordinator.java)

# stock-agent 模块说明

## 模块定位

`stock-agent` 是可独立启动的 Spring Boot 服务，负责自然语言交互、知识检索、长期记忆、受控工具调用、研究诊断、交易计划草案和独立交易建议。它与 `stock-backend` 在代码、构建、进程和生命周期上分离，不共享 Java Entity、Repository、Service 或 DTO JAR。

Agent 独立运行不等于复制 Backend 的权威数据和交易能力。行情、候选、账户、券商、策略及交易决策事实仍通过只读内部 HTTP API 获取；Backend 不可用时，普通对话、Provider、RAG、Memory 和会话管理继续工作，事实依赖能力明确返回 `FACT_SOURCE_UNAVAILABLE`。

## 能力目录

| 业务包 | 主要职责 | 详细文档 |
|---|---|---|
| `interaction` | 会话、任务、聊天入口与应用编排 | [interaction/README.md](./interaction/index.md) |
| `knowledge` | RAG、Embedding、长期 Memory | [knowledge/README.md](./knowledge/index.md) |
| `decision` | 交易建议、冻结上下文与反事实评估 | [decision/README.md](./decision/index.md) |
| `tradeplan` | 交易计划草案、校验与人工复核 | [tradeplan/README.md](./tradeplan/index.md) |
| `runtime` | Provider、模型调用、预算、熔断与运行状态 | [runtime/README.md](./runtime/index.md) |
| `tooling` | 工具白名单、事实模型、证据与审计 | [tooling/README.md](./tooling/index.md) |
| `integration` | Backend HTTP 客户端、Wire DTO 与 Port 适配器 | [integration/README.md](./integration/index.md) |
| `shared` | API、认证、公共配置和基础异常 | [shared/README.md](./shared/index.md) |

## 依赖原则

```text
interaction / tooling / decision
              │
              ▼
        Agent 业务 Port
              ▲
              │ implements
      integration.adapter
              │
              ▼
stock-backend /api/internal/agentFacts/**
```

- `shared` 不依赖任何业务能力包。
- `runtime` 管理模型运行，不直接调用 Backend。
- `tooling` 与 `decision` 只依赖各自 Port，不直接依赖 HTTP 客户端。
- `integration` 负责协议和模型转换，不承载 Prompt、路由、建议或交易判断。
- `interaction` 负责用例编排，不直接访问外部 HTTP、数据库实现或券商接口。
- Agent 不直连 Backend MySQL，不调用券商写接口，不参与真实订单放行。

## 独立运行与降级

运行状态分别暴露 `agentCoreStatus`、`providerStatus`、`embeddingStatus` 和 `backendFactsStatus`。`backendFactsStatus=UNAVAILABLE` 只表示权威事实能力降级，不应使 Agent 核心服务整体变为不可启动。

Backend 连接只在实际事实查询或显式状态检查时发生。事实源禁用、超时、鉴权失败、响应解析失败或网络不可达时，不得使用 Mock、空对象或模型推断替代权威事实。Backend 恢复后，后续查询自动恢复，无需重启 Agent。

## 文档维护规则

`documents/agent` 是 Agent 架构和能力设计的权威目录。新增、移动、重构或删除能力时，应在同一变更中更新对应页面的源码索引和证据状态；源码旁 README 只保留职责与门户入口。

## 端到端能力设计

### 请求类型与执行路径

| 能力 | 入口 | 主要编排 | 是否需要 Backend 事实 | 持久化 |
|---|---|---|---|---|
| 普通聊天 | `POST /api/agent/chat` | `AgentConversationService` | 否，能力路由可选择空工具集 | Session、Task、Chat Memory |
| 研究分析 | `POST /api/agent/chat` | `AgentApplicationService.research` | 按问题选择 | Task、Evidence、工具审计 |
| 运行诊断 | `POST /api/agent/chat` | `AgentApplicationService.diagnose` | Provider 诊断不需要；交易诊断需要 | Task、Evidence、工具审计 |
| 日报 | `POST /api/agent/reports/daily` | `AgentApplicationService.dailyReport` | 需要受控证据 | Task |
| RAG/Memory | `/api/agent/rag/**`、`/api/agent/memories/**` | `knowledge` | 否 | MongoDB |
| 交易计划 | `/api/agent/tasks/tradePlan`、`/api/agent/tradePlans/**` | `TradePlanAgentService`、`TradePlanService` | 是 | `agent_trade_plan` |
| 交易建议 | `/api/agent/tradeDecisions/**` | `AgentTradeDecisionService` | 是 | 决策上下文、建议和评估集合 |
| Runtime 管理 | `/api/agent/runtime/**` | `AgentRuntimeManagementService` | 仅状态汇总按需探测 | Provider 预算集合 |

### 一次受控 Agent 调用

```text
HTTP Controller
  → 从 Redis Session 解析 userScope
  → 会话归属与同会话串行锁
  → 能力路由选择任务策略和最小工具集合
  → 创建 AgentTask
  → 组装 RAG、Memory 和只读 Evidence
  → Runtime 校验输入、预算、并发和 Provider 状态
  → AiServices 绑定 ChatMemory 与允许工具
  → Provider 返回文本
  → 按 outputType 解析并校验结构化结果
  → 通过后提交 ChatMemory，失败则丢弃本次尝试
  → 保存任务、证据引用和审计摘要
```

## 数据所有权

| 数据 | 权威所有者 | Agent 中的用途 |
|---|---|---|
| 行情、交易日历、模型、候选和策略状态 | Backend | 只读事实和 Evidence |
| 账户、持仓、券商和执行门禁 | Backend | 只读脱敏摘要，不作为下单授权 |
| 会话、Agent 任务和工具审计 | Agent | 用户交互和可追溯执行记录 |
| RAG Chunk、Memory 和向量 | Agent | 知识上下文，不是实时业务事实 |
| 交易计划草案 | Agent | 人工审批对象，不是订单 |
| 交易建议及反事实评估 | Agent | 独立研究结果，不影响真实交易 |
| 真实订单、成交和风控状态 | Backend | Agent 不写入、不拥有 |

## 运行与故障隔离

- Provider 故障只影响模型生成，不能改变 Backend 自动交易状态。
- Backend 事实故障返回 `FACT_SOURCE_UNAVAILABLE`，不会计入 Provider 熔断失败。
- Embedding 故障只影响 RAG 和向量 Memory，普通无检索对话仍可执行。
- 结构化输出校验失败不会提交本轮 Chat Memory，避免错误结果污染后续会话。
- DeepSeek 预算状态不可用时采用失败关闭策略，阻止云端调用；Ollama 不进入云端费用预算。
- 同一会话通过进程内锁串行执行，避免并发修改短期 Memory。

## 模块变更检查清单

1. 新增能力先确定所属业务包，不在根包或 `shared` 平铺业务类。
2. 需要 Backend 事实时先定义 Port 和 Agent 内部事实模型，再实现 `integration.adapter`。
3. 新增模型输出必须提供独立 `AgentStructuredOutputHandler` 和字段校验。
4. 新增工具必须加入能力白名单、`AgentToolCatalog`、审计和 Evidence 设计。
5. 新增持久化对象必须明确用户范围、生命周期、集合名称和清理策略。
6. 涉及交易的输出只能是建议或草案，真实写入继续由 Backend 完成复核。
7. README 与代码同批更新，跨模块文档只链接，不复制完整设计。
