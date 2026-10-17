<!-- AI_GENERATE_START --------------------- -->
# AI 股票预测与交易决策系统

> 基于 Java、DJL/PyTorch、LSTM 和财经新闻情感分析的 A 股数据分析、预测、策略评估与交易执行平台。

## 在线体验

| 项目 | 信息 |
|---|---|
| 在线演示 | **[AI 股票交易系统](http://117.72.216.67)** |
| 测试账号 | `test` |
| 测试密码 | `123456` |
| 使用说明 | 测试账号仅用于演示页面和模型能力，不代表真实交易授权 |

> 演示环境可能受数据采集、模型状态和服务器资源影响。请勿在公开环境中使用真实券商密码、Token 或其他敏感凭据。真实委托能力默认受运行开关和风控门禁保护。

## 项目简介

这是一个前后端分离的 AI 股票交易决策系统，围绕“数据采集 -> 模型预测 -> 智能决策 -> 交易执行”建立完整业务链路。

系统重点解决以下问题：

- 将行情、新闻、交易日历和可交易性事实统一沉淀，支持数据质量审计。
- 使用 LSTM 对股票历史序列进行次日涨跌预测，并保存训练数据快照、模型版本和推理结果。
- 使用财经新闻情感模型提取市场情绪，将情绪与 LSTM 预测融合为候选评分。
- 使用策略模式、技术指标、回测和运行状态对候选进行进一步筛选。
- 在交易执行前重新校验行情、账户、持仓、交易日历、T+1 和风控条件。
- 使用统一 Quartz 任务中心管理数据同步、预测、选股、训练、回测和交易任务。

## 核心能力

### 数据与快照

- 股票基础资料、日线行情、分钟行情和财经新闻采集。
- 交易日历、停牌、涨跌停和数据完整性校验。
- 按股票流式生成训练样本，并分块写入版本化数据快照。
- 支持快照状态、样本数量、数据日期范围和失败原因查询。

### AI 模型

- Java 17 + DJL API + PyTorch Engine 统一承载训练和推理。
- LSTM 模型支持多股票共享训练、行业特征和市场参考序列特征。
- 财经新闻情感模型支持本地 Hugging Face PyTorch 制品加载和预测。
- 模型版本、训练记录、预测批次、评估指标和激活指针分离管理。
- 训练产物可回滚，候选模型不会自动覆盖当前生产模型。

### 智能决策

- LSTM 预测与新闻情感双因子综合评分。
- 保守、均衡、激进三种策略模式。
- 移动止损、RSI、成交量和布林带等技术指标。
- 选股排名、交易信号、候选有效期和失效原因管理。
- 日线基准回测、策略状态和切换审计。
- 支持基于市场参考序列的规则自动切换，默认关闭并带冷却保护。

### 交易执行

- 交易前风险门禁、账户和持仓快照校验。
- T+1 可卖数量校验、涨跌停和停牌校验。
- 券商适配、委托提交、订单回查、成交和持仓同步。
- 真实交易写入开关与模型预测、智能决策能力独立控制。
- 敏感凭据仅通过环境变量或受控部署配置管理。

### 运维与可观测性

- WebSocket 实时查看后端日志和关键任务进度。
- Quartz 任务统一查询、触发、暂停、恢复和停止。
- 模型训练、预测、快照、选股和回测状态可追踪。
- 前端提供模型运维、情感模型、策略配置、任务管理和系统配置页面。

## 系统架构

```text
┌──────────────────┐
│ React + Vite 前端 │
└────────┬─────────┘
         │ HTTP / WebSocket
┌────────▼─────────────────────────────────────────┐
│ Java Spring Boot 单体后端                         │
│                                                   │
│  dataCollector                                    │
│      ↓                                            │
│  modelService                                      │
│      ↓                                            │
│  strategyAgent                                     │
│      ↓                                            │
│  tradingExecutor                                   │
│      ↓                                            │
│  券商适配与交易执行                                │
└───────┬───────────────────────┬───────────────────┘
        │                       │
   MySQL / Quartz          MongoDB / 模型制品
```

### 模块边界

| 模块 | 后端包 | 主要职责 |
|---|---|---|
| 数据采集 | `com.stock.dataCollector` | 股票资料、行情、新闻、交易日历和数据质量 |
| 模型服务 | `com.stock.modelService` | 数据快照、LSTM 训练、情感推理、模型版本、预测和评估 |
| 智能决策 | `com.stock.strategyAgent` | 确定性策略、预测融合、选股排名、技术指标、回测、Agent 对话和交易计划决策 |
| 交易执行 | `com.stock.tradingExecutor` | 认证、运行开关、Quartz、风控、券商、订单和审计 |

模块之间通过 DTO、不可变快照和明确的领域结果协作，不直接共享持久化 Entity。

## 技术栈

### 后端

| 组件 | 技术 |
|---|---|
| 语言与框架 | Java 17、Spring Boot 3.2.x |
| 数据访问 | Spring Data JPA、Spring Data MongoDB |
| 关系数据库 | MySQL 8 |
| 文档数据库 | MongoDB 8 |
| AI 推理与训练 | DJL 0.31.1、PyTorch Engine 2.5.1 |
| 技术分析 | TA4J 0.15 |
| 任务调度 | Quartz JDBC 持久化调度 |
| 接口文档 | Springdoc OpenAPI |

### 前端

| 组件 | 技术 |
|---|---|
| 框架 | React 19 |
| 构建工具 | Vite 7 |
| 语言 | TypeScript 5 |
| UI | Ant Design、TailwindCSS |
| 状态管理 | Zustand |
| 图表 | ECharts / Recharts |

## 项目结构

```text
stock-trading4/
├── stock-frontend/                 # React + Vite 前端
├── stock-backend/                  # Spring Boot、模型、策略和交易执行
│   └── data/                       # 本地模型制品和训练产物（默认不纳入 Git）
│       ├── lstm-artifacts/         # LSTM 训练快照与参数制品
│       └── models/sentiment/       # 版本化情感模型制品
├── docs/                           # 需求、设计、部署和专题文档
├── docker-compose.yml              # 本地和部署编排
├── .env.example                    # 环境变量示例，不包含真实密钥
├── AGENTS.md                       # 项目开发与协作规范
└── README.md                       # 项目介绍和快速开始
```

## 快速开始

### 环境要求

- Java 17+
- Maven 3.8+
- Node.js 20.19+ 或 22.12+
- Docker Desktop 与 Docker Compose

### 本地启动数据库

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d stock-mysql stock-mongo
```

### 启动后端

```bash
cd stock-backend
mvn spring-boot:run
```

后端默认地址：`http://localhost:8080`

### 启动前端开发服务器

```bash
cd stock-frontend
npm install
npm run dev
```

前端默认地址：`http://localhost:5173`

### Docker 启动完整服务

```powershell
Copy-Item .env.example .env
docker compose --env-file .env up -d --build
docker compose --env-file .env ps
docker compose --env-file .env logs -f stock-backend
```

完整部署前请配置数据库、模型制品、新闻数据源和券商相关环境变量。不要把 `.env`、密码、Token、私钥或模型密钥提交到 Git 仓库。

模型目录约定：LSTM 训练产物位于 `stock-backend/data/lstm-artifacts`，情感模型版本位于 `stock-backend/data/models/sentiment/<version>`。Docker Compose 默认将情感模型目录只读挂载到容器内的 `/models/sentiment`。

## 访问与接口

启动后端后可以访问：

- Swagger UI：`http://localhost:8080/swagger-ui.html`
- OpenAPI：`http://localhost:8080/v3/api-docs`
- 登录接口：`POST /api/auth/login`
- 模型运维：`/api/modelOperations`
- 预测批次：`/api/modelPredictions`
- 策略配置：`/api/strategy/config`
- 策略状态：`/api/strategy/state`
- 自动切换：`POST /api/strategy/autoSwitch?enabled=true`
- 任务管理：`/api/jobs`

所有业务接口均需要遵守当前认证和运行开关约束。策略模块只生成候选和信号，不能绕过 `tradingExecutor` 直接调用券商写接口。

## 页面导航

- **数据总览**：查看账户、行情、模型和任务等核心运行概览。
- **市场行情**：查看市场行情、K 线和股票价格走势。
- **股票信息**：查询股票基础资料和数据状态。
- **历史订单**：查询券商历史委托记录。
- **交易记录**：查看系统交易记录和收益信息。
- **运行日志**：实时查看后端运行日志和任务输出。
- **涨跌预测**：管理 LSTM 数据快照、训练、模型版本、激活、预测和效果评估。
- **情感模型**：查看情感模型制品状态并执行新闻文本分析测试。
- **策略配置**：配置选股权重、技术指标、策略模式和自动切换。
- **任务管理**：查看、触发、暂停、恢复和停止 Quartz 任务。
- **系统设置**：管理数据采集、模型训练、通知和调度等平台运行开关。

## 任务与模型运行原则

- 所有后台任务通过 `JobConfig + JobSchedulerService` 接入 Quartz，业务类不使用 `@Scheduled`。
- LSTM 训练默认关闭，启用训练前必须确认数据快照完整、样本数量、资源余量和参数范围。
- 训练成功只产生候选模型版本，模型激活必须单独执行。
- 预测目标日必须使用目标交易日前一个有效交易日的数据。
- 自动切换任务默认关闭，启用后按市场参考序列评估，并受 24 小时冷却时间保护。
- 回测、模拟候选、真实成交三类结果分开记录，不能将回测收益直接当作实盘收益。

## 开发与验证

后端编译：

```powershell
cd stock-backend
$env:JAVA_HOME="C:\Program Files\Java\jdk-17"
mvn compile -DskipTests
```

前端构建：

```bash
cd stock-frontend
npm run build
```

项目当前以代码审查、文档审查、构建检查和目标环境手工验证为主。构建成功只能证明源码可构建，不能替代真实数据库、模型推理、券商登录或生产任务验证。

## 文档中心

- [文档索引](docs/README.md)
- [系统架构需求](docs/四模块总体架构需求.md)
- [系统架构设计](docs/四模块总体架构设计.md)
- [数据采集需求](docs/01-数据采集/需求.md)
- [数据采集设计](docs/01-数据采集/设计.md)
- [模型服务需求](docs/02-模型服务/需求.md)
- [模型服务设计](docs/02-模型服务/设计.md)
- [智能决策需求](docs/03-智能决策/需求.md)
- [智能决策设计](docs/03-智能决策/设计.md)
- [交易执行需求](docs/04-交易执行/需求.md)
- [交易执行设计](docs/04-交易执行/设计.md)

需求文档只描述业务背景、目标和验收标准；设计文档描述模块边界、技术选型、数据流和关键实现方式。

## 部署与安全说明

- 生产部署使用 Docker Compose 和不可变镜像标签。
- 数据库、模型制品和日志目录应使用持久化卷。
- 大体积情感模型制品保存在文件存储或模型制品仓库，不写入 MongoDB 文档。
- 真实交易写入、券商凭据和 OCR 密钥必须通过受控环境变量注入。
- 公开演示账号只能用于测试，部署到个人或生产环境后应立即修改默认密码或关闭默认账号。
- 生产环境的真实账户登录、委托、订单回查和模型效果必须单独完成验证，不能仅凭 README 或构建结果确认。

## 免责声明

本项目用于技术研究、数据分析和交易决策辅助，不构成投资建议，也不保证收益。股票市场存在本金损失风险。使用者应自行承担数据源、模型误差、网络故障、交易执行和投资决策风险。

## 许可证

MIT License

## 反馈与贡献

欢迎通过 GitHub Issue 提交问题、改进建议和使用反馈。涉及真实交易、模型制品或敏感配置的问题，请先脱敏后再公开提交。
<!-- AI_GENERATE_END --------------------- -->
