# 股票交易系统 - 架构设计

## 目录

1. [系统架构概览](#1-系统架构概览)
2. [模块设计](#2-模块设计)
3. [服务通信](#3-服务通信)
4. [数据流程](#4-数据流程)
5. [部署架构](#5-部署架构)
6. [技术栈](#6-技术栈)
7. [未来扩展](#7-未来扩展)

---

## 1. 系统架构概览

### 1.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              用户层                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  Web端: React Frontend (UmiJS)                                        │   │
│  │  未来: 微信小程序 / iOS App                                            │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        │ HTTP / WebSocket
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           API网关 (Nginx)                                       │
│   /api/*        → Spring Boot 后端 (8080)                                   │
│   /ai-api/*     → Python AI 服务 (8001)                                      │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
          ┌─────────────────────────────┼─────────────────────────────┐
          ▼                             ▼                             ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────────┐
│   Spring Boot       │  │   Python AI         │  │   基础设施               │
│   后端 (8080)      │  │   服务 (8001)       │  │   MySQL + Redis         │
│                     │  │                    │  │                         │
│   9大核心模块        │  │   FinBERT + LSTM   │  │                         │
│                     │  │   + Dexter Client   │  │                         │
└─────────────────────┘  └─────────────────────┘  └─────────────────────────┘
```

### 1.2 系统上下文

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              外部系统                                           │
│                                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌────────────┐  │
│  │ AKTools API │    │ 新浪/东财   │    │ Dexter API  │    │ 中信证券   │  │
│  │  (行情数据)  │    │  (财经新闻)  │    │ (基本面分析) │    │  (交易执行) │  │
│  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └──────┬─────┘  │
└─────────┼───────────────────┼───────────────────┼───────────────────┼────────┘
          │                   │                   │                   │
          ▼                   ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                           我们的系统                                             │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                         前端 (Port 80)                                  │  │
│  │                    (个人Dashboard)                                       │  │
│  └─────────────────────────────────────────────────────────────────────────┘  │
│                                      │                                        │
│                                      ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────────────┐  │
│  │                       后端 (Port 8080)                                  │  │
│  │  ┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌──────┐┌────┐┌───┐│  │
│  │  │数据采集││情感分析││LSTM预测││Dexter ││综合选股││决策引擎││风控││交易││模型迭代│  │
│  │  └──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└──┬───┘└────┘└───┘└───┘│  │
│  └───────┼─────────┼─────────┼─────────┼─────────┼─────────────────────────┘  │
│          │         │         │         │         │                            │
│          └─────────┴─────────┼─────────┴─────────┘                            │
│                            ▼                                                   │
│  ┌───────────────────────────────────────────────────────────────────────┐   │
│  │              AI服务 (Port 8001)                                        │   │
│  │  ┌─────────────────────────────────────────────────────────────────┐  │   │
│  │  │  FinBERT (情感分析) + LSTM (价格预测) + Dexter Client (基本面)   │  │   │
│  │  └─────────────────────────────────────────────────────────────────┘  │   │
│  └───────────────────────────────────────────────────────────────────────┘   │
│                                                                              │
└──────────────────────────────────────────────────────────────────────────────┘
          │                   │                   │
          ▼                   ▼                   ▼
┌──────────────────────────────────────────────────────────────────────────────┐
│                          数据层                                                │
│                                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐                    │
│  │   MySQL     │    │  MongoDB    │    │   Redis     │                    │
│  │ (主数据)    │    │ (文档/日志) │    │  (缓存)     │                    │
│  └─────────────┘    └─────────────┘    └─────────────┘                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

### 1.3 设计理念

- **个人项目**: 专为单人使用优化，无需负载均衡
- **简单部署**: 仅使用 Docker Compose（无需K8s）
- **资源高效**: 目标 2核4G 服务器
- **易于扩展**: 轻松添加微信/iOS通知

---

## 2. 模块设计

### 2.1 后端9大核心模块

| 模块 | 职责 | 依赖 |
|------|------|------|
| **数据采集** | 从外部API获取股票数据、新闻 | 无 |
| **情感分析** | FinBERT情感分析（代理到AI服务） | 数据采集 |
| **LSTM预测** | LSTM价格预测（代理到AI服务） | 数据采集 |
| **Dexter分析** | 基本面分析（集成在py-service中） | 数据采集 |
| **综合选股** | 整合三因子，选出最优股票 | 情感分析、LSTM预测、Dexter分析 |
| **决策引擎** | 生成交易信号 | 综合选股、风控管理 |
| **风控管理** | 风控检查（止损、熔断） | 交易执行 |
| **交易执行** | 通过券商API执行交易 | 无 |
| **模型迭代** | 模型性能监控、训练数据收集、模型更新 | 交易执行、综合选股 |

### 2.2 AI服务集成 (py-service)

```
┌────────────────────────────────────────────────────────────────┐
│                    Python AI 服务 (8001)                         │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                      API层                                 │   │
│  │   /api/sentiment/*  |  /api/lstm/*  |  /api/dexter/*  │   │
│  └────────────────────────────┬────────────────────────────┘   │
│                               │                                │
│  ┌────────────────────────────▼────────────────────────────┐   │
│  │                     服务层                                │   │
│  │                                                          │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐   │   │
│  │  │ 情感分析服务  │  │ 预测服务    │  │ Dexter客户端  │   │   │
│  │  │ (FinBERT)  │  │   (LSTM)    │  │ (HTTP API)  │   │   │
│  │  └─────────────┘  └─────────────┘  └──────────────┘   │   │
│  └────────────────────────────┬────────────────────────────┘   │
│                               │                                │
│  ┌────────────────────────────▼────────────────────────────┐   │
│  │                      模型层                               │   │
│  │    FinBERT (transformers)  |  LSTM (tensorflow)        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└────────────────────────────────────────────────────────────────┘
```

### 2.3 模块依赖图

```
                    ┌─────────────┐
                    │   数据采集   │
                    └──────┬──────┘
                           │
         ┌─────────────────┼─────────────────┐
         │                 │                 │
         ▼                 ▼                 ▼
┌─────────────┐   ┌─────────────┐   ┌─────────────┐
│  情感分析    │   │ LSTM预测     │   │ Dexter分析  │
│  (py-service)│   │ (py-service)│   │ (py-service)│
└──────┬──────┘   └──────┬──────┘   └──────┬──────┘
       │                  │                  │
       └──────────────────┼──────────────────┘
                          │
                          ▼
               ┌─────────────────┐
               │    综合选股     │
               └────────┬────────┘
                        │
            ┌───────────┴───────────┐
            │                       │
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
                     │ (反馈)
                     ▼
              ┌─────────────┐
              │   模型迭代    │◄──────── 综合选股
              └──────┬──────┘
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
   ┌─────────────┐       ┌─────────────┐
   │ LSTM预测     │       │  情感分析    │
   │ (模型更新)   │       │  (模型更新)   │
   └─────────────┘       └─────────────┘
```

---

## 3. 服务通信

### 3.1 后端 → AI服务通信

```
后端 (Java)                                      AI服务 (Python)
┌──────────────────┐                          ┌────────────────────────┐
│                  │   HTTP/REST              │                        │
│  Feign Client    │ ─────────────────────▶  │  FastAPI Endpoints    │
│                  │   {                      │                        │
│  - sentiment()   │     "text": "..."        │  POST /api/sentiment  │
│  - predict()    │   }                      │  POST /api/lstm       │
│  - dexter()    │                          │  POST /api/dexter     │
│                  │ ◀────────────────────── │                        │
│                  │   {                      │  Response: JSON        │
│                  │     ...                  │                        │
│                  │   }                      │                        │
└──────────────────┘                          └────────────────────────┘
```

### 3.2 Feign Client定义

```java
@FeignClient(
    name = "ai-service",
    url = "${ai.service.url:http://localhost:8001}"
)
public interface AIServiceClient {
    
    // 情感分析
    @PostMapping("/api/sentiment/analyze")
    SentimentResponse analyzeSentiment(@Body SentimentRequest request);
    
    // LSTM预测
    @PostMapping("/api/lstm/predict")
    PredictionResponse predict(@Body PredictionRequest request);
    
    // Dexter分析
    @PostMapping("/api/dexter/analyze")
    DexterResponse analyzeDexter(@Body DexterRequest request);
}
```

### 3.3 AI服务API端点

#### 情感分析
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/sentiment/analyze` | 分析单条文本 |
| POST | `/api/sentiment/analyze/batch` | 批量分析 |
| POST | `/api/sentiment/analyze/news` | 新闻情感分析 |

#### LSTM预测
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/lstm/predict` | 预测次日价格 |
| POST | `/api/lstm/predict/batch` | 批量预测 |
| POST | `/api/lstm/forecast` | 多日预测 |
| GET | `/api/lstm/model/info` | 模型状态 |

#### Dexter分析
| 方法 | 路径 | 描述 |
|------|------|------|
| POST | `/api/dexter/analyze` | 获取基本面分析 |
| POST | `/api/dexter/suggestion` | 获取交易建议 |
| GET | `/api/dexter/quote/{code}` | 获取行情数据 |

---

## 4. 数据流程

### 4.1 每日交易流程

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              09:30 - 任务触发                                  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 1: 数据采集                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • fetchStockList()           - 获取股票列表                               │  │
│  │  • fetchRealTimePrices()      - 获取实时行情                               │  │
│  │  • fetchStockNews()           - 获取财经新闻                               │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                    ┌───────────────────┼───────────────────┐
                    ▼                   ▼                   ▼
┌───────────────────┴───┐   ┌───────────┴────────┐   ┌──────┴────────┐
│ Step 2: 情感分析      │   │ Step 3: LSTM预测  │   │ Step 4: Dexter │
│ (py-service)         │   │ (py-service)      │   │ (py-service)  │
│                      │   │                    │   │               │
│ • analyze(news)      │   │ • predict(data)    │   │ • analyze()   │
│ • calculateScore()   │   │ • getConfidence()  │   │ • getScore()  │
└──────────┬───────────┘   └──────────┬───────────┘   └───────┬────────┘
           │                          │                       │
           └──────────────────────────┼───────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 5: 综合选股                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • getComprehensiveRanking()                                              │  │
│  │  • 计算: Score = LSTM×0.4 + Sentiment×0.3 + Dexter×0.3                │  │
│  │  • 选出: Top1 + Top3备选                                                 │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 6: 风控检查                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • checkBeforeBuy()          - 检查是否可买入                               │  │
│  │  • 规则: 日亏损<3%, 月亏损<10%                                           │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                            ┌───────────┴───────────┐
                            │                       │
                            ▼                       ▼
                   ┌─────────────┐          ┌─────────────┐
                   │  风控通过    │          │  风控拦截    │
                   └──────┬──────┘          └─────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 8: 交易执行                                                             │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • executeBuy()            - 执行买入                                     │  │
│  │  • executeSell()           - 执行卖出                                     │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 9: 模型迭代 (后台异步)                                                   │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • collectTradingData()    - 收集交易数据                                   │  │
│  │  • evaluateModelPerformance() - 评估模型性能                               │  │
│  │  • triggerRetraining()     - 触发模型重训练 (当性能下降时)                │  │
│  │  • validateModel()        - 验证新模型                                     │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Step 10: 推送通知 (未来)                                                     │
│  ┌───────────────────────────────────────────────────────────────────────────┐  │
│  │  • 微信小程序           - 推送买入/卖出通知                              │  │
│  │  • iOS推送通知          - 推送交易结果                                   │  │
│  └───────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 部署架构

### 5.1 服务器资源 (2核4G)

| 服务 | CPU | 内存 | 说明 |
|------|-----|------|------|
| 前端 (Nginx) | 0.25 | 64MB | 静态文件 + API代理 |
| 后端 (Spring Boot) | 0.5 | 256MB | 业务逻辑 |
| AI服务 (Python) | 0.5 | 256MB | ML推理 |
| MySQL | 0.5 | 256MB | 数据量小 |
| MongoDB | 0.25 | 128MB | 文档存储 |
| Redis | 0.25 | 128MB | 缓存+会话 |

**总计: 约 2 CPU, 1GB RAM** (预留50%给系统)

### 5.2 基础设施部署 (阿里云服务器)

#### 服务器信息
| 项目 | 值 |
|------|-----|
| 服务器 | 阿里云 CentOS 7 |
| IP地址 | 124.220.36.95 |
| SSH端口 | 22 |
| SSH用户 | root |

#### 5.2.1 MySQL (MariaDB 5.5) 安装

```bash
# 安装方式: YUM (CentOS 7)
yum install -y mariadb-server

# 配置文件
/etc/my.cnf

# 数据目录
/var/lib/mysql

# 日志目录
/var/log/mariadb/mariadb.log

# 启动服务
systemctl start mariadb
systemctl enable mariadb
```

| 配置项 | 值 |
|--------|-----|
| 端口 | 6033 |
| 用户名 | root |
| 密码 | Root.123456 |
| 数据目录 | /var/lib/mysql |
| Socket | /var/lib/mysql/mysql.sock |

#### 5.2.2 Redis 7 安装

```bash
# 安装方式: YUM (CentOS 7)
yum install -y redis

# 配置文件
/etc/redis.conf

# 数据目录
/var/lib/redis

# 启动服务
systemctl start redis
systemctl enable redis
```

| 配置项 | 值 |
|--------|-----|
| 端口 | 6379 |
| 密码 | Root.123456 |
| 绑定地址 | 0.0.0.0 |
| 数据目录 | /var/lib/redis |
| 持久化 | RDB |

#### 5.2.3 MongoDB 6 安装

```bash
# 安装方式: YUM (CentOS 7)
# 添加MongoDB仓库
cat > /etc/yum.repos.d/mongodb-org.repo << 'EOF'
[mongodb-org-6.0]
name=MongoDB Repository
baseurl=https://repo.mongodb.org/yum/redhat/7/mongodb-org/6.0/x86_64/
gpgcheck=1
enabled=1
gpgkey=https://www.mongodb.org/static/pgp/server-6.0.asc
EOF

yum install -y mongodb-org

# 配置文件
/etc/mongod.conf

# 数据目录
/var/lib/mongo

# 日志目录
/var/log/mongodb/mongod.log

# 启动服务
systemctl start mongod
systemctl enable mongod
```

| 配置项 | 值 |
|--------|-----|
| 端口 | 27017 |
| 用户名 | admin |
| 密码 | Root.123456 |
| 数据目录 | /var/lib/mongo |
| 日志 | /var/log/mongodb/mongod.log |
| 认证 | 已启用 |

#### 5.2.4 安装后配置

##### MySQL (MariaDB) 配置
```bash
# 1. 修改端口为6033
sed -i '/\[mysqld\]/a port=6033' /etc/my.cnf

# 2. 设置root密码
mysqladmin -u root password 'Root.123456'

# 3. 授权远程访问
mysql -u root -p'Root.123456' -e "GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' IDENTIFIED BY 'Root.123456' WITH GRANT OPTION; FLUSH PRIVILEGES;"

# 4. 重启服务
systemctl restart mariadb
```

##### Redis 配置
```bash
# 1. 修改绑定地址和密码
sed -i 's/^bind 127.0.0.1/bind 0.0.0.0/' /etc/redis.conf
sed -i 's/^protected-mode yes/protected-mode no/' /etc/redis.conf
echo "requirepass Root.123456" >> /etc/redis.conf

# 2. 重启服务
systemctl restart redis
```

##### MongoDB 配置
```bash
# 1. 修改配置文件启用认证
cat > /etc/mongod.conf << 'EOF'
storage:
  dbPath: /var/lib/mongo
  journal:
    enabled: true
systemLog:
  destination: file
  logAppend: true
  path: /var/log/mongodb/mongod.log
net:
  port: 27017
  bindIp: 0.0.0.0
security:
  authorization: enabled
EOF

# 2. 重启服务
systemctl restart mongod

# 3. 创建管理员用户
mongosh --eval '
db = db.getSiblingDB("admin");
db.createUser({
  user: "admin",
  pwd: "Root.123456",
  roles: [{ role: "root", db: "admin" }]
});
'

# 4. 重启服务使认证生效
systemctl restart mongod
```

#### 5.2.6 连接信息汇总

| 服务 | 主机 | 端口 | 用户名 | 密码 |
|------|------|------|--------|------|
| MySQL | 124.220.36.95 | 6033 | root | Root.123456 |
| Redis | 124.220.36.95 | 6379 | - | Root.123456 |
| MongoDB | 124.220.36.95 | 27017 | admin | Root.123456 |

#### 5.2.7 服务管理命令

```bash
# 查看服务状态
systemctl status mariadb
systemctl status redis
systemctl status mongod

# 重启服务
systemctl restart mariadb
systemctl restart redis
systemctl restart mongod

# 停止服务
systemctl stop mariadb
systemctl stop redis
systemctl stop mongod

# 查看端口监听
netstat -tlnp | grep -E "6033|6379|27017"
```

### 5.3 Docker Compose 生产部署

```yaml
version: '3.8'

services:
  frontend:
    build: ./stock-front
    container_name: stock-frontend
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - stock-network
    restart: unless-stopped

  backend:
    build: ./stock-backend
    container_name: stock-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/stock_trading
      - SPRING_REDIS_HOST=redis
      - STOCK_AI_SERVICE_URL=http://py-service:8001
    depends_on:
      - mysql
      - redis
      - py-service
    networks:
      - stock-network
    restart: unless-stopped

  py-service:
    build: ./py-service
    container_name: stock-py-service
    ports:
      - "8001:8001"
    environment:
      - PYTHONUNBUFFERED=1
      - DEXTER_API_KEY=${DEXTER_API_KEY}
    networks:
      - stock-network
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    container_name: stock-mysql
    ports:
      - "6033:3306"
    environment:
      - MYSQL_ROOT_PASSWORD=Root.123456
      - MYSQL_DATABASE=stock_trading
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - stock-network
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: stock-redis
    ports:
      - "6379:6379"
    command: redis-server --requirepass Root.123456
    volumes:
      - redis-data:/data
    networks:
      - stock-network
    restart: unless-stopped

  mongodb:
    image: mongo:6
    container_name: stock-mongodb
    ports:
      - "27017:27017"
    environment:
      - MONGO_INITDB_ROOT_USERNAME=admin
      - MONGO_INITDB_ROOT_PASSWORD=Root.123456
    volumes:
      - mongodb-data:/data/db
    networks:
      - stock-network
    restart: unless-stopped

networks:
  stock-network:
    driver: bridge

volumes:
  mysql-data:
  redis-data:
  mongodb-data:
```

### 5.3 部署命令

```bash
# 构建并启动
docker-compose up -d --build

# 查看日志
docker-compose logs -f

# 停止
docker-compose down

# 重新构建特定服务
docker-compose up -d --build py-service
```

### 5.4 端口映射

| 服务 | 端口 | 访问方式 |
|------|------|----------|
| 前端 | 80 | 浏览器 |
| 后端 | 8080 | API |
| AI服务 | 8001 | 内部 |
| MySQL | 6033 | 开发/外部 |
| Redis | 6379 | 开发/外部 |
| MongoDB | 27017 | 开发/外部 |

---

## 6. 技术栈

### 6.1 后端 (Spring Boot)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.2.x |
| JDK | Java | 17 |
| ORM | MyBatis-Plus | 3.5.x |
| 调度 | Quartz | - |
| 缓存 | Redis | 7.x |
| WebSocket | Spring WebSocket | - |

### 6.2 前端 (React)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | React | 18.x |
| UI库 | Ant Design | 5.x |
| 构建工具 | UmiJS | 4.x |

### 6.3 AI服务 (Python)

| 组件 | 技术 | 版本 |
|------|------|------|
| 框架 | FastAPI | 0.109.x |
| 服务器 | Uvicorn | 0.27.x |
| NLP模型 | FinBERT | - |
| DL框架 | TensorFlow | 2.15.x |
| HTTP客户端 | httpx | - |

### 6.4 基础设施

| 组件 | 技术 | 版本 |
|------|------|------|
| 容器 | Docker | 1.13.1 |
| 数据库 | MySQL (MariaDB) | 5.5.68 |
| 文档数据库 | MongoDB | 6.0.27 |
| 缓存 | Redis | 3.2.12 |
| CI/CD | GitHub Actions | - |

---

## 7. 未来扩展

### 7.1 微信小程序

```
┌────────────────────────────────────────────────────────────────┐
│                      微信小程序                                  │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐        │
│  │  今日推荐     │  │  持仓查询     │  │  交易记录     │        │
│  └──────────────┘  └──────────────┘  └──────────────┘        │
│                                                                 │
│  功能:                                                          │
│  • 每日股票推荐                                                  │
│  • 持仓追踪                                                      │
│  • 买入/卖出推送通知                                             │
└────────────────────────────────────────────────────────────────┘
```

### 7.2 iOS App

```
┌────────────────────────────────────────────────────────────────┐
│                       iOS App                                   │
│                                                                 │
│  框架: SwiftUI / React Native                                  │
│                                                                 │
│  功能:                                                          │
│  • 同微信小程序功能                                              │
│  • 推送通知 (APNs)                                              │
│  • 小组件快速查看                                                │
└────────────────────────────────────────────────────────────────┘
```

### 7.3 通知服务设计

```python
# py-service/app/services/notification.py

class NotificationService:
    """统一通知服务"""
    
    def __init__(self):
        self.wechat_client = WeChatClient()
        self.apns_client = APNsClient()
    
    async def send_trading_notification(self, signal: TradingSignal):
        """发送交易信号通知"""
        
        message = self._build_message(signal)
        
        # 发送到微信
        await self.wechat_client.send(message)
        
        # 发送推送通知
        await self.apns```

---

## _client.send(message)
附录: 模块接口汇总

| 模块 | 输入 | 输出 |
|------|------|------|
| 数据采集 | - | StockInfo, DailyPrice, News |
| 情感分析 | text | SentimentResult |
| LSTM预测 | OHLCV数据 | PredictionResult |
| Dexter分析 | stockCode | DexterResult |
| 综合选股 | - | SelectResult |
| 决策引擎 | AnalysisResult | TradingSignal |
| 风控管理 | - | RiskCheckResult |
| 交易执行 | OrderRequest | OrderResult |
| 模型迭代 | TradingRecord, ModelPerformance | ModelUpdateResult |
