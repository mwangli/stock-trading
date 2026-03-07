# 动态任务调度系统设计

日期：2026-03-08

## 目标

在后端实现动态任务调度系统，新增任务配置实体与管理 API，统一由调度服务管理 `ThreadPoolTaskScheduler`，并将现有 `@Scheduled` 任务迁移到动态调度体系中。

## 范围

- 新增 `com.stock.job` 包
- 实现 `JobConfig` 实体与 `JobConfigRepository`
- 实现 `JobSchedulerService` 管理动态任务
- 重构 `DataSyncScheduler`、`StrategyScheduler`，移除 `@Scheduled`
- 新增 `JobAdminController` 管理任务
- 删除旧的 `com.stock.dataCollector.controller.JobController`

## 方案选型

### 方案 A（推荐）：DB 驱动动态调度

- 任务配置持久化到数据库
- 应用启动加载启用任务并注册调度
- 管理 API 直接操作任务配置并触发重建

**优势**：持久化、可运维、符合新增实体与仓库要求。

### 方案 B：内存动态调度

- 任务配置仅保存在内存
- 重启后配置丢失

**劣势**：不满足持久化场景，对仓库要求不匹配。

### 方案 C：配置文件驱动

- 从 `application.yml` 读取任务配置
- 通过刷新接口重载

**劣势**：动态性不足，运维成本较高。

## 组件设计

### 1. `JobConfig` 实体

字段（可按需要扩展）：

- `id`：主键
- `jobKey`：任务唯一标识（如 `DATA_SYNC_DAILY`）
- `jobName`：任务名称
- `cron`：Cron 表达式
- `enabled`：是否启用
- `handler`：执行器标识（用于映射到具体任务）
- `description`：任务描述
- `createdAt` / `updatedAt`：时间戳

### 2. `JobConfigRepository`

基于 Spring Data JPA，支持 `findByEnabledTrue`、`findByJobKey` 等查询。

### 3. `JobSchedulerService`

职责：

- 持有 `ThreadPoolTaskScheduler`
- 启动加载 DB 中启用任务并注册
- 提供 `scheduleJob`、`cancelJob`、`reloadJob`、`reloadAll`、`runOnce` 等方法
- 维护 `jobKey -> ScheduledFuture` 映射

### 4. 任务处理器映射

使用固定映射，将 `jobKey/handler` 映射到具体方法：

- `DATA_SYNC_LIST` → `DataSyncScheduler.syncStockListDaily()`
- `DATA_SYNC_DAILY` → `DataSyncScheduler.syncDailyStockData()`
- `DATA_SYNC_HISTORY` → `DataSyncScheduler.syncAllHistoricalData()`
- `STRATEGY_SELECT` → `StrategyScheduler.runStockSelection()`
- `STRATEGY_SIGNAL` → `StrategyScheduler.runSignalGeneration()`
- `STRATEGY_INTRADAY` → `StrategyScheduler.checkIntradaySell()`
- `STRATEGY_FORCE_SELL` → `StrategyScheduler.checkForceSell()`
- `STRATEGY_SWITCH` → `StrategyScheduler.checkTimeBasedSwitch()`

### 5. `JobAdminController`

路径建议：`/api/job/admin`

接口：

- `GET /list`：查询任务列表
- `POST /create`：新增任务
- `PUT /update`：更新任务配置（自动重载）
- `POST /run`：手动执行
- `POST /pause`：禁用并取消调度
- `POST /resume`：启用并重建调度

## 数据流

1. 应用启动 → `JobSchedulerService` 加载启用任务 → 注册调度
2. 管理 API 变更任务 → 保存 `JobConfig` → 重建调度
3. 手动执行 → 直接调用任务处理器方法

## 错误处理

- Cron 解析异常：记录日志并跳过注册
- 任务执行异常：捕获并记录日志，不影响调度器运行

## 验证方案

- 对变更文件执行 `lsp_diagnostics` 确保无语法错误
- 运行 `mvn compile`（后端）确保编译通过
