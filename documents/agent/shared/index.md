---
id: agent-shared
title: Shared 能力
sidebar_position: 1
owner: agent-shared
type: capability
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /agent/shared/
source_paths:
  - stock-agent/src/main/java/com/stock/agent/shared/README.md
  - stock-agent/src/main/java/com/stock/agent/shared/config/AgentWebConfig.java
  - stock-agent/src/main/java/com/stock/agent/shared/auth/AgentAuthInterceptor.java
  - stock-agent/src/main/java/com/stock/agent/shared/error/AgentExceptionHandler.java
---

<DocMetadata />

**IDEA 源码入口**：[Web 装配 `AgentWebConfig.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/shared/config/AgentWebConfig.java) · [用户鉴权 `AgentAuthInterceptor.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/shared/auth/AgentAuthInterceptor.java) · [异常边界 `AgentExceptionHandler.java`](pathname://../../../stock-agent/src/main/java/com/stock/agent/shared/error/AgentExceptionHandler.java)

# shared 业务包

## 模块定位

`shared` 只保存多个 Agent 能力共同需要、语义稳定且不属于特定业务域的基础设施。它是最底层依赖，不应演变为业务类、DTO 和工具代码的集中堆放区。

## 目录结构与职责

```text
shared/
├── api
│   └── ResponseDTO.java          # 统一 HTTP 响应
├── auth                           # Session、当前用户和内部令牌解析
├── config                         # Agent 通用配置与 Web 装配
├── error                          # 基础异常和全局异常处理
└── README.md
```

## 允许内容

- `ResponseDTO<T>` 等统一协议外壳。
- 当前用户上下文、共享 Session 属性和认证拦截器。
- 内部 API Token 的基础配置与校验设施。
- 不属于特定能力的 Agent 通用配置类型。
- 通用错误码、基础异常和全局异常响应转换。

## 禁止内容

- 业务请求、响应 DTO 或领域枚举。
- MongoDB Entity、Repository 和业务 Service。
- Prompt、Agent 路由、工具定义和 Provider 实现。
- Backend Wire DTO 或 Backend 与 Agent 共享的 Java 契约 JAR。
- 仅被单一业务包使用的所谓公共类。

## 依赖规则

`shared` 不得依赖 `interaction`、`knowledge`、`decision`、`tradeplan`、`runtime`、`tooling` 或 `integration`。其他能力包可以单向依赖 `shared`，但应优先将业务语义留在所属能力包内。

## 认证边界

当前阶段 Agent 与 Backend 共享 Redis Session，登录仍由 Backend 提供。Agent 只解析已有身份，不复制账号、密码或登录逻辑。后续如改为签名访问令牌，本包可承载通用验签基础设施，但用户权限和业务范围仍由具体用例校验。

内部服务令牌与用户登录身份是两套不同边界：`X-Internal-Token` 只保护服务间事实 API，不能替代用户授权；用户 Session 也不能直接作为内部服务凭据。

## 扩展约束

向 `shared` 增加类型前必须确认至少两个独立能力包确实复用、语义一致且不会形成反向依赖。无法满足时，应将类型保留在其业务包中。

## 重点能力详细设计

### Web 访问控制

`AgentWebConfig` 注册两条互斥拦截规则：

| 路径 | 拦截器 | 身份来源 |
|---|---|---|
| `/api/internal/**` | `InternalTokenInterceptor` | `X-Internal-Token` |
| 其他 `/api/**` | `AgentAuthInterceptor` | Redis 共享 HTTP Session |
| `/api/agent/runtime/health` | 无登录拦截 | 用于基础健康探测 |

内部接口明确排除共享 Session 拦截，防止把浏览器登录态误当服务间认证；外部 Agent 接口也不能仅凭内部 Token 访问。

### 用户身份解析

`AgentAuthInterceptor` 要求 Session 同时存在：

- `AUTH_USER_ID`，类型为 `Long`。
- `AUTH_USERNAME`，类型为 `String`。

缺失时直接返回 HTTP 401 和 `ResponseDTO.error("请先登录")`。`AgentRequestUserResolver` 从相同 Session 构造 `userScope`，业务 Controller 只传递该服务端值，不能从请求 DTO 接受用户 ID。

共享 Redis Session 只是当前认证协议。它允许 Agent 独立进程验证已有会话，但 Backend 停机后无法创建新登录；未来切换签名令牌时应替换本包认证设施，不把账号密码逻辑迁入 Agent。

### 内部令牌

`InternalTokenInterceptor` 使用固定 Header `X-Internal-Token` 对 `/api/internal/**` 做服务间校验。Token 必须来自运行配置，不写入日志、Memory、RAG、审计摘要或错误响应。内部 Token 只证明调用服务身份，不代表终端用户权限。

### 统一响应与异常

`ResponseDTO<T>` 提供 `success(data)` 和 `error(message)` 两种外层契约。Controller 统一返回该类型，不使用裸 DTO 或 Map。

`FactSourceUnavailableException` 使用稳定错误码 `FACT_SOURCE_UNAVAILABLE`。`AgentExceptionHandler` 将其转换为 HTTP 503，避免前端把事实源错误当作空列表或正常降级。日志只记录错误码和异常类型，不输出内部 Token 或完整响应体。

### 配置模型

`AgentProperties` 集中承载真正跨能力的运行参数：

| 配置组 | 关键参数 |
|---|---|
| Core | `enabled`、`provider`、`timeoutMs`、`maxOutputChars`、`maxConcurrency` |
| Provider | DeepSeek/Ollama 的 enabled、baseUrl、model、maxTokens |
| Budget | 每日上限、输入/输出峰值单价、安全系数、预留 Token |
| Circuit Breaker | 开关、失败阈值、打开时间 |
| Capabilities | report、tradePlan、RAG、Memory 开关 |
| Memory/Tool | TTL、Chat Memory 条数、工具超时、审计开关 |
| Embedding | Provider、制品路径、版本、维度、Pooling、归一化、Fallback |

配置类只表达运行参数，不执行 Provider 调用、业务查询或状态机。

### shared 准入判断

新增类进入本包前必须同时满足：

1. 被至少两个独立业务包使用。
2. 不包含某一业务域的状态和规则。
3. 不需要反向依赖任何业务包。
4. 生命周期与 Agent 应用一致，而非某项能力独占。
5. 放入后不会促使其他模块绕过 Port 或 Service。

不满足这些条件的 DTO、异常、配置和工具支持应留在所属业务包。
