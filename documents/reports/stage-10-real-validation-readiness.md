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

测试结果为 `stage10 real validation tests: passed`。Python 语法和 SQLite 兼容列检查通过。

## 当前项目预检

在 `2026-10-30` 加载根 `.env` 后执行准入命令，结果为 `BLOCKED`，且 `brokerHttpAttempted=false`：

```text
Qlib 日历未覆盖 2026-10-30
没有 ACTIVE 模型
```

本地准入按设计在模型和日历门禁处停止，没有在该命令中重复连接券商。

## 真实券商验证结果

同日使用 `ZXBROKER_ENABLED=true` 和 `ZXBROKER_CAPTCHA_AUTO_SUBMIT_ENABLED=true` 启动单次 Python 进程，账号、加密密码、签名、完整性参数和百度 OCR 凭据均直接读取根 `.env`：

- 百度 OCR 原图和 6 倍放大图结果一致后，真实券商验证码登录成功。
- Token 在同一 Python 进程内按 30 分钟有效期复用，没有写入日志、SQLite 或版本库。
- 账户同步成功：可用资金 0.02 元、总资产 0.02 元、持仓市值 0、冻结资金 0。
- 当前持仓 0、当日委托 0、当日成交 0。

在用户明确授权后执行了一次真实买入接口探测：`000001`、100 股、限价 0.01 元。券商返回：

```text
ERRORNO=-410011
ERRORCODE=-100084
ERRORMESSAGE=输入错误：交易所不允许为空
```

接口未返回委托号，当日委托再次查询仍为 0，因此没有成交，也不需要撤单。当前 Java 历史版本和 Python 实现都没有发送交易所字段，真实证据表明该下单契约并未完整验证。在找到券商页面、官方客户端或成功请求的原始字段证据前，不增加猜测参数，不重复提交。

## 未完成证据

- 已完成真实账户、持仓、当日委托和成交的空账户核对；收盘后五档响应未提供独立完整档位字段。
- 尚未验证 Token 到期重新登录、成功委托、真实状态文案延迟、撤单时序和部分成交事实。
- 真实下单参数缺少有证据来源的交易所字段，当前写入链路不能进入生产。
- 尚未产生任何真实小资金成交和阶段 09 的真实绩效样本。
- 当前阶段证据等级为 `SOURCE_IMPLEMENTED`，不能标记为 `RUNTIME_VERIFIED` 或 `PRODUCTION_VERIFIED`。
- 未执行 Java Maven、React 编译、构建或启动。

## 下一阶段

阶段 11 的旧系统删除必须等待真实只读验证和至少一轮受控小资金链路完成。当前证据不足，Java、React、Quartz、Agent、MySQL 和 MongoDB 继续保留，禁止为了追求代码减少而提前删除唯一已验证的券商实现参考。
