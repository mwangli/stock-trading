<!-- AI_GENERATE_START----------------- -->
# stock-trading

面向 A 股的纯 Python T+1 自动交易验证系统。仓库只保留 BaoStock、Qlib、LightGBM、Parquet、SQLite 和 ZX 券商 HTTP 契约，不再包含 Java、React、Quartz、Agent、MySQL 或 MongoDB 运行链路。

## 交易链路

```text
D-1 收盘后：更新数据并生成 Top 5
D 日下午：从可交易 Top 5 中买入一只
D+1 日上午：卖出全部可卖持仓
D+1 收盘后：同步券商事实、绩效和模型评估
```

当前基线模型固定为 `Alpha158 + LightGBM Regression`。模型负责横截面排名，确定性代码根据资金、持仓、价格范围和 T+1 规则生成订单；券商最终判断订单是否接受，本地只保证同一 `clientOrderId` 不重复提交并记录返回结果。

## 目录

```text
stock-trading4/
├── stock-trading/          # 唯一应用，CLI + SQLite
├── documents/              # ADR 和阶段证据
├── .tmp/                   # 临时验证文件，不提交
├── .env                    # 本地敏感配置，不提交
└── README.md
```

运行数据位于 `stock-trading/var/`：

- `market/`：Parquet 原始行情。
- `qlib/`：Qlib 日历、股票池和特征数据。
- `artifacts/`：模型、预测和回测制品。
- `state/trading.db`：任务、模型、订单、成交、持仓和绩效状态。

## 安装

在 `stock-trading/` 目录创建 Python 3.11-3.13 虚拟环境：

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -e ".[research]"
```

Linux 使用 `.venv/bin/python`。

## 关键命令

```bash
# 状态与恢复
stock-trading show-state
stock-trading recover-state
stock-trading recover-broker-orders --date YYYY-MM-DD

# 数据、训练与预测
stock-trading sync-daily --date YYYY-MM-DD
stock-trading build-qlib --codes sh.600000 sz.000001
stock-trading train-baseline --train-start YYYY-MM-DD --train-end YYYY-MM-DD \
  --valid-start YYYY-MM-DD --valid-end YYYY-MM-DD \
  --test-start YYYY-MM-DD --test-end YYYY-MM-DD
stock-trading predict-latest --date YYYY-MM-DD

# 操作系统调度调用的无人值守窗口
stock-trading run-morning-trading --date YYYY-MM-DD
stock-trading run-afternoon-trading --date YYYY-MM-DD
stock-trading update-live-performance --date YYYY-MM-DD
```

上午/下午入口只生成幂等计划并提交。券商返回委托号时记录为 `SUBMITTED`，明确拒绝时记录为 `REJECTED`，通信结果不确定时记录为 `UNKNOWN`；订单终态由独立恢复命令或收盘后同步更新，本地不再设置真实交易开关或准入门禁。

## 调度

应用不内置 Quartz 或其他调度框架。生产环境使用 `cron` 或 `systemd timer` 在北京时间触发 CLI：

- 交易日前收盘后更新行情并生成预测。
- 交易日开盘后调用上午卖出入口。
- 交易日下午调用买入入口。
- 收盘后同步绩效并执行滚动模型评估。

## 当前证据

阶段 1-10 已完成数据、模型、回测、SQLite、券商协议、生产推理、影子计划、执行恢复、绩效评估和真实准入源码闭环。真实 Action 110 已进入券商交易时间校验，但成功委托、真实撤单和成交终态恢复由后续合法交易时段验证。

- [架构决策](documents/decisions/adr-002-python-t1-minimal-rebuild.md)
- [阶段 10：真实验证准入](documents/reports/stage-10-real-validation-readiness.md)
- [阶段 11：纯 Python 替换收口](documents/reports/stage-11-python-only-cutover.md)
- [阶段 12：券商结果直记](documents/reports/stage-12-broker-result-recording.md)

`SOURCE_IMPLEMENTED` 只表示源码和文件层面完成，不代表测试、运行或真实交易验证通过。
<!-- AI_GENERATE_END----------------- -->
