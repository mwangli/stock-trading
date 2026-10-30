<!-- AI_GENERATE_START-------------------------------------------------------------------------- -->
# AGENTS.md - 纯 Python T+1 自动交易项目规范

## 交互语言

- 所有面向用户的说明使用中文；代码、命令、类名和协议字段保留英文。
- 结论必须区分源码检查、测试、运行验证和真实交易验证，不得把前一层证据描述成后一层。
- 不直接粘贴包含账号、Token、密码、股东账号、Cookie 或委托号的原始输出。

## 执行控制

- 默认只修改源码、配置和文档并进行静态检查。
- 未经用户当前请求明确授权，不运行测试、安装依赖、训练模型、回测、启动应用或调用真实券商写接口。
- 默认保留未提交差异，未经当前请求明确授权禁止 `git commit`、`git push` 和发布。
- 临时测试脚本和中间结果统一放入根 `.tmp/`，完成后删除。

## 项目目标

本项目只保留一套面向 A 股的纯 Python T+1 交易验证链路：

```text
BaoStock 数据 → Parquet/Qlib → Alpha158 + LightGBM
→ Top 5 → 下午买入 → 次日上午卖出
→ 券商事实回写 → SQLite 绩效 → 滚动评估与重训请求
```

首要原则：

1. 最少组件、单一权威路径，不恢复 Java、React、Quartz、Agent、MySQL 或 MongoDB 双轨。
2. 模型输出只负责候选排名，确定性 Python 逻辑根据资金、持仓、T+1 和价格范围生成订单。
3. 券商判断订单是否接受；本地不设置真实交易开关、人工准入或重复风控门禁，只使用 `clientOrderId` 防止同一计划重复提交并完整记录券商结果。
4. 账号、密码、Token、股东账号和签名参数只从根 `.env` 读取，不写日志、文档、SQLite 或版本库。

## 项目结构

```text
stock-trading4/
├── stock-trading/
│   ├── pyproject.toml
│   ├── src/stock_trading/
│   │   ├── market/       # BaoStock、行情模型、质量审计和 Parquet
│   │   ├── research/     # Qlib、标签、训练、推理和回测
│   │   ├── broker/       # ZX 券商 HTTP、验证码、解析与协议模型
│   │   ├── state/        # SQLite 权威状态
│   │   └── trading/      # 计划、执行、结果同步与绩效
│   └── var/              # 本地数据、Qlib、模型制品和 SQLite，不提交
├── documents/
│   ├── decisions/        # 当前架构决策
│   └── reports/          # 分阶段证据
├── .tmp/                 # 临时验证文件，不提交
├── .env                  # 本地敏感配置，不提交
└── README.md
```

## Python 规范

- Python 版本为 `3.11` 至 `3.13`，依赖以 `stock-trading/pyproject.toml` 为唯一事实源。
- 使用类型标注、`dataclass`、`Decimal`、`date/datetime` 和结构化 DTO；资金、价格、数量不得用无约束字符串表达。
- `market` 负责外部行情和不可变原始事实；`research` 负责研究制品；`broker` 只负责已验证协议；`state` 负责持久化；`trading` 负责编排和安全边界。
- 不增加 Web 页面、REST 服务、消息队列、数据库服务、工作流引擎或独立模型服务，除非现有 CLI + SQLite 方案有明确且已验证的能力缺口。
- 新增或修改 Python 文件必须保持首尾 `AI_GENERATE_START/END` 标记并递增计数；格式敏感文件不添加标记。

## 数据与状态

- 原始行情写入 Parquet；Qlib 数据和模型制品位于 `stock-trading/var/`；运行状态只写 SQLite。
- SQLite 是任务、模型、预测、订单意图、券商事实、持仓和绩效的唯一业务状态库。
- MySQL/MongoDB 只属于已删除旧系统，不得重新引入；历史数据库仅允许按用户明确要求只读取证，禁止清库或迁移写入。
- 任何全量回填、训练或重建操作必须显式指定数据范围，并与每日增量运行分开。

## 交易执行

- 真实订单来自已持久化计划，SQLite 只保存幂等标识和券商返回结果，不判断订单能否被券商接受。
- 每次业务运行使用稳定 `businessDate` 和 `clientOrderId` 幂等。
- 上午和下午自动入口只生成计划并提交；订单终态由独立恢复命令或收盘后事实同步更新。
- 资金、持仓、允许股票前缀和 5 至 20 元价格范围用于生成订单，不作为提交前的第二套准入流程。
- 券商返回委托号、明确拒绝或通信结果不确定时，分别记录 `SUBMITTED`、`REJECTED` 或 `UNKNOWN`。
- Action 110 只发送官方 H5 和真实历史响应已确认的字段；禁止猜测券商参数。

## 调度边界

- 项目不内置调度框架。使用操作系统 `cron` 或 `systemd timer` 调用幂等 CLI。
- 上午入口：`run-morning-trading --date YYYY-MM-DD`。
- 下午入口：`run-afternoon-trading --date YYYY-MM-DD`。
- 收盘后分别调用行情更新、预测和 `update-live-performance`；模型重训由 SQLite 训练请求和确定性策略触发，不按固定每日 Cron 训练。
- 操作系统只负责按时调用命令，券商负责交易时间、资金、权限及订单合法性的最终判断。

## 文档与验证

- `documents/decisions/adr-002-python-t1-minimal-rebuild.md` 是架构权威。
- `documents/reports/stage-*.md` 保存阶段证据，不维护已删除模块的说明文档。
- 文档状态使用 `PROPOSED`、`SOURCE_IMPLEMENTED`、`COMPILED`、`RUNTIME_VERIFIED`、`PRODUCTION_VERIFIED`、`SUPERSEDED`。
- 项目不保留正式测试目录；临时测试放 `.tmp/`。未获授权时只执行引用扫描、语法级人工检查、差异检查、编码检查和敏感信息扫描。
<!-- AI_GENERATE_END-------------------------------------------------------------------------- -->
