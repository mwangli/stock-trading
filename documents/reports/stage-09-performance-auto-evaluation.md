---
id: stage-09-performance-auto-evaluation
title: 阶段 09：绩效与自动评估报告
sidebar_position: 109
owner: system
type: report
status: RUNTIME_VERIFIED
last_verified: 2026-10-30
slug: /reports/stage-09-performance-auto-evaluation/
source_paths:
  - stock-trading/src/stock_trading/trading/performance.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/config.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 09：绩效与自动评估报告

## 阶段目标

把券商账户和成交事实转换为每日可审计绩效，并在达到滚动窗口后自动判断继续观察、保持生产模型、停止新增交易并请求重训，或在没有生产模型时请求首次训练。

## 命令入口

```text
update-live-performance --date YYYY-MM-DD [--external-cash-flow 金额]
show-training-requests
```

`external-cash-flow` 用于记录已知入金或出金。日收益计算会扣除该金额，避免本金从 2000 元提高到 2 万元时被误判为模型利润。正常无资金变动时保持默认值 0。

## 绩效口径

每日使用券商 `total_assets` 作为权益，`available_cash` 作为现金，`total_position` 作为持仓市值：

```text
净损益 = 当日权益 - 上日权益 - 外部现金流
毛损益 = 净损益 + 估算交易费用
净收益率 = 净损益 / 上日权益
毛收益率 = 毛损益 / 上日权益
```

- 毛收益用于判断模型在暂时忽略小资金固定手续费后是否具备预测边际。
- 净收益用于判断当前本金规模下能否达到真实业务收益目标。
- 二者同时保存，不能用毛收益代替真实账户净收益。

券商成交响应当前没有经过验证的费用字段，因此费用按成交事实估算：每个券商订单收取 `max(最低佣金, 成交额×佣金率)`，全部成交额计过户费，卖出额另计印花税。同一订单多笔成交只计算一次最低佣金。

## 模型版本归属

每个 `live_performance_snapshot` 保存当日 `ACTIVE` 模型版本。滚动窗口只读取同一 `model_version_id` 的收益，模型切换后不会把新旧模型表现混入同一次评估。没有 `ACTIVE` 模型时版本为空，只用于判断需要建立生产模型。

## 滚动决策

默认窗口为 20 个有效收益日，可通过环境变量调整：

- `WARMUP`：有效收益样本不足窗口，只记录指标。
- `KEEP`：窗口毛收益大于最低阈值，且净值最大回撤不超过上限。
- `RETRAIN`：存在 `ACTIVE` 模型，但毛收益不达标或回撤越界。
- `TRAIN_REQUIRED`：达到评估窗口但没有 `ACTIVE` 模型。

`RETRAIN` 会在同一 SQLite 事务中把当前模型标记为 `DEGRADED`、把 `REAL_TRADING_ENABLED` 设为 `false`，并创建唯一 `PERFORMANCE_DEGRADATION` 重训请求。`TRAIN_REQUIRED` 创建唯一 `NO_ACTIVE_MODEL` 请求并保持新增交易关闭。同一业务日期重复运行不会生成重复请求。

正收益日比例、滚动毛收益、滚动净收益和最大回撤全部记录在 `model_evaluation`。正收益日比例是解释指标，不单独建立一票否决 Gate。

## 本地伪券商测试

临时 SQLite 和内存券商快照验证通过：

- 两个有效收益日均为正时决策为 `KEEP`，生产模型和交易开关保持不变。
- 买入成交额 1000 元时估算费用为 5.01 元，账户净损失 10 元对应毛损失 4.99 元。
- 滚动毛收益不达标时模型自动变为 `DEGRADED`，交易开关关闭，并只生成一条重训请求。
- 同日重放不重复生成评估和训练请求。
- 权益增加 5000 元且外部现金流为 5000 元时，当日损益和收益率均为 0。
- 没有 `ACTIVE` 模型但达到窗口时决策为 `TRAIN_REQUIRED`，请求类型为 `NO_ACTIVE_MODEL`。
- 绩效窗口按模型版本隔离，不把模型切换前后的收益混合评分。

测试结果为 `stage09 live performance tests: passed`。Python 语法和 SQLite 表结构检查通过。

## 验证边界

- 测试使用伪造账户、成交和模型状态，没有连接真实券商。
- 费用是基于当前费率配置的估算值，不是券商交割单中的最终费用。
- 外部入金或出金需要在执行命令时明确传入；当前已验证券商接口不能自动提供银证转账流水。
- 重训请求已持久化，但尚未由生产调度器自动消费并执行新的 Qlib 训练。
- 当前真实模型仍为 `REJECTED`，真实交易开关仍为 `false`。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

阶段 10 已实现只读准入和小资金门禁，但当前环境预检仍为 `BLOCKED`。只有补齐当日交易日历、真实券商配置和 `ACTIVE` 模型后，才能完成账户、持仓、五档、委托和成交的真实只读核对，再以小资金、单股票和可随时撤单的方式验证真实链路。
