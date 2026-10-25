---
id: agent-interaction
title: Interaction 能力
sidebar_position: 1
owner: agent-interaction
type: capability
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /agent/interaction/
source_paths:
  - stock-agent/src/main/java/com/stock/agent/interaction/README.md
  - stock-agent/src/main/java/com/stock/agent/interaction/controller/AgentConversationController.java
  - stock-agent/src/main/java/com/stock/agent/interaction/service/AgentApplicationService.java
  - stock-agent/src/main/java/com/stock/agent/interaction/service/AgentSessionService.java
---

<DocMetadata />

**IDEA 源码入口**：[会话接口 `AgentConversationController.java`](../../../stock-agent/src/main/java/com/stock/agent/interaction/controller/AgentConversationController.java) · [任务编排 `AgentApplicationService.java`](../../../stock-agent/src/main/java/com/stock/agent/interaction/service/AgentApplicationService.java) · [会话管理 `AgentSessionService.java`](../../../stock-agent/src/main/java/com/stock/agent/interaction/service/AgentSessionService.java)

# interaction 业务包

## 模块定位

`interaction` 是 Agent 面向用户的会话和任务应用层，负责将 HTTP 请求转换为研究、诊断、报告或普通聊天用例，并维护会话与任务生命周期。它负责业务编排，不实现 Provider 协议、Embedding、Backend HTTP 或工具底层查询。

## 业务职责

- 创建、查询和维护用户会话。
- 接收聊天、研究、诊断和报告请求。
- 创建任务并维护运行、降级、失败、完成等状态。
- 按任务类型组织上下文、Knowledge、Tooling 和 Runtime 调用。
- 将结构化结果转换为稳定的接口响应。
- 保证会话、任务和历史查询受当前登录用户范围约束。

## 目录结构

```text
interaction/
├── controller        # 会话与聊天 HTTP 入口
├── service           # 应用编排、会话和任务服务
├── dto               # 请求与响应契约
├── entity            # 会话与任务文档
├── repository        # 会话与任务持久化
├── model             # 任务类型和状态
└── output            # 研究、诊断和报告结构化结果
```

## 核心流程

```text
用户请求
  → Controller 解析当前用户
  → AgentApplicationService 识别任务类型
  → AgentSessionService / AgentTaskService 建立上下文
  → Knowledge 补充检索与记忆
  → Tooling 选择必要只读工具
  → Runtime 调用 Provider
  → 结构化输出与任务状态持久化
  → DTO 响应
```

普通无工具对话不应因为 Backend 不在线而失败。只有任务明确需要行情、策略、候选、账户或券商事实时，才通过 Tooling 的业务 Port 触发事实调用。

## 依赖边界

- 可以依赖 `knowledge`、`tooling`、`runtime` 和 `shared` 的公开能力。
- 不直接依赖 `integration.client`、Backend Wire DTO 或 Backend Java 代码。
- Controller 只负责协议转换、入口日志、身份解析和响应封装。
- Repository 只负责本能力的 MongoDB 文档，不访问 Backend 数据库。

## 状态与扩展约束

任务状态必须区分创建、运行、等待模型、等待工具、完成、降级、失败、需复核、取消和过期等业务含义。Provider、Embedding 与 Backend 事实源故障应分别记录，不能统一吞成空回答。

新增任务类型时，需要同时明确请求 DTO、输出类型、允许工具、Knowledge 使用方式、超时预算和任务终态。不要在 Controller 中增加模型调用、Repository 拼装或外部 HTTP 逻辑。

## 重点能力详细设计

### 统一聊天路由

`AgentConversationService.chat` 首先校验问题非空且不超过 2000 字符，再调用 `AgentCapabilityRouter` 选择任务策略和最小工具集。当前路由规则由服务端维护，模型不能自行扩大工具权限。

| 问题语义 | 策略 | 主要工具 |
|---|---|---|
| 问候、身份和能力说明 | `CONVERSATION` | 无 |
| 收益、胜率和账户收益 | `FACT_QUERY` | `PERFORMANCE_OVERVIEW` |
| Provider、Ollama、DeepSeek、模型超时 | `DIAGNOSTIC` | 无，读取 Runtime 状态 |
| 没有买入、下单、资金、持仓和账户 | `DIAGNOSTIC` | 交易日历、候选、券商状态、任务诊断 |
| 调度或 Job 失败 | `DIAGNOSTIC` | 任务诊断 |
| 最新价格和行情 | `FACT_QUERY` | 最新价格 |
| 模型版本和训练状态 | `ANALYSIS` | 模型概览 |
| 策略、候选、排名和选股 | `ANALYSIS` | 策略状态、预测候选 |
| 交易日和开休市 | 保持当前策略 | 交易日历 |

路由结果是 `AgentCapabilitySelection(strategy, allowedTools)`。后续 Runtime 只绑定该集合，工具执行层还会再次校验工具名是否在允许范围内。

### 同会话串行化

聊天使用 `userScope:sessionId` 作为锁键；未创建会话时使用 `userScope:new-session`。同一用户同一会话请求串行执行，避免 Chat Memory、任务状态和工具上下文交叉。不同用户或不同会话可以按 Runtime 并发上限并行。

### Session 生命周期

`AgentSessionService` 负责：

- 创建带 UUID 的 Session，并保存标题、用户范围和 `ACTIVE` 状态。
- `ensure` 在请求未传 Session 时创建，在已传时执行归属校验。
- 所有读取通过 `sessionId + userScope` 验证所有权。
- 每次创建、完成、失败或取消任务后更新会话最后活动时间。
- Session 状态包括 `ACTIVE`、`CLOSED`、`EXPIRED`。

用户范围完全来自服务端共享 Session，Controller 不采信请求体中的用户身份。

### Task 生命周期与结果保存

`AgentTaskService.create` 直接创建 `RUNNING` 任务，并记录任务类型、问题、创建时间。完成时将 Runtime 状态映射为：

| Runtime 状态 | Task 状态 |
|---|---|
| `COMPLETED` | `COMPLETED` |
| `DEGRADED` | `DEGRADED` |
| `FAILED` | `FAILED` |

结构化输出保存摘要、JSON、证据摘要、证据引用和不确定性；文本长度受 `agent.maxOutputChars` 限制。异常失败只保存脱敏且限长的错误摘要。会话历史最多返回最近 200 个任务，并恢复为创建时间正序。

取消操作只允许作用于未结束任务；`COMPLETED`、`FAILED`、`CANCELLED` 不能再次取消。当前任务枚举还预留 `CREATED`、`WAITING_MODEL`、`WAITING_TOOL`、`NEEDS_REVIEW` 和 `EXPIRED`，使用这些状态前应确保 Service 实际维护对应转换。

### 响应与证据合并

聊天完成后以结构化 `resultSummary` 为回答，失败时回退到错误信息。Evidence 同时合并：

- Task 结构化输出中的 `evidenceRefs`。
- 工具审计生成的 `toolCall:{id}` 引用。

合并使用有序去重集合。`capabilities` 返回本任务实际成功执行的工具名，而不是路由阶段理论允许的工具名。

## 接口与存储

| API | 功能 |
|---|---|
| `POST /api/agent/sessions` | 创建会话 |
| `GET /api/agent/sessions` | 查询当前用户会话 |
| `GET /api/agent/sessions/{sessionId}` | 查询会话详情 |
| `GET /api/agent/sessions/history/{sessionId}` | 查询会话任务历史 |
| `POST /api/agent/chat` | 统一聊天入口 |
| `POST /api/agent/reports/daily` | 创建日报任务 |
| `GET /api/agent/tasks/details/{taskId}` | 查询任务 |
| `GET /api/agent/tasks/evidence/{taskId}` | 查询任务证据结果 |
| `POST /api/agent/tasks/cancel/{taskId}` | 取消任务 |

MongoDB 集合 `agent_session` 保存会话，`agent_task` 保存任务及结构化结果。Repository 不暴露给其他模块直接写入。

## 扩展设计要求

新增路由关键词时应避免多个高优先级分支互相覆盖；新增任务状态必须给出明确进入和退出条件；新增响应字段需同时检查持久化长度、脱敏规则和历史数据兼容性。
