<!-- AI_GENERATE_START - -->
# 运行与故障诊断

## 数据不可用

先查看概览和运行状态页的契约信息，再依次核对：

1. `trading.db` 是否存在且控制台容器可读。
2. `console_contract_version` 是否为 `1`。
3. `contract.json` 和 `instruments.parquet` 是否存在。
4. `asOfDate` 是否符合预期。
5. Python 最近任务是否成功完成。

控制台不承担修复动作。数据修复、重跑和恢复只能在 Python 工程中执行。

## 日志

日志页面只能读取 `STOCK_TASK_LOG` 指定的固定文件。API 不接受文件路径参数，避免目录穿越和任意文件读取。

## Agent

Agent 未配置 Provider 时仍可创建会话，但只返回“Provider 未配置”的明确提示。Agent 回答引用 SQLite 事实和 Markdown 路径；不得将回答描述为已执行交易、训练、调度或修复。

## 证据等级

- 源码检查：只说明接口、引用和配置静态一致。
- 编译检查：只说明制品可构建。
- 运行检查：说明进程和本地数据读取可用。
- 生产检查：需要实际部署、真实挂载和 Python 并发写入证据。
<!-- AI_GENERATE_END - -->
