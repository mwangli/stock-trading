# Stock Trading - AI 股票自动交易系统

# 项目演示

**[AI 股票交易系统 - 在线演示](http://124.220.36.95:8080/)**

---

基于 LSTM 神经网络与财经新闻情感分析的智能股票交易决策系统，支持自动化交易、实时数据分析和 AI 模型预测。

## 项目简介

这是一个完整的 AI 股票交易系统，采用前后端分离架构设计：

- **后端服务** (Java Spring Boot 3.2): 提供 RESTful API，业务逻辑处理，AI 模型推理
- **前端应用** (React 19 + Vite 7 + Ant Design 6): 可视化 Dashboard，数据展示，交易操作
- **数据存储**: MySQL (业务数据) + MongoDB (文档数据/模型存储) + Redis (缓存)

### 核心特性

- **双策略引擎**: 
  - **选股策略** (天级): LSTM 预测 (60%) + 情感分析 (40%) 双因子选股
  - **T+1 卖出策略** (分钟级): 基于分钟级价格走势的实时卖出决策
- **T+1 交易**: 短线交易策略，当日买入次日卖出
- **多指标决策**: 移动止损、RSI 超买、成交量背离、布林带突破
- **定时任务调度**: 每日自动更新数据、预测和分析
- **动态任务管理**: 支持运行时调整任务参数
- **WebSocket 实时推送**: 日志和通知实时推送
- **Docker 一键部署**: 使用 Docker Compose 快速部署
- **CI/CD 自动化**: GitHub Actions 自动构建和部署
- **无文件 I/O 模型管理**: 模型直接序列化为内存字节流 (`byte[] params`) 并通过 MongoDB Binary Storage 持久化，推理时通过 MongoDB 标识符（`mongo:ID`）动态加载，完全消除本地磁盘文件依赖。

---

## 技术栈

### 后端 (Backend)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.2 |
| JDK | OpenJDK | 17 |
| ORM | Spring Data JPA | 自动建表 |
| 数据库 | MySQL / MongoDB | 8.0 / 6.0 |
| 缓存 | Redis | 7.x |
| HTTP | OkHttp | 4.12 |
| 工具 | Hutool / FastJSON2 | 5.8 / 2.0 |
| AI 框架 | DJL (Deep Java Library) | 0.28.0 |
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

```
stock-trading/
├── backend/                        # Java Spring Boot 后端服务
│   ├── src/main/java/com/stock/
│   │   ├── Application.java       # 主启动类
│   │   ├── config/                # 全局配置
│   │   │   ├── SchedulingConfig.java    # 定时任务配置
│   │   │   └── WebSocketConfig.java      # WebSocket 配置
│   │   ├── dataCollector/         # 数据采集模块
│   │   │   ├── client/            # API 客户端
│   │   │   ├── controller/        # 控制器
│   │   │   ├── entity/            # 数据实体
│   │   │   ├── listener/          # 事件监听
│   │   │   ├── repository/        # 数据访问层
│   │   │   ├── scheduled/         # 定时任务
│   │   │   ├── service/           # 服务层
│   │   │   └── util/              # 工具类
│   │   ├── modelService/          # AI 模型模块
│   │   │   ├── config/            # 配置
│   │   │   ├── controller/        # 控制器
│   │   │   ├── dataset/           # 数据集处理
│   │   │   ├── dto/               # 数据传输对象
│   │   │   ├── entity/            # 实体
│   │   │   ├── inference/         # 模型推理
│   │   │   ├── listener/          # 事件监听
│   │   │   ├── model/             # LSTM 模型
│   │   │   ├── repository/        # 数据访问层
│   │   │   └── service/           # 服务层
│   │   ├── strategyAnalysis/     # 策略分析模块
│   │   │   ├── config/            # 配置
│   │   │   ├── controller/        # 控制器
│   │   │   ├── decision/          # 决策引擎
│   │   │   ├── dto/               # 数据传输对象
│   │   │   ├── entity/            # 实体
│   │   │   ├── enums/             # 枚举定义
│   │   │   ├── intraday/          # 日内交易策略 (T+1 卖出)
│   │   │   ├── optimizer/         # 策略优化
│   │   │   ├── repository/        # 数据访问层
│   │   │   ├── scheduled/         # 定时任务
│   │   │   ├── selector/          # 股票筛选
│   │   │   └── switcher/          # 策略开关
│   │   ├── tradingExecutor/      # 交易执行模块
│   │   │   ├── broker/            # 券商接口
│   │   │   ├── config/            # 配置
│   │   │   ├── controller/        # 控制器
│   │   │   ├── entity/            # 实体
│   │   │   ├── enums/             # 枚举定义
│   │   │   ├── execution/         # 交易执行
│   │   │   ├── fee/               # 手续费计算
│   │   │   ├── risk/              # 风控管理
│   │   │   └── time/              # 时间控制
│   │   ├── job/                   # 动态任务模块
│   │   │   ├── bootstrap/         # 任务引导
│   │   │   ├── controller/        # 控制器
│   │   │   ├── entity/            # 实体
│   │   │   ├── repository/        # 数据访问层
│   │   │   └── service/           # 服务层
│   │   ├── event/                 # 事件处理
│   │   ├── handler/               # WebSocket 处理器
│   │   ├── logging/               # 日志模块
│   │   └── service/               # 通用服务
│   ├── src/main/resources/
│   │   ├── application.yml       # 应用配置
│   │   └── logback-spring.xml    # 日志配置
│   ├── pom.xml                   # Maven 配置
│   └── Dockerfile                # Docker 构建配置
│
├── frontend/                   # React 前端应用
│   ├── src/
│   │   ├── App.tsx               # 主应用组件
│   │   ├── main.tsx              # 入口文件
│   │   ├── components/           # 通用组件
│   │   ├── layouts/              # 布局组件
│   │   ├── locales/              # 国际化资源
│   │   ├── pages/                # 页面组件
│   │   ├── store/                # 状态管理
│   │   └── utils/                # 工具函数
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   └── tailwind.config.js
│
├── docs/                          # 项目文档
│   ├── requirement/              # 需求文档
│   ├── design/                   # 设计文档
│   └── plans/                    # 实施计划
│
├── docker-compose.yml            # Docker 编排配置
├── pom.xml                       # 父项目配置
├── AGENTS.md                     # 项目开发指南
└── README.md                     # 项目说明
```

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
# 使用 Docker Compose 启动数据库和缓存
docker-compose up -d mysql redis mongo
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
# 一键启动所有服务
docker-compose up -d --build

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 访问
# 前端 + 后端 API: http://localhost:8080
```

---

## 模块架构

### 数据采集模块 (com.stock.dataCollector)

- 股票列表同步
- 实时行情采集
- 历史 K 线获取
- 财经新闻采集
- 财经新闻采集 (证券平台)


### AI 模型模块 (com.stock.modelService)

- LSTM 价格预测模型
- 情感分析推理
- 模型加载与管理 (MongoDB 存储)
- 预测结果缓存

#### 情感分析模型（FinBERT 中文版）部署说明

情感分析使用 DJL 加载本地 TorchScript 模型，目录 `models/sentiment/`（项目根目录）。

**DJL 所需文件：**

| 文件 | 说明 |
|------|------|
| `sentiment.pt` | TorchScript 模型权重 |
| `tokenizer.json` | 分词器配置 |
| `config.json` | 模型配置 |
| `serving.properties` | DJL 服务配置（`engine=PyTorch`、`option.modelName=sentiment`） |

**DJL 格式转换：**

Hugging Face 原始格式需转换为 TorchScript（`.pt`）后 DJL 才能加载。使用官方 **djl-convert** 工具：

```bash
pip install https://publish.djl.ai/djl_converter
djl-convert -m yiyanghkust/finbert-tone-chinese -o models/sentiment -t text-classification
```

完整步骤与排错见 [情感分析模型格式转换指南](docs/02-模型服务/模型格式转换.md)。

**配置（application.yml）：**

```yaml
models:
  sentiment:
    model-path: "models/sentiment"
    model-source: "local"
    download-pretrained: false
```

**接口：**

- 健康检查：`GET /api/models/sentiment/health`
- 重新加载：`POST /api/models/sentiment/reload`

`modelLoaded=true` 表示 FinBERT 已加载；`false` 时回退到规则模式。

#### Git LFS 与 CI/CD

模型文件由 Git LFS 管理（`.gitattributes`: `models/** filter=lfs diff=lfs merge=lfs -text`）。
CI/CD 部署：push tag 触发，服务器执行 `git clone + lfs pull + mvn + docker build + compose up`。

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
docker-compose up -d

# 服务状态
docker-compose ps

# 日志查看
docker-compose logs -f backend
```

### 端口映射

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8080 | Spring Boot 服务 |
| 前端开发 | 5173 | Vite 开发服务器 |
| MySQL | 3306 | 数据库 |
| MongoDB | 27017 | 文档数据库 |
| Redis | 6379 | 缓存服务 |

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

- [文档索引](./docs/README.md) - 文档结构和快速入口
- [00-系统架构 - 需求](./docs/00-系统架构/需求.md)
- [00-系统架构 - 设计](./docs/00-系统架构/设计.md)

### 模块文档

| 模块 | 需求文档 | 设计文档 | 测试文档 |
|------|----------|----------|----------|
| 数据采集 | [01-数据采集 - 需求](./docs/01-数据采集/需求.md) | [01-数据采集 - 设计](./docs/01-数据采集/设计.md) | - |
| AI 模型 | [02-模型服务 - 需求](./docs/02-模型服务/需求.md) | [02-模型服务 - 设计](./docs/02-模型服务/设计.md) | - |
| 交易策略 | [03-策略分析 - 需求](./docs/03-策略分析/需求.md) | [03-策略分析 - 设计](./docs/03-策略分析/设计.md) | - |
| 交易执行 | [04-交易执行 - 需求](./docs/04-交易执行/需求.md) | [04-交易执行 - 设计](./docs/04-交易执行/设计.md) | - |

---

## 常见问题

### 1. 数据库连接失败

检查 Docker 容器是否正常运行：
```bash
docker-compose ps
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
