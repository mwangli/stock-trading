---
id: backend-overview
title: Backend 总览
sidebar_position: 1
owner: backend
type: architecture
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /backend/
source_paths:
  - stock-backend/src/main/java/com/stock/StockBackendApplication.java
---

<DocMetadata />

**IDEA 源码入口**：[Backend 应用入口 `StockBackendApplication.java`](../../stock-backend/src/main/java/com/stock/StockBackendApplication.java)

# Stock Backend 整体架构

## 文档定位

本文件是 `stock-backend` 的整体架构入口，与 Backend 启动类 `StockBackendApplication` 位于同一组合根 `com.stock` 下。

这里说明六个业务包如何协作、依赖方向、公共技术装配、数据所有权和安全边界。各领域内部能力与详细设计由文档门户对应领域页面维护。

## 代码结构

```text
com/stock/
├── StockBackendApplication.java  # Backend 唯一启动类和 Spring 组合根
├── market/                       # 股票、行情、新闻、日历和数据质量
├── model/                        # 情感、LSTM、训练、预测和模型生命周期
├── decision/                     # 选股、信号、策略状态和回测
├── execution/                    # 用户、账户、风控、券商和真实交易
├── schedule/                     # Quartz、任务控制和执行记录
└── platform/                     # 技术装配、公共契约和内部事实 API
```

Backend 只保留“业务包 → 职责层”两级结构。业务包内部根据实际代码建立 `controller`、`service`、`repository`、`entity`、`dto`、`algorithm`、`client`、`adapter`、`validator`、`config` 等职责目录，不创建空目录或重复分层。

## 模块职责

| 模块 | 权威职责 | 不负责 |
|---|---|---|
| [Market](./market/index.md) | 市场事实采集、标准化、持久化和质量治理 | 模型推断、策略判断、真实交易 |
| [Model](./model/index.md) | 训练快照、模型训练、推理、评估、激活和回滚 | 选股规则和真实委托 |
| [Decision](./decision/index.md) | 候选排名、信号、策略状态和历史回测 | 券商账户事实和最终执行 |
| [Execution](./execution/index.md) | 用户、账户、风险、委托、成交同步和绩效 | 训练模型和生成策略候选 |
| [Schedule](./schedule/index.md) | Quartz 触发、任务配置、运行控制和执行审计 | 业务算法和持久化事实解释 |
| [Platform](./platform/index.md) | Spring 装配、存储配置、统一契约和内部聚合 | 具体市场、模型、策略和交易规则 |

## 主业务依赖方向

```text
market → model → decision → execution
   ↑        ↑         ↑          ↑
   └────────┴─────────┴──────────┘
              schedule 触发

platform 提供公共契约和应用装配，
并通过公开 Service 聚合只读内部 API。
```

箭头表示下游消费上游公开能力：

- `model` 可以调用 `market.service` 和市场 DTO，不访问市场 Repository 或 Entity。
- `decision` 可以调用 `market`、`model` 的公开 Service 和 DTO。
- `execution` 可以读取市场事实、模型资格和决策候选，但必须自行完成最终风控和券商校验。
- `schedule` 可以触发各模块公开 Service，不复制业务算法。
- `platform` 可以聚合各模块公开 Service，不直接读取业务 Repository。
- `market`、`model` 和 `decision` 不反向依赖 `execution`，确保事实、推断和策略不受券商实现污染。

## 启动与应用装配

`com.stock.StockBackendApplication` 是 Backend 唯一启动类。启动类位于六个业务包共同父包，`@SpringBootApplication` 默认以 `com.stock` 为组件扫描根。

Maven `spring-boot-maven-plugin.mainClass` 必须保持为：

```text
com.stock.StockBackendApplication
```

启动过程由 Spring 和平台组件共同完成：

1. 创建应用上下文并绑定类型化配置。
2. 初始化 MySQL、MongoDB、Redis、HTTP 和线程池配置。
3. 执行 Flyway MySQL 迁移。
4. 初始化 MongoDB 必要索引。
5. 补齐固定任务配置并重建 Quartz RAMJobStore。
6. 检查模型制品和可选能力状态。
7. 应用就绪后记录完整启动耗时。

启动类只负责建立组合根和启动进程，不放置业务 Bean、数据访问、调度逻辑或异常处理。

## 分层职责

| 职责层 | 设计要求 |
|---|---|
| `controller` | HTTP 参数、入口日志、协议转换和 `ResponseDTO<T>` 封装 |
| `service` | 业务流程、事务、状态转换和用例编排 |
| `repository` | MySQL、MongoDB、Redis 的数据访问 |
| `entity` | 持久化对象及与持久化状态直接相关的枚举 |
| `dto` / `vo` | 接口输入输出、跨模块只读结果和内部值对象 |
| `algorithm` / `calculator` | 无持久化副作用的模型、指标、评分和费用计算 |
| `client` / `adapter` | 外部证券、券商和供应商调用及内部契约适配 |
| `parser` | 外部响应解析和格式标准化 |
| `validator` | 数据完整性、交易时间和业务门禁校验 |
| `config` | Spring Bean、类型化参数和基础设施配置 |

Controller 不直接访问 Repository；DTO 不承载查询、外部调用或状态机；纯计算不依赖 Spring 持久化组件。

## 数据所有权

| 数据 | 权威模块 | 主要存储 |
|---|---|---|
| 股票资料、行情、新闻、交易日历 | `market` | MySQL / MongoDB |
| 训练快照、模型参数、预测和评估 | `model` | MySQL / MongoDB / GridFS |
| 策略配置、候选、信号和回测 | `decision` | MySQL / MongoDB |
| 用户、账户、持仓、订单、成交和绩效 | `execution` | MySQL / MongoDB |
| 任务配置和执行记录 | `schedule` | MySQL，Quartz 使用 RAMJobStore |
| 共享 Session 和可重建缓存 | `platform` 装配，各模块使用 | Redis |

Redis 不保存不可丢失的权威事实。跨模块不得传递持久化 Entity，统一使用明确 DTO。

## 核心业务链路

### 市场到候选

```text
市场数据采集
  → 数据质量确认
  → 训练快照与模型推理
  → 预测评估和交易资格
  → 策略过滤与综合评分
  → 候选批次
```

每个阶段都保留业务日期、信息截止时间、来源批次和版本。缺失或覆盖不足必须明确失败、等待或跳过，不能用默认值伪装完整。

### 候选到真实交易

```text
候选批次
  → 真实写入开关
  → 交易日和交易时间
  → 最新行情和可交易状态
  → 账户、资金、持仓和 T+1
  → 仓位、费用、数量和幂等
  → 券商委托
  → 订单回查、成交同步和绩效聚合
```

候选、模型和 Agent 输出都不是最终订单。`execution` 是唯一真实交易写入边界。

### 调度执行

```text
job_config
  → Quartz Trigger
  → Quartz Job
  → schedule.task
  → 业务 Service
  → job_execution_record
```

Quartz Job 只传播运行上下文并记录执行状态。历史回补、预测恢复、训练状态和券商同步游标由业务模块自身管理。

## 公共契约与内部 API

`platform` 提供 `ResponseDTO<T>`、公共异常、稳定查询 Port 和内部事实 DTO。公共契约只有在确实存在跨模块调用价值时才进入平台包。

`/api/internal/agentFacts/**` 面向 `stock-agent` 提供最小、只读、脱敏事实，并使用 `X-Internal-Token` 认证。Agent 不直接连接 Backend Repository，不持有券商凭据，也不控制真实交易放行。

## 关键安全原则

- 真实交易写入默认关闭，必须显式开启环境级门禁。
- 自动交易紧急停止只阻断后续自动委托，不删除订单和审计事实。
- 网络超时或结果不确定时优先回查订单，禁止立即重复提交。
- 模型训练成功只生成候选版本，不自动激活生产模型。
- 预测完成不等于具备交易资格，必须通过覆盖和评估门禁。
- 历史评价和回测必须遵守信息截止时间，禁止未来数据泄漏。
- 密码、Token、Cookie、私钥和券商凭据不得进入日志、DTO 或代码仓库。

## 文档维护

- Backend 整体结构、模块关系、依赖方向和公共约束在本文件维护。
- 模块内部功能、重点能力和详细设计在 `documents/backend` 对应领域维护。
- 跨 Backend、Agent、Frontend 的系统关系、端到端业务流程和部署方式见[系统总览](../system/project-overview.md)。
- 尚未完成完整验证的专项设计保留在所属领域，通过 `PROPOSED`、`SOURCE_IMPLEMENTED`、`COMPILED`、`RUNTIME_VERIFIED`、`PRODUCTION_VERIFIED` 或 `SUPERSEDED` 标识证据等级。
- Backend 模块和源码包不维护 README，职责、边界和跨类设计统一维护在本门户。

修改模块职责、跨模块 DTO、启动扫描、持久化配置或安全边界时，必须同步更新本文件和受影响领域文档。
