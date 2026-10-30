---
id: stage-04-sqlite-state-loop
title: 阶段 04：SQLite 状态闭环报告
sidebar_position: 104
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-04-sqlite-state-loop/
source_paths:
  - stock-trading/src/stock_trading/config.py
  - stock-trading/src/stock_trading/cli.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/research/backtest.py
---

<DocMetadata />

# 阶段 04：SQLite 状态闭环报告

## 阶段目标

使用一个本地 SQLite 文件保存任务、模型、预测、影子订单、成交、持仓和绩效状态，形成可恢复、可幂等的最小运行闭环，不引入 MySQL、MongoDB、Redis、ORM 或迁移框架。

## 已完成

- 使用 Python 标准库 `sqlite3` 建立单文件状态库。
- 默认路径为 `stock-trading/var/state/trading.db`，可通过 `STOCK_TRADING_STATE_DB` 覆盖。
- 启用 WAL、外键校验、30 秒锁等待和事务提交。
- 模型制品、Top 5、真实成本回测订单、成交和每日权益可一次性事务导入。
- 使用模型、Top 5 和回测摘要的 SHA-256 指纹构造稳定业务键。
- 同一制品重复导入返回 `SKIPPED`，不重复写入任何业务表。
- 制品变化会生成新任务运行，但模型、预测、订单和绩效仍通过唯一键更新。
- 导入失败时业务表整体回滚，任务记录保留 `FAILED` 和错误原因。
- 应用重启后，遗留的 `QUEUED` 或 `RUNNING` 任务统一保守标记为 `FAILED`，不自动继续新增风险。
- 当前模型 Rank IC 为负，模型状态自动保存为 `REJECTED`。
- `REAL_TRADING_ENABLED` 初始化并持续保持为 `false`。

## 数据表

```text
task_run             任务业务键、状态、起止时间和失败原因
model_version         模型哈希、特征集、状态、制品和指标
prediction            每日 Top 5 排名、分数和成熟标签
shadow_order          影子买卖订单与幂等 client_order_id
shadow_execution      影子成交、价格和费用
position              当前持仓、可卖数量和平均成本
performance_snapshot  每日现金、市值和账户权益
system_setting        安全开关；真实交易默认关闭
```

## 真实制品导入

对 `portfolio-backtest-realistic` 执行导入：

```text
task_run：1
model_version：1
prediction：600
shadow_order：234
shadow_execution：234
position：0
performance_snapshot：119
```

当前回测期末已平仓，因此 `position=0`。代码同时支持期末未平仓持仓，能够保存数量、T+1 可卖数量和含买入费用的平均成本。

## 幂等与恢复验证

### 重复导入

同一指纹首次返回 `SUCCESS`，第二次返回 `SKIPPED`。两次之后所有业务表计数完全一致，没有重复预测、订单或成交。

### 事务失败

使用不存在的回测制品触发导入失败：

```text
task_run：1，状态 FAILED
model_version：0
prediction：0
shadow_order：0
shadow_execution：0
performance_snapshot：0
```

证明模型和预测虽然先进入事务，但后续订单制品失败时全部回滚，不会留下半完成状态。

### 中断恢复

人工构造一个 `RUNNING` 任务后执行恢复命令：

```text
recoveredRuns：1
最终状态：FAILED
error_message：application restarted before task completion
```

### 数据完整性

- SQLite `journal_mode=wal`。
- `PRAGMA foreign_key_check` 返回 0 条违规。
- 重复订单业务键为 0。
- 重复预测业务键为 0。
- 真实交易开关为 `false`。

## 命令入口

```text
sync-state     导入模型、预测和指定影子回测场景
show-state     查看表计数、最新任务、最新模型和安全开关
recover-state  将中断任务保守标记为失败
```

## 安全结论

状态闭环已经具备“运行有记录、重复不重做、失败不留半状态、重启不冒险继续”的最小属性。当前模型仍为 `REJECTED`，SQLite 中只有影子订单和成交，不存在真实券商订单标识或真实持仓。

## 已知限制

- 当前是单进程 SQLite，不支持多节点同时写入；这与单机最小系统目标一致。
- 没有历史 schema 迁移框架；新项目直接维护最终建表语句。
- 任务恢复只做保守失败，不自动重放预测或交易。
- 影子成交来自回测制品，不是券商事实。
- 未执行任何券商 HTTP 请求、Java Maven 或 React 构建。

## 下一阶段

阶段 05 将把已经验证的 Java 券商 HTTP 契约逐项迁移到 Python。先实现账户、持仓、行情、委托和成交的只读影子同步，再实现受 SQLite 幂等键和安全开关约束的下单/撤单；当前模型未通过收益门槛，因此阶段五验收仍禁止发送真实委托。
