# StockTrading - AI 股票自动交易系统

基于 LSTM 神经网络与 FinBERT 情感分析的智能股票交易决策系统，支持自动化交易、实时数据分析和 AI 模型预测。

## 项目简介

这是一个完整的 AI 股票交易系统，采用微服务架构设计：
- **后端服务** (Java Spring Boot): 提供 RESTful API，处理业务逻辑
- **前端应用** (React + UmiJS): 提供可视化 Dashboard
- **AI 服务** (Python FastAPI): 提供 LSTM 价格预测和情感分析

### 核心特性

- **T+1 交易策略**: 短线交易策略，当日买入次日卖出
- **双因子选股模型**: LSTM 预测 (60%) + 情感分析 (40%)
- **定时任务调度**: 每日自动更新数据、预测和分析
- **Docker 一键部署**: 使用 Docker Compose 快速部署
- **CI/CD 自动化**: GitHub Actions 自动构建和部署

---

## 项目结构

```
stock-trading/
├── stock-backend/                    # Java Spring Boot 后端服务
│   ├── src/
│   │   ├── main/java/               # 源代码
│   │   └── main/resources/          # 配置和脚本
│   ├── pom.xml                      # Maven 配置
│   └── Dockerfile                    # 容器配置
├── stock-frontend/                   # React 前端应用
│   ├── src/                          # TypeScript/React 源码
│   ├── config/                       # 构建配置
│   ├── package.json                  # Node 依赖
│   └── Dockerfile                    # 容器配置
├── stock-service/                     # Python AI 服务
│   ├── app/                          # 应用代码
│   │   ├── api/                      # API 路由
│   │   ├── services/                 # 业务逻辑
│   │   ├── models/                   # AI 模型
│   │   └── core/                     # 配置
│   ├── tests/                        # 测试代码
│   ├── requirements.txt               # Python 依赖
│   └── Dockerfile                    # 容器配置
├── documents/                         # 项目文档
│   ├── requirements/                 # 需求文档
│   │   ├── 01-数据采集/              # 数据采集需求
│   │   ├── 02-情感分析/              # 情感分析需求
│   │   ├── 03-LSTM预测/              # LSTM 预测需求
│   │   ├── 04-模型迭代/              # 模型迭代需求
│   │   ├── 05-综合选股/              # 综合选股需求
│   │   ├── 06-决策引擎/              # 决策引擎需求
│   │   ├── 07-风控管理/              # 风控管理需求
│   │   └── 08-交易执行/              # 交易执行需求
│   └── design/                       # 设计文档
│       ├── 00-整体架构设计/           # 架构设计 & 部署指南
│       ├── 01-数据采集/              # 数据采集服务设计
│       ├── 02-情感分析/              # 情感分析服务设计
│       ├── 03-LSTM预测/              # LSTM 预测服务设计
│       ├── 04-模型迭代/              # 模型迭代服务设计
│       ├── 05-综合选股/              # 选股算法设计
│       ├── 06-决策引擎/              # 决策引擎设计
│       ├── 07-风控管理/              # 风控策略设计
│       └── 08-交易执行/              # 交易执行设计
├── docker-compose.yml                # Docker 编排配置
├── AGENTS.md                         # 项目开发指南
└── README.md                         # 项目说明
```

---

## 技术栈

### 后端 (Java)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.2 |
| JDK | Java | 17 |
| ORM | JPA (Hibernate) / MyBatis-Plus | - |
| 数据库 | MySQL | 8.0 |
| 文档数据库 | MongoDB | 6.0 |
| 缓存 | Redis | 7.x |
| 任务调度 | Quartz | - |
| API 文档 | SpringDoc OpenAPI | - |

### 前端 (React)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| 语言 | TypeScript | 4.9 |
| UI 组件 | Ant Design | 5.x |
| 高级组件 | Pro Components | - |
| 开发框架 | UmiJS | 4.x |
| 状态管理 | DVA | - |
| 图表 | Ant Design Charts | - |

### AI 服务 (Python)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | FastAPI | 0.109.x |
| 服务器 | Uvicorn | 0.27.x |
| NLP 模型 | FinBERT (Transformers) | - |
| 深度学习 | TensorFlow | 2.15.x |
| 数据验证 | Pydantic | 2.x |
| HTTP 客户端 | httpx | - |

### 基础设施 (Docker)

| 组件 | 技术 | 版本 |
|------|------|------|
| 容器 | Docker | 20.10+ |
| 编排 | Docker Compose | 2.0+ |
| CI/CD | GitHub Actions | - |

---

## 系统架构

### 整体架构

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                    用户层                                           │
│  ┌─────────────────────────────┐              ┌─────────────────────────────┐    │
│  │         WEB 端              │              │        移动端 (规划中)       │    │
│  │  React Frontend (UmiJS)    │              │   微信小程序 / iOS App      │    │
│  │  http://localhost:8000     │              │                             │    │
│  └─────────────────────────────┘              └─────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              │ HTTP / WebSocket
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                   应用层                                            │
│                                                                                     │
│  ┌─────────────────────────────┐      ┌─────────────────────────────┐            │
│  │       Java 服务 (8080)       │      │     Python AI 服务 (8001)   │            │
│  │                             │      │                             │            │
│  │  ┌───────────────────────┐  │      │  ┌───────────────────────┐  │            │
│  │  │   1. 数据采集模块     │  │      │  │   1. 情感分析模块     │  │            │
│  │  │   2. 综合选股模块     │  │      │  │   2. LSTM预测模块     │  │            │
│  │  │   3. 决策引擎模块     │  │      │  │   3. 风控计算模块     │  │            │
│  │  │   4. 交易执行模块     │  │      │  │   4. 模型迭代模块     │  │            │
│  │  └───────────────────────┘  │      │  └───────────────────────┘  │            │
│  └─────────────────────────────┘      └─────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                   数据层                                            │
│                                                                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐  │
│  │    MySQL        │  │    MongoDB      │  │     Redis       │  │   文件存储  │  │
│  │  (业务数据)      │  │  (文档数据)      │  │   (缓存数据)    │  │ (模型/日志) │  │
│  │   :3306         │  │   :27017        │  │   :6379         │  │            │  │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────┘
                                              │
                                              ▼
┌─────────────────────────────────────────────────────────────────────────────────────┐
│                                 基础设施层                                         │
│                                                                                     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                 │
│  │     Docker       │  │  Nginx (网关)    │  │  GitHub Actions │                 │
│  │  容器化部署      │  │   反向代理       │  │    CI/CD        │                 │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                 │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

### 8大核心模块

| 模块 | 职责 | 说明 |
|------|------|------|
| **数据采集** | 从外部 API 获取股票数据、新闻 | 对接 AKTools / AKShare |
| **情感分析** | FinBERT 情感分析 | 分析财经新闻情绪 |
| **LSTM 预测** | LSTM 价格预测 | 预测次日价格走势 |
| **综合选股** | 双因子选股 | Score = LSTM×0.6 + Sentiment×0.4 |
| **决策引擎** | 生成交易信号 | 买入/卖出/持有决策 |
| **风控管理** | 风控检查 | 止损、熔断等策略 |
| **交易执行** | 通过券商 API 执行交易 | 对接证券平台 |
| **模型迭代** | 模型性能监控与训练 | 收集数据、更新模型 |

### 模块依赖图

```
                    ┌─────────────┐
                    │   数据采集   │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │  情感分析    │ │ LSTM预测     │ │             │
    │  (FinBERT)  │ │  (LSTM)     │ │             │
    └──────┬──────┘ └──────┬──────┘ └─────────────┘
          │                 │
          └────────┬────────┘
                   │
                   ▼
          ┌─────────────────┐
          │    综合选股     │
          └────────┬────────┘
                   │
       ┌───────────┴───────────┐
       ▼                       ▼
┌─────────────┐         ┌─────────────┐
│  决策引擎    │         │  风控管理    │
└──────┬──────┘         └──────┬──────┘
       │                       │
       └───────────┬───────────┘
                   │
                   ▼
          ┌─────────────┐
          │   交易执行    │
          └──────┬──────┘
                 │
                 ▼ (反馈)
          ┌─────────────┐
          │   模型迭代    │
          └─────────────┘
```

---

## 快速开始

### 环境要求

- Java JDK 17+
- Maven 3.9.2
- Node.js >= 18.0.0
- pnpm (推荐)
- Python 3.10+
- MySQL 8.0+
- MongoDB 6.0+
- Redis 7.x
- Docker & Docker Compose (可选)

### 本地开发启动

```bash
# 1. 克隆项目
git clone https://github.com/mwangli/stock-trading.git
cd stock-trading

# 2. 启动基础设施 (MySQL / MongoDB / Redis)
docker-compose up -d mysql mongo redis

# 3. 启动后端 (端口 8080)
cd stock-backend
mvn spring-boot:run

# 4. 启动前端 (端口 8000)
cd ../stock-frontend
pnpm install
npm start

# 5. 启动 Python AI 服务 (端口 8001)
cd ../stock-service
python -m venv venv
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001
```

访问 http://localhost:8000

### Docker 一键部署

```bash
# 构建并启动所有服务
docker-compose up -d

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

服务地址：
- 前端: http://localhost:8000
- 后端: http://localhost:8080
- AI 服务: http://localhost:8001

---

## 开发指南

### 后端开发

```bash
cd stock-backend

# 编译打包
mvn clean install

# 运行测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 跳过测试编译
mvn clean install -DskipTests
```

### 前端开发

```bash
cd stock-frontend

# 安装依赖
pnpm install

# 启动开发服务器
npm start

# 代码检查
npm run lint

# 自动修复 lint 问题
npm run lint:fix

# 类型检查
npm run tsc

# 代码格式化
npm run prettier

# 运行测试
npm run jest -- path/to/test.tsx
```

### Python AI 服务开发

```bash
cd stock-service

# 创建虚拟环境
python -m venv venv
# Windows
venv\Scripts\activate
# Linux/Mac
source venv/bin/activate

# 安装依赖
pip install -r requirements.txt

# 启动开发服务器
uvicorn app.main:app --reload --host 0.0.0.0 --port 8001

# 运行测试
pytest

# 运行测试并生成覆盖率报告
pytest --cov=app
```

---

## API 接口文档

### 后端服务 (Spring Boot) - 端口 8080

#### 股票数据接口 `/api/data`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/api/data/stocks` | 获取股票列表 | - |
| GET | `/api/data/stocks/{code}/history` | 获取历史K线数据 | `days`: 天数(默认30) |
| GET | `/api/data/stocks/{code}/price` | 获取实时价格 | - |
| POST | `/api/data/stocks/{code}/sync` | 同步单只股票历史 | `days`: 天数(默认60) |
| POST | `/api/data/sync/all` | 全量同步所有股票 | - |

#### 情感分析接口 `/api/sentiment`

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/sentiment/analyze` | 分析单条文本情感 | `{ "text": "..." }` |
| POST | `/api/sentiment/stock/{stockCode}` | 计算股票情感得分 | `[{ news item }]` |
| POST | `/api/sentiment/market` | 获取市场整体情绪 | `[{ news item }]` |
| POST | `/api/sentiment/ranking` | 获取股票情感排名 | `{ stockCode: [news] }` |

#### 选股接口 `/api/selection`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/selection/comprehensive` | 获取综合选股结果 |
| GET | `/api/selection/lstm` | 获取 LSTM 预测排名 |
| GET | `/api/selection/sentiment` | 获取情感分析排名 |

#### 交易接口 `/api/trading`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/trading/orders` | 获取交易订单列表 |
| POST | `/api/trading/orders` | 创建新订单 |
| GET | `/api/trading/positions` | 获取当前持仓 |

#### 账户接口 `/api/account`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/account/info` | 获取账户信息 |
| GET | `/api/account/balance` | 获取账户余额 |
| GET | `/api/trading/statistics` | 获取交易统计 |

### Python AI 服务 (FastAPI) - 端口 8001

#### 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 服务状态检查 |
| GET | `/health` | 详细健康检查 |

#### 数据采集接口 `/api/data`

| 方法 | 路径 | 说明 | 参数 |
|------|------|------|------|
| GET | `/api/data/stock/list` | 获取 A 股股票列表 | - |
| GET | `/api/data/stock/prices` | 获取历史 K 线数据 | `symbol`, `start_date`, `end_date`, `period`, `adjust` |
| GET | `/api/data/stock/quote` | 获取单只股票实时行情 | `symbol` |
| GET | `/api/data/stock/quotes` | 获取多只股票实时行情 | `symbols`(逗号分隔) |
| GET | `/api/data/stock/financial` | 获取财务报表数据 | `symbol` |
| GET | `/api/data/news` | 获取股票新闻 | `symbol`(可选) |

#### 情感分析接口 `/api/sentiment`

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/sentiment/analyze` | 分析单条文本情感 | `{ "text": "..." }` |
| POST | `/api/sentiment/analyze/batch` | 批量情感分析 | `{ "texts": [...] }` |
| POST | `/api/sentiment/analyze/news` | 分析新闻情感 | `{ "news": [...] }` |
| GET | `/api/sentiment/market/{stock_code}` | 获取股票市场情绪 | `news_count` |
| POST | `/api/sentiment/market/analyze` | 计算市场情绪 | `{ "news": [...] }` |
| GET | `/api/sentiment/model/info` | 获取模型信息 | - |

#### LSTM 预测接口 `/api/lstm`

| 方法 | 路径 | 说明 | 请求体 |
|------|------|------|--------|
| POST | `/api/lstm/predict` | 预测次日价格 | `{ "symbol": "..." }` |
| POST | `/api/lstm/predict/batch` | 批量预测 | `{ "symbols": [...] }` |
| POST | `/api/lstm/forecast` | 多日预测 | `{ "symbol": "...", "days": 5 }` |
| GET | `/api/lstm/model/info` | 获取模型状态 | - |
| GET | `/api/lstm/training/history` | 获取训练历史 | - |

### 通用响应格式

**成功响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

**错误响应:**
```json
{
  "code": 500,
  "message": "Internal Server Error",
  "data": null
}
```

---

## 项目文档

详细设计文档见 `documents/` 目录：

| 文档 | 说明 |
|------|------|
| [整体架构设计](documents/design/00-整体架构设计/整体架构设计.md) | 系统架构、模块设计、部署方案 |
| [部署指南](documents/design/00-整体架构设计/部署指南.md) | Docker 部署详细指南 |
| [数据采集服务设计](documents/design/01-数据采集/数据采集服务设计.md) | 数据采集模块设计 |
| [情感分析服务设计](documents/design/02-情感分析/情感分析服务设计.md) | FinBERT 情感分析设计 |
| [LSTM 预测服务设计](documents/design/03-LSTM预测/LSTM预测服务设计.md) | LSTM 价格预测设计 |
| [模型迭代服务设计](documents/design/04-模型迭代/模型迭代服务设计.md) | 模型训练与迭代设计 |
| [选股算法设计](documents/design/05-综合选股/选股算法设计.md) | 综合选股算法设计 |
| [决策引擎设计](documents/design/06-决策引擎/决策引擎设计.md) | 交易决策引擎设计 |
| [风控策略设计](documents/design/07-风控管理/风控策略设计.md) | 风险管理策略设计 |
| [交易执行设计](documents/design/08-交易执行/交易执行设计.md) | 交易执行模块设计 |

---

## 在线演示

- **地址**: http://124.220.36.95:8000
- **账号**: guest / guest

---

## 每日交易流程

```
09:30 - 任务触发
    │
    ▼
Step 1: 数据采集
    │  • 获取股票列表
    │  • 获取实时行情
    │  • 获取财经新闻
    │
    ▼
Step 2: 情感分析 (FinBERT)      Step 3: LSTM 预测
    │                              │
    │  • 分析新闻情绪               │  • 预测次日价格
    │  • 计算情感得分               │  • 获取置信度
    │                              │
    └──────────────┬───────────────┘
                   │
                   ▼
Step 4: 综合选股
    │  • Score = LSTM×0.6 + Sentiment×0.4
    │  • 选出 Top1 + Top3 备选
    │
    ▼
Step 5: 风控检查
    │  • 日亏损 < 3%
    │  • 月亏损 < 10%
    │
    ▼
Step 6: 交易执行
    │  • 执行买入/卖出
    │
    ▼
Step 7: 模型迭代 (后台异步)
    │  • 收集交易数据
    │  • 评估模型性能
    │  • 触发模型重训练
    │
    ▼
完成
```

---

## 已知问题

⚠️ **2025-02-09**: 目前暂时无法从 ZX 证券平台获取股票接口数据，待后期优化

---

## 待优化方向

1. 获取更多股票历史数据用于模型增量训练
2. 模型超参数调优，提高价格趋势预测准确率
3. 支持更多证券平台对接
4. 完善风险管理策略

---

## 页面展示

| 功能 | 截图 |
|------|------|
| 收益数据统计 | ![收益统计](https://github.com/mwangli/stock-trading/assets/48406369/4b22cc32-c6b9-4a9d-a9df-c29f65a4a5bb) |
| 交易订单查询 | ![交易订单](https://github.com/mwangli/stock-trading/assets/48406369/bd16016b-4085-413d-a609-1643922616c9) |
| 股票价格查看 | ![股票价格](https://github.com/mwangli/stock-trading/assets/48406369/e080bff3-cc17-4fa3-b642-9a9ea8d3b241) |
| 模型预测表现 | ![模型预测](https://github.com/mwangli/stock-trading/assets/48406369/8d6272ac-773f-4a7d-9993-faf0694f9707) |
| 定时任务调度 | ![定时任务](https://github.com/mwangli/stock-trading/assets/48406369/bb10ea48-2f1a-401d-bca4-823d51e8f5bc) |
| 实时日志跟踪 | ![实时日志](https://github.com/mwangli/stock-trading/assets/48406369/4aaf1d15-6049-4913-b972-c7b6146dbf66) |

---

## 许可证

[MIT License](LICENSE.txt)

---

## 作者

- **mwangli** - [GitHub](https://github.com/mwangli)
