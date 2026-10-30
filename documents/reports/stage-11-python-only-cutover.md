---
id: stage-11-python-only-cutover
title: 阶段 11：纯 Python 替换收口
sidebar_position: 120
owner: system
type: report
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-30
slug: /reports/stage-11-python-only-cutover/
source_paths:
  - README.md
  - AGENTS.md
  - stock-trading/pyproject.toml
  - stock-trading/src/stock_trading/cli.py
  - stock-trading/src/stock_trading/trading/execution.py
---

<DocMetadata />

# 阶段 11：纯 Python 替换收口

## 用户决策

用户在 `2026-10-30` 明确要求继续阶段 11，并决定真实撤单或成交终态恢复由后续自行验证。因此阶段 11 按“源码与工程单一路径收口”实施，不再等待成功委托或真实成交证据。

## 最终结构

仓库生产代码只保留：

```text
stock-trading/
├── pyproject.toml
└── src/stock_trading/
    ├── market/
    ├── research/
    ├── broker/
    ├── state/
    └── trading/
```

- BaoStock 提供历史行情，Parquet 保存原始事实。
- Qlib 和 LightGBM 负责 Alpha158 训练、推理和 Top 5。
- SQLite 保存任务、模型、预测、订单意图、券商事实、持仓和绩效。
- ZX 券商 HTTP 提供登录、账户、行情、委托、撤单和成交事实。
- 操作系统 `cron` 或 `systemd timer` 负责时间触发。

## 删除内容

已从版本库删除：

- `stock-server/`：Spring Boot、Java 模型、交易、Quartz、Agent 和 MySQL/MongoDB 持久化链路。
- `stock-client/`：React/Vite 管理界面和文档工作台。
- `model-files/`：DJL Embedding 和情绪模型旧制品。
- 根 Maven、Node、Docker Compose 和 Java/React CI/CD 配置。
- 只描述已删除模块的系统、模块、Agent、模型生命周期和旧部署文档。

外部 MySQL、MongoDB 实例及其中历史数据没有执行删除、清库、迁移或写入。

## 新增无人值守入口

Python CLI 新增：

```text
run-morning-trading --date YYYY-MM-DD
run-afternoon-trading --date YYYY-MM-DD
```

两个入口统一执行：

```text
恢复券商未终态订单
→ 生成当天幂等影子计划
→ 使用 SQLite 门禁执行计划订单
→ 输出恢复、计划和执行结果
```

它们不会绕过同日准入、真实交易开关、当前模型、持仓、资金、价格、权限、幂等或未知订单阻断。

## 保留的验证边界

已完成源码和文件层面检查：

- 根目录只剩纯 Python 应用、ADR、阶段报告和本地运行目录。
- 旧 Java、React、Quartz、Agent、MySQL/MongoDB 配置引用已纳入残留扫描。
- 敏感券商配置仍只存在于被忽略的根 `.env`。
- 上午/下午自动入口复用现有计划、执行和恢复服务，没有新增第二套订单状态机。

本阶段未执行 Python 安装、编译、测试、训练、回测或应用启动，也未再次提交真实委托。成功委托、真实撤单、部分成交和重启恢复仍需用户后续在合法交易时段验证。

## 证据等级

阶段 11 状态为 `SOURCE_IMPLEMENTED`：工程结构和源码权威路径已经完成替换，但不能标记为 `RUNTIME_VERIFIED` 或 `PRODUCTION_VERIFIED`。