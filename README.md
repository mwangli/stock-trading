<!-- AI_GENERATE_START------------ -->
# stock-trading

本仓库正在从 Java 多模块交易平台收敛为纯 Python 的最小 T+1 验证系统。改造期间以 `documents/decisions/adr-002-python-t1-minimal-rebuild.md` 为目标方案，现有 Java、React 和数据库代码只作为交易编排与运行恢复的迁移参考，尚未删除。

## 当前结构

```text
stock-trading4/
├── stock-trading/                 # 新的最小 Python 应用
│   ├── pyproject.toml
│   └── src/stock_trading/         # 数据、模型、交易与状态闭环
├── stock-server/                  # 旧 Java 实现，迁移完成前保留
├── stock-client/                  # 旧 React 管理界面，迁移完成前保留
├── documents/                     # 决策和迁移证据
└── model-files/                   # 旧模型制品，暂不进入新链路
```

## 目标交易链路

```text
D-1 收盘更新日线并生成 Top 5
→ D 日下午分阶段买入一只可交易股票
→ D+1 日上午分阶段卖出
→ 回写成交与收益
→ 滚动评估并按规则触发重训
```

新链路首个模型固定为 `Qlib Alpha158 + LightGBM Regression`。历史数据由 BaoStock 提供，原始行情写入 Parquet，Qlib 保存模型数据，SQLite 保存任务和交易状态。实盘最终事实仍以券商账户、持仓、委托和成交为准。

## 当前进度

- 已完成 BaoStock 日线与 5 分钟数据、Parquet 分区和时间边界审计。
- 已完成 Qlib Alpha158、LightGBM 基线、自定义 T+1 标签和真实成本组合回测。
- 已完成 SQLite 任务、模型、预测、影子订单、成交、持仓和绩效状态闭环。
- 已迁移 ZX 券商账户、持仓、行情、委托与成交契约，并通过本地伪响应测试；尚未连接真实券商。
- 已完成指定交易日生产推理，`2026-10-29` 重算 Top 5 与训练制品最大分数差为 0。
- 已完成上午卖出和下午买入影子计划，真实 Top 5 可直接生成单股票 `PLANNED` 订单。
- 已完成单阶段订单执行、部分成交后撤单、未知状态阻断和重启事实恢复。
- 当前模型为 `REJECTED`，真实交易开关为 `false`；下一步实现成交驱动的持仓、绩效和自动评估。
- Java、React、Quartz、Agent、MySQL 和 MongoDB 仅在 Python 交易闭环和小资金验证完成后删除。

## 文档入口

- [纯 Python T+1 最小化重建](documents/decisions/adr-002-python-t1-minimal-rebuild.md)
- [现有系统总览](documents/system/project-overview.md)
- [现有交易执行](documents/modules/stock-trading/交易执行.md)

`SOURCE_IMPLEMENTED` 只表示源码已经落地，不代表编译、运行、数据库或真实交易验证通过。
<!-- AI_GENERATE_END------------ -->
