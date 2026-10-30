---
id: stage-10-real-validation-readiness
title: 阶段 10：真实验证准入报告
sidebar_position: 110
owner: system
type: report
status: SOURCE_IMPLEMENTED
last_verified: 2026-10-30
slug: /reports/stage-10-real-validation-readiness/
source_paths:
  - stock-trading/src/stock_trading/trading/validation.py
  - stock-trading/src/stock_trading/state/store.py
  - stock-trading/src/stock_trading/trading/orchestrator.py
  - stock-trading/src/stock_trading/cli.py
---

<DocMetadata />

# 阶段 10：真实验证准入报告

## 阶段目标

在不猜测券商接口、不自动放开真实写入的前提下，建立“本地门禁 → 真实券商只读事实 → 当日准入记录 → 显式开启小资金买入”的单一路径。阻断条件存在时必须在任何券商 HTTP 调用前停止。

## 命令入口

```text
validate-real-readiness --date YYYY-MM-DD
enable-real-trading --date YYYY-MM-DD
disable-real-trading
```

- `validate-real-readiness` 先执行本地门禁，全部通过后才读取券商账户、持仓、当日委托、成交和 Top 5 五档行情。
- `enable-real-trading` 只接受当天 `READ_ONLY_VERIFIED` 记录，且记录模型必须仍是当前 `ACTIVE` 模型。
- `disable-real-trading` 无条件关闭新增买单；关闭后卖出和撤单仍可执行。

## 本地门禁

以下条件任一不满足即记录 `BLOCKED`，并保持 `brokerHttpAttempted=false`：

- 验证日期必须是机器当前日期，并存在于 Qlib 日历。
- ZX 券商接入已启用，账号、密码、签名和完整性参数均已配置，并且存在进程 Token 或完整的百度 OCR 自动登录配置。
- 存在 `ACTIVE` 模型。
- 目标交易日前存在该 `ACTIVE` 模型生成的最新 Top 5，且预测日期为前 1 至 7 个自然日。
- SQLite 中没有 `SUBMITTING`、`SUBMITTED`、`PARTIAL`、`CANCEL_SUBMITTING` 或 `UNKNOWN` 订单。

当前 CLI 已迁移 Java 验证过的登录字段和百度 OCR 双路径流程。OCR 自动提交仍需通过 `ZXBROKER_CAPTCHA_AUTO_SUBMIT_ENABLED=true` 显式开启，登录 Token 仅在当前 Python 进程缓存 30 分钟。

## 券商只读门禁

本地门禁通过后验证：

- 总资产大于零、可用资金非负。
- 持仓数量和可卖数量合法。
- 当日委托不存在 `UNKNOWN` 状态。
- 默认账户总资产不超过 5000 元，可通过 `REAL_VALIDATION_MAX_ASSETS` 显式调整。
- Top 5 中至少一只股票同时满足交易权限、5 至 20 元价格范围和完整五档行情。
- 单只候选五档查询失败时继续检查后续候选；仅当 Top 5 全部不满足时阻断准入。

通过后保存 `READ_ONLY_VERIFIED`，但不会自动开启真实买入。账户、持仓、委托和成交事实仍同步到 SQLite，便于与券商页面逐项核对。

## 模型和订单绑定

下午买入影子订单新增 `model_version_id` 和预测日期。真实买单只有在以下条件同时满足时才能进入 `PREPARED`：

- 订单参数与已持久化影子计划完全一致。
- 计划模型等于当前 `ACTIVE` 模型。
- 预测日期早于目标交易日。
- 新增交易开关已开启。

卖出计划不依赖模型状态或新增交易开关，确保模型降级、紧急停止后仍能退出已有 T+1 持仓。SQLite 初始化会为旧状态库补充新增影子订单列，不删除既有记录。

## 本地测试

临时 SQLite 和伪券商验证通过：

- 券商未启用、配置缺失或没有 `ACTIVE` 模型时返回 `BLOCKED`，伪券商调用次数为 0。
- 本地门禁、账户事实和 Top 5 五档全部通过时保存 `READ_ONLY_VERIFIED`。
- 未通过同日只读验证不能开启新增交易；通过后可开启，并可随时无条件关闭。
- 买入计划绑定当前模型时可进入 `PREPARED`。
- 旧模型买入计划在另一个模型激活后被拒绝。
- 新增交易关闭且没有 `ACTIVE` 模型时，卖出计划仍可进入 `SUBMITTING`。
- 卖出计划在提交前重新查询可卖数量；计划数量超过最新券商事实时不创建订单意图。
- 买入计划在提交前重新检查单持仓、权限、最新价格、可用资金和预计费用。
- 默认 5000 元小资金上限参与券商事实门禁。
- 当前 `ACTIVE` 模型存在较旧但仍新鲜的预测时，不会被其他模型较新的预测批次误阻断。
- 历史委托第 12 列股东账号和第 13 列市场可正确解析，且委托对象输出不会显示股东账号。
- 深 A 委托会选择 `SZACCOUNT` 和深圳股东账号；缺少目标市场账号时在 Action 110 前失败关闭。
- 同一进程重复提交时复用股东账号缓存，不重复拉取历史委托。

测试结果为 `stage10 real validation tests: passed` 和 `stage10 broker account protocol tests: passed`。Python 导入、协议字段和 SQLite 兼容列检查通过。

## 当前项目预检

在 `2026-10-30` 加载根 `.env` 后执行准入命令，结果为 `BLOCKED`，且 `brokerHttpAttempted=false`：

```text
Qlib 日历未覆盖 2026-10-30
没有 ACTIVE 模型
```

本地准入按设计在模型和日历门禁处停止，没有在该命令中重复连接券商。

## 下单参数证据

本阶段没有把登录资金账号当作股东账号使用。两类账号用途不同：`ZXBROKER_ACCOUNT` 仅用于登录；Action 110 下单还要求沪 A 或深 A 对应的 `WTAccount` 和 `WTAccountType`。

- 根 `.env` 对应 MySQL 的 `broker_order` 共 383 条，日期范围为 `2023-05-17` 至 `2024-12-05`，覆盖 170 只股票。历史事实稳定表明 `000/001/002/003` 属于深圳，`600/601/603/605` 属于上海，但该表没有保存原始下单请求或股东账号。
- 券商官方 H5 `service_stock.js` 的 Action 110 明确发送 `isformat`、`PriceType`、`Direction`、`StockCode`、`Price`、`Volume`、`WTAccount`、`WTAccountType` 和 `ProPrice`。
- 真实行情响应确认 `000001` 返回 `SZACCOUNT`，`600000` 返回 `SHACCOUNT`。
- Action 5018 历史委托返回 1470 条记录，并在第 12、13 列提供两套不同的股东账号及“上海/深圳”市场。股东账号只在当前 Python 进程内按市场缓存，不写日志、文档、SQLite 或 MySQL；委托对象的 `repr` 也明确隐藏该字段。
- 券商不接受从 `2000-01-01` 一次查询至今的超长历史窗口，因此实现从当前日期向前按两年窗口回溯，找到本次股票所需市场账号后立即停止。

## 真实券商验证结果

同日使用 `ZXBROKER_ENABLED=true` 和 `ZXBROKER_CAPTCHA_AUTO_SUBMIT_ENABLED=true` 启动单次 Python 进程，账号、加密密码、签名、完整性参数和百度 OCR 凭据均直接读取根 `.env`：

- 百度 OCR 原图和 6 倍放大图结果一致后，真实券商验证码登录成功。
- Token 在同一 Python 进程内按 30 分钟有效期复用，没有写入日志、SQLite 或版本库。
- 账户同步成功：可用资金 0.02 元、总资产 0.02 元、持仓市值 0、冻结资金 0。
- 当前持仓 0、当日委托 0、当日成交 0。

在用户明确授权后，第一次执行 `000001`、100 股、限价 0.01 元探测时，旧请求因缺少股东账号市场参数被拒绝：

```text
ERRORNO=-410011
ERRORCODE=-100084
ERRORMESSAGE=输入错误：交易所不允许为空
```

按官方 H5 和真实历史委托证据补齐参数后，再次执行相同探测。券商返回：

```text
ERRORNO=-410011
ERRORMESSAGE=[-1020]交易中心当前时间不允许委托
```

错误已经从“交易所为空”推进到券商交易时间门禁，证明 `WTAccount`、`WTAccountType` 及相关 Action 110 字段被协议接受。接口仍未返回委托号，当日委托查询为 0，因此没有成交，也不需要撤单。本次只验证了请求契约进入交易时间校验，不能等同于成功委托或完整实盘验证。

## 未完成证据

- 已完成真实账户、持仓、当日委托和成交的空账户核对；收盘后五档响应未提供独立完整档位字段。
- 尚未验证 Token 到期重新登录、成功委托、真实状态文案延迟、撤单时序和部分成交事实。
- Action 110 股东账号和市场类型缺失已修复，并真实到达券商交易时间门禁；尚未在合法交易时段获得成功委托号。
- 尚未验证真实撤单、部分成交、成交回写，也未产生阶段 09 的真实绩效样本。
- 当前阶段证据等级为 `SOURCE_IMPLEMENTED`，不能标记为 `RUNTIME_VERIFIED` 或 `PRODUCTION_VERIFIED`。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

用户已明确决定继续阶段 11，并自行在后续合法交易时段验证成功委托、撤单和成交终态恢复。阶段 10 的未完成证据保留，但不再作为旧系统源码删除的阻断条件；替换结果见阶段 11 报告。
