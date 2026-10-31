<!-- AI_GENERATE_START --- -->
# 部署说明

## 进程

宿主机运行 Python CLI 和 Cron；Docker Compose 只运行 `stock-backend` 与 `stock-frontend`。

## 挂载

- `STOCK_TRADING_VAR_DIR` -> `/data/stock-trading`，只读。
- `STOCK_TRADING_LOG_DIR` -> `/var/log/stock-trading`，只读。
- `STOCK_TRADING_DOCUMENTS_DIR` -> `/data/stock-trading-documents`，只读。
- `./documents` -> `/app/documents`，只读。

后端不需要可写数据卷。Agent 对话保存在 JVM `HttpSession` 内存中，退出登录、Session 超时或服务重启后清理。

## 环境变量

Docker Compose 固定启用 `prod` profile。必填：`CONSOLE_PASSWORD_HASH`、`STOCK_TRADING_VAR_DIR`、`STOCK_TRADING_LOG_DIR`、`STOCK_TRADING_DOCUMENTS_DIR`。

本地直接启动后端时默认启用 `local` profile，开发账号为 `admin`，密码为 `admin123`；本地也可通过 `CONSOLE_USERNAME` 和 `CONSOLE_PASSWORD_HASH` 覆盖。生产环境不得启用 `local` profile 或使用默认开发密码。

可选：`CONSOLE_USERNAME`、`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`、`AGENT_TIMEOUT_SECONDS`、`AGENT_MAX_TOKENS`。

部署环境不再需要 MySQL、Mongo、Flyway、模型文件、Tushare Token 或券商账号密码。

单实例部署无需额外会话组件。若未来扩展为多个后端实例，需要保持同一用户的 Session 粘滞到同一实例，否则不同实例之间不会共享 Agent 对话。

## 下线旧系统

旧 MySQL 和 Mongo 数据只归档，不迁入新控制台。切换前应保留数据库备份、旧镜像标识和最终运行日志；确认新页面均从 Python 契约读取后，再停止旧数据库容器。
<!-- AI_GENERATE_END --- -->
