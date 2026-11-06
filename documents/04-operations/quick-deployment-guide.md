---
id: operations-quick-deployment
title: 快速部署手册
sidebar_position: 5
owner: operations
type: runbook
status: SOURCE_VERIFIED
last_verified: 2026-11-06
slug: /operations/quick-deployment/
source_paths:
  - stock-trading@README.md
  - stock-trading@pyproject.toml
  - stock-trading@src/stock_trading/config.py
  - stock-trading@src/stock_trading/data_sync.py
  - stock-trading@.github/script/bootstrap-server.sh
  - stock-trading@.github/script/install-release.sh
  - stock-trading@.github/script/scheduler.sh
  - docker-compose.yml
---

# 快速部署手册

本手册先部署必需的 Python 核心，再说明如何可选部署只读 Web 控制台。只想了解行情、模型和排名时，不需要配置券商账号，也不需要部署 Web。

## 1. Windows 本地快速体验

准备 Git 和 `uv`，然后执行：

```cmd
git clone https://github.com/mwangli/stock-trading.git
cd stock-trading
git switch stock-trading
uv python install 3.12
uv venv --python 3.12 .venv
uv pip install --python .venv\Scripts\python.exe -e .
.venv\Scripts\stock-help.exe
```

研究命令没有券商凭据也可以运行。可在仓库根目录新建 `base.env`：

```dotenv
STOCK_TRADING_HISTORY_DAYS=1095
STOCK_TRADING_DATA_QUALITY_MIN_RATIO=0.8
MODEL_FEATURE_BATCH_DAYS=60
STOCK_TRADING_LOG_LEVEL=INFO
```

首次准备研究数据时，选择约三年的交易日范围：

```cmd
.venv\Scripts\stock-collect.exe --date 2023-11-06:2026-11-06
.venv\Scripts\stock-prepare.exe --date 2023-11-06:2026-11-06
.venv\Scripts\stock-train.exe
.venv\Scripts\stock-rank.exe
```

日期只是示例，应改为实际需要的开始和结束日期。完成后重点检查：

- `data/market/` 是否生成 Parquet。
- `data/qlib/` 是否生成 Qlib 数据。
- `data/artifacts/models/` 是否存在模型制品。
- `data/state/trading.db` 是否创建。
- `logs/run.log` 是否记录任务过程。

不要为了“试一下”直接执行 `stock-trading`。该命令可能提交真实买卖委托，必须先完整配置券商、OCR、交易权限和通知，并理解 [T+1 交易闭环](../02-business/t1-trading-loop.md)。

## 2. Linux 单机安装

项目生产目录固定为 `~/stock-trading/`。在普通用户下克隆并准备环境：

```bash
git clone --branch stock-trading https://github.com/mwangli/stock-trading.git ~/stock-trading/source
cd ~/stock-trading/source
bash .github/script/bootstrap-server.sh --apply
uv python install 3.12
uv venv --managed-python --python 3.12 ~/stock-trading/venv
uv pip install --python ~/stock-trading/venv/bin/python -e .
~/stock-trading/venv/bin/stock-help
```

`bootstrap-server.sh` 会检查或安装 `uv`、Cron、`flock`、`logrotate` 和基础目录。正式生产推荐使用版本标签发布的 Wheel，而不是长期运行可编辑安装。

## 3. 配置环境文件

Windows 使用源码根目录，Linux 使用 `~/stock-trading/`。应用按文件名顺序读取 `*.env`，后读取文件覆盖先读取文件和进程环境。可以拆分为：

```text
base.env      # 数据、模型和日志
broker.env    # 券商、OCR、Bark
server.env    # stock-sync 的服务器连接
```

研究与模型常用配置：

```dotenv
STOCK_TRADING_HISTORY_DAYS=1095
STOCK_TRADING_DATA_QUALITY_MIN_RATIO=0.8
STOCK_TRADING_ALLOWED_PREFIXES=SH60,SZ00
STOCK_TRADING_MAX_PREDICTION_AGE_DAYS=7
MODEL_FEATURE_BATCH_DAYS=60
MODEL_EVALUATION_OBSERVATIONS=6
MODEL_MAX_DRAWDOWN=0.10
MODEL_MIN_TOTAL_GROSS_RETURN=0
STOCK_TRADING_LOG_LEVEL=INFO
```

真实交易还需要 `ZXBROKER_*`、百度 OCR 和可选 `DEVICE_KEY`。`stock-sync` 需要 `SERVER_IP`、`SERVER_USER`、`SERVER_PASS`。密码、Token、签名和 API Key 只写本机环境文件，不写版本库、文档或日志。

环境文件缺失或券商变量不完整时会告警，但不阻断发布和非券商命令；真正执行相关功能时仍会失败并给出缺失项。

## 4. 标准生产发布

`stock-trading` 分支的标准发布由 `vX.Y.Z` 标签触发：

1. 确认 `pyproject.toml` 版本与标签一致。
2. 确认标签提交就是远端 `stock-trading` 分支最新提交。
3. GitHub Actions 构建 Wheel，并上传 Wheel、调度、Cron、logrotate 和安装脚本。
4. `install-release.sh` 安装 Python 3.12 环境，验证全部 CLI，更新 `~/stock-trading/config/` 与当前用户 Cron，并保留最近三个版本。

发布不会自动采集数据、训练模型、生成排名或执行交易。首次发布后仍需人工完成数据准备和业务验证。

## 5. 调度与数据同步

当前 Cron 关键时点：

- 09:35：`stock-trading`，先卖后买。
- 13:05：`stock-trading --buy`，下午买入重试。
- 16:30：`stock-collect -> stock-prepare -> stock-rank`。
- 20:20：`stock-order -> stock-review`。
- 周日 03:00：`stock-clean`；每日 03:30：logrotate。

本地与服务器之间同步前先预览计划：

```bash
stock-sync --pull all --dry-run
stock-sync --push market,qlib,model --dry-run
```

确认后去掉 `--dry-run`。`state` 只允许从服务器拉取，不能推送，避免本地 SQLite 覆盖生产状态。

## 6. 可选部署 Web 控制台

控制台要求 Linux Docker 主机上已经存在：

```text
/root/stock-trading/data
/root/stock-trading/logs/run.log
/root/stock-trading/config/stock-data.htpasswd
```

`stock-web` 分支推送会执行后端测试、前端构建、镜像推送和双容器更新。生产至少需要配置镜像仓库、服务器连接、`CONSOLE_PASSWORD_HASH` 和数据文件 Basic Auth；DeepSeek 与 GitHub 开发 Agent 均可保持关闭。

本地只查看源码时不必启动控制台。若自行运行 Compose，应先确认绝对挂载路径指向实际 Python 数据目录，避免页面连接到空目录或旧目录。

## 7. 部署后最小核对

按层次检查，不把单一成功信号扩大解释：

1. `stock-help` 能列出全部公开命令。
2. 数据、模型、SQLite、配置和日志目录存在且权限正确。
3. Cron 托管区块、`scheduler.sh`、时区和 `data/state/scheduler.lock` 正确。
4. 手工运行非交易命令后，核对日志、Parquet、模型制品和 SQLite。
5. Web 控制台部署时，再检查容器健康、实际挂载和页面接口。
6. 真实交易只能通过券商委托号、成交、撤单和完整闭环证明。
