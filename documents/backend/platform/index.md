---
id: platform-overview
title: Platform 平台装配
sidebar_position: 1
owner: platform
type: domain
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /backend/platform/
source_paths:
  - stock-backend/src/main/java/com/stock/platform/controller/InternalAgentFactsController.java
  - stock-backend/src/main/java/com/stock/platform/service/InternalAgentFactsService.java
  - stock-backend/src/main/java/com/stock/platform/aspect/ApiLoggingAspect.java
---

<DocMetadata />

**IDEA 源码入口**：[内部事实接口 `InternalAgentFactsController.java`](../../../stock-backend/src/main/java/com/stock/platform/controller/InternalAgentFactsController.java) · [事实聚合 `InternalAgentFactsService.java`](../../../stock-backend/src/main/java/com/stock/platform/service/InternalAgentFactsService.java) · [接口日志 `ApiLoggingAspect.java`](../../../stock-backend/src/main/java/com/stock/platform/aspect/ApiLoggingAspect.java)

# Platform 平台装配模块

## 模块定位

`com.stock.platform` 负责 Backend 的 Spring 技术装配、认证拦截、持久化与缓存配置、统一响应、异常处理、内部事实 API 和跨业务只读聚合。

平台模块提供公共契约和应用装配，不承载市场、模型、决策或交易算法，也不直接操作其他业务模块的 Repository。

## Backend 六业务包

| 包根 | 职责 |
|---|---|
| `com.stock.market` | 股票、行情、新闻、交易日历和数据质量 |
| `com.stock.model` | 情感、LSTM、训练、预测、评估和模型生命周期 |
| `com.stock.decision` | 选股、信号、策略状态、决策和回测 |
| `com.stock.execution` | 用户、账户、风控、券商、委托、同步和绩效 |
| `com.stock.schedule` | Quartz、任务配置、任务控制和执行记录 |
| `com.stock.platform` | 认证装配、持久化配置、内部接口和公共契约 |

## 重点能力详细设计

### 1. 应用启动与扫描根

Backend 启动入口位于六个业务包共同父包：`com.stock.StockBackendApplication`。启动类以 `com.stock` 为 Spring 扫描根，Platform 通过配置类和启动组件参与应用装配，但不拥有启动类。Maven `mainClass`、容器启动命令和部署脚本必须引用同一入口。

启动阶段主要顺序：

1. 创建 Spring 应用上下文并绑定类型化配置。
2. 初始化关系型、MongoDB 和 Redis 客户端配置。
3. 由 Flyway 迁移 MySQL 结构。
4. 初始化必要的 MongoDB 索引。
5. `schedule.JobBootstrap` 补齐固定任务并重建 Quartz RAMJobStore。
6. `ModelArtifactWarmup` 检查模型制品并暴露真实可用状态。
7. `StartupTimeLogger` 记录启动阶段和耗时。

核心持久化依赖不可用时不能伪装服务正常。可选模型或 Agent 能力不可用时可以降级，但降级状态必须可见，并且不得扩大真实交易权限。

### 2. JPA 与 Mongo Repository 隔离

`RelationalPersistenceConfig` 和 `MongoPersistenceConfig` 都以 `com.stock` 为扫描范围，通过 Repository 类型和注解过滤区分 JPA 与 MongoDB。

设计目标是允许业务包按业务聚合，而不要求再建立按数据库划分的一级包。约束包括：

- JPA Repository 只管理 MySQL Entity。
- Mongo Repository 只管理 Document。
- 同一 Repository 不能同时表达两种存储语义。
- 新增持久化对象时必须确认其 Repository 能被正确配置识别。
- 不通过扩大排除规则掩盖错误继承或错误注解。

Repository 扫描变更属于平台级高影响修改，需要同步检查全部业务包和启动配置。

### 3. Redis 缓存与失败降级

`RedisCacheConfig` 统一缓存序列化、缓存管理和错误处理。Redis 只保存共享 Session 或可重建查询缓存，不作为行情、模型状态、订单或账户的权威来源。

缓存读取失败时可以回源权威 Service，并记录降级状态；缓存写入失败不能反向覆盖数据库成功结果。数据库或券商事实读取失败时，不允许用陈旧缓存伪装成最新事实。

缓存 Key、TTL 和失效时机应由明确业务场景定义。股票资料更新、模型激活等改变查询结果的操作必须主动清理相关缓存。

### 4. MongoDB 索引初始化

`MongoIndexInitializer` 在应用启动时幂等创建关键索引，用于保证查询性能和业务唯一性。索引设计应对应真实查询和幂等约束，例如股票代码加日期、批次标识、券商业务标识或状态加更新时间。

初始化器只管理索引，不迁移或改写业务文档。索引创建失败必须记录具体 collection 和索引信息；涉及唯一索引时应先清理历史重复数据，不能依赖启动时静默覆盖。

### 5. 统一响应、异常与接口日志

`ResponseDTO<T>` 是 Controller 的唯一外层响应，至少包含 `success`、`message` 和 `data`。业务列表、分页和聚合结果由明确 DTO 承载，不使用原始 `Map` 代替契约。

全局异常处理负责将参数错误、业务拒绝和系统异常转换为统一响应，同时保留服务端诊断上下文。响应和日志不得泄露密码、Token、Cookie、私钥或券商凭据。

`ApiLoggingAspect` 为所有 Controller 记录入口类名、方法名和安全参数摘要。关键 Controller 仍需记录业务级入口日志。大对象、二进制内容和敏感字段必须脱敏或排除。

### 6. 内部 Token 认证

`InternalTokenInterceptor` 保护 `/api/internal/agentFacts/**` 等内部接口：

1. 从 `X-Internal-Token` 读取凭据。
2. 校验服务端是否配置内部 Token。
3. 使用受控比较确认请求 Token。
4. 失败时返回统一 `ResponseDTO` 错误并终止请求。

内部 Token 只解决 Backend 与 Agent 的服务间认证，不代替外部网关隔离、用户权限或交易风控。Token 不进入 URL、业务日志或返回对象。

### 7. Agent Facts 只读聚合

`InternalAgentFactsController` 提供最新价格、交易日历、策略状态、预测候选、券商状态、绩效、模型概览、任务诊断和交易执行门禁等结构化事实。

`InternalAgentFactsService` 及相关聚合 Service 只能调用业务模块公开 Service 和 DTO，禁止直接访问业务 Repository 或暴露 Entity。这样可以保证：

- 业务模块继续拥有事实解释权。
- Agent 只能获得最小、只读、脱敏数据。
- 聚合接口不会绕过交易和模型门禁。
- 业务持久化结构变化不会直接扩散到 Agent。

Backend 与 `stock-agent` 不共享 Java DTO JAR，各自维护 HTTP 契约，并通过接口检查保持字段一致。

### 8. 信息截止时间与决策上下文

`InternalTradeDecisionContextService` 按候选的 `informationCutoffAt` 组装市场、新闻、账户和持仓事实。所有查询必须限制在截止时间或更早：

- 账户使用 `getAccountAtOrBefore` 等历史快照查询。
- 行情日期不得晚于基础交易日和信息截止日期。
- 新闻窗口以截止时间向前回看。
- 缺失或超出覆盖范围的事实写入质量标记，而不是读取当前最新值替代。

该设计防止 Agent 评价历史决策时获得未来信息。上下文只用于研究与评价，不能直接触发真实委托。

### 9. Adaptive Strategy 只读总览

`AdaptiveStrategyOverviewService` 聚合策略模式、配置版本、切换记录、模型状态和相关任务状态，为前端提供只读总览。它不修改策略状态，也不直接控制调度或交易。

总览中的每个分区应保留数据时间和不可用原因。某个下游能力失败时，可以返回其他独立分区，但不能把失败分区填成默认“正常”。

### 10. 公共契约准入条件

平台公共契约只接收满足以下条件的类型：

- 确实被两个或以上模块稳定使用。
- 不包含具体业务 Entity 或供应商响应结构。
- 语义长期稳定，适合作为跨模块边界。
- 无法由单一业务模块自然拥有。

当前典型契约包括 `ResponseDTO<T>`、全局异常类型和 `JobExecutionQueryPort`。不为形式统一增加无调用价值的 Facade、Port、Mapper 或兼容层。

## 持久化与缓存装配

| 存储 | 权威职责 |
|---|---|
| MySQL | 用户、配置、任务、模型元数据、交易状态及需要事务和锁约束的数据 |
| MongoDB | 行情、新闻、预测、回测、账户和持仓快照等时序或大文档数据 |
| MongoDB GridFS | LSTM 参数、训练快照和其他大模型制品 |
| Redis | 共享登录 Session 和可重建查询缓存，不作为权威事实源 |
| Flyway | 仅管理 MySQL 结构迁移 |

## 职责目录

```text
com/stock/platform/
├── aspect/       # 全局接口日志等横切能力
├── bootstrap/    # 启动期模型与运行日志动作
├── config/       # 持久化、缓存和平台 Bean 配置
├── controller/   # 内部事实与跨业务聚合接口
├── dto/          # 统一响应和内部 API 契约
├── exception/    # 公共异常及全局异常处理
├── interceptor/  # 内部令牌等平台拦截
├── port/         # 稳定的跨模块查询契约
└── service/      # 只读聚合和平台用例
```

Backend 整体结构见 [Stock Backend 整体架构](../index.md)，跨系统关系、Agent 安全边界和部署分流见[系统总览](../../system/project-overview.md)。

## 维护规则

- 平台聚合不得直接访问业务 Repository 或暴露业务 Entity。
- 公共契约必须具备多个真实调用方，不为形式统一增加 Facade、Port 或兼容层。
- 启动、扫描、认证和持久化配置变更时，检查 Maven `mainClass`、组件扫描、Repository 扫描和部署配置。
- 内部 API 变更必须同步检查 `stock-agent` 客户端 DTO 和调用方。
