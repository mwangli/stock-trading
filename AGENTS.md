<!-- AI_GENERATE_START-------------------------------------------------------------------------- -->
# AGENTS.md - 只读股票交易控制台

## 交互与执行

- 面向用户使用中文，代码、命令和协议字段保留英文。
- 默认只修改源码和文档并进行静态检查；未经当前请求明确授权，不执行 Maven/npm 编译、构建、测试、启动、Docker、部署或浏览器验证。
- 默认保留未提交差异；未经当前请求明确授权，禁止 `git commit`、`git push` 和发布。
- 不触碰其他工作区或覆盖来源不明的变更。

## 架构边界

- `master` 只保留 `stock-server` 单体后端和 `stock-web` 单体前端。
- Python `stock-trading` 分支是行情、模型、交易、绩效和任务状态的唯一权威实现。
- Java 只能只读访问 `trading.db`、Parquet、日志和 Markdown 文档，不得调用 Python CLI、券商或训练流程。
- Java 不得写入、迁移或修复 `trading.db`，也不写本地状态文件；Agent 会话和消息仅保存在 JVM `HttpSession`，退出、超时或服务重启后清理。
- 不重新引入 MySQL、MongoDB、Flyway、JPA、Quartz、DJL、PyTorch、TA4J、Tushare、券商 SDK、向量库、本地 Embedding 或多 Provider Agent。

## 后端规范

- 根 `pom.xml` 只聚合 `stock-server` 与 `stock-web`；唯一后端启动类是 `com.stock.console.StockConsoleApplication`。
- SQLite 使用 `mode=ro`、`query_only` 和 `busy_timeout` 读取 `console_v_*`。
- Parquet 通过 DuckDB JDBC 查询；查询参数必须校验，不接受任意文件路径。
- 日志接口只读取配置中的固定文件；文档接口只访问配置中的固定根目录。
- 认证账号和 BCrypt 密码哈希来自环境变量，不建立用户表。
- Agent 工具全部只读，回答必须带来源，不生成可审批交易计划。

## 前端规范

- 页面只保留概览、行情、交易记录、模型与预测、Agent、运行状态与日志、文档工作台。
- 默认简体中文，不恢复 i18n、菜单拖拽或旧路由兼容。
- 不提供训练、任务控制、券商同步、交易审批和参数修改按钮。
- 主题等个人偏好仅存 `localStorage`。

## 文件与验证

- 修改文本文件优先使用 `apply_patch`；Windows UTF-8 多行补丁失败时立即切换 Git Bash heredoc 或可靠的无 BOM UTF-8 写入。
- Java、TypeScript、CSS、Markdown 等 AI 修改文件维护首尾 `AI_GENERATE_START/END` 并递增计数；JSON、YAML、XML、TOML、锁文件等格式敏感文件不加标记。
- 汇报必须区分源码检查、编译、测试、运行和生产验证。
<!-- AI_GENERATE_END-------------------------------------------------------------------------- -->
