# StockTrading - AI 股票自动交易系统

基于 LSTM 神经网络的股票交易决策系统，支持自动化交易、实时数据分析和模型预测。

## 项目结构

```
stock-trading/
├── stock-backend/          # Java Spring Boot 后端服务
│   ├── src/               # 源代码
│   ├── pom.xml            # Maven 配置
│   └── AGENTS.md          # 后端开发指南
├── stock-front/           # React 前端应用
│   ├── src/               # TypeScript/React 源码
│   ├── package.json       # Node 依赖
│   ├── config/            # 构建配置
│   └── AGENTS.md          # 前端开发指南
├── documents/              # 项目文档
│   ├── requirements/      # 需求文档
│   │   ├── 01-introduction/  # 项目概述
│   │   ├── 02-data/          # 数据采集需求
│   │   ├── 03-analysis/      # 智能分析需求
│   │   ├── 04-trading/       # 交易执行需求
│   │   ├── 05-risk/          # 风控管理需求
│   │   └── 06-nlp/           # NLP技术方案
│   └── design/            # 设计文档
│       ├── 01-architecture/  # 系统架构设计
│       ├── 02-module/        # 核心模块设计
│       ├── 03-database/      # 数据库设计
│       └── 04-api/           # 接口设计
├── .openskills/           # AI 开发技能库
├── AGENTS.md              # 项目开发指南
├── README.md              # 项目说明
└── LICENSE.txt            # 许可证
```

## 技术栈

### 后端
- **框架**: Spring Boot 2.6.6
- **JDK**: Java 8/9
- **ORM**: MyBatis-Plus
- **数据库**: MySQL + MongoDB
- **缓存**: Redis
- **任务调度**: Quartz
- **机器学习**: DeepLearning4J (LSTM)

### 前端
- **框架**: React 18 + TypeScript 4.9
- **UI 组件**: Ant Design 5.x + Pro Components
- **开发框架**: UmiJS 4.x
- **状态管理**: DVA
- **图表**: Ant Design Charts

## 快速开始

### 环境要求

- Java JDK 21
- Maven 3.9.6
- Node.js >= 12.0.0
- pnpm (推荐包管理器)
- MySQL 8.0+
- MongoDB
- Redis

### 安装依赖

```bash
# 1. 克隆项目
git clone https://github.com/mwangli/stock-trading.git
cd stock-trading

# 2. 安装后端依赖
cd stock-backend
mvn clean install

# 3. 安装前端依赖
cd ../stock-front
pnpm install
```

### 启动开发服务器

```bash
# 1. 启动后端 (端口 8080)
cd stock-backend
mvn spring-boot:run

# 2. 启动前端 (端口 8000)
cd stock-front
npm start
```

访问 http://localhost:8000

## 功能特性

1. **自动化交易**: 对接证券平台 API，实现股票自动买卖
2. **AI 预测**: 基于 LSTM 模型分析股票趋势
3. **T+1 策略**: 短线交易策略，当日买入次日卖出
4. **定时任务**: 使用 Quartz 每日自动更新股票数据
5. **数据可视化**: 收益统计、订单查询、实时股价
6. **DevOps**: K8S + GitHub Actions 自动化部署
7. **分布式训练**: 支持离线模型训练

## 开发指南

### 后端开发

```bash
cd stock-backend

# 编译
mvn clean install

# 运行测试
mvn test -Dtest=ClassName

# 跳过测试编译
mvn clean install -DskipTests
```

详见 [stock-backend/AGENTS.md](stock-backend/AGENTS.md)

### 前端开发

```bash
cd stock-front

# 安装依赖
pnpm install

# 启动开发服务器
npm start

# 代码检查
npm run lint

# 类型检查
npm run tsc

# 运行测试
npm run jest -- path/to/test.tsx
```

详见 [stock-front/AGENTS.md](stock-front/AGENTS.md)

## 项目文档

- **详细文档**: https://www.yuque.com/mwangli/ha7323/axga8dz9imansvl4
- **开发指南**: [AGENTS.md](AGENTS.md)
- **后端指南**: [stock-backend/AGENTS.md](stock-backend/AGENTS.md)
- **前端指南**: [stock-front/AGENTS.md](stock-front/AGENTS.md)

## 在线演示

- **地址**: http://124.220.36.95:8000
- **账号**: guest / guest

## 已知问题

⚠️ **2025-02-09**: 目前暂时无法从 ZX 证券平台获取股票接口数据，待后期优化

## 待优化方向

1. 获取更多股票历史数据用于模型增量训练
2. 模型超参数调优，提高价格趋势预测准确率
3. 支持更多证券平台对接
4. 完善风险管理策略

## 页面展示

| 功能 | 截图 |
|------|------|
| 收益数据统计 | ![收益统计](https://github.com/mwangli/stock-trading/assets/48406369/4b22cc32-c6b9-4a9d-a9df-c29f65a4a5bb) |
| 交易订单查询 | ![交易订单](https://github.com/mwangli/stock-trading/assets/48406369/bd16016b-4085-413d-a609-1643922616c9) |
| 股票价格查看 | ![股票价格](https://github.com/mwangli/stock-trading/assets/48406369/e080bff3-cc17-4fa3-b642-9a9ea8d3b241) |
| 模型预测表现 | ![模型预测](https://github.com/mwangli/stock-trading/assets/48406369/8d6272ac-773f-4a7d-9993-faf0694f9707) |
| 定时任务调度 | ![定时任务](https://github.com/mwangli/stock-trading/assets/48406369/bb10ea48-2f1a-401d-bca4-823d51e8f5bc) |
| 实时日志跟踪 | ![实时日志](https://github.com/mwangli/stock-trading/assets/48406369/4aaf1d15-6049-4913-b972-c7b6146dbf66) |

## 许可证

[MIT License](LICENSE.txt)

## 作者

- **mwangli** - [GitHub](https://github.com/mwangli)

