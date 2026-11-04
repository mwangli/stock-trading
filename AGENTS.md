<!-- AI_GENERATE_START------------------------------------------------------------------------------ -->
# AGENTS.md - 只读股票交易控制台

##
优先使用 cmd.exe 不使用 powershell

## 设计原则
- 项目追求最简设计原则，在满足基本需求的情况下，不过度设计，不引入不必要的技术框架
- 不过分做防御性编程动作，不写冗余代码，要求代码思路简洁清晰


## 代码规范
- 后端代码必要低耦合，方法单一远测，减少冗余代码
- 前端代码必须使用组件化思想，统一风格和样式

## 交互与执行
- 不需要每次修改都来回调整REDADME,这个文档以后单独维护，代码变更的时候忽略
- 面向用户使用中文，代码、命令和协议字段保留英文。
- 默认只修改源码和文档并进行静态检查；未经当前请求明确授权，不执行 Maven/npm 编译、构建、测试、启动、Docker、部署或浏览器验证。
- 每次代码、配置或文档修改完成且源码检查通过后，默认执行 `git commit` 和 `git push`；提交只包含本次任务及用户明确要求一并提交的变更，不覆盖来源不明的修改。
- `git commit`、`git push` 与发布是独立操作。发布、部署、打标签、启动生产流水线或任何生产环境变更，必须获得用户在当前请求中的明确授权；提交推送不代表已授权发布。
- 如果目标分支的普通推送会自动触发发布，在未获得发布授权时必须使用不会触发发布的安全推送方式，例如带 `[skip ci]` 的提交或非发布分支，并在推送后确认发布流水线未执行，禁止为了完成推送而擅自发布。
- 不触碰其他工作区或覆盖来源不明的变更。

## 架构边界

- `master` 只保留 `stock-server` 单体后端和 `stock-web` 单体前端。
- Python `stock-trading` 分支是行情、模型、交易、绩效和任务状态的唯一权威实现。
- Java 只能只读访问 `trading.db`、Parquet、日志和 Markdown 文档，不得调用 Python CLI、券商或训练流程。
- Java 不得写入、迁移或修复 `trading.db`；只允许在独立 `agent.db` 中保存 Agent 会话、长期记忆、工具审计和开发任务状态，不得把 Agent 状态混入交易数据库。
- 不重新引入 MySQL、MongoDB、Flyway、JPA、Quartz、DJL、PyTorch、TA4J、Tushare、券商 SDK、向量库、本地 Embedding 或多 Provider Agent。

## 后端规范

- 根 `pom.xml` 只聚合 `stock-server` 与 `stock-web`；唯一后端启动类是 `com.stock.console.StockConsoleApplication`。
- SQLite 使用 `mode=ro`、`query_only` 和 `busy_timeout` 读取 `console_v_*`。
- Parquet 通过 DuckDB JDBC 查询；查询参数必须校验，不接受任意文件路径。
- 日志接口只读取配置中的固定文件；文档接口只访问配置中的固定根目录。
- 认证账号和 BCrypt 密码哈希来自环境变量，不建立用户表。
- 行情、交易、模型、日志和文档 Agent 工具保持只读，回答必须带来源，不生成可审批交易计划。
- GitHub 开发 Agent 是唯一写能力，只允许写配置的代码仓库和目标分支；不得访问交易数据库、券商接口或 Python CLI。
- GitHub 开发 Agent 不得修改 `.github/workflows/**`，提交前必须校验目标分支 HEAD，禁止强推和覆盖并发提交。

## 前端规范

- 页面只保留概览、行情、交易记录、模型与预测、Agent、运行状态与日志、文档工作台。
- 默认简体中文，不恢复 i18n、菜单拖拽或旧路由兼容。
- 不提供训练、任务控制、券商同步、交易审批和参数修改按钮。
- 主题等个人偏好仅存 `localStorage`。

## 文件与验证

- 修改文本文件优先使用 `apply_patch`；Windows UTF-8 多行补丁失败时立即切换 Git Bash heredoc 或可靠的无 BOM UTF-8 写入。
- Java、TypeScript、CSS、Markdown 等 AI 修改文件维护首尾 `AI_GENERATE_START/END` 并递增计数；JSON、YAML、XML、TOML、锁文件等格式敏感文件不加标记。
- 汇报必须区分源码检查、编译、测试、运行和生产验证。
<!-- AI_GENERATE_END------------------------------------------------------------------------------ -->
