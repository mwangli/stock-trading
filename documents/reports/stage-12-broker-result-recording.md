---
id: stage-12-broker-result-recording
title: 阶段 12：券商结果直记
sidebar_position: 130
owner: system
type: report
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-30
slug: /reports/stage-12-broker-result-recording/
source_paths:
  - stock-trading/src/stock_trading/cli.py
  - stock-trading/src/stock_trading/broker/client.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/trading/execution.py
---

<DocMetadata />

# 阶段 12：券商结果直记

## 用户决策

用户明确要求删除复杂提单门禁。订单能否提交成功由券商判断，本地只需发起请求并记录结果。

## 删除内容

- 删除 `validate-real-readiness`、`enable-real-trading` 和 `disable-real-trading` 命令。
- 删除 `REAL_TRADING_ENABLED`、`real_validation_run` 及对应配置和 Service。
- 删除提单前对 ACTIVE 模型、当日只读验证、持仓、价格、资金和全局活动订单的重复门禁。
- 删除正常提交流程中的成交轮询、超时自动撤单和撤单终态等待。
- 删除上午和下午提单前的订单恢复读取，避免只读同步失败阻止券商提单。
- 删除券商客户端的 `write_guard` 和 `cancel_guard` 回调。

## 保留内容

- Top 5、价格范围、允许股票前缀、真实持仓和可用资金继续用于生成订单，因为它们决定标的和数量。
- `clientOrderId` 唯一约束继续防止同一计划因调度重跑而重复提交。
- Action 110 继续只使用已经通过真实响应确认的请求字段。
- `recover-broker-orders` 和每日券商事实同步继续更新 `SUBMITTED`、`PARTIAL`、`FILLED`、`CANCELLED` 等事实状态。

## 提交流程

```text
读取已持久化计划
→ 使用 clientOrderId 预留一次提交
→ 调用券商 Action 110
→ 返回委托号：记录 SUBMITTED
→ 券商明确拒绝：记录 REJECTED 和原因
→ 通信异常或结果不确定：记录 UNKNOWN 和原因
```

正常提交命令不再等待成交，也不自动撤单。后续定时同步读取券商事实并更新最终状态。

## 验证边界

本阶段只完成源码、引用、差异和敏感信息静态检查，没有运行 Python 测试、启动应用或调用真实券商写接口。状态为 `SOURCE_IMPLEMENTED`，不代表真实委托已经成功。
