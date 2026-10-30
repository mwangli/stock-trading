---
id: adr-002-python-t1-minimal-rebuild
title: ADR-002：纯 Python T+1 最小化重建
sidebar_position: 4
owner: system
type: decision
status: PROPOSED
last_verified: 2026-10-30
slug: /decisions/adr-002-python-t1-minimal-rebuild/
source_paths:
  - stock-trading/pyproject.toml
  - stock-trading/src/stock_trading/market/baostock_client.py
  - stock-trading/src/stock_trading/market/audit.py
  - stock-server/stock-trading/src/main/java/com/stock/trading/adapter/ZXBrokerAdapter.java
  - stock-server/stock-trading/src/main/java/com/stock/trading/adapter/ZXRequestUtils.java
---

<DocMetadata />

# ADR-002：纯 Python T+1 最小化重建

## 目标

只验证一个问题：`Alpha158 + 横截面模型` 产生的 Top 5，是否能稳定指导小资金 T+1 交易。新链路不兼容旧模型和旧工作流，最终只保留 Python、Qlib、Parquet、SQLite 和经过验证的券商 HTTP 契约。

## 固定交易时序

```text
D-1 收盘后：更新日线并生成 D 日 Top 5
D 日 13:05-14:54：从可交易 Top 5 中分阶段买入一只
D+1 日 09:35-11:25：分阶段卖出全部可卖持仓
D+1 收盘后：回写成交、收益和模型评估
```

模型标签固定为：

```text
label(D-1, stock)
= D+1 上午卖出窗口 VWAP / D 日下午买入窗口 VWAP - 1
```

BaoStock 的 5 分钟时间戳是 K 线结束时间。实测每天 48 根：上午 `09:35-11:30`，下午 `13:05-15:00`。因此为避免使用窗口外数据，标签只选择完整落在执行窗口内的 K 线：

- 买入窗口 `13:05-14:54`：使用结束时间 `13:10-14:50` 的 5 分钟 K 线。
- 卖出窗口 `09:35-11:25`：使用结束时间 `09:40-11:25` 的 5 分钟 K 线。

不得把 `09:35` K 线解释为 `09:35-09:40`，也不得把 `13:05` K 线纳入从 `13:05` 才开始执行的标签窗口。

## 数据源决策

首版 Python 链路只使用 BaoStock `0.9.4` 采集历史数据：

- `query_daily_history_k_AStock` 按交易日一次返回全市场日线，用于每日增量和历史回填。
- `query_history_k_data_plus(..., frequency="5")` 按股票拉取 5 分钟数据，用于标签建设和窗口 VWAP。
- 日线、交易日历和股票池进入 Qlib 本地数据；原始日线与分钟线按不可变分区写入 Parquet。
- Tushare 不进入新 Python 生产运行依赖，只在数据源验收阶段保留为外部抽样对照，完成全市场审计后再决定是否彻底删除旧实现。

券商当前已验证能力只有实时价格、五档行情、账户、持仓、委托、撤单、当日或历史委托与成交。现有源码没有经过验证的历史日 K 或分钟 K 接口及参数，因此券商只作为实盘行情和成交事实源，不能作为当前历史训练数据源。

## BaoStock 实测结论

2026-10-30 使用 BaoStock `0.9.4` 对沪深主板、创业板和科创板五只股票进行了只读测试：

- 2025-10-20 至 2025-10-24 共 25 个股票交易日，全部为每天 48 根，时间边界完全一致。
- 同一只股票同一天重复请求 5 次，行数和内容哈希一致；首次约 1.07 秒，后续约 0.04 秒。
- 2025-08-01 至 2025-10-31 共 300 个股票交易日，没有缺 K；有 41 日的分钟聚合 OHLC 与日线最大相对差超过 0.1%，17 日的成交量或成交额差超过 1%。
- 2026-08-03 至 2026-10-29 共 290 个股票交易日，没有缺 K；成交量和成交额聚合一致，但有 46 日的分钟聚合 OHLC 与日线最大相对差超过 0.1%。
- 2020-01-02 可取得 5 分钟数据；2014-12-31、2015-01-05 和 2016-01-04 的抽样为空，精确最早可用日期仍需继续扫描。
- 2026-10-29 的全市场日线返回 5,223 行，在当前代理网络下耗时约 36 秒。

结论：BaoStock 的可用性和时间完整性足以支持快速研究，但分钟线不能被视为对官方日线的精确拆分。每个分区必须记录来源版本并执行 48 根、时间边界、日线聚合偏差和重复请求一致性审计；异常数据不能静默进入标签。

## 最小工程结构

```text
stock-trading/
├── pyproject.toml
└── src/stock_trading/
    ├── cli.py
    ├── config.py
    └── market/
        ├── baostock_client.py
        ├── models.py
        ├── audit.py
        └── storage.py
```

首版不提供 Web 页面、REST 服务、消息队列、MySQL、MongoDB、Redis、Quartz、Agent 或独立模型服务。时间触发交给操作系统的 `cron` 或 `systemd timer`，Python 应用只提供可重复执行、可审计的命令。

## 分阶段实施

1. **数据契约**：完成 BaoStock 日线、5 分钟线、时间边界、Parquet 分区和质量审计。
2. **模型基线**：转换 Qlib 数据，运行 `Alpha158 + LightGBM Regression`，输出每日 Top 5 和横截面评估指标。
3. **组合回测**：按真实下午买入、次日上午卖出窗口计算窗口 VWAP，并独立计算手续费、滑点和无法成交。
4. **状态闭环**：SQLite 保存任务、模型、预测、订单、成交、持仓和绩效。
5. **券商迁移**：逐项复制已验证契约，先只读影子运行，再启用限价下单和撤单。
6. **生产推理**：加载固定模型对指定交易日重新计算全股票池分数，原子输出 Top K 并幂等写入 SQLite。
7. **影子交易计划**：根据真实持仓、资金、五档行情和 Top 5 生成上午卖出与下午单股票买入计划，不调用券商写接口。
8. **订单执行与恢复**：实现分阶段委托、部分成交、撤单终态、未知状态和应用重启后的券商事实恢复。
9. **绩效与自动评估**：按真实成交更新持仓和绩效，触发滚动模型评估和受控重训。
10. **真实验证**：先完成券商只读影子同步，再由通过门槛的 `ACTIVE` 模型执行受控小资金验证。
11. **替换收口**：小资金稳定运行后删除 Java、React、Quartz、Agent 和旧数据库链路，不保留双轨。

## 验证边界

当前已完成前七个阶段：BaoStock 数据基础、Qlib Alpha158 与 LightGBM 基线、单持仓 T+1 组合回测、SQLite 状态闭环、Python 券商契约迁移、生产推理和影子交易计划。生产推理在 `2026-10-29` 真实 Qlib 数据上重算的 Top 5 与阶段 2 制品顺序完全一致，最大分数差为 0；该制品已直接生成 `2026-10-30` 第一名 `SZ000559` 的下午影子买入计划。20 只股票样本的 IC 与 Rank IC 仍为负，模型状态为 `REJECTED`，真实交易开关保持关闭。当前环境没有券商凭据，尚未执行真实券商 HTTP 请求、订单终态恢复或绩效闭环，因此旧 Java 和前端暂不删除。
