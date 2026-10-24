<!-- AI_GENERATE_START --- -->
# integration 业务包

## 模块定位

`integration` 是 Agent 对外部 Backend 事实服务的协议适配层。当前只有一个 Backend 集成，因此直接使用 `integration.client`、`integration.adapter`、`integration.dto` 和 `integration.config`，不再增加无业务区分价值的 `backend` 中间包。

## 业务职责

- 配置 Backend 地址、启用开关、连接超时和读取超时。
- 使用 `RestClient` 调用 `/api/internal/agentFacts/**`。
- 自动携带 `X-Internal-Token`，但不记录 Token 内容。
- 定义只用于 HTTP JSON 交换的 Backend Wire DTO。
- 将 HTTP、超时、鉴权和 JSON 解析失败转换为统一事实源异常。
- 将 Wire DTO 转换为 `tooling` 或 `decision` 的 Agent 内部事实模型。
- 提供 Backend 事实源状态检查实现。

## 目录结构

```text
integration/
├── client
│   └── BackendInternalApiClient.java
├── adapter
│   ├── BackendToolFactAdapter.java
│   └── BackendDecisionFactAdapter.java
├── dto                   # Backend HTTP Wire DTO
├── config
│   ├── BackendClientConfig.java
│   └── BackendServiceProperties.java
└── README.md
```

## 调用流程

```text
ToolFactQueryPort / TradeDecisionFactPort
  ← integration.adapter
  → BackendInternalApiClient
  → X-Internal-Token
  → stock-backend 内部只读 API
  → Backend Wire DTO
  → Agent 内部事实模型
```

`BackendInternalApiClient` 只处理 HTTP 协议和 Wire DTO；两个 Adapter 分别实现工具事实和决策事实 Port，并承担模型转换。这样业务包不会感知 URL、Header、RestClient 或 Backend JSON 结构。

## 非职责与依赖边界

- 不包含 Prompt、Agent 路由、工具选择或交易建议。
- 不访问 Agent MongoDB、Backend MySQL、Repository 或券商写接口。
- 不定义 Backend 与 Agent 共同依赖的 Java JAR。
- `adapter` 可以依赖 `tooling.port`、`decision.port` 及其内部事实模型。
- `client` 只依赖 `config`、`dto`、`shared.error` 和 HTTP 基础设施。
- `runtime`、`interaction` 不直接依赖本包 Client。

## 降级规则

`backend.service.enabled=false` 时不发起事实请求，并报告事实源不可用。超时、网络故障、内部鉴权失败和协议解析失败统一进入清晰的事实不可用路径，但应保留可诊断的内部原因。Backend 恢复后，下一次请求重新调用，不要求重启 Agent。

## 扩展约束

新增 Backend API 必须先定义业务 Port，再新增最小 Wire DTO 和 Adapter 映射，不能让 Wire DTO 泄漏到业务包。只有外部系统生命周期、协议和错误语义确实不同时，才增加新的命名边界。

## 重点能力详细设计

### 唯一 HTTP 客户端

`BackendInternalApiClient` 是 Agent 调用 Backend 内部事实 API 的唯一低层入口。所有 GET/POST 请求先读取统一 `BackendFactsEnvelope`，要求：

1. 响应对象非空。
2. `success=true`。
3. `data` 非空。
4. `data` 可以转换成目标 Wire DTO。

任何条件不满足都转换成 `FactSourceUnavailableException`，业务层不会收到空对象并误判为“没有数据”。

### 错误转换规则

| 原始情况 | Agent 行为 |
|---|---|
| `backend.service.enabled=false` | 不发 HTTP 请求，直接抛出事实源禁用异常 |
| HTTP 401/403 | 转换为内部鉴权失败，不重试、不降级为模型猜测 |
| 其他 HTTP 错误 | 保留状态码摘要并转换为事实源不可用 |
| 建连、读取或协议异常 | 转换为事实源不可用 |
| JSON/Wire DTO 解析失败 | 转换为响应解析失败 |
| 业务 Envelope 失败或空数据 | 转换为明确失败，不返回默认对象 |

`FactSourceUnavailableException` 会穿过工具执行层和 Provider 调用层直接返回，不计入 Provider 熔断失败。对外由统一异常处理转换为 HTTP 503 和稳定错误码 `FACT_SOURCE_UNAVAILABLE`。

### 客户端配置

`BackendClientConfig` 创建命名为 `backendFactsRestClient` 的专用 `RestClient`：

| 配置 | 默认值 | 作用 |
|---|---:|---|
| `backend.service.enabled` | `true` | 是否允许查询 Backend 事实 |
| `backend.service.baseUrl` | `http://localhost:8080` | Backend 根地址 |
| `backend.service.connectTimeoutMs` | `3000` | TCP 建连超时 |
| `backend.service.readTimeoutMs` | `10000` | 响应读取超时 |
| `internal.api.token` | 环境配置 | 自动写入 `X-Internal-Token` |

创建 Bean 时只装配客户端，不发起网络探测。`status()` 仅在健康查询时按需调用当天交易日历，并返回 `DISABLED`、`AVAILABLE` 或 `UNAVAILABLE`。

### 当前事实接口

| Client 方法 | Backend 路径 | 返回用途 |
|---|---|---|
| `latestPrice` | `/latestPrice` | 最新日线事实 |
| `tradingCalendar` | `/tradingCalendar` | 交易日确认 |
| `strategyState` | `/strategyState` | 策略开关和版本 |
| `predictionCandidates` | `/predictionCandidates` | 指定候选批次 |
| `latestPredictionCandidates` | `/latestPredictionCandidates` | 目标交易日最新候选 |
| `brokerStatus` | `/brokerStatus` | 券商连接状态 |
| `brokerFacts` | `/brokerFacts` | 脱敏账户摘要 |
| `performanceOverview` | `/performanceOverview` | 收益概览 |
| `modelOverview` | `/modelOverview` | 模型运行概览 |
| `jobDiagnostic` | `/jobDiagnostic` | 任务执行诊断 |
| `tradeExecutionGate` | `/tradeExecutionGate` | 真实交易门禁摘要 |
| `tradeDecisionContexts` | `/tradeDecisionContexts` | 冻结决策上下文 |
| `tradeDecisionEvaluationFacts` | `/tradeDecisionEvaluationFacts` | 批量反事实评估数据 |

以上路径统一位于 `/api/internal/agentFacts/**`，表中省略公共前缀。

### Adapter 与模型隔离

`BackendToolFactAdapter` 实现 `ToolFactQueryPort` 和 `FactSourceStatusPort`，把 Wire DTO 转换成 `tooling.model`；`BackendDecisionFactAdapter` 实现 `TradeDecisionFactPort`，把 Wire DTO 转换成 `decision.port.model`。业务包只认识自身事实类型，因此 Backend JSON 字段变化只影响 `integration.dto` 和对应 Adapter。

### 新增接口步骤

1. 在调用方业务包定义语义明确的 Port 方法和内部事实类型。
2. 在 `integration.dto` 新增最小 Wire DTO，不复用 Backend Java 类。
3. 在 `BackendInternalApiClient` 增加协议调用和 Envelope 校验。
4. 在对应 Adapter 完成 Wire DTO 到业务事实的显式转换。
5. 定义禁用、鉴权、超时、空数据和解析失败行为。
6. 更新本 README 的接口表和降级说明。
<!-- AI_GENERATE_END --- -->
