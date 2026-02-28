# Stock Trading - AI 股票自动交易系统

基于 LSTM 神经网络与财经新闻情感分析的智能股票交易决策系统，支持自动化交易、实时数据分析和 AI 模型预测。

## 项目简介

这是一个完整的 AI 股票交易系统，采用前后端分离架构设计：

- **后端服务** (Java Spring Boot 3.2): 提供 RESTful API，业务逻辑处理，AI 模型推理
- **前端应用** (React + Ant Design Pro 5): 可视化 Dashboard，数据展示，交易操作
- **数据存储**: MySQL (业务数据) + MongoDB (文档数据) + Redis (缓存)

### 核心特性

- **T+1 交易策略**: 短线交易策略，当日买入次日卖出
- **双因子选股模型**: LSTM 预测 (60%) + 情感分析 (40%)
- **定时任务调度**: 每日自动更新数据、预测和分析
- **Docker 一键部署**: 使用 Docker Compose 快速部署
- **CI/CD 自动化**: GitHub Actions 自动构建和部署

---

## 技术栈

### 后端 (Backend)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.2 |
| JDK | OpenJDK | 17 |
| ORM | MyBatis-Plus | 3.5.5 |
| 数据库 | MySQL / MongoDB | 8.0 / 6.0 |
| 缓存 | Redis | 7.x |
| HTTP | OkHttp | 4.12 |
| 工具 | Hutool / FastJSON2 | 5.8 / 2.0 |

### 前端 (Frontend)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| UI 库 | Ant Design Pro | 5.x |
| 构建工具 | UmiJS | 4.x |
| 语言 | TypeScript | 5.x |
| 图表 | @ant-design/charts | 1.4 |

---

## 项目结构

```
stock-trading/
├── backend/                        # Java Spring Boot 后端服务
│   ├── src/main/java/com/stock/
│   │   ├── config/                # 全局配置
│   │   ├── databus/               # 数据采集模块
│   │   │   ├── controller/        # 前端路由控制器
│   │   │   ├── collector/         # 数据采集器
│   │   │   ├── client/            # API 客户端 (Tushare 等)
│   │   │   ├── entity/            # 数据实体
│   │   │   ├── repository/        # 数据访问层
│   │   │   └── scheduled/         # 定时任务
│   │   ├── models/                # AI 模型模块
│   │   │   ├── controller/        # AI模型相关控制器
│   │   │   ├── model/             # LSTM 模型
│   │   │   └── inference/         # 模型推理
│   │   ├── strategy/              # 策略模块
│   │   │   ├── decision/          # 决策引擎
│   │   │   ├── selector/          # 股票筛选
│   │   │   └── enums/             # 枚举定义
│   │   ├── executor/              # 执行模块
│   │   │   ├── execution/         # 交易执行
│   │   │   ├── risk/              # 风控管理
│   │   │   └── enums/             # 订单状态
│   │   └── (根包)                 # 主启动类和全局配置
│   ├── src/main/resources/
│   │   ├── static/                # 前端静态文件 (构建后生成)
│   │   └── application.yml        # 应用配置
│   ├── pom.xml                    # Maven 配置
│   └── Dockerfile                 # Docker 构建配置
│
├── frontend/                      # React 前端应用
│   ├── src/
│   │   ├── pages/                 # 页面组件
│   │   ├── components/            # 通用组件
│   │   ├── services/              # API 服务
│   │   ├── models/                # 数据模型
│   │   └── utils/                 # 工具函数
│   ├── config/                    # 构建配置
│   ├── package.json               # Node 依赖
│   └── tsconfig.json              # TypeScript 配置
│
├── mobile/                        # 移动端 (规划中)
│   └── package.json               # React Native 配置
│
├── documents/                     # 项目文档
│   ├── design/                    # 设计文档
│   │   ├── 00-整体架构设计/       # 架构设计 & 部署指南
│   │   ├── 01-databus/           # 数据采集设计
│   │   ├── 02-intelligence/      # 智能服务设计
│   │   ├── 03-strategy/          # 策略服务设计
│   │   └── 04-executor/          # 执行服务设计
│   └── requirements/              # 需求文档
│       ├── 01-databus/           # 数据采集需求
│       ├── 02-intelligence/      # 智能服务需求
│       ├── 03-strategy/          # 策略服务需求
│       └── 04-executor/          # 执行服务需求
│
├── docker-compose.yml             # Docker 编排配置
├── pom.xml                        # 父项目配置
├── AGENTS.md                      # 项目开发指南
└── README.md                      # 项目说明
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
npm start

# 访问：http://localhost:8000
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

### 数据采集模块 (com.stock.databus)

- 股票列表同步
- 实时行情采集
- 历史 K 线获取
- 财经新闻采集
- RSS 源解析 (ROME 框架)
- 网页抓取 (Jsoup)

### AI 模型模块 (com.stock.models)

- LSTM 价格预测模型
- 情感分析推理
- 模型加载与管理
- 预测结果缓存

### 策略模块 (com.stock.strategy)

- 综合选股算法
- 决策引擎
- 交易信号生成
- 股票评分排名

### 执行模块 (com.stock.executor)

- 风控检查
- 订单执行
- 持仓管理
- 交易记录

### 项目配置 (com.stock)

- REST API 配置
- 定时任务调度
- 静态文件服务
- 全局配置类

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
| 交易信号 | `/api/signals` | 获取交易信号 |
| 持仓信息 | `/api/positions` | 获取持仓数据 |

---

## 数据库配置

### MySQL (业务数据)

```yaml
url: jdbc:mysql://localhost:3306/stock_trading
username: root
password: Root.123456
```

### MongoDB (文档数据)

```yaml
uri: mongodb://admin:Root.123456@localhost:27017/stock_trading
```

### Redis (缓存)

```yaml
host: localhost
port: 6379
password: Root.123456
```

---

## 开发流程

1. **需求分析**: 更新 `documents/requirements/` 下的需求文档
2. **设计评审**: 更新 `documents/design/` 下的设计文档
3. **代码实现**: 按照 AGENTS.md 规范编写代码
4. **测试验证**: 编写单元测试和集成测试
5. **代码审查**: 确保 lint 和 tests 通过
6. **提交部署**: Git 提交并推送到仓库

---

## 测试

### 后端测试

```bash
cd backend

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 跳过测试打包
mvn package -DskipTests
```

### 前端测试

```bash
cd frontend

# 运行测试
npm test

# 代码检查
npm run lint

# 类型检查
npm run tsc
```

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
| 后端 + 前端 | 8080 | Spring Boot 服务 |
| MySQL | 3306 | 数据库 |
| MongoDB | 27017 | 文档数据库 |
| Redis | 6379 | 缓存服务 |

---

## 核心功能

### 1. 数据采集

- 使用 Tushare API 获取 A 股数据
- ROME 框架解析 RSS 财经新闻
- Jsoup 网页抓取补充数据源
- 定时任务自动更新

### 2. AI 预测

- LSTM 神经网络预测次日价格
- 财经新闻情感分析
- 双因子综合评分

### 3. 交易决策

- 每日自动生成交易信号
- 风控规则检查
- T+1 策略执行

### 4. 可视化

- Dashboard 数据展示
- K 线图可视化
- 持仓盈亏分析
- 交易记录查询

---

## 项目文档

- [AGENTS.md](./AGENTS.md) - 项目开发指南
- [架构设计](./documents/design/00-整体架构设计/整体架构设计.md)
- [部署指南](./documents/design/00-整体架构设计/部署指南.md)
- [需求文档](./documents/requirements/)

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

---

## 许可证

MIT License

## 联系方式

如有问题请提交 Issue 或联系开发团队。