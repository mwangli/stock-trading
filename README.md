<!-- AI_GENERATE_START------------- -->
# stock-console

`stock-web` 分支是纯 Python T+1 交易系统的只读 Web 外壳，与核心交易代码共用同一个代码库。核心行情采集、模型训练、预测、交易执行、状态恢复和绩效计算位于 `stock-trading` 分支；两个分支共享同一套 `var/market` Parquet 和 `var/state/trading.db` 数据结构。

## 工程结构

```text
stock-trading4/
├── pom.xml             # 聚合 stock-server
├── stock-server/       # 单一 Spring Boot 后端 Maven 模块
├── stock-client/       # React + Vite 前端，由 Docker 独立构建
├── documents/          # 控制台架构、契约与运维文档
└── docker-compose.yml  # 仅前端和后端两个服务
```

根 `pom.xml` 仅聚合 `stock-server`；前端由 `stock-client/Dockerfile` 使用 npm/Vite 独立构建。

## 系统边界

```text
Host Cron -> Python CLI -> Parquet + trading.db + logs
                              ↑ read-only
                         Spring Boot
                              ↑
                         React/Nginx
```

- Java 通过 DuckDB 直接只读查询 Python 生成的 SQLite 业务表、Parquet 和日志。
- Java 不调用券商、不训练模型、不生成或执行交易计划、不触发 Python CLI。
- Java 不对 `trading.db` 建表、迁移、修复或更新状态。
- Java 不写数据库或本地状态文件；Agent 会话和消息仅保存在当前登录的 JVM `HttpSession` 内存中。
- 退出登录、Session 超时或后端重启后对话会清空；PC 与手机的不同登录 Session 不同步。
- Agent 仅使用 DeepSeek-compatible Provider、SQLite 事实和 Markdown 文档，不包含交易审批、向量库、本地 Embedding 或多 Provider 路由。

## 后端

后端包位于 `com.stock.console`：

- `auth`：环境变量单用户登录和 Cookie Session。
- `facts`：DuckDB 挂载 `trading.db` 并查询现有业务表。
- `market`：DuckDB 查询 Parquet 股票目录和日 K。
- `dashboard`、`trading`、`model`：只读业务接口。
- `operations`：任务状态、固定日志文件和 SSE。
- `agent`：只读事实问答、文档检索和 Session 内对话上下文。
- `documents`：受控 Markdown 目录扫描、读取与搜索。

依赖仅保留 Spring Web、Validation、Spring Security Crypto、DuckDB JDBC、Jackson 和 LangChain4j OpenAI-compatible。Java 不引入独立 SQLite JDBC 写入链路。

## 前端

前端固定保留七个页面：概览、行情、交易记录、模型与预测、Agent、运行状态与日志、文档工作台。默认简体中文，主题偏好保存在浏览器 `localStorage`。

已移除旧新闻、情绪模型、训练弹窗、任务控制、券商同步、交易审批、菜单拖拽、双语、WebSocket 日志和旧路由兼容。

## 数据读取

- `var/state/trading.db` 由 DuckDB 以 SQLite 只读附件方式打开，控制台只执行固定 `SELECT`。
- `var/market/instruments/stocks.parquet` 提供代码、名称、交易状态和数据日期。
- 历史日线位于 `var/market/daily_history/code=<code>/bars.parquet`。
- 5 分钟行情位于 `var/market/minute_5m/code=<code>/bars.parquet`。

控制台不要求 Python 额外维护控制台专用视图、元数据表或文件清单。文件缺失、表不存在或不可读时，服务保持启动，相关页面显示具体数据源不可用原因。

## 配置

本地直接启动后端时默认启用 `local` profile，登录账号默认为 `admin`，但密码哈希仍须通过 `CONSOLE_PASSWORD_HASH` 显式提供。可通过 `CONSOLE_USERNAME` 修改账号，不在源码或配置中保存明文开发密码。

生产部署至少需要：

```text
CONSOLE_PASSWORD_HASH=<BCrypt hash>
STOCK_TRADING_VAR_DIR=/srv/stock-trading/var
STOCK_TRADING_LOG_DIR=/var/log/stock-trading
STOCK_TRADING_DOCUMENTS_DIR=/srv/stock-trading
```

Docker Compose 固定启用 `prod` profile，不使用本地默认密码。Agent 可选配置：`DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL`、`AGENT_TIMEOUT_SECONDS`、`AGENT_MAX_TOKENS`。生产账号默认为 `admin`，可通过 `CONSOLE_USERNAME` 修改。

Agent 对话最多保留 20 个会话，每个会话最多保留 50 条消息；对话只存在于单个登录 Session 内，不需要独立数据目录。

本地开发可通过 Nginx Basic Auth 直接读取远程 SQLite 和 Parquet：

```text
STOCK_REMOTE_DATA_URL=http://117.72.216.67/dev-data
STOCK_REMOTE_DATA_USERNAME=developer
STOCK_REMOTE_DATA_PASSWORD=<独立只读密码>
```

未配置 `STOCK_REMOTE_DATA_URL` 时，后端继续读取 `STOCK_TRADING_DB` 和 `STOCK_MARKET_DIR` 指定的本地文件。远程模式通过 DuckDB HTTPFS 读取 `/sqlite/trading.db` 与 `/market/**`，只执行既有固定查询。

## API

- `/api/auth/*`
- `/api/dashboard/overview`
- `/api/market/status|instruments|bars`
- `/api/trading/orders|fills|positions`
- `/api/models/overview|predictions|evaluations`
- `/api/operations/status|logs|logs/stream`
- `/api/agent/status|sessions|chat`
- `/api/documents/*`

不存在训练、预测重试、任务触发、暂停恢复、券商同步、交易审批或运行参数修改接口。

## 验证边界

默认只执行源码、引用、依赖、编码和差异检查。未获得当次明确授权时，不执行 Maven/npm 编译、测试、启动、Docker 构建或浏览器验证；源码检查结果不得描述为编译或运行通过。
<!-- AI_GENERATE_END------------- -->
