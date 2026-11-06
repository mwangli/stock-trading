---
id: operations-observability
title: 日志与可观测性
sidebar_position: 30
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /operations/observability/
source_paths:
  - stock-trading@src/stock_trading/config.py
  - stock-trading@src/stock_trading/utils/logging_utils.py
  - stock-trading@src/stock_trading/utils/notifications.py
  - stock-trading@.github/config/stock-trading.logrotate
  - stock-server/src/main/java/com/stock/console/operations/OperationsService.java
  - stock-client/src/desktop/pages/logs/index.tsx
---

# 日志与可观测性

## Python 日志

业务日志使用统一格式，包含时间、级别、任务、步骤、进度和目标。结构化命令结果写标准输出，普通日志写标准错误；默认同时启用控制台日志和 `logs/run.log` 文件日志。

可用环境变量：

| 变量 | 默认值 | 作用 |
|---|---:|---|
| `STOCK_TRADING_LOG_LEVEL` | `INFO` | 日志级别 |
| `STOCK_TRADING_CONSOLE_LOG` | `1` | 是否写控制台 |
| `STOCK_TRADING_FILE_LOG` | `1` | 是否写文件 |
| `NO_COLOR` | 未设置 | 禁止交互终端颜色 |

这些变量可写入项目根目录 `*.env`。Windows 使用 20 MB、14 个备份的进程内轮转；Linux 使用 `WatchedFileHandler` 配合每日 logrotate，单文件达到 20 MB 时也会轮转，保留 14 份并压缩历史文件。生产 Cron 关闭控制台日志，统一写 `~/stock-trading/logs/run.log`。

## 通知与敏感信息

日志不得输出账户、股东账号、密码、Token、Cookie、验证码、签名或完整券商响应。配置 `DEVICE_KEY` 后，交易执行结果和 CLI 业务异常可推送 Bark；系统异常通知设置 30 天有效期，通知统一归档。Bark 失败只记录警告，不改变原业务命令结果。

## Web 控制台观测

控制台把以下信号分开：Backend 进程、SQLite、Parquet、日志路径和 Agent Provider。日志接口支持普通读取和 SSE 增量推送，PC 与移动端使用独立页面但共享后端来源。

## 故障判断顺序

1. 判断失败的是 Python CLI、控制台进程还是页面请求。
2. 核对目标命令的任务状态与同一时间段日志。
3. 核对数据、模型和 SQLite 文件更新时间。
4. 涉及容器时核对实际挂载，不只看 Compose 声明。
5. 涉及交易时核对券商委托号、成交、撤单与本地状态是否一致。

没有日志不等于任务未运行，页面为空也不等于业务失败；必须结合路径、时间、任务状态和业务事实判断。
