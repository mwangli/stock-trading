---
id: stage-07-shadow-trading-plans
title: 阶段 07：影子交易计划报告
sidebar_position: 107
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-07-shadow-trading-plans/
source_paths:
  - stock-trading/src/stock_trading/trading/orchestrator.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/config.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 07：影子交易计划报告

## 阶段目标

把生产 Top 5 和券商只读事实转换为可审计、可重复执行的上午卖出与下午买入计划。该阶段只生成 `PLANNED` 影子订单，不调用券商委托或撤单接口。

## 命令入口

```text
plan-shadow-morning --date YYYY-MM-DD
plan-shadow-afternoon --date YYYY-MM-DD
```

命令只负责显式业务日期的计划生成。时间触发继续交给后续操作系统定时器；影子回放不以当前机器时间限制执行。

## 上午卖出计划

- 读取券商当前持仓，只处理 `available_quantity > 0` 的股票。
- 使用券商五档行情卖三价 `ASK3` 生成第一阶段影子卖单。
- 数量严格使用券商可卖数量，不使用总持仓推算 T+1 可卖数量。
- 买入要求 100 股整数倍；卖出允许一次性卖出零股，避免公司行为产生的零股无法退出。
- 每只股票使用稳定业务键 `SHADOW:MORNING_SELL:日期:股票:STAGE1`。

## 下午买入计划

- 账户存在任何真实持仓时不生成新买单，保持单持仓约束。
- 只读取目标交易日前最近一批 Top 5，不使用当天或未来预测。
- 预测必须早于交易日 1 至 7 个自然日；超过 7 日视为陈旧信号并直接失败。
- 默认权限前缀为 `SH60,SZ00`，即首版只允许沪市主板和深市主板，不默认交易科创板或创业板。
- 逐名检查权限和当前价格 `5-20` 元，选择第一只通过的候选。
- 第一阶段买入价格使用买三价 `BID3`。
- 按 100 股整手计算最大数量，同时预留 `max(最低佣金, 成交额×佣金率)` 和过户费。
- 只生成一笔买入计划；不会把 Top 5 合并成多股票持仓。

## SQLite 幂等

上午和下午分别使用以下任务业务键：

```text
SHADOW_MORNING_SELL:businessDate
SHADOW_AFTERNOON_BUY:businessDate
```

同一业务日期实际生成订单后写入 `SUCCESS` 和 `PLANNED` 订单；再次运行返回 `SKIPPED`。没有可卖持仓、已有持仓或全部候选均不可买时，任务记为可重试的 `SKIPPED`，不会永久封住同日后续计划。订单写入支持应用中断后的幂等重试，只写入 `shadow_order`，不会进入 `broker_order_intent`，也不会产生 `shadow_execution`。

## 合成边界测试

本地临时 SQLite 和内存券商读取器验证通过：

- 155 股持仓中券商返回 55 股可卖时，只计划卖出 55 股。
- 可卖数量为 0 的持仓被标记为 `NO_AVAILABLE_QUANTITY`。
- 上午重复执行订单数保持 1。
- 下午第一名科创板被标记为 `NO_TRADING_PERMISSION`。
- 第二名当前价格 25 元被标记为 `PRICE_OUT_OF_RANGE`。
- 第三名沪市主板股票通过过滤，2000 元可用资金生成 200 股买入计划。
- 已有持仓时返回 `POSITION_EXISTS`，不生成买单。
- 预测超过 7 个自然日时直接拒绝，不创建任务或订单。
- 下午重复执行只保留一笔买单。
- 同一预测日存在多个模型时，按最近写入的预测批次选择，不使用模型数值 ID 猜测权威批次。
- 全部过程中 `shadow_execution=0`、`REAL_TRADING_ENABLED=false`。

测试结果为 `stage07 shadow orchestration tests: passed`。

## 真实模型制品集成

将阶段 6 的真实 `2026-10-29` Top 5 制品导入新的临时 SQLite，以 `2026-10-30` 为目标交易日执行下午计划：

```text
选择股票：SZ000559
候选排名：1
模型状态：REJECTED
```

证明生产推理制品可以直接进入影子交易选择链，没有重新解析或人工复制候选。

## 安全边界

- `ShadowTradingOrchestrator` 的券商依赖只包含账户、持仓和五档行情读取方法，没有委托方法。
- 影子命令没有调用 `submit_limit_order` 或 `cancel_order` 的代码路径。
- 当前模型仍为 `REJECTED`，本阶段仅验证候选到订单计划的确定性转换。
- 当前环境没有券商凭据，因此 CLI 未连接真实券商；所有券商事实测试均来自内存读取器。

## 未完成能力

- 尚未提交真实或模拟券商委托，也未轮询部分成交和终态。
- 尚未实现分阶段改价、撤单确认、未知状态恢复和应用重启恢复。
- 尚未根据真实成交更新持仓和绩效。
- 尚未配置 `cron` 或 `systemd timer`。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

阶段 08 实现订单执行状态机和券商事实恢复。仍先使用伪券商测试 `SUBMITTING → SUBMITTED/PARTIAL/FILLED/CANCELLED/REJECTED`，真实交易开关继续关闭。
