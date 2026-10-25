---
id: agent-tradeplan
title: Tradeplan 能力
sidebar_position: 1
owner: agent-tradeplan
type: capability
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /agent/tradeplan/
source_paths:
  - stock-agent/src/main/java/com/stock/agent/tradeplan/README.md
  - stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanAgentService.java
  - stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanService.java
  - stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanStateMachine.java
---

<DocMetadata />

**IDEA 源码入口**：[计划生成 `TradePlanAgentService.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanAgentService.java) · [计划服务 `TradePlanService.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanService.java) · [状态机 `TradePlanStateMachine.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/tradeplan/service/TradePlanStateMachine.java)

# tradeplan 业务包

## 模块定位

`tradeplan` 负责生成、保存、校验和人工复核交易计划草案。计划是供用户研究和审批的结构化建议，不是订单，也不能直接进入券商执行链。

## 业务职责

- 根据用户目标、研究上下文和允许工具生成结构化计划草案。
- 校验标的、方向、数量表达、价格条件、风险说明和有效期等字段。
- 管理草案、待复核、通过、驳回、过期等状态转换。
- 保存计划版本和人工复核结果。
- 提供创建、查询和复核接口。

## 目录结构

```text
tradeplan/
├── controller        # 创建、查询和复核接口
├── service           # Agent 生成与计划应用服务
├── output            # 结构化输出处理
├── validator         # 计划字段和业务边界校验
├── entity            # 计划持久化文档
├── repository        # 计划存储
├── dto               # 请求、响应和草案契约
└── model             # 计划状态
```

## 核心流程

```text
计划请求
  → TradePlanAgentService 组装上下文与允许工具
  → Runtime 调用类型化 Agent
  → TradePlanStructuredOutputHandler 解析
  → TradePlanValidator 校验
  → TradePlanService 保存草案
  → 人工复核与状态机变更
```

## 依赖边界

- 可以通过 `tooling` 获取只读、脱敏事实和证据。
- 依赖 `runtime` 执行 Provider 调用和结构化输出。
- 依赖 `shared` 的认证、响应与异常基础能力。
- 不调用 Backend 交易写接口、订单 Service 或券商适配器。
- 不修改候选、持仓、资金、风控参数或自动交易状态。

## 安全边界

计划通过人工复核也不等于获得真实交易执行权限。Backend 若未来消费计划，必须重新读取最新行情、资金、持仓、交易日历和运行开关，并执行完整权限、风控、幂等与执行隔离。Agent 计划中的价格和数量只能作为建议输入，不能成为跳过校验的权威事实。

Provider、事实源或结构化解析失败时，计划进入明确失败或需复核状态，不生成空计划或默认可执行计划。过期计划不得继续展示为当前有效建议。

## 扩展约束

新增计划字段或状态时，应同步更新 DTO、Entity、Validator、状态机和结构化输出契约。任何与真实订单关联的设计必须在 Backend 侧建立独立受控入口，不得在本包直接增加券商写调用。

## 重点能力详细设计

### 草案生成门禁

`TradePlanAgentService.generate` 在调用模型前执行以下步骤：

1. 校验候选批次非空且 `agent.tradePlanEnabled=true`。
2. 创建或校验当前用户 Session。
3. 通过 `EvidenceAssembler` 获取候选、交易日、策略、执行门禁、券商事实和 Provider 状态。
4. 任一 Evidence 为 `UNKNOWN`、`FAILED` 或 `EXPIRED` 时停止生成。
5. 要求 Evidence 能解析出预测批次、策略版本和目标交易日。
6. 用户指定股票代码时，必须能在当前候选 Evidence 中找到。
7. Runtime 使用 `outputType=TRADE_PLAN` 且不再绑定工具，只消费已经冻结的 Evidence。

模型返回后再次校验候选批次、预测批次、策略版本、目标交易日和可选股票代码必须与 Evidence 一致，防止模型替换交易对象或版本。

### 创建校验

`TradePlanValidator` 对最终 `TradePlanCreateRequest` 执行硬约束：

| 字段 | 规则 |
|---|---|
| 候选批次、股票代码 | 必填 |
| 方向 | 仅 `BUY` 或 `SELL` |
| 计划金额 | 必须大于 0 |
| 卖出数量 | 正整数且为 100 的倍数 |
| Evidence | 至少一项，每项非空且最长 256 字符 |
| 有效期 | 必须晚于当前时间 |
| 幂等键 | 必填，最长 128 字符 |

这里不校验实时价格、可用资金、最新持仓、T+1 或涨跌停，因为计划不是执行指令。这些条件必须由 Backend 在真实交易前重新读取和校验。

### 幂等与用户隔离

计划按 `idempotencyKey + userScope` 幂等。重复请求返回原计划，不重复创建。查询、批准和拒绝均使用 `planId + userScope`，其他用户即使知道 planId 也不能访问。

计划持久化保存候选批次、预测批次、股票、方向、金额/数量、目标交易日、策略版本、价格约束、原因、风险警告、不确定性、Evidence、有效期和人工审批信息。

### 状态机

```text
PENDING_APPROVAL
  ├── APPROVED
  ├── REJECTED
  └── EXPIRED

APPROVED / REJECTED / EXPIRED
  └── 无后续转换
```

`TradePlanStateMachine` 是状态转换唯一校验点。批准时如果计划已经过期，自动转换为 `EXPIRED`，不会继续批准。拒绝只能从允许的起始状态转换。

### 人工审批并发控制

审批请求必须携带客户端看到的 `version`，服务端与当前 Entity 版本不一致时拒绝并提示刷新。该校验配合持久化版本字段，防止两个审批页面基于旧状态同时操作。

批准后记录 `approvedBy`、`approvedAt`、评论和“等待计划级复核”消息；拒绝后记录 `rejectedBy`、`rejectedAt` 和评论。评论最大保存 500 字符。

`APPROVED` 仍然只是 Agent 计划审批通过，不代表订单已创建，不进入券商队列，也不能跳过 Backend 风控。

### 结构化输出

`TradePlanStructuredOutputHandler` 只处理 `TRADE_PLAN`，将模型 JSON 转换为 `TradePlanDraft`。Runtime 结构化解析通过后才进入 `TradePlanService.create`。Provider 返回空文本、非法 JSON、证据不一致或事实不可用时不创建计划。

## 接口与存储

| API | 功能 |
|---|---|
| `POST /api/agent/tasks/tradePlan` | 基于事实生成并保存草案 |
| `POST /api/agent/tradePlans` | 保存已构造的计划请求 |
| `GET /api/agent/tradePlans/details/{planId}` | 查询所属用户计划 |
| `POST /api/agent/tradePlans/approve/{planId}` | 按版本批准 |
| `POST /api/agent/tradePlans/reject/{planId}` | 按版本拒绝 |

MongoDB 集合 `agent_trade_plan` 保存计划。该集合不保存券商凭据、订单号或真实成交状态。

## 扩展设计要求

如果未来由 Backend 消费 APPROVED 计划，应新增只读拉取或受控提交契约，并由 Backend 生成新的执行幂等键、重新取数和风控。禁止直接把 Agent planId 当作券商订单幂等键，也不能在本包增加“审批后自动下单”逻辑。
