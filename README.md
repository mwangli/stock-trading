<!-- AI_GENERATE_START---------------------------------------------------------------------------------------------- -->
# stock-trading

面向 A 股的纯 Python T+1 自动交易验证系统。项目只保留 BaoStock、Qlib、LightGBM、Parquet、SQLite 和经过真实响应确认的 ZX 券商 HTTP 契约。

## 目标与边界

系统只验证一个问题：`Alpha158 + LightGBM Regression` 产生的横截面 Top 5，能否稳定指导小资金 T+1 交易。

模型只负责评分和排名；Python 确定性逻辑负责候选过滤、数量计算、T+1 时序、幂等提交和结果记录；券商最终判断订单是否接受。项目不包含 Java、Web 页面、Quartz、Agent、MySQL、MongoDB、消息队列或独立模型服务。

## 交易链路

```text
D-1 收盘后：更新数据并生成 D 日 Top 5
D 日下午：从可交易 Top 5 中买入一只
D+1 日上午：卖出全部可卖持仓
D+1 收盘后：同步券商事实、绩效和模型评估
```

当前标签与实际交易窗口保持一致：

```text
label(D-1, stock)
= D+1 上午卖出窗口 VWAP / D 日下午买入窗口 VWAP - 1
```

BaoStock 5 分钟时间戳按 K 线结束时间解释。买入窗口使用完整落在 `13:05-14:54` 内的 21 根 K 线，卖出窗口使用完整落在 `09:35-11:25` 内的 22 根 K 线。窗口不完整的数据不能进入标签或回测成交。

## 交易规则

- 每次最多持有一只股票，买入数量按 100 股整数倍计算。
- 候选来自 Top 5，代码固定使用 `0.01-1000000` 元的宽价格边界，等同于不设置业务价格门槛；由真实可用资金、100 股整手和手续费动态决定能否买入。
- 默认研究和交易范围为沪市 `60`、深市 `00`；历史成功委托确认 `ALLOWED`，明确权限拒绝记录 `DENIED`，证据不足保持 `UNKNOWN`。
- 上午卖出目标来自券商当前可卖持仓；下午买入目标来自 Top 5，存在其他持仓时不新增股票。
- 真实委托不保存或复用旧五档价格和旧数量；每次提交前重新读取账户、持仓和五档行情。
- 每次实时尝试使用独立 `clientOrderId`，SQLite 只记录恢复和审计所需的委托流水，不作为价格或数量事实源。
- 委托后每 5 秒查询一次券商状态，最多观察 60 秒；未全部成交时申请撤单，只有确认 `CANCELLED` 后才重新报价。
- 部分成交后按最新资金、持仓和可卖数量计算剩余委托；通信结果不确定或撤单未确认时停止重挂。
- Action 110 只发送官方 H5 和真实历史响应已经确认的字段，禁止猜测参数。

## 数据与状态

- BaoStock：历史日线、5 分钟线和交易日历。
- Parquet：不可变原始行情和质量审计输入。
- Qlib：Alpha158 特征、训练数据和横截面预测。
- SQLite：任务、模型、预测、委托尝试、券商事实、成交和绩效的本地状态库。
- ZX 券商：实时账户、持仓、行情、委托和成交事实；不作为历史训练数据源。

Windows 使用源码项目根目录，Linux 使用当前运行用户的 `~/stock-trading/`；两端均从根目录 `.env` 读取配置，并将运行数据写入根目录 `data/`：

```text
<runtime>/
├── market/                # Parquet 原始行情
├── qlib/                  # Qlib 日历、股票池和特征
├── artifacts/             # 模型、预测和回测制品
└── state/trading.db       # SQLite 权威状态
```

## 项目结构

```text
stock-trading4/
├── pyproject.toml
├── src/stock_trading/
│   ├── market/            # 数据采集与质量审计
│   ├── research/          # 标签、Qlib、训练、推理和回测
│   ├── broker/            # ZX 券商 HTTP 契约
│   ├── state/             # SQLite 状态
│   ├── trading/           # 计划、提交、恢复和绩效
│   └── initialization.py  # 可恢复、幂等的初始化协调器
├── data/                  # 本地运行数据，不提交
├── .tmp/                  # 临时验证文件，不提交
├── .env                   # 本地敏感配置，不提交
└── README.md              # 项目唯一说明文档
```

## 安装

虚拟环境目录名本身没有功能差异：`.venv` 和 `venv` 都是普通目录。项目约定 Windows 本地使用 `.venv`，便于与源码目录区分；Linux 生产环境固定使用 `~/stock-trading/venv`，路径由发布流水线和 systemd 引用。

Windows 在仓库根目录使用 `uv` 创建 Python 3.12 虚拟环境并安装项目：

```cmd
uv python install 3.12
uv venv --python 3.12 .venv
uv pip install --python .venv\Scripts\python.exe -e .
```

不需要激活虚拟环境，也不需要把 `.venv\Scripts` 加入 Windows `PATH`。

### 生产发布前置条件

服务器基础环境由运维预先安装，发布流水线不执行系统软件安装：

- `uv`，用于安装托管的 Python 3.12、创建或复用 `~/stock-trading/venv` 并安装生产依赖；流水线不负责安装 `uv`。
- `systemd`，用于运行八个具名 Service、八个 Timer 和 journald 日志。
- `~/stock-trading/.env`，由用户或运维独立创建和维护。首次发布时若该文件不存在但旧 `/etc/stock-trading/env` 可读，流水线会以 `0600` 权限迁移一次；已有文件不会被覆盖。两处都不可用时发布直接失败。

流水线构建并上传当前 Wheel 到 `~/stock-trading/releases/<version>/` 后，使用服务器预装的 `uv` 安装并管理 Python 3.12，在 `~/stock-trading/venv` 创建或复用虚拟环境，然后安装 Wheel、部署八个具名 Service 和八个 Timer。Linux 运行数据固定写入 `~/stock-trading/data/`；流水线只创建 `market`、`qlib`、`artifacts` 和 `state` 目录，不迁移或清理旧数据。生产依赖默认从阿里云 PyPI 镜像下载，网络超时为 300 秒并最多重试 5 次。流水线不修改用户 `PATH`，也不执行 `init`；初始化和历史数据迁移均由运维手工执行。

## 命令行

Windows 本地通过 `.venv` 中的入口直接执行，不依赖 PATH：

```cmd
.venv\Scripts\stock-init.exe
.venv\Scripts\stock-train.exe
.venv\Scripts\stock-collect.exe
.venv\Scripts\stock-rank.exe
.venv\Scripts\stock-sell.exe
.venv\Scripts\stock-buy.exe
.venv\Scripts\stock-broker.exe
.venv\Scripts\stock-perform.exe
.venv\Scripts\stock-evaluate.exe
.venv\Scripts\stock-clean.exe
```

每个命令都独立提供 `-h` 和 `--help`，帮助页只显示当前命令的功能、参数和示例：

```cmd
.venv\Scripts\stock-broker.exe -h
.venv\Scripts\stock-collect.exe --help
```

Linux 服务器不配置交互 PATH，所有日常任务通过 systemd 启动，日志通过 journald 查看：

```bash
systemctl start stock-collect
journalctl -fu stock-collect
```

日期格式固定为 `YYYY-MM-DD`；省略日期时默认当天；日期范围包含开始日和结束日；参数数量或格式错误时直接显示中文提示。

### 初始化

```bash
# 首次初始化；已完成阶段通过文件检查后自动跳过
stock-init
```

初始化按五个阶段执行：股票基础信息、日线/复权因子/5 分钟线、Qlib、模型训练与回测、最近交易日预测。券商历史同步已经从 `stock-init` 剥离，需要时单独执行 `stock-broker --date DATE[:DATE]`。

历史研究股票池按证券生命周期与研究窗口是否相交确定，包含窗口内上市或退市的 SH60/SZ00 股票，避免只保留当前存续股票造成幸存者偏差。最新预测使用独立实时股票池，只保留快照日仍上市、可交易且当日非 ST 的证券；模型构建也会跳过整个研究窗口内没有任何非 ST 有效交易日的股票。窗口开始前已经退市的证券基础档案继续保留用于审计，但不会下载研究行情。

BaoStock 不允许并发采集。初始化和 `collect` 都在主线程使用唯一的 BaoStock 长连接顺序采集，不创建采集子进程或并发连接；网络失败时关闭并重建当前连接。`STOCK_TRADING_HISTORY_DAYS` 控制研究窗口，默认 `1095` 天，允许范围为 365 至 1825 天。

初始化和 `collect` 不创建采集子进程，也不为当前 BaoStock 请求创建临时线程。按下 `Ctrl+C` 后由同步请求自然返回或抛出中断，再由主线程退出；如果 SDK 正在阻塞读取，可能需要等待当前一轮请求结束。已经按股票编号写入的 Parquet 数据会保留，后续 `init` 仅按对应编号文件是否存在决定跳过。

Linux 每次启动任一业务命令前，都会检查同名旧主进程及其子进程。后启动的同名命令按 `SIGINT`、`SIGTERM`、`SIGKILL` 顺序接管旧实例，确认旧进程全部退出后才继续，并在 `state/<command>-owner.pid` 记录当前主进程；不同业务命令相互隔离，不会终止其他 `stock-*` 命令或普通 Python 进程。`stock-sell` 和 `stock-buy` 共用 `trading-owner.pid`，任何新的实时交易都会先接管尚未结束的旧交易，禁止买卖并行。未发现旧进程时不增加日志，完成接管时只输出一条 `WARNING`，说明已终止的命令和 PID。该机制用于避免同一命令重复运行，不是 BaoStock 连接锁，也不阻止 `stock-init` 与 `stock-collect` 分别运行。

初始化不使用任务缓存判断是否跳过。行情阶段按实际文件完整性判断是否跳过；`init` 不执行周期性清理，该职责由生产环境的 `stock-clean.timer` 每周日调用 `clean` 完成。`task_run` 只记录业务任务结果，不作为初始化完成依据。

初始化不会自动提交无意义真实订单探测权限。历史成功委托和后续真实受理订单会更新市场权限；非交易时间、资金不足等结果不能证明权限，保持 `UNKNOWN` 并由券商在真实提交时最终判断。

### 数据

```bash
# 默认研究股票池，采集当天的日线、复权因子和5分钟线
stock-collect

# 指定股票和单日
stock-collect --codes sh.600000,sz.000001 --date 2026-10-29

# 指定股票和日期范围
stock-collect --codes sh.600000,sz.000001 --date 2023-01-01:2026-10-29
```

`collect` 是唯一公开行情采集命令，同时采集每只股票的日线、复权因子和5分钟线。省略 `--codes` 时从已有股票基础信息中确定默认研究股票，基础信息不存在会直接报错，不自动执行 `init`。省略 `--date` 时只处理当天；单日格式为 `--date 2026-10-29`，范围格式为 `--date 2023-01-01:2026-10-29`，格式错误会返回正确示例。`collect` 不读取覆盖标记，始终采集指定日期并与股票编号对应的真实文件合并。

采集在主线程按股票顺序执行，登录一次 BaoStock 后复用同一连接，依次采集每只股票的日线、复权因子和5分钟线。单类数据失败不会阻止同一股票其他类别写入，但最终命令会汇总失败并返回非零结果。复权因子查询结果为空时首次写入固定列结构的空 Parquet，使用该编号文件本身表示已处理；后续采集为空时保留已有记录。5分钟原始数据按股票压缩为单个 Parquet，只保留最近90个自然日；上午卖出窗口、下午买入窗口 VWAP/高低价聚合至少保留5年。

### 数据清理

`clean` 直接检查并整理实际文件，不使用额外清理缓存。默认保留规则：

- 原始 5 分钟数据保留最近 90 个自然日。
- T+1 分钟窗口聚合结果保留 5 年。
- SQLite `task_run` 运行日志保留 180 天。
- 券商账户和持仓快照保留 1 年。
- 预测、回测/实盘绩效、模型评估、已终结委托和成交事实保留 5 年。
- 活动委托、当前持仓、模型版本、训练请求、券商权限永久保留；已完成训练请求保留 5 年。

清理完成后 SQLite 会执行 WAL 截断、`VACUUM` 和优化，以实际释放磁盘空间。生产环境由 `stock-clean.timer` 每周日 03:00 执行一次 `clean`。按全市场约三年初始化数据估算，改造后原始分钟数据约 1.2-1.8GB，长期窗口聚合约 100-300MB，整个持久化目录目标控制在 3-5GB；实际大小取决于股票数量、Parquet 压缩率和模型版本数量。

### 模型与回测

```bash
# 使用现有 Qlib，按 70%/15%/15% 自动切分后训练
stock-train

# 按交易日先后使用自定义训练、验证和测试比例
stock-train --split 6:3:1

# 等待当天 collect 成功后生成完整横截面排名
stock-rank
stock-rank --date 2026-10-29

# 压缩旧分钟分区，并执行分钟和 SQLite 分级清理；默认以当天为基准
stock-clean
stock-clean --date 2026-10-31
```

每次训练写入 `data/artifacts/alpha158-lightgbm/versions/<version>/` 的独立目录。只有模型、指标和预测制品全部写完并通过读取检查后，才会原子替换 `current.json` 指针。训练失败或 Ctrl+C 不会覆盖当前可用模型；排名、回测和初始化统一读取指针指向的版本。

### 券商与交易

```bash
# 同步当天券商事实并恢复本地委托；也可指定日期
stock-broker
stock-broker --date 2026-10-29

# 按实时持仓和五档执行上海时区当天上午卖出
stock-sell

# 按 Top 5、实时资金和五档执行上海时区当天下午买入
stock-buy

# 等待 broker 成功后生成当日已实现绩效
stock-perform
stock-perform --date 2026-10-29

# 等待 performance 成功后执行模型评估
stock-evaluate
stock-evaluate --date 2026-10-29
```

日线、复权因子和 5 分钟线统一由 `collect` 完成，成功写入 Parquet 后立即刷新 Qlib；不再保留独立 `prepare` 命令。`rank` 等待同一业务日的 `collect` 成功；`performance` 等待 `broker` 成功；`evaluate` 等待 `performance` 成功。真实交易统一由 `stock-sell` 和 `stock-buy` 在执行窗口内完成；命令不接受日期参数，只处理上海时区当天交易。

`stock-buy` 从 Top 5 中按顺序检查允许前缀、已确认的券商权限和实时价格范围，固定首个通过的模型候选；不读取或校验其他持仓。每次尝试前重新读取五档并复核价格范围，再按券商实时可用资金计算整手数量，订单最终是否合法仍由券商裁定。`stock-sell` 只固定本次处理的持仓标的，并按券商实时可卖持仓计算数量。价格和数量均不缓存。提交后每 5 秒查询一次，最长观察 60 秒。未全部成交时先申请撤单，确认券商终态为 `CANCELLED` 后，再根据最新事实提交剩余数量。提交结果不确定、撤单未确认、达到最大尝试次数或执行窗口结束时立即停止，禁止盲目重挂。

当前唯一执行策略为固定配置 `FIXED_TIME_URGENCY`，默认执行窗口 15 分钟、最多 12 次尝试：

| 执行窗口进度 | 买入价格 | 卖出价格 | 目的 |
| --- | --- | --- | --- |
| 前 50% | 买三 `BID3` | 卖三 `ASK3` | 被动等待价格改善 |
| 50%-85% | 买一 `BID1` | 卖一 `ASK1` | 回到最优同侧报价 |
| 最后 15% | 卖一 `ASK1` | 买一 `BID1` | 跨价提高完成率 |

策略参数统一定义在 `src/stock_trading/config.py`，不从 `.env` 或操作系统环境变量读取。调整这些值属于代码策略变更，需要同步审查策略行为：

| 代码常量 | 固定值 | 含义 |
| --- | ---: | --- |
| `TRADING_MIN_PRICE` | `0.01` | 回测和实时买入使用的最低参考价格 |
| `TRADING_MAX_PRICE` | `1000000` | 回测和实时买入使用的最高参考价格 |
| `EXECUTION_WINDOW_SECONDS` | `900` | 单次买入或卖出执行窗口 15 分钟 |
| `ORDER_WAIT_SECONDS` | `60` | 单笔委托最多观察 60 秒 |
| `ORDER_POLL_SECONDS` | `5` | 每 5 秒查询一次委托状态 |
| `CANCEL_CONFIRM_SECONDS` | `30` | 撤单后最多等待 30 秒确认终态 |
| `MAX_ATTEMPTS` | `12` | 一个执行窗口内最多提交 12 次 |
| `PASSIVE_FRACTION` | `0.50` | 前 50% 窗口使用买三或卖三 |
| `NORMAL_FRACTION` | `0.85` | 50%-85% 使用买一或卖一，之后进入主动跨价阶段 |

当前五档接口只解析价格，没有每档挂单量，因此最后阶段只跨到对手一档，不直接使用对手二至五档扫单。后续只有在确认五档数量字段并积累真实成交、撤单和滑点数据后，才考虑多策略选择或自动适应。

主动按 `Ctrl+C` 时，如果已有 `SUBMITTED` 或 `PARTIAL` 委托，程序会先申请撤单并等待券商确认；如果正处于提交结果不确定或待撤状态，则保留恢复流水并退出，不会自动提交新单。之后运行 `stock-broker` 或重新执行相同方向的 `stock-sell`、`stock-buy`，都会先查询券商当日委托再决定是否继续。

## 调度

应用不内置调度框架。生产环境统一使用 systemd timer，按北京时间执行：

| 时间 | Timer | Service | 职责 |
| --- | --- | --- | --- |
| 工作日 09:35 | `stock-sell.timer` | `stock-sell` | 上午卖出 |
| 工作日 13:05 | `stock-buy.timer` | `stock-buy` | 下午买入 |
| 工作日 16:30 | `stock-collect.timer` | `stock-collect` | 更新 Parquet 并刷新 Qlib |
| 工作日 17:00 | `stock-rank.timer` | `stock-rank` | 等待 collect 后生成排名 |
| 工作日 20:20 | `stock-broker.timer` | `stock-broker` | 同步券商订单并组装交易闭环 |
| 工作日 20:30 | `stock-perform.timer` | `stock-perform` | 等待 broker 后生成已实现绩效 |
| 工作日 20:35 | `stock-evaluate.timer` | `stock-evaluate` | 等待 performance 后评估模型 |
| 周日 03:00 | `stock-clean.timer` | `stock-clean` | 清理过期数据 |

Timer 不重试、不加锁，非交易日由各业务命令按实际空数据或券商返回结果结束。模型重训不按固定 Timer 执行。

## 运行日志

所有 CLI 命令都会打印业务开始、关键参数、阶段进度、完成耗时和失败原因。日志写入标准错误流，结构化结果写入标准输出；日志不会输出券商账户、股东账号、凭据、验证码、Token、Cookie 或完整响应报文。

Windows 本地直接执行 `.venv\Scripts\stock-*.exe`，日志显示在当前终端。Linux 服务器统一通过 systemd 执行，标准输出和标准错误全部写入 journald，不再维护 `run.log` 或 logrotate：

```bash
# 手工执行 collect
systemctl start stock-collect

# 持续查看 collect 日志
journalctl -fu stock-collect

# 查看 collect 当天日志
journalctl -u stock-collect --since today
```

默认日志级别为 `INFO`，可通过环境变量调整：

```text
STOCK_TRADING_LOG_LEVEL=WARNING
```

## Bark 通知

在项目根目录 `.env` 中配置 `DEVICE_KEY` 后，买卖委托被券商受理时会推送交易摘要；委托被拒绝、结果未知或任意 CLI 业务异常时会推送失败信息。正常的数据同步、训练、预测和评估保持静默。

异常通知只包含经过脱敏和裁剪的错误摘要，不再附加文件日志 URL；完整日志统一通过对应的 `journalctl -u stock-任务` 查询。Bark 推送失败只记录本地警告，不改变原业务结果。

## 当前验证状态

工程链路已经覆盖数据采集、Alpha158 特征、LightGBM 训练、Top 5 推理、组合回测、SQLite 状态、券商登录、订单提交和事实恢复，但盈利能力尚未通过验证。

已留存的 20 只股票基线结果：

```text
IC Mean:        -0.0136767
Rank IC Mean:   -0.0178742
1 万元完整成本净收益: -22.75%
2 万元完整成本净收益: -19.00%
5 万元完整成本净收益: -19.19%
```

这些结果表明当前模型不能作为“稳定盈利”证据，扩大股票池、消除幸存者偏差并完成滚动样本外验证仍是模型侧的首要任务。代码已经取消本地复杂提单门禁，因此技术上可以按实时券商事实提交委托，但真实交易不应被误认为模型已经达到业务收益目标。

真实券商验证已确认登录、30 分钟 Token 缓存、账户/持仓/委托/成交读取以及 Action 110 的股东账号和市场参数。真实探测已到达券商交易时间校验，但尚未取得成功委托号，也未验证真实撤单、部分成交和完整绩效闭环。

当前仓库变更只完成源码和文件层面检查。除非另有明确记录，不能把 `SOURCE_IMPLEMENTED` 描述为测试通过、运行通过或生产验证通过。
<!-- AI_GENERATE_END----------------------------------------------------------------------------------------------- -->
