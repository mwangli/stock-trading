---
id: operations-deployment
title: 部署、发布与数据挂载
sidebar_position: 20
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /operations/deployment/
source_paths:
  - stock-trading@.github/workflows/release.yml
  - stock-trading@.github/script/install-release.sh
  - stock-trading@.github/script/scheduler.sh
  - stock-trading@.github/config/stock-trading.cron
  - stock-trading@src/stock_trading/data_sync.py
  - .github/workflows/ci-cd.yml
  - docker-compose.yml
---

# 部署、发布与数据挂载

## Python 主线发布

`stock-trading` 分支由 `v*.*.*` 标签触发 `Release Wheel`：

- 标签必须与 `pyproject.toml` 版本一致。
- 标签提交必须与远端 `stock-trading` 分支一致。
- 流水线构建 Wheel，上传 Wheel、Cron、logrotate、`scheduler.sh` 和安装脚本。
- 服务器安装脚本使用托管 Python 3.12，验证全部 `stock-*` CLI。
- 调度配置安装到 `~/stock-trading/config/`，Cron 以托管区块更新，不覆盖用户其他任务。
- 发布目录只保留最近三个版本。

安装和交易是两个边界。发布脚本不执行采集、Qlib 准备、训练、排名或真实交易。

## Python 调度

应用不内置 Quartz。Cron 使用 `CRON_TZ=Asia/Shanghai`，统一调用 `scheduler.sh`；脚本把业务输出写入 `logs/run.log`，并使用同一个非阻塞 `flock` 锁避免任务重叠。

| 时间 | 调度链 |
|---|---|
| 工作日 09:35 | `stock-trading`，先卖后买 |
| 工作日 13:05 | `stock-trading --buy`，下午买入重试 |
| 工作日 16:30 | `stock-collect -> stock-prepare -> stock-rank` |
| 工作日 20:20 | `stock-order -> stock-review` |
| 周日 03:00 | `stock-clean` |
| 每日 03:30 | logrotate |

同一条调度链使用 `set -e` 语义，前一命令失败时后续命令不会执行。

## 数据同步

`stock-sync` 使用 SSH/SCP 在本地项目 `data/` 与服务器 `$HOME/stock-trading/data` 之间同步：

- `market`、`qlib`、`model` 支持拉取或推送。
- `state` 只允许从服务器拉取，并以服务器 `trading.db` 覆盖本地文件。
- 同一范围不能在一次命令中同时拉取和推送。
- 环境文件、券商 Token、调度锁和日志不在同步范围。
- `--dry-run` 只生成计划，不执行 SSH/SCP。

## Web 控制台发布

`stock-web` 分支推送、版本标签或授权的手工触发会运行 `CI/CD`：

1. Maven 测试与前端构建。
2. 分别构建并推送 Backend、Frontend 镜像。
3. 上传 Compose 到服务器临时目录。
4. 先更新 Backend 并等待健康，再更新 Frontend。
5. 健康检查失败时恢复上一组镜像，最后删除临时目录。

## 数据挂载

| 宿主机来源 | 容器目标 | 模式 | 用途 |
|---|---|---|---|
| `/root/stock-trading/data` | `/data/stock-trading` | 只读 | Backend 读取 SQLite、Parquet 和制品 |
| `/root/stock-trading/data` | `/srv/stock-data` | 只读 | Nginx 提供受 Basic Auth 保护的只读文件入口 |
| `/root/stock-trading/logs` | `/root/stock-trading/logs` | 只读 | Backend 日志页面和 SSE |
| 镜像内 `/app/documents` | `STOCK_DOCUMENT_ROOTS` | 镜像只读 | 文档 API 与 Agent 检索 |

配置值正确不代表挂载正确。生产核对必须同时检查环境变量、`docker inspect` 的实际源路径和容器内文件时间，防止读取空目录或旧数据。

## 验证层级

1. 源码检查：入口、配置和调用链一致。
2. CI 构建/测试：制品可生成，自动检查通过。
3. 安装或容器健康：运行入口存活。
4. 挂载与接口：目标数据确实可读。
5. Python 业务制品：Parquet、模型、排名和 SQLite 符合预期。
6. 真实交易：券商委托、成交、撤单和完整闭环成立。

任一层成功都不能替代后一层证据。
