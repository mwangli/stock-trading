---
id: project-overview
title: 系统总览
sidebar_position: 1
owner: system
type: overview
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-27
source_paths:
  - README.md
  - stock-server/pom.xml
  - stock-server/stock-trading/src/main/java/com/stock/trading/service/RealTradingService.java
  - stock-server/stock-platform/src/main/java/com/stock/platform/task/RealTradingJob.java
slug: /system/
---

<DocMetadata />

# AI-AGENT 智能股票交易系统

系统面向 A 股市场，使用市场数据和模型预测形成每日排名，再由确定性 Java 交易流程完成分阶段卖出、模型前三名买入竞争、订单确认和绩效回写。系统采用七个 Maven 子模块组成的模块化单体，只有 `stock-application` 启动 Spring Boot 进程。

## 设计结论

- `stock-model` 是选股排名的唯一权威来源，不再由独立 Strategy 模块二次打分。
- `stock-trading` 直接读取当日可用预测批次，按 `effectiveModelScore` 降序使用前三名。
- `realTradingMorningSell` 和 `realTradingAfternoonBuy` 的启停状态是自动交易唯一控制入口。
- 不保留策略模式、策略配置、技术指标选股、策略回测、止损止盈、真实写入总门禁或紧急停止状态。
- 订单轮询、部分成交、撤单确认、资金释放、T+1 可卖数量和券商事实同步继续保留，因为它们决定订单是否能安全地继续执行，而不是策略判断。

## 七模块结构

```text
stock-base → stock-data → stock-model → stock-trading
           → stock-platform → stock-agent → stock-application
```

| 模块 | 权威职责 |
|---|---|
| `stock-base` | 最小公共响应和缓存契约 |
| `stock-data` | 股票、行情、新闻和工作日规则 |
| `stock-model` | 训练、推理、融合、预测批次和排名 |
| `stock-trading` | 分阶段买卖、券商委托、订单与成交事实、绩效 |
| `stock-platform` | Quartz 任务配置、启停、触发和执行记录 |
| `stock-agent` | 对话、RAG、Memory，以及模型排名、交易任务、委托和成交的只读查询 |
| `stock-application` | 唯一启动入口、持久化和最终装配 |

## 核心业务链路

```text
市场数据与工作日规则
→ 模型训练、融合和当日预测
→ 按 effectiveModelScore 生成当日排名
→ 09:35 上午分阶段卖出全部可卖持仓
→ 13:05 模型前三名竞争唯一资金执行权
→ 单一活动买单、部分成交锁定股票
→ 订单、成交、持仓和收益回写
```

### 上午卖出

上午任务对券商返回的全部可卖持仓并行处理，每只股票同一时间最多一笔活动卖单。价格从卖三、卖一、买一逐步提高成交概率，最后阶段优先使用最优五档即时成交剩余撤销；券商不支持时使用最新买五保护限价。窗口结束仍未成交的剩余数量撤单并记录，不伪造成交。

### 下午买入

下午任务读取目标交易日一致且明细完整的最新预测批次，选取模型排名前三名。三个候选可以并发准备，但共享唯一资金执行权，全账户任何时间最多存在一笔活动买单。部分成交后锁定该股票，后续只允许使用剩余资金继续买入同一股票。

### 任务关闭与重启

每次准备提交新委托前，交易服务重新读取当前任务的 `job_config.status`。任务关闭时撤销活动委托并等待券商终态后退出。应用重启后以券商当日活动委托、成交和持仓为事实恢复，不依赖内存轮询状态。

## 安全与事实边界

- 模型排名决定候选顺序，不拥有券商写权限。
- Agent 只读查询模型排名、任务、订单和成交，不控制交易任务或直接下单。
- 资金、持仓、T+1 可卖数量、交易时段、价格和券商订单状态由 Java 确定性逻辑处理。
- 撤单必须确认到 `CANCELLED`、`FILLED` 或 `REJECTED`；状态未知时不能释放买入执行权。
- 单次接口提交成功不代表成交成功，真实收益只按券商成交事实计算。

## 运行与验证边界

文档状态为 `SOURCE_IMPLEMENTED`，表示源码和配置已按当前设计修改。编译、启动、数据库初始化、Quartz 触发、券商五档字段、最优五档委托类型和真实成交仍需在对应环境独立验证。

## 文档导航

| 阅读目标 | 权威文档 |
|---|---|
| Backend 模块边界 | [Backend 模块总览](../modules/后端模块总览.md) |
| 模型训练与排名 | [Model 模块](../modules/stock-model/模型模块.md) |
| 分阶段交易和订单约束 | [Trading 模块](../modules/stock-trading/交易执行.md) |
| 任务启停和 Cron | [Platform 模块](../modules/stock-platform/任务调度.md) |
| Agent 只读能力 | [Agent 模块](../modules/stock-agent/智能代理总览.md) |
| 前端路由和页面 | [Frontend 模块](../modules/stock-frontend/前端设计.md) |
| 应用装配 | [Application 模块](../modules/stock-application/应用装配.md) |
