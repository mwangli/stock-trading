---
id: code-python-core
title: Python 核心实现
sidebar_position: 5
owner: code
type: implementation
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /code/python-core/
source_paths:
  - stock-trading@src/stock_trading/cli.py
  - stock-trading@src/stock_trading/shortcuts.py
  - stock-trading@src/stock_trading/data_sync.py
  - stock-trading@src/stock_trading/market
  - stock-trading@src/stock_trading/research
  - stock-trading@src/stock_trading/trading
  - stock-trading@src/stock_trading/state
---

# Python 核心实现

## 目录职责

```text
src/stock_trading/
├── market/      # BaoStock、Parquet、质量检查、交易日历和 T+1 窗口
├── research/    # Qlib、标签、Alpha158、LightGBM、完整排名和回测
├── broker/      # ZX 登录、验证码、协议模型、解析和 HTTP 客户端
├── trading/     # 目标选择、限价执行、订单同步、收益与模型复盘
├── state/       # SQLite Schema、事务、幂等状态与清理
├── utils/       # 日志、通知、时间、代码转换和工作流支持
├── data_sync.py # 本地与服务器之间的 SSH/SCP 数据同步
├── config.py    # 路径、*.env 加载与固定交易参数
├── cli.py       # Typer 参数和命令实现
└── shortcuts.py # 独立 stock-* 入口与帮助文案
```

## 公开命令与组合边界

- `stock-collect`、`stock-prepare`、`stock-train`：数据和模型。
- `stock-rank`、`stock-trading`：完整排名和真实交易。
- `stock-order`、`stock-review`：历史订单同步、FIFO 结算、收益汇总和模型判断。
- `stock-clean`、`stock-sync`、`stock-help`：维护、数据搬运和帮助。

公开 CLI 负责参数解析和顶层任务边界，业务逻辑下沉到领域模块。每个关键业务命令在 SQLite 记录任务开始、结束和错误，结构化结果写入标准输出，业务日志写入统一日志系统。

## 实现方式

关键实现原则：

- 市场原始数据、研究制品、运行状态和券商事实分层存储。
- 状态写入通过 SQLite 事务和唯一业务键保持幂等。
- 股票代码、日期范围、文件位置和命令参数在边界处校验。
- 网络调用集中在 BaoStock、券商、Bark 和数据同步适配器，不散落到研究代码。
- 模型只输出分数和排名，交易约束集中在 `trading/`。
- 未确认的券商结果记录为 `UNKNOWN`，不猜测成交或撤单成功。
- `stock-sync` 只搬运明确的数据范围，`state` 只允许从服务器拉取，避免本地状态覆盖生产事实。

## 状态库

当前 SQLite Schema 版本为 4，核心表包括：

| 表 | 作用 |
|---|---|
| `task_run` | 命令运行状态和错误 |
| `model_version` | 模型制品、状态和指标 |
| `prediction_rank` | 完整横截面排名；由旧 `prediction` 无损重命名 |
| `account_snapshot` | 最近一次交易前同步的账户资金快照 |
| `order_attempt` | 本地交易目标、报价尝试和恢复状态 |
| `broker_order` / `broker_order_fee` | 券商委托、成交和费用事实 |
| `realized_trade` | FIFO 完整闭环收益 |
| `model_evaluation` / `training_request` | 月度评估和待处理重训请求 |
| `broker_permission` | 已确认的券商证券权限结果 |

版本 1-3 会执行已确认的 `prediction -> prediction_rank` 迁移；更高版本或没有明确迁移路径的版本仍拒绝破坏性自动升级，不会通过删除业务状态来“修复”启动问题。

## 扩展建议

新增功能时优先扩展现有命令和领域模块，不新增第二套调度、状态库或交易执行入口。涉及交易规则的改动应同时核对目标选择、执行、状态恢复、券商同步、复盘、控制台只读查询和文档，避免只修改其中一层。
