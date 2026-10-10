<!-- AI_GENERATE_START ---------------- -->
# Stock Trading - AI 股票自动交易系统

# 项目演示

**[AI 股票交易系统 - 在线演示](http://124.220.36.95:8080/)**

---

基于 LSTM 神经网络与财经新闻情感分析的智能股票交易决策系统，支持自动化交易、实时数据分析和 AI 模型预测。

## 项目简介

这是一个采用前端与 Java 后端分工的 AI 股票交易系统：

- **后端服务** (Java Spring Boot 3.2): 使用 DJL + PyTorch Engine 完成 LSTM 训练、模型加载、在线推理、策略、风控和交易执行
- **前端应用** (React 19 + Vite 7 + Ant Design 6): 可视化 Dashboard、数据展示和操作
- **数据存储**: MySQL (业务数据) + MongoDB (行情、新闻、模型版本、指标和小型 LSTM 参数)

### 核心特性

- **双策略引擎**: 
  - **选股策略** (天级): LSTM 预测 (60%) + 情感分析 (40%) 双因子选股
  - **T+1 卖出策略** (分钟级): 基于分钟级价格走势的实时卖出决策
- **T+1 交易**: 短线交易策略，当日买入次日卖出
- **多指标决策**: 移动止损、RSI 超买、成交量背离、布林带突破
- **定时任务调度**: 每日自动更新数据、预测和分析
- **固定任务调度**: 按代码维护的固定时间执行数据、模型、选股和交易任务
- **运行日志查看**: 通过 WebSocket 查看运行日志，业务通知渠道暂不实现
- **Docker 一键部署**: 使用 Docker Compose 快速部署
- **CI/CD 自动化**: GitHub Actions 自动构建和部署
- **版本化模型制品**: Java 训练后将 LSTM 参数、配置、指标、状态和摘要保存到 MongoDB，通过独立激活指针支持回滚。

---

## 技术栈

### 后端 (Backend)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.2 |
| JDK | OpenJDK | 17 |
| ORM | Spring Data JPA | 自动建表 |
| 数据库 | MySQL / MongoDB | 8.0 / 8.0 |

| HTTP | OkHttp | 4.12 |
| 工具 | Hutool / FastJSON2 | 5.8 / 2.0 |
| AI 框架 | DJL + PyTorch Engine | DJL 0.31.1 + PyTorch 2.5.1 |
| 技术分析 | TA4J | 0.15 |



### 前端 (Frontend)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 19.x |
| 构建工具 | Vite | 7.x |
| 语言 | TypeScript | 5.x |
| 样式 | TailwindCSS | 4.x |
| 状态管理 | Zustand | 最新 |
| 路由 | React Router | 6.x |
| 图表 | ECharts / Recharts | 按需选择 |

---

## 项目结构

```text
stock-trading4/
├── frontend/                       # React + Vite 前端
├── backend/                        # Java 训练、DJL/PyTorch 推理、风控和交易
├── documents/                      # 与前后端平级的系统设计和运维文档
├── docker-compose.yml              # 在线服务、数据库和模型运行环境
├── .env.example
├── pom.xml
├── AGENTS.md
└── README.md
```

职责边界：

- `frontend/` 只负责页面展示和操作。
- `backend/` 负责数据采集、DJL 模型训练、PyTorch Engine 推理、策略、风控、调度和券商调用。
- `documents/` 保存跨端需求、架构、模型契约、部署和回滚文档。

---

## 快速开始

### 环境要求

- Java 17+
- Node.js 18+
- Maven 3.6+
- Docker & Docker Compose (可选)

### 本地开发

#### 1. 启动基础设施

```bash
# 使用 Docker Compose 启动数据库
docker compose up -d stock-mysql stock-mongo
```

#### 2. 启动后端

```bash
cd backend

# 编译并启动
mvn spring-boot:run

# 访问 API: http://localhost:8080
# 访问前端：http://localhost:8080
```

#### 3. 启动前端 (开发模式)

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 访问：http://localhost:5173
```

### Docker 部署

```bash
# 首次部署仅需填写数据库密码、券商/OCR 凭据和高风险能力门禁。
Copy-Item .env.example .env

# 一键启动所有服务
docker compose --env-file .env up -d --build

# 查看服务状态
docker compose --env-file .env ps

# 查看日志
docker compose --env-file .env logs -f

# 访问
# 前端: http://localhost:3000
# 后端 API: http://localhost:8080
```

常规运行参数已经直接写入 `docker-compose.yml` 或 `backend/src/main/resources/application.yml`。
`.env` 仅保留敏感凭据、高风险能力门禁和数据库持久化约束，避免重复维护普通配置。CI/CD 在部署命令中分别注入前端、后端不可变镜像标签，不会并发改写服务器 `.env`。

### 前后端自动部署

推送到 `master` 后，GitHub Actions 会按变更目录分别执行后端或前端流水线。两条流水线使用独立并发组，可以同时构建、推送和部署；每条流水线仅重建自己的容器。

后端改动涉及 `backend/`、根 `pom.xml` 或 `docker-compose.yml` 时：

1. 使用 Java 17 打包后端。
2. 构建并推送 `latest` 和提交 SHA 两个 ACR 镜像标签。
3. 将 Compose 配置同步到服务器。
4. 只重建 `stock-backend`，不重启 MySQL、MongoDB 或前端。
5. 等待容器健康检查；失败时自动恢复上一个后端镜像。

前端改动涉及 `frontend/` 或 `docker-compose.yml` 时：

1. 使用前端 Dockerfile 完成依赖安装和生产构建。
2. 构建并推送 `latest` 和提交 SHA 两个 ACR 镜像标签。
3. 将 Compose 配置同步到服务器。
4. 只重建 `stock-web`，不重启后端或数据库。
5. 等待容器健康检查；失败时自动恢复上一个前端镜像。

ACR 地址、命名空间、服务器地址和 SSH 主机指纹已固化在工作流配置中。仓库或 `production` Environment 只需配置：

- `ACR_USERNAME`、`ACR_PASSWORD`
- `SERVER_USER`、`SERVER_PASSWORD`、`SERVER_DEPLOY_DIR`
- 可选 `SERVER_PORT`，默认 `22`

---

## 模块架构

### 数据采集模块 (com.stock.dataCollector)

- 股票列表同步
- 实时行情采集
- 历史 K 线获取
- 财经新闻采集
- 财经新闻采集 (证券平台)


### AI 模型模块

模型能力采用“Java DJL API + PyTorch Engine”的统一架构：

- LSTM 使用原有 `StockLSTMModel`，由统一调度任务离线训练并通过 PyTorch Engine 推理。
- MongoDB 保存 LSTM 参数、训练配置、输入契约、指标、状态和 SHA-256；激活指针与版本文档分离。
- 情感模型由 DJL 加载版本化本地 Hugging Face PyTorch 制品；大体积 Transformer 权重不写入 MongoDB。
- 模型不可用或参数摘要不匹配时，真实候选生成失败关闭。

Python 和 ONNX Runtime 已移除。生产容器必须包含与 DJL 版本匹配的 PyTorch Engine、JNI 和原生 CPU 运行库；正式训练默认关闭，只能通过 `LSTM_TRAINING_ENABLED=true` 显式开启。

详细设计：

- [模型服务需求](documents/02-模型服务/需求.md)
- [模型服务设计](documents/02-模型服务/设计.md)
- [模型训练与推理架构方案评估](documents/00-系统架构/2026-10-10-模型训练与推理架构方案评估.md)

### 策略分析模块 (com.stock.strategyAnalysis)

#### 选股策略 (天级)
- 综合选股算法 (双因子模型)
- 决策引擎
- 交易信号生成
- 股票评分排名

#### T+1 卖出策略 (分钟级)
- 移动止损 (动态跟踪最高价)
- RSI 超买监控 (14 分钟周期)
- 成交量背离检测
- 布林带突破检测
- 多指标聚合决策
- 尾盘强制卖出 (14:57)

### 交易执行模块 (com.stock.tradingExecutor)

- 风控检查 (止损/仓位/熔断)
- 订单执行
- 持仓管理
- 交易记录
- 手续费计算

### 动态任务模块 (com.stock.job)

- 任务引导和初始化
- 任务状态管理
- 动态参数调整

### WebSocket 模块

- 日志实时推送
- 通知实时推送

---

---

## API 文档

启动后端后访问：

- Swagger UI: http://localhost:8080/swagger-ui.html
- API 文档：http://localhost:8080/v3/api-docs

### 核心接口

| 模块 | 路径 | 说明 |
|------|------|------|
| 股票信息 | `/api/stocks` | 获取股票列表/详情 |
| 实时行情 | `/api/prices/realtime` | 获取实时价格 |
| 历史 K 线 | `/api/prices/history` | 获取历史数据 |
| 财经新闻 | `/api/news` | 获取相关新闻 |
| 交易信号 | `/api/signals` | 获取交易信号 (买入/卖出) |
| 持仓信息 | `/api/positions` | 获取持仓数据 |
| 策略状态 | `/api/strategyAnalysis/status` | 获取策略运行状态 |

---

## 开发流程

1. **需求分析**: 更新 `docs/` 下的需求与设计文档
2. **设计评审**: 按模块维护设计文档
3. **代码实现**: 按照 AGENTS.md 规范编写代码
4. **代码审查**: 确保 lint 通过、逻辑与文档一致
5. **提交部署**: Git 提交并推送到仓库

---

## 关于测试

本项目**不维护自动化测试用例**（无单元测试、集成测试或 E2E 测试），原因如下：

1. **质量不可控**：由 AI 生成的测试多为 Mock 驱动，难以覆盖真实依赖与边界，对业务正确性保障有限。
2. **维护成本高**：测试代码需随需求与实现同步更新，在本项目中投入产出比低，不如将精力集中在实现与文档上。
3. **仍需人工审查**：即便测试通过，关键逻辑仍依赖人工审查与联调验证，自动化测试无法替代。

因此，本项目依赖**代码审查、手工验证与文档**保证质量；构建与打包时默认不运行测试（如 Maven 打包可使用 `mvn package -DskipTests`）。

---

## 部署

### Docker Compose 部署

```bash
# 生产环境部署
docker compose --env-file .env up -d

# 服务状态
docker compose --env-file .env ps

# 日志查看
docker compose --env-file .env logs -f backend
```

### 端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8080 | Spring Boot 服务 |
| 前端开发 | 5173 | Vite 开发服务器 |
| MySQL | 3306 | 数据库 |
| MongoDB | 27017 | 文档数据库 |


---

## 核心功能

### 1. 数据采集

- 使用证券平台 API 获取 A 股数据
- 定时任务自动更新

### 2. AI 预测

- LSTM 神经网络预测次日价格
- 财经新闻情感分析
- 双因子综合评分

### 3. 交易决策

#### 选股策略 (每日执行)
- 每日自动生成交易信号
- LSTM(60%) + 情感 (40%) 双因子选股
- 股票排名和评分

#### T+1 卖出策略 (每分钟执行)
- 基于分钟级价格走势实时决策
- 移动止损保护利润
- 多指标聚合 (移动止损 40% + RSI20% + 成交量 20% + 布林带 20%)
- 动态阈值调整 (根据当日收益)
- 尾盘强制卖出 (14:57)

### 4. 可视化

- Dashboard 数据展示
- K 线图可视化
- 持仓盈亏分析
- 交易记录查询
- 策略运行状态监控

---

## 策略效果指标

### T+1 卖出策略目标

| 指标 | 目标值 | 说明 |
|------|--------|------|
| 高点捕获率 | > 75% | 卖出价 / 当日最高价 |
| 胜率 | > 60% | 卖出价 > 次日开盘价 |
| 平均优化收益 | > 0.5% | 相比随机卖出的超额收益 |
| 最大连续失败 | < 5 次 | 连续卖出价低于次高价 |
| 决策延迟 | < 100ms | 分钟级决策响应时间 |

---

## 项目文档

### 开发指南

- [AGENTS.md](./AGENTS.md) - 项目开发指南 (代码规范、构建命令)

### 文档中心

所有核心需求与设计文档位于 `docs/` 目录：

- [文档索引](documents/README.md) - 文档结构和快速入口
- [00-系统架构 - 需求](documents/00-系统架构/需求.md)
- [00-系统架构 - 设计](documents/00-系统架构/设计.md)

### 模块文档

| 模块 | 需求文档 | 设计文档 | 测试文档 |
|------|----------|----------|----------|
| 数据采集 | [01-数据采集 - 需求](documents/01-数据采集/需求.md) | [01-数据采集 - 设计](documents/01-数据采集/设计.md) | - |
| AI 模型 | [02-模型服务 - 需求](documents/02-模型服务/需求.md) | [02-模型服务 - 设计](documents/02-模型服务/设计.md) | - |
| 交易策略 | [03-策略分析 - 需求](documents/03-策略分析/需求.md) | [03-策略分析 - 设计](documents/03-策略分析/设计.md) | - |
| 交易执行 | [04-交易执行 - 需求](documents/04-交易执行/需求.md) | [04-交易执行 - 设计](documents/04-交易执行/设计.md) | - |

---

## 常见问题

### 1. 数据库连接失败

检查 Docker 容器是否正常运行：
```bash
docker compose --env-file .env ps
```

### 2. 前端白屏

检查后端是否正常启动，前端静态文件是否正确打包到 `backend/src/main/resources/static`

### 3. 数据采集失败

检查 API 密钥配置和网络连接

### 4. T+1 策略未触发

- 检查持仓股票是否在监控列表
- 检查分钟级数据是否正常接收
- 查看策略日志确认指标计算状态

---

## 许可证

MIT License

## 联系方式

如有问题请提交 Issue 或联系开发团队。
<!-- AI_GENERATE_END ---------------- -->
