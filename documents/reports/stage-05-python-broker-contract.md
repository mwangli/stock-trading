---
id: stage-05-python-broker-contract
title: 阶段 05：Python 券商契约迁移报告
sidebar_position: 105
owner: system
type: report
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-30
slug: /reports/stage-05-python-broker-contract/
source_paths:
  - stock-trading/src/stock_trading/broker/client.py
  - stock-trading/src/stock_trading/broker/parser.py
  - stock-trading/src/stock_trading/broker/models.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 05：Python 券商契约迁移报告

## 阶段目标

把现有 Java 中已经使用的 ZX 券商 HTTP 地址、Action、请求字段和响应索引迁移到纯 Python 链路。当前模型仍为 `REJECTED`，因此本阶段只允许只读影子同步和本地契约测试，不允许真实委托。

## 已迁移契约

固定请求地址为 `https://weixin.citicsinfo.com/reqxml`，未新增或猜测其他券商地址。已迁移以下 Action：

```text
33     实时价格与五档行情
100    登录
110    限价委托
111    撤单
113    当日委托
114    当日成交
115    历史成交
116    账户资金
117    当前持仓
5018   历史委托
41092  算术验证码
```

所有请求继续携带 Java 已使用的 `cfrom=H5`、`tfrom=PC`、`newindex=1`、`MobileCode`、`intacttoserver` 和毫秒级 `reqno`。账户、持仓、委托和成交继续按现有 Java 字段索引解析；历史查询继续使用 `StartPos`、`MaxCount`、`BeginDate` 和 `EndDate` 分页。

## Python 实现

- 使用 Python 标准库 `urllib` 发送表单请求，没有新增 HTTP 框架。
- Token 只保存在当前 Python 进程内，默认 30 分钟过期，不写日志和 SQLite。
- 登录请求字段与 Java 保持一致，但验证码识别器必须显式注入；没有经过验收的识别器时自动登录直接拒绝。
- 提供账户、持仓、实时价格、五档行情、当日或历史委托与成交查询。
- 新增 `sync-broker-shadow` 命令，把一次只读券商快照事务写入 SQLite。
- 不保存账号、密码、Token、签名密钥和股东账号；SQLite 只保存交易闭环必需的资金、持仓、订单与成交事实。

## SQLite 状态

新增以下最小表：

```text
broker_account_snapshot   账户资金快照
broker_position_snapshot  持仓快照
broker_order_fact         券商委托事实
broker_fill_fact          券商成交事实
broker_order_intent       真实写入幂等意图
```

只读快照使用内容哈希形成稳定业务键。完全相同的快照第二次导入返回 `SKIPPED`。委托事实按券商委托号更新，成交事实按订单、股票、方向、价格、数量和时间生成哈希去重。

## 写入安全边界

代码中保留了 Java 已使用的限价委托和撤单字段，但没有提供真实下单 CLI。任何真实委托必须同时满足：

1. SQLite `REAL_TRADING_ENABLED=true`。
2. 最新模型状态为 `ACTIVE`。
3. 先使用唯一 `client_order_id` 创建 `PREPARED` 订单意图。
4. 提交前原子切换为 `SUBMITTING`，同一业务键不能重复提交。
5. A 股数量大于零且为 100 股整数倍，价格大于零，方向只能为 `BUY` 或 `SELL`。
6. 撤单仅允许已记录券商订单号且状态为 `SUBMITTED` 或 `PARTIAL` 的订单，并原子切换为 `CANCEL_SUBMITTING`。

当前真实交易开关为 `false`，最新模型为 `REJECTED`，两层门禁都会拒绝真实写入。

## 本地契约测试

使用本地伪响应执行了以下测试，没有连接券商：

- 通用字段、Action 和请求地址一致性。
- 验证码图片解码、登录字段和响应 Token 更新。
- 账户、持仓、当日委托、当日成交解析。
- 历史委托、历史成交日期和分页字段。
- 实时价格与完整五档行情解析。
- 只读快照首次导入成功、重复导入 `SKIPPED`。
- 默认客户端在发出 HTTP 前拒绝真实委托。
- SQLite 开关关闭时拒绝创建真实订单意图。
- 临时启用开关并构造 `ACTIVE` 模型后，限价委托字段、唯一提交和重复拒绝通过伪响应验证。
- 撤单字段、状态转换和重复撤单拒绝通过伪响应验证。
- SQLite 外键检查无违规。

测试结果为 `stage05 broker contract tests: passed`。同时修复了阶段 04 遗留的 SQLite 连接未显式关闭问题，避免 Windows 下数据库文件长期锁定。

## 阶段 04 回归

在新的临时 SQLite 文件中重新导入真实研究制品：

```text
prediction：600
shadow_order：234
shadow_execution：234
performance_snapshot：119
model_version.status：REJECTED
REAL_TRADING_ENABLED：false
```

第二次导入仍返回 `SKIPPED`，原有表计数没有变化。证明新增券商表和连接管理没有破坏阶段 04 状态闭环。

## 未验证边界

- 当前进程环境没有配置 ZX 券商账号、Token、签名密钥或完整性参数，因此没有执行真实券商 HTTP 请求。
- 没有调用验证码 OCR 服务，也没有证明自动登录在真实券商环境可用。
- 没有验证真实五档字段、历史分页边界、Token 刷新、限价委托、撤单或券商错误码。
- 没有发送真实委托，也没有产生真实账户或成交数据。
- 未执行 Java Maven、React 编译、构建或启动。

## 阶段结论

Python 已具备替代 Java 券商适配器的源码契约和本地状态边界，但尚不具备删除 Java 券商实现的运行证据。进入最终替换收口前，至少需要真实环境只读影子同步通过，并在模型达到 `ACTIVE` 后完成受控小资金验证。
