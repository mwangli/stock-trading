<!-- AI_GENERATE_START-------- -->
# stock-trading

本仓库正在从 Java 多模块交易平台收敛为纯 Python 的最小 T+1 验证系统。改造期间以 `documents/decisions/adr-002-python-t1-minimal-rebuild.md` 为目标方案，现有 Java、React 和数据库代码只作为已验证券商契约与历史实现参考，尚未删除。

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

- 已建立 Python 工程骨架、BaoStock 客户端、5 分钟边界定义、Parquet 写入和数据质量审计源码。
- 已验证 BaoStock `0.9.4` 可返回全市场日线和 5 分钟 K 线。
- 已完成 BaoStock CLI、全市场日线和分钟 Parquet 的真实只读验证；尚未运行 Qlib、训练模型、回测或执行券商请求。
- Java、React、Quartz、Agent、MySQL 和 MongoDB 仅在新链路完成影子与小资金验证后删除。

## 文档入口

- [纯 Python T+1 最小化重建](documents/decisions/adr-002-python-t1-minimal-rebuild.md)
- [现有系统总览](documents/system/project-overview.md)
- [现有交易执行](documents/modules/stock-trading/交易执行.md)

`SOURCE_IMPLEMENTED` 只表示源码已经落地，不代表编译、运行、数据库或真实交易验证通过。
<!-- AI_GENERATE_END-------- -->
