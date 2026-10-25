---
id: schedule-overview
title: Schedule 任务调度
sidebar_position: 1
owner: schedule
type: domain
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-25
slug: /backend/schedule/
source_paths:
  - stock-backend/src/main/java/com/stock/schedule/README.md
  - stock-backend/src/main/java/com/stock/schedule/service/JobSchedulerService.java
  - stock-backend/src/main/java/com/stock/schedule/service/JobExecutionService.java
  - stock-backend/src/main/java/com/stock/schedule/bootstrap/JobBootstrap.java
---

<DocMetadata />

**IDEA 源码入口**：[任务注册 `JobSchedulerService.java`](../../../stock-backend/src/main/java/com/stock/schedule/service/JobSchedulerService.java) · [任务执行 `JobExecutionService.java`](../../../stock-backend/src/main/java/com/stock/schedule/service/JobExecutionService.java) · [启动对账 `JobBootstrap.java`](../../../stock-backend/src/main/java/com/stock/schedule/bootstrap/JobBootstrap.java)

# Schedule 统一调度模块

## 模块定位

`com.stock.schedule` 是 Backend 的统一后台任务边界，负责固定任务目录、Quartz 注册、Cron 触发、手工触发、暂停恢复、中断和执行记录。业务类不得使用 `@Scheduled` 建立第二套调度入口。

## 核心能力

| 能力 | 主要职责 |
|---|---|
| 任务目录 | 固定 `jobName`、Quartz Job 类、默认 Cron 和模块归属 |
| 启动对账 | 补齐配置、清理孤儿任务并重建 RAMJobStore |
| 运行控制 | 注册、暂停、恢复、手工触发和停止请求 |
| 执行审计 | 记录来源、操作人、状态、摘要、错误和耗时 |
| 中断协作 | 传播线程中断与业务取消检查 |
| 业务触发 | 调用各模块公开 Service，不承载业务算法 |

## 重点能力详细设计

### 1. 固定任务目录

`JobSchedulerService` 显式维护 `jobName -> Quartz Job class` 映射。只有登记在固定目录中的任务才能注册、恢复、手工触发或中断，防止数据库任意类名进入反射执行链。

`JobBootstrap` 同时维护默认 Cron、描述、模块和同步模式。新增任务必须同时具备：

1. 固定任务名称。
2. 对应 Quartz Job 类。
3. 默认 `JobConfig`。
4. 调用业务 Service 的 `task` 或业务入口。
5. 执行日志和本文任务目录说明。

任务名称是数据库配置、Quartz Key、执行记录和前端管理页的共同标识，不能随意重命名。

### 2. 启动对账与 RAMJobStore 重建

MySQL `job_config` 是任务启停和 Cron 的持久化来源，Quartz 使用 RAMJobStore 保存本次进程的运行调度状态。应用启动时执行：

1. `JobBootstrap` 幂等补齐缺失的默认任务配置。
2. 保留数据库中已有任务的 Cron 和启停状态，不用默认值覆盖运维修改。
3. `JobSchedulerService` 删除已不在固定目录中的历史 Quartz 定义。
4. 根据启用配置重新创建 JobDetail 和 Cron Trigger。
5. 返回已注册、暂停、未注册及下一次触发时间等运行状态。

RAMJobStore 在重启后丢失是预期行为，因此不能把 Quartz 内存状态当作权威配置。数据库存在但代码未登记的任务不会被动态执行。

### 3. 注册、暂停和恢复

暂停任务时，`JobManagementService` 先更新 `job_config` 启停状态，再调用 Quartz 暂停；恢复时先确认任务已登记并存在 Quartz 定义，再恢复触发器。

暂停只阻止后续触发，不等于中断正在运行的实例。停止当前执行必须使用运行记录 `runId` 发起单独中断请求。

Cron 变更和重新注册需要保持同一 `jobName`，避免生成重复 JobKey。调度异常必须抛出并保留上下文，不能只改数据库状态而假装 Quartz 已同步成功。

### 4. 手工触发

`JobExecutionService.triggerManually` 的流程：

1. 校验任务配置存在且处于启用状态。
2. 校验 Quartz 中已经注册对应任务。
3. 生成唯一 `runId`。
4. 创建状态为 `QUEUED` 的执行记录，来源为 `MANUAL`，保存操作人。
5. 将 `runId`、`source` 和 `triggeredBy` 放入 Quartz `JobDataMap`。
6. 调用 Quartz 触发同一固定 Job。

提交 Quartz 失败时必须把执行记录更新为失败，不能留下永久 `QUEUED`。计划触发由 Quartz Job 创建或补齐运行记录，来源为 `SCHEDULED`。

### 5. 执行记录状态机

`job_execution_record` 保存每次运行的 `runId`、`jobName`、触发来源、触发人、计划时间、开始时间、结束时间、摘要、错误和耗时。

状态定义：

| 状态 | 含义 |
|---|---|
| `QUEUED` | 已创建记录，等待 Quartz 工作线程 |
| `RUNNING` | Quartz Job 已进入业务执行 |
| `SUCCESS` | 业务动作完整成功 |
| `FAILED` | 发生未处理异常或关键步骤失败 |
| `SKIPPED` | 因交易日、数据窗口等正常业务条件跳过 |
| `REJECTED` | 因门禁或权限不满足而拒绝 |
| `CANCELLED` | 收到取消请求或应用停止中断 |

`JobSkippedException` 用于表达可审计的业务跳过，不能与系统异常混为失败。执行记录是调度审计事实，不替代业务模块自身的批次和状态记录。

### 6. 中断协作

`InterruptibleQuartzJob` 实现 Quartz `InterruptableJob`，保存当前执行线程并在停止请求时调用 `Thread.interrupt()`。`JobExecutionService` 同时登记 `runId` 级取消请求。

中断是协作式机制：

- Quartz Job 将中断状态传递给 `task`。
- 长循环、批量回补、训练和同步在安全检查点检查线程中断。
- 捕获 `InterruptedException` 后必须恢复线程中断标志。
- 正在进行的数据库原子写或券商请求不能被强行破坏，应在当前安全步骤完成后退出。
- 中断请求发送失败时保留真实运行状态，不能直接把记录写成 `CANCELLED`。

### 7. Quartz Job、Task 与业务 Service

三层职责固定为：

| 层 | 职责 |
|---|---|
| `quartz` | 读取运行上下文，调用统一执行记录包装 |
| `task` | 选择业务用例、组织任务参数和处理中断 |
| 业务 Service | 数据同步、训练、预测、决策和交易规则 |

Quartz Job 不实现股票循环、模型算法、策略评分、风险判断或券商协议。`schedule` 可以调用各业务模块公开 Service，但不能访问其 Repository 或 Entity。

### 8. Misfire 与失败恢复

Cron Trigger 使用不补跑策略。服务停机期间错过的任务不会在恢复后集中补执行，避免行情同步、训练和真实交易任务同时堆积。

需要补历史数据的场景由独立回补任务和持久化游标处理。预测、评估、新闻回填、券商同步等业务恢复逻辑归各自模块，调度模块只负责再次触发和记录结果。

## 固定任务目录

| 任务 | 业务用途 |
|---|---|
| `stockListSync` | 同步股票基础资料和生命周期信息 |
| `dailyStockDataSync` | 同步最近交易日日线数据 |
| `stockPriceHistoryBackfill` | 修复历史行情缺口 |
| `newsDailySync` | 采集和整理财经新闻 |
| `newsCollectionBackfill` | 分批回填历史新闻治理和事件事实 |
| `newsFeatureSnapshotBackfill` | 分批生成 temporal-news-v1 股票日级快照并执行点时审计 |
| `tradeCalendarMonthlySync` | 维护交易日历覆盖 |
| `lstmOfflineTraining` | 串行训练基础时序模型和所需的 MASTER 挑战者，并自动评估晋级 |
| `dailyModelPrediction` | 生成每日模型预测批次 |
| `sentimentMengziShadow` | 执行情感候选影子推理和评估 |
| `masterShadowPrediction` | 默认启用；补齐 MASTER 影子预测和后验结果，按最近窗口搜索双模型连续权重并刷新自动选择状态 |
| `stockSelection` | 生成策略排名和候选 |
| `realTradingAutoBuy` | 对候选执行 Java 风控和自动买入检查 |
| `realTradingT1Exit` | 执行符合条件的 T+1 退出流程 |
| `broker-current-facts-sync` | 同步账户、持仓、订单和成交事实 |

`dailyModelPrediction` 在工作日 `06:15` 生成 Ranking LSTM 基础批次，`masterShadowPrediction` 在 `06:20` 保持 MASTER 每日影子预测并刷新下一批次使用的权重，`stockSelection` 在 `06:30` 消费固定权重版本。单个候选批次执行期间不得改变权重。任一模型权重为 `0` 也不停止对应预测，确保系统能够在后续样本外窗口自动恢复。

## 职责目录

```text
com/stock/schedule/
├── bootstrap/    # 默认任务补齐和启动协调
├── config/       # Quartz 配置
├── controller/   # 任务查询、触发、暂停和停止接口
├── dto/          # 任务与执行记录响应契约
├── entity/       # 任务配置和执行记录
├── enums/        # 执行状态和触发来源
├── quartz/       # 固定 Quartz Job 入口
├── repository/   # 任务配置和执行历史数据访问
├── service/      # 注册、控制和执行记录编排
├── support/      # Quartz 基类和中断支持
└── task/         # 业务任务入口，调用业务 Service
```

## 依赖与维护规则

- `schedule` 可以调用各业务模块公开 Service，但不得访问其 Repository 或 Entity。
- Quartz Job 和 `task` 不复制业务算法，不拼装持久化对象。
- 新增任务必须同时维护固定映射、默认配置、执行入口、日志和本文任务目录。
- 修改任务名称、Cron 或状态语义前，检查数据库既有配置、前端任务页和执行历史兼容性。
