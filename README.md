<!-- AI_GENERATE_START-------------------------------------------------------------------------------------------------------- -->
# stock-trading

面向 A 股的纯 Python T+1 模型研究与自动交易验证项目。当前主链路由 BaoStock、Parquet、Qlib、Alpha158、LightGBM、SQLite 和 ZX 券商 HTTP 协议组成，不包含 Java、Web 页面、Quartz、Agent、MySQL、MongoDB、消息队列或独立模型服务。

## 核心链路

```text
BaoStock 原始行情
  -> Parquet 日线、复权因子、5 分钟线与交易窗口聚合
  -> Qlib 数据
  -> Alpha158 + LightGBM Regression
  -> 完整横截面排名
  -> Rank5 买入 / Rank15 持仓轮换
  -> ZX 券商委托与成交事实
  -> SQLite 已实现收益
  -> 月度模型评估与重训请求
```

模型只负责评分和排名；确定性 Python 逻辑负责股票池、候选筛选、持仓轮换、数量计算、T+1 时序、报价、幂等和恢复；券商最终判断订单是否合法以及是否成交。

## 交易与标签

预测日、买入日和卖出日按交易日历顺延：

```text
label(D, stock)
= D+2 上午卖出窗口 VWAP / D+1 下午买入窗口 VWAP - 1
```

BaoStock 5 分钟时间戳按 K 线结束时间解释：

- 下午买入窗口：完整落在 `13:05-14:54` 内的 21 根 K 线。
- 上午卖出窗口：完整落在 `09:35-11:25` 内的 22 根 K 线。
- 窗口不完整或成交量为 0 时，不生成该窗口的有效 VWAP，也不能用于标签或回测成交。

当前交易规则：

- 默认交易范围为 `SH60`、`SZ00`，可通过 `STOCK_TRADING_ALLOWED_PREFIXES` 调整。
- 下午无持仓时，从最近完整横截面排名的 Top5 依次筛选，固定首个满足代码前缀、券商权限、实时价格和资金条件的候选。
- 已有持仓时跳过下午买入；同一业务日已产生未拒绝的买入目标时恢复该目标。
- 上午读取券商实时可卖持仓；Rank15 以内继续持有，跌出 Rank15 或不在排名中的持仓进入卖出目标。
- 每次委托前重新读取账户、持仓和五档行情，价格和数量不复用旧快照。
- 买入数量按实时可用资金、每笔基础费用和 100 股整手计算；卖出数量按实时可卖持仓计算。
- 每次报价尝试使用独立 `clientOrderId`；委托号、明确拒绝和通信不确定分别记录为 `SUBMITTED`、`REJECTED` 和 `UNKNOWN`。
- 委托每 5 秒轮询一次，单次最多观察 60 秒；未全部成交时先撤单，只有确认撤单终态后才允许重新报价。
- 单个买入或卖出窗口为 15 分钟，最多尝试 12 次。前 50% 使用买三/卖三，50%-85% 使用买一/卖一，最后 15% 跨到对手一档。
- 五档协议当前只解析价格，不依据未确认的档位数量字段扫单；Action 110 只发送官方 H5 和真实历史响应已确认的字段。

交易固定参数定义在 `src/stock_trading/config.py`：

| 参数 | 当前值 | 含义 |
| --- | ---: | --- |
| `TRADING_ENTRY_RANK` | `5` | 允许买入的最高排名范围 |
| `TRADING_EXIT_RANK` | `15` | 持仓保留排名 |
| `TRADING_MIN_PRICE` | `0.01` | 最低参考价格 |
| `TRADING_MAX_PRICE` | `1000000` | 最高参考价格 |
| `TRADING_ORDER_FEE` | `5` | 缺少真实费用字段时的单笔基础费用 |
| `EXECUTION_WINDOW_SECONDS` | `900` | 单方向执行窗口 |
| `ORDER_WAIT_SECONDS` | `60` | 单次委托观察时间 |
| `ORDER_POLL_SECONDS` | `5` | 委托状态轮询间隔 |
| `CANCEL_CONFIRM_SECONDS` | `30` | 撤单终态确认时间 |
| `MAX_ATTEMPTS` | `12` | 单方向最多委托次数 |

## 数据与状态

- `data/market/`：股票基础信息、日线、复权因子、5 分钟行情和 T+1 窗口聚合。
- `data/qlib/`：Qlib 日历、股票池和二进制特征数据。
- `data/artifacts/`：模型版本、指标、预测、评估和回测制品。
- `data/state/trading.db`：任务、模型、预测、委托尝试、券商订单、权限、已实现交易、模型评估和重训请求。
- `data/state/broker-token.json`：按账户范围隔离的本地 Token 缓存，默认有效期 30 分钟。

SQLite 是运行状态的唯一权威库，但实时价格、资金、持仓、委托和成交终态仍以券商返回为准。

原始数据质量由 `STOCK_TRADING_DATA_QUALITY_MIN_RATIO` 控制，默认 `0.8`。`collect`、`prepare`、`train` 和 `rank` 使用同一最低覆盖率；达到阈值可继续，未达到阈值直接失败。

数据保留规则：

- 原始 5 分钟行情保留 90 个自然日。
- T+1 交易窗口聚合保留约 5 年。
- SQLite 已结束任务记录保留 180 天。
- SQLite 预测、模型评估和已终结委托尝试保留约 5 年。
- 模型版本、券商订单、已实现交易、活动委托、权限和训练请求不由普通历史清理规则删除。

`stock-clean` 删除到期记录后，仅在确实删除了 SQLite 数据时执行 WAL 截断、`VACUUM` 和优化。

## 收益口径

研究与真实交易使用两套明确分离的口径：

- 模型回测同时输出毛收益和净收益，但模型能力判断以不扣固定买卖费用的毛收益、毛回撤等指标为主。
- 真实交易只统计券商成交事实形成的完整买卖闭环；未卖出持仓、浮动盈亏、账户总资产和入金出金都不计入策略收益。
- FIFO 按成交数量匹配买入与卖出；部分卖出只确认已经匹配数量的收益。
- 券商响应没有费用字段时，每笔成交委托使用 `5` 元基础费用，来源记为 `BUSINESS_ZERO`。
- 后续如解析到真实聚合费用或明细，来源记为 `BROKER_ACTUAL`；存在聚合总费用时不再重复叠加明细。
- `stock-evaluate` 按卖出月份汇总已实现毛收益。默认需要 6 个有效月份，要求总毛收益大于 0、盈利月份比例不低于 60%、毛回撤不高于 10%；不满足时生成待处理重训请求，但不会在评估命令内直接训练。

## 项目结构

```text
stock-trading4/
├── pyproject.toml
├── src/stock_trading/
│   ├── market/            # BaoStock、Parquet、质量检查和交易窗口聚合
│   ├── research/          # Qlib、标签、Alpha158、LightGBM、排名和回测
│   ├── broker/            # ZX 登录、验证码、协议解析和 HTTP 客户端
│   ├── state/             # SQLite 结构与业务状态
│   ├── trading/           # 目标选择、执行、券商同步、结算和模型评估
│   ├── utils/             # 时间、日志、通知、帮助和公共工作流支持
│   ├── config.py          # 路径、环境变量和固定交易参数
│   ├── cli.py             # CLI 参数与命令实现
│   └── shortcuts.py       # 独立命令入口和帮助文案
├── .github/
│   ├── config/            # Cron 与 logrotate 模板
│   ├── script/            # 服务器引导和版本安装脚本
│   └── workflows/         # Wheel 发布流水线
├── data/                  # 本地运行数据，不提交
├── .tmp/                  # 临时验证文件，不提交
└── README.md              # 项目唯一说明文档
```

## 安装

项目声明支持 Python `3.11-3.12`；本地和生产安装统一使用 Python 3.12。

Windows：

```cmd
uv python install 3.12
uv venv --python 3.12 .venv
uv pip install --python .venv\Scripts\python.exe -e .
```

无需激活虚拟环境，直接执行 `.venv\Scripts\stock-*.exe`。

Linux 生产目录固定为 `~/stock-trading/`：

```text
~/stock-trading/
├── venv/
├── releases/
├── data/
├── config/
└── logs/run.log
```

服务器需具备 `uv`、`crontab`、`flock`、`logrotate` 和 `setsid`。可使用发布包中的脚本检查或补齐基础环境：

```bash
bash ~/stock-trading/releases/<tag>/script/bootstrap-server.sh --check
bash ~/stock-trading/releases/<tag>/script/bootstrap-server.sh --apply
```

Windows 本地开发将环境文件放在源码根目录，Linux 生产将环境文件放在 `$HOME/stock-trading/`。

## 环境变量

应用按文件名顺序读取项目根目录的 `*.env`，后读取文件中的同名变量覆盖先读取文件和进程环境。没有 `.env` 文件、文件不可读、配置行无效或关键券商变量缺失时只输出 `WARN`，不阻断发布和非券商命令；实际使用券商功能时仍会按业务要求校验。账号、密码、Token、签名、验证码和 API 密钥不得写入版本库或日志。

例如可拆分为项目根目录下的 `base.env` 和 `broker.env`，格式为：

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

BAOSTOCK_PROXY=
DEVICE_KEY=
ZXBROKER_BASE_URL=https://weixin.citicsinfo.com/reqxml
ZXBROKER_MOBILE_CODE=
ZXBROKER_ACCOUNT=
ZXBROKER_ENCODED_PASSWORD=
ZXBROKER_SIGN_KEY=
ZXBROKER_INTACT_TO_SERVER=
ZXBROKER_TOKEN=
BAIDU_OCR_API_KEY=
BAIDU_OCR_SECRET_KEY=
```

SQLite 数据库固定为项目 `data/state/trading.db` 本地文件，不提供网络服务，不配置数据库账号、密码或加密密钥；服务器文件权限和主机访问控制是其安全边界。

## 命令行

当前公开入口以 `pyproject.toml` 为准：

```cmd
.venv\Scripts\stock-help.exe
.venv\Scripts\stock-collect.exe
.venv\Scripts\stock-prepare.exe
.venv\Scripts\stock-train.exe
.venv\Scripts\stock-rank.exe
.venv\Scripts\stock-sell.exe
.venv\Scripts\stock-buy.exe
.venv\Scripts\stock-broker.exe
.venv\Scripts\stock-perform.exe
.venv\Scripts\stock-evaluate.exe
.venv\Scripts\stock-clean.exe
```

项目没有 `stock-init`。首次使用和日常运行都通过独立命令显式完成，命令不会隐式串联未声明的前置步骤。

### 数据准备

```bash
# 无参数时检查最近已完成交易日，只补齐缺失原始数据
stock-collect

# 检查并补齐指定历史范围
stock-collect --date 2023-11-02:2026-11-02

# 根据现有 Parquet 增量、幂等准备 Qlib
stock-prepare
stock-prepare --date 2023-11-02:2026-11-02

# 忽略现有 Qlib 并全量重建
stock-prepare --force
```

`stock-collect` 自动检查最近交易日的股票基础信息快照，并根据日线、复权因子和 5 分钟线缺失清单补采；它不准备 Qlib。`stock-prepare` 只读取本地 Parquet，不连接 BaoStock；原始数据覆盖率不足时终止。

### 模型与排名

```bash
# 已有完整模型时直接复用并激活
stock-train

# 强制重新训练，默认按交易日 70:15:15 切分
stock-train --force
stock-train --force --split 6:3:1

# 生成指定日期的完整横截面排名
stock-rank
stock-rank --date 2026-11-02
```

训练先校验原始 Parquet 和 Qlib 覆盖率，再生成独立模型版本。候选模型训练完成后直接登记并激活，回测结果用于记录和比较，不作为激活门禁。固定回测资金档位为 `1000`、`2000`、`3000`、`5000`、`10000` 元。

物理内存低于 16 GiB 时使用分批 Alpha158 特征和内存映射训练路径；其他环境使用单次加载路径。分批路径默认每 60 个交易日生成一批，并保留可恢复的批次断点。

`stock-rank` 要求同一业务日 `stock-collect` 成功、Qlib 覆盖目标日期且存在可用模型；已有完整且股票集合一致的预测制品时复用，否则重新生成并导入 SQLite。

### 交易、结算与评估

```bash
# 仅处理上海时区当天，不接受日期参数
stock-sell
stock-buy

# 同步当天、单日或日期范围内的券商事实
stock-broker
stock-broker --date 2026-11-02
stock-broker --date 2026-10-01:2026-11-02

# 汇总指定业务日的已实现绩效
stock-perform
stock-perform --date 2026-11-02

# 根据月度已实现绩效形成 KEEP、RETRAIN 或 WARMUP 决策
stock-evaluate
stock-evaluate --date 2026-11-02

# 按保留规则清理数据
stock-clean
stock-clean --date 2026-11-02
```

`stock-perform` 要求同一业务日 `stock-broker` 成功；`stock-evaluate` 要求同一业务日 `stock-perform` 成功。各命令立即检查前置任务，不在进程内轮询等待。

## 生产调度

应用不内置调度框架。当前 `.github/config/stock-trading.cron` 使用北京时间、项目根目录 `*.env` 和一个非阻塞 `flock` 串行执行：

| 时间 | 命令链 | 职责 |
| --- | --- | --- |
| 工作日 09:35 | `stock-sell` | 卖出跌出 Rank15 的可卖持仓 |
| 工作日 13:05 | `stock-buy` | 无持仓时从 Top5 筛选买入 |
| 工作日 16:30 | `stock-collect -> stock-prepare -> stock-rank` | 更新行情、Qlib 和完整排名 |
| 工作日 20:20 | `stock-broker -> stock-perform -> stock-evaluate` | 同步事实、结算闭环并评估模型 |
| 周日 03:00 | `stock-clean` | 清理到期数据 |
| 每日 03:30 | `logrotate` | 轮转运行日志 |

发布流水线由 `v*.*.*` 标签触发，要求标签版本与 `pyproject.toml` 一致，并要求标签提交与远端 `stock-trading` 分支一致。流水线构建 Wheel、上传发布文件并调用服务器安装脚本；安装脚本使用托管 Python 3.12，校验所有 CLI，更新当前用户 crontab 的托管区块，保留最近 3 个版本目录。发布流程不执行数据采集、Qlib 准备、训练、排名或真实交易。

## 日志与通知

CLI 将业务日志写入标准错误流，结构化结果写入标准输出。日志不得输出账户、股东账号、密码、Token、Cookie、验证码或完整协议响应。

生产 Cron 将输出追加到：

```text
~/stock-trading/logs/run.log
```

日志每日检查轮转，单文件达到 20 MB 时轮转，保留 14 份并压缩历史文件。

配置 `DEVICE_KEY` 后，交易执行结果和 CLI 异常可发送 Bark 通知。通知失败只记录本地警告，不改变原业务结果。

## 验证边界

README 只描述当前源码和仓库内发布配置能够证明的行为。验证结论必须分层：

- **源码检查**：入口、参数、调用链、配置、状态结构和调度模板一致。
- **测试**：需要单独执行测试或临时验证脚本后才能声明。
- **运行验证**：需要实际执行对应 CLI，并核对日志、Parquet、模型制品和 SQLite。
- **真实交易验证**：需要券商真实委托、成交、撤单及完整买卖闭环证据。
- **生产验证**：需要核对发布流水线、服务器安装结果、Cron、进程、日志和业务制品。

截至 2026-11-03，`v0.1.47` 曾完成一次生产链路验证：数据质量阈值为 `0.8`，排名制品与 SQLite 均生成 3017 条唯一连续排名；买入流程到达实时行情边界但未提交订单；模型评估为 `WARMUP`，有效已实现月份为 `0/6`。这些是该次运行证据，不代表后续代码、行情、模型或真实交易结果自动持续有效。
<!-- AI_GENERATE_END--------------------------------------------------------------------------------------------------------- -->
