---
id: business-performance
title: 收益与模型评估
sidebar_position: 40
owner: business
type: design
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /business/performance-evaluation/
source_paths:
  - stock-trading@src/stock_trading/trading/broker_sync.py
  - stock-trading@src/stock_trading/trading/review.py
  - stock-trading@src/stock_trading/state/store.py
  - stock-trading@src/stock_trading/research/backtest.py
---

# 收益与模型评估

## 两套收益口径

研究回测与真实交易必须分开解释：

- 回测同时输出毛收益和净收益；模型能力判断主要观察不扣固定费用的毛收益、毛回撤等指标。
- 真实交易只统计券商成交事实形成的完整买卖闭环；浮动盈亏、未卖持仓、账户总资产和入金出金不计入策略收益。

## 已实现交易

`stock-order` 同步历史成交订单，并使用 FIFO 按成交数量匹配买入和卖出。部分卖出只确认已匹配数量对应的收益，未卖出的买入继续保留为待闭环交易。

```text
毛利润 = 卖出金额 - 买入成本
净利润 = 毛利润 - 买入费用 - 卖出费用
毛收益率 = 毛利润 / 买入成本
净收益率 = 净利润 / 含费用成本
```

券商没有返回费用时，每笔成交委托使用 5 元基础费用并标记 `BUSINESS_ZERO`；解析到真实聚合费用或明细时标记 `BROKER_ACTUAL`，存在聚合总费用时不重复叠加明细。

## 月度模型复盘

`stock-review` 先汇总指定业务日已实现收益，再按卖出月份聚合最近 6 个有效月份的毛收益：

- 样本不足：`WARMUP`。
- 总复合毛收益大于 0、盈利月份比例不低于 60%、最大毛回撤不高于 10%：`KEEP`。
- 样本充足但任一指标不达标：`RETRAIN`，并生成待处理训练请求。

复盘命令本身不会直接训练模型。训练由独立 `stock-train` 命令完成，避免评估、资源消耗和模型切换形成隐式链路。

## 解释边界

- 模型切换前后的真实收益按模型版本分别归属。
- 没有足够已实现月份时，不能把 `WARMUP` 解释为模型通过。
- 控制台持仓展示不是实时市值证明，最终资金与持仓仍应以券商查询为准。
- 页面统计和 SQLite 结果只证明已经记录的事实，不证明未来收益。
