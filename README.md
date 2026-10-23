<!-- AI_GENERATE_START--------------------------------- -->
# AI-AGENT 智能股票交易系统

面向 A 股市场的数据采集、模型预测、策略分析、Agent 决策增强、风险控制和自动交易平台。

系统采用 **三模块架构**：`stock-backend` 承载核心业务与交易安全边界，`stock-agent` 承载独立 Agent 能力，`stock-frontend` 承载独立 Web 应用。Java 后端始终保留事实校验、硬风险和券商执行权。

## 核心能力

- 股票资料、日线、分钟线、新闻、交易日历和可交易状态维护。
- Java DJL 与 PyTorch Engine 承载 LSTM 训练、推理和财经情感分析。
- 确定性选股、技术指标、信号生成、策略状态和历史回测。
- Agent 研究、诊断、RAG、Memory、交易计划和受控候选判断。
- 账户、持仓、订单、成交、T+1、风险门禁和券商委托。
- Quartz 统一任务管理、执行记录和跨模块业务编排。
- React 管理界面独立构建，通过反向代理访问 Backend 与 Agent。

## 项目结构

```text
stock-trading4/
├── stock-backend/           # Spring Boot 后端，内部按业务包组织
├── stock-agent/             # Agent、RAG、Memory 和决策增强
├── stock-frontend/          # React + Vite 独立前端
├── model-artifacts/         # 固定模型制品
├── documents/               # 长期架构和模块文档
├── docker-compose.yml
├── pom.xml                  # 三模块聚合与统一版本管理
└── AGENTS.md
```

## 模块关系

```text
stock-frontend → HTTP API → stock-backend
stock-frontend → HTTP API → stock-agent
stock-backend  → 内部交易门禁 API → stock-agent
stock-agent    → 内部事实 API → stock-backend
```

根 `pom.xml` 只聚合 `stock-backend`、`stock-agent`、`stock-frontend` 并管理统一依赖版本。Backend 内部的 `base`、`data`、`model`、`strategy`、`trading`、`job` 和启动装配只使用包与子目录表达边界，不再拆分 Maven 模块。Agent 与 Backend 不共享 Java 实现，通过受 `X-Internal-Token` 保护的内部 HTTP 契约协作；Frontend 独立构建并由 Nginx 分流请求。

## 数据存储

| 存储 | 权威职责 |
|---|---|
| MySQL | 用户、配置、任务、模型元数据、交易状态和需要事务约束的常规业务数据 |
| MongoDB | 行情、新闻、预测明细、回测、Agent 会话/RAG 等增量、时序和大文档数据；GridFS 保存模型二进制 |
| Redis | 可重建查询缓存与 Backend/Agent 共享 Session，不作为业务事实源 |

## 交易安全边界

- 模型预测、策略信号和 Agent 建议都不是最终订单。
- 真实委托前重新检查交易日、时间、价格、账户、资金、持仓、T+1 和仓位；委托受理与最终成交状态以券商返回事实为准。
- 不在应用侧预占资金或阻止不同委托并发提交；资金不足、可卖数量不足等冲突由券商最终裁决。
- `TRADING_REAL_WRITE_ENABLED` 默认关闭。
- Agent 不持有券商凭据，不直接调用券商接口。
- Agent 的 `GATED` 模式只能减少或延迟 Java 基线允许的买入候选。
- Agent、模型或外部 Provider 不可用时，不得扩大交易权限。

## 模型制品

- Embedding 和 Sentiment 固定模型位于 `model-artifacts/`，通过 Git LFS 管理并只读加载。
- LSTM 模型由 MongoDB GridFS 和模型激活记录管理，保留候选、激活和回滚语义。
- 模型训练成功只生成候选版本，不自动替换生产激活模型。

## 运行环境

- Java 17
- Maven 3.8+
- Node.js 20.19+ 或兼容版本
- MySQL 8
- MongoDB 8
- Redis 7
- Docker 与 Docker Compose

敏感配置通过 `.env` 或运行环境注入，禁止提交真实数据库密码、Agent API Key、券商账户、密码或签名密钥。

## 文档

- [文档索引](documents/README.md)
- [系统概览](documents/00-系统架构/系统概览.md)
- [模块依赖与边界](documents/00-系统架构/模块依赖与边界.md)
- [核心业务链路](documents/00-系统架构/核心业务链路.md)
- [部署与运行](documents/00-系统架构/部署与运行.md)

模块的业务用途、实现方式、输入输出、依赖关系和降级边界统一维护在 `documents/<module>/` 下。

## 免责声明

本项目用于研究、工程实践和个人交易辅助。模型预测、回测结果、策略评分和 Agent 输出均不构成投资建议，也不能保证真实收益。真实交易必须在充分理解风险、完成独立验证并显式开启写入能力后使用。
<!-- AI_GENERATE_END--------------------------------- -->
