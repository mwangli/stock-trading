---
id: stage-08-order-execution-recovery
title: 阶段 08：订单执行与恢复报告
sidebar_position: 108
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-08-order-execution-recovery/
source_paths:
  - stock-trading/src/stock_trading/trading/execution.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/broker/client.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 08：订单执行与恢复报告

## 阶段目标

把阶段 07 的 `PLANNED` 订单变成可恢复的单阶段执行状态机。任何券商结果不确定、未知状态或重启后无法唯一匹配的情况都停止新增风险，不把 HTTP 调用成功等同于成交。

## 命令入口

```text
execute-shadow-order --client-order-id <稳定订单键>
recover-broker-orders --date YYYY-MM-DD
```

执行命令仍受 SQLite `REAL_TRADING_ENABLED=true`、最新模型 `ACTIVE` 和唯一活动订单三重门禁约束。当前项目状态不满足前两项，因此无法通过 CLI 发出真实委托。

## 状态机

```text
PLANNED
  → PREPARED
  → SUBMITTING
  → SUBMITTED / PARTIAL
  → FILLED / CANCELLED / REJECTED
```

额外保守状态：

- `CANCEL_SUBMITTING`：撤单请求已获得本地唯一执行权，但尚未确认券商终态。
- `UNKNOWN`：委托 HTTP 结果不确定、券商返回未知状态、撤单后未确认终态，或重启后无法唯一匹配。
- `UNKNOWN`、`SUBMITTING`、`SUBMITTED`、`PARTIAL` 和 `CANCEL_SUBMITTING` 均阻止创建另一笔真实订单。
- 已进入 `FILLED`、`CANCELLED` 或 `REJECTED` 的订单不能回退到活动状态。

状态同时写入 `broker_order_intent` 和对应 `shadow_order`。同一 `client_order_id` 再次执行只返回既有终态，不重复调用券商；即使真实交易开关随后关闭，仍可读取和重放已完成结果。

## 提交与撤单

- 订单意图必须先持久化为 `PREPARED`，券商客户端写入守卫再原子切换到 `SUBMITTING`。
- 券商明确返回拒绝时记录 `REJECTED`；网络或协议结果不确定时记录 `UNKNOWN`。
- 活动订单在配置的轮询次数内未终结时发起撤单。
- `REAL_TRADING_ENABLED=false` 只阻止新增委托，不阻止撤销已经存在的活动订单。
- 撤单只有在券商事实变为 `FILLED`、`CANCELLED` 或 `REJECTED` 后才释放执行权。
- 撤单未受理、轮询超时或状态未知时进入 `UNKNOWN`，禁止下一阶段或下一只股票继续下单。

当前执行器处理一个稳定 `STAGEn` 订单。后续价格阶段必须在前一阶段确认终态后，按最新五档、资金、持仓和剩余可交易数量重新生成；不能预先批量创建，也不能使用未经验证的最优五档委托参数。

## 重启恢复

`recover-broker-orders` 读取券商账户、持仓、当日委托和成交并写入 SQLite：

- 已有券商订单号时按订单号恢复状态。
- `SUBMITTING` 没有券商订单号时，只允许用股票、方向、数量和价格唯一匹配一笔尚未绑定的当日委托。
- 没有匹配或存在多个相同候选时进入 `UNKNOWN`，不猜测订单归属。
- `PREPARED` 表示尚未获得券商写入权，不因重启自动标记未知，可以在门禁仍满足时重新提交。

## 本地伪券商测试

临时 SQLite 和内存券商验证通过：

- `SUBMITTED → FILLED` 后不撤单，重复执行不重复提交。
- `SUBMITTED → PARTIAL → CANCEL_SUBMITTING → CANCELLED`，撤单只调用一次。
- 券商明确拒绝后记录 `REJECTED`。
- 券商返回 `UNKNOWN` 后立即停止，并阻止第二笔订单创建。
- 重启时唯一匹配的 `SUBMITTING` 委托恢复为 `SUBMITTED` 并绑定券商订单号。
- 重启时两笔相同券商委托无法唯一归属，订单进入 `UNKNOWN`。
- 订单完成后关闭真实交易开关，既有终态仍可幂等读取。
- 关闭真实交易开关后，既有 `SUBMITTED` 订单仍可进入 `CANCEL_SUBMITTING`。

测试结果为 `stage08 order execution tests: passed` 和 `stage08 cancel gate test: passed`。Python 语法检查和 SQLite 状态转换检查通过。

## 验证边界

- 所有委托、撤单、轮询和重启恢复均使用内存伪券商，没有连接真实 ZX 券商。
- 没有验证真实券商委托号、状态文案延迟、当日委托可见性、部分成交明细或撤单响应时序。
- 没有启用 `REAL_TRADING_ENABLED` 的项目运行库，也没有把当前 `REJECTED` 模型改为 `ACTIVE`。
- 尚未实现按真实成交更新持仓成本、现金、费用和每日绩效。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

阶段 09 从券商成交事实生成持仓与绩效快照，计算不含手续费和含手续费两套收益，并用滚动指标驱动模型评估和再训练请求。实施结果见《阶段 09：绩效与自动评估报告》。
