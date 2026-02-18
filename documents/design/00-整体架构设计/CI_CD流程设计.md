# 股票交易系统 - CI/CD 流程设计与分析

## 目录

1. [CI/CD 概述](#1-cicd-概述)
2. [现有流程分析](#2-现有流程分析)
3. [流程详细设计](#3-流程详细设计)
4. [环境配置](#4-环境配置)
5. [部署配置](#5-部署配置)
6. [监控与回滚](#6-监控与回滚)
7. [优化建议](#7-优化建议)

---

## 1. CI/CD 概述

### 1.1 什么是 CI/CD

| 概念 | 全称 | 含义 |
|------|------|------|
| CI | Continuous Integration | 持续集成 - 频繁地向主干分支合并代码，并自动构建测试 |
| CD | Continuous Delivery | 持续交付 - 自动将代码部署到测试/生产环境 |
| CD | Continuous Deployment | 持续部署 - 自动将代码完全部署到生产环境 |

### 1.2 项目 CI/CD 目标

- **自动化构建**：代码提交后自动触发构建
- **自动化测试**：运行单元测试和集成测试
- **自动化部署**：通过 SSH 自动部署到服务器
- **镜像管理**：使用阿里云容器镜像服务管理镜像
- **版本追踪**：通过 Git Tag 管理发布版本

---

## 2. 现有流程分析

### 2.1 当前 CI/CD 架构

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           GitHub Actions                                        │
│                                                                                 │
│  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ┌─────────────┐       │
│  │   触发      │──▶│   构建测试   │──▶│  构建镜像   │──▶│   部署      │       │
│  │  Push/PR   │   │  Maven/NPM  │   │   Docker    │   │   SSH      │       │
│  └─────────────┘   └─────────────┘   └─────────────┘   └─────────────┘       │
│       │                 │                │                │                  │
│       │                 │                │                │                  │
│       ▼                 ▼                ▼                ▼                  │
│  main 分支         后端测试          推送镜像         服务器部署              │
│  Tag v*.*.*        前端构建          到阿里云         自动更新               │
└─────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         阿里云服务器 (124.220.36.95)                            │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                      Docker Compose                                       │   │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐     │   │
│  │  │Frontend │  │Backend  │  │AI Service│  │ MySQL   │  │ Redis   │     │   │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘     │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 2.2 工作流文件位置

- **配置文件**: `.github/workflows/ci-cd.yml`
- **镜像仓库**: 阿里云容器镜像服务 (registry.cn-shenzhen.aliyuncs.com)
- **部署方式**: Docker Compose

### 2.3 当前流程阶段

| 阶段 | 触发条件 | 执行内容 |
|------|----------|----------|
| **Build & Test** | Push/PR | 安装依赖、运行测试、构建前端 |
| **Build Images** | Push main/Tag | 构建并推送 Docker 镜像到阿里云 |
| **Deploy** | Push main | SSH 部署到服务器 |
| **Release** | Tag v*.*.* | 创建 GitHub Release |

---

## 3. 流程详细设计

### 3.1 第一阶段：构建与测试 (Build and Test)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Build & Test Pipeline                                  │
│                                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────┐   │
│  │  Checkout    │──▶│  Setup JDK   │──▶│  Setup Node  │──▶│ Setup Python│   │
│  │  代码检出    │    │    21        │    │    20        │    │   3.11     │   │
│  └──────────────┘    └──────────────┘    └──────────────┘    └────────────┘   │
│                                                                                 │
│         │                 │                 │                 │                │
│         ▼                 ▼                 ▼                 ▼                │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐    ┌────────────┐   │
│  │ Maven Cache  │    │ NPM Cache    │    │   Pip Cache  │    │            │   │
│  │   依赖缓存   │    │   依赖缓存   │    │   依赖缓存   │    │            │   │
│  └──────────────┘    └──────────────┘    └──────────────┘    └────────────┘   │
│                                                                                 │
│         │                 │                 │                                  │
│         ▼                 ▼                 ▼                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                          并行执行                                         │   │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                  │   │
│  │  │  后端测试   │   │  前端构建   │   │ Python Lint │                  │   │
│  │  │ mvn test    │   │ pnpm build  │   │   ruff      │                  │   │
│  │  └─────────────┘   └─────────────┘   └─────────────┘                  │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**详细步骤：**

| 步骤 | 命令 | 说明 |
|------|------|------|
| Checkout | actions/checkout@v4 | 检出代码 |
| Setup JDK | setup-java@v4 | JDK 21，带 Maven 缓存 |
| Setup Node | setup-node@v4 | Node.js 20，带 pnpm 缓存 |
| Setup Python | setup-python@v3 | Python 3.11，带 pip 缓存 |
| Install Frontend | pnpm install | 安装前端依赖 |
| Install Python | pip install -r requirements.txt | 安装 Python 依赖 |
| Backend Tests | mvn test -B | 运行后端单元测试 |
| Frontend Build | pnpm run build | 构建前端生产包 |
| Python Lint | ruff check app/ | Python 代码检查 |

**缓存策略：**

```yaml
# Maven 缓存
cache: 'maven'

# npm/pnpm 缓存
cache: 'npm'
cache-dependency-path: stock-front/pnpm-lock.yaml

# pip 缓存
cache: 'pip'
cache-dependency-path: py-service/requirements.txt
```

---

### 3.2 第二阶段：构建镜像 (Build Images)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          Build Images Pipeline                                   │
│                                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                     │
│  │  Checkout    │──▶│ Setup Buildx │──▶│   Login      │                     │
│  │  代码检出    │    │  Docker构建  │    │  阿里云仓库  │                     │
│  └──────────────┘    └──────────────┘    └──────────────┘                     │
│                                                                     │            │
│                                                                     ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    并行构建三个镜像                                      │   │
│  │                                                                         │   │
│  │  ┌─────────────┐   ┌─────────────┐   ┌─────────────┐                  │   │
│  │  │   Backend   │   │  Frontend   │   │   Python    │                  │   │
│  │  │  Dockerfile │   │  Dockerfile │   │  Dockerfile │                  │   │
│  │  │  Multi-arch │   │    Nginx    │   │  FastAPI    │                  │   │
│  │  └──────┬──────┘   └──────┬──────┘   └──────┬──────┘                  │   │
│  │         │                 │                 │                          │   │
│  │         ▼                 ▼                 ▼                          │   │
│  │  ┌───────────────────────────────────────────────────────────────┐    │   │
│  │  │           推送到阿里云容器镜像服务                               │    │   │
│  │  │  registry.cn-shenzhen.aliyuncs.com/mwangli/...                 │    │   │
│  │  └───────────────────────────────────────────────────────────────┘    │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                 │
│  ┌──────────────┐    ┌──────────────┐                                          │
│  │   设置输出    │──▶│  镜像标签    │                                          │
│  │  outputs     │    │ sha + latest │                                          │
│  └──────────────┘    └──────────────┘                                          │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**镜像构建配置：**

| 镜像 | 上下文 | 标签策略 |
|------|--------|----------|
| stock-backend | ./stock-backend | :sha + :latest |
| stock-frontend | ./stock-front | :sha + :latest |
| stock-py-service | ./py-service | :sha + :latest |

**镜像名称格式：**
```
registry.cn-shenzhen.aliyuncs.com/mwangli/{镜像名}:{版本}
```

**缓存策略：**
```yaml
cache-from: type=gha        # GitHub Actions 缓存
cache-to: type=gha,mode=max  # 最大缓存模式
```

---

### 3.3 第三阶段：部署 (Deploy)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           Deploy Pipeline                                        │
│                                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                     │
│  │  获取镜像    │──▶│  生成部署    │──▶│   SSH 连接   │                     │
│  │  from output │    │   脚本       │    │   服务器     │                     │
│  └──────────────┘    └──────────────┘    └──────────────┘                     │
│                                                                     │            │
│                                                                     ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        服务器执行                                        │   │
│  │                                                                         │   │
│  │  1. cd /opt/stock-trading                                             │   │
│  │  2. git pull origin main                                              │   │
│  │  3. docker-compose -f docker-compose.production.yml pull             │   │
│  │  4. docker-compose -f docker-compose.production.yml up -d            │   │
│  │  5. docker system prune -f                                           │   │
│  │                                                                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

**部署步骤：**

| 步骤 | 命令 | 说明 |
|------|------|------|
| Checkout | actions/checkout@v4 | 检出部署配置 |
| 生成脚本 | 创建 deploy.sh | 替换镜像版本 |
| SSH 连接 | appleboy/ssh-action@v1.0.0 | 连接服务器 |
| Git Pull | git pull origin main | 拉取最新代码 |
| Pull 镜像 | docker-compose pull | 拉取新镜像 |
| 重启服务 | docker-compose up -d | 重启容器 |
| 清理 | docker system prune | 清理未使用资源 |

---

### 3.4 第四阶段：发布 Release

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                          Release Pipeline                                        │
│                                                                                 │
│  ┌──────────────┐    ┌──────────────┐                                         │
│  │  触发条件   │──▶│  创建 Release │                                         │
│  │  Tag v*.*.* │    │  GitHub API  │                                         │
│  └──────────────┘    └──────────────┘                                         │
│                                                                     │            │
│                                                                     ▼            │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                        Release 内容                                     │   │
│  │                                                                         │   │
│  │  • Tag: v1.0.0                                                        │   │
│  │  • Name: Stock Trading v1.0.0                                         │   │
│  │  • 自动生成变更日志（可选）                                              │   │
│  │  • 预构建镜像已推送到阿里云                                              │   │
│  │                                                                         │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 4. 环境配置

### 4.1 GitHub Secrets 配置

| Secret 名称 | 说明 | 示例值 |
|-------------|------|--------|
| ALIYUN_REGISTRY_USER | 阿里云镜像仓库用户名 | admin |
| ALIYUN_REGISTRY_PASSWORD | 阿里云镜像仓库密码 | ******** |
| SERVER_HOST | 服务器 IP 地址 | 124.220.36.95 |
| SERVER_USER | SSH 用户名 | root |
| SERVER_PASSWORD | SSH 密码 | ******** |

### 4.2 阿里云容器镜像服务配置

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     阿里云容器镜像服务 (ACR)                                     │
│                                                                                 │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │  命名空间: mwangli                                                       │   │
│  │                                                                         │   │
│  │  镜像列表:                                                               │   │
│  │  ├── stock-backend        │ latest │ sha-xxx │                         │   │
│  │  ├── stock-frontend       │ latest │ sha-xxx │                         │   │
│  │  └── stock-py-service    │ latest │ sha-xxx │                         │   │
│  │                                                                         │   │
│  │  地域: 华南3 (深圳)                                                      │   │
│  │  访问域名: registry.cn-shenzhen.aliyuncs.com                            │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 4.3 服务器环境要求

| 软件 | 版本 | 说明 |
|------|------|------|
| Docker | 20.10+ | 容器引擎 |
| Docker Compose | 2.0+ | 容器编排 |
| Git | 2.0+ | 代码管理 |
| 系统 | CentOS 7+ / Ubuntu 20.04+ | 操作系统 |

---

## 5. 部署配置

### 5.1 生产环境 docker-compose 配置

需要创建 `docker-compose.production.yml` 文件：

```yaml
version: '3.8'

services:
  frontend:
    image: ${REGISTRY}/mwangli/stock-frontend:${IMAGE_TAG}
    container_name: stock-frontend
    ports:
      - "8000:80"
    depends_on:
      - backend
    networks:
      - stock-network
    restart: unless-stopped

  backend:
    image: ${REGISTRY}/mwangli/stock-backend:${IMAGE_TAG}
    container_name: stock-backend
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=jdbc:mysql://mysql:3306/stock_trading
      - SPRING_DATA_MONGODB_URI=mongodb://mongo:27017/stock_trading
      - SPRING_REDIS_HOST=redis
      - SPRING_REDIS_PORT=6379
      - STOCK_AI_SERVICE_URL=http://stock-service:8001
    depends_on:
      - mysql
      - mongo
      - redis
      - stock-service
    networks:
      - stock-network
    restart: unless-stopped

  stock-service:
    image: ${REGISTRY}/mwangli/stock-py-service:${IMAGE_TAG}
    container_name: stock-ai-service
    ports:
      - "8001:8001"
    environment:
      - PYTHONUNBUFFERED=1
    networks:
      - stock-network
    restart: unless-stopped

  mysql:
    image: mysql:8.0
    container_name: stock-mysql
    environment:
      - MYSQL_ROOT_PASSWORD=root123
      - MYSQL_DATABASE=stock_trading
    ports:
      - "3306:3306"
    volumes:
      - mysql-data:/var/lib/mysql
    networks:
      - stock-network
    restart: unless-stopped

  mongo:
    image: mongo:6.0
    container_name: stock-mongo
    environment:
      - MONGO_INITDB_DATABASE=stock_trading
    ports:
      - "27017:27017"
    volumes:
      - mongo-data:/data/db
    networks:
      - stock-network
    restart: unless-stopped

  redis:
    image: redis:7-alpine
    container_name: stock-redis
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    networks:
      - stock-network
    restart: unless-stopped

networks:
  stock-network:
    driver: bridge

volumes:
  mysql-data:
  mongo-data:
  redis-data:
```

### 5.2 部署目录结构

```
/opt/stock-trading/
├── .env                    # 环境变量配置
├── docker-compose.yml      # 开发环境
├── docker-compose.production.yml  # 生产环境
├── stock-backend/          # 代码
├── stock-frontend/
├── stock-service/
└── backup/                # 备份目录
```

---

## 6. 监控与回滚

### 6.1 健康检查配置

```yaml
services:
  backend:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

  stock-service:
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8001/api/health"]
      interval: 30s
      timeout: 10s
      retries: 3
```

### 6.2 回滚流程

```bash
# 方式1：使用上一个版本的镜像回滚
docker-compose -f docker-compose.production.yml pull
docker-compose -f docker-compose.production.yml up -d

# 方式2：指定特定版本回滚
IMAGE_TAG=v1.0.0
docker-compose -f docker-compose.production.yml up -d --build

# 方式3：使用 docker history 查看历史
docker history ${IMAGE_NAME}:${TAG}
```

### 6.3 监控脚本

```bash
#!/bin/bash
# monitor.sh - 服务监控脚本

echo "=== 容器状态 ==="
docker ps

echo "=== 资源使用 ==="
docker stats --no-stream

echo "=== 服务健康检查 ==="
curl -sf http://localhost:8000/ || echo "Frontend: FAIL"
curl -sf http://localhost:8080/actuator/health || echo "Backend: FAIL"
curl -sf http://localhost:8001/api/health || echo "AI Service: FAIL"

echo "=== 最近日志 ==="
docker-compose logs --tail=50
```

---

## 7. 优化建议

### 7.1 当前流程不足

| 问题 | 影响 | 建议 |
|------|------|------|
| 缺少测试报告 | 无法评估测试覆盖率 | 添加 JaCoCo / coverage |
| 部署脚本未分离 | 不够灵活 | 使用 ansible/terraform |
| 无回滚自动机制 | 回滚困难 | 添加回滚 workflow |
| 无灰度发布 | 风险高 | 考虑蓝绿部署 |

### 7.2 优化方案

#### 7.2.1 添加测试报告

```yaml
- name: Generate coverage reports
  run: |
    mvn jacoco:report
    pnpm run test -- --coverage

- name: Upload coverage
  uses: codecov/codecov-action@v3
  with:
    files: ./stock-backend/target/site/jacoco/jacoco.xml
```

#### 7.2.2 添加回滚 Workflow

```yaml
name: Rollback

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Version to rollback to'
        required: true

jobs:
  rollback:
    runs-on: ubuntu-latest
    steps:
      - name: Rollback deployment
        uses: appleboy/ssh-action@v1.0.0
        with:
          host: ${{ secrets.SERVER_HOST }}
          script: |
            cd /opt/stock-trading
            docker-compose -f docker-compose.production.yml pull ${{ github.event.inputs.version }}
            docker-compose -f docker-compose.production.yml up -d
```

### 7.3 完整 CI/CD 流程图

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           完整 CI/CD 流程                                         │
│                                                                                 │
│  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐  │
│  │   代码推送   │────▶│  自动构建   │────▶│  单元测试   │────▶│  集成测试   │  │
│  │  git push   │     │   Maven     │     │   JUnit    │     │   API Test  │  │
│  └─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘  │
│                                                                         │      │
│       │                                                               │      │
│       │ 失败                                                           ▼      │
│       │                                                        ┌─────────────┐  │
│       │                                                        │   构建镜像   │  │
│       │                                                        │   Docker    │  │
│       │                                                        └──────┬──────┘  │
│       │                                                               │         │
│       │ 成功                                                          ▼         │
│       │                                                        ┌─────────────┐  │
│       │                                                        │  推送镜像   │  │
│       │                                                        │   ACR      │  │
│       │                                                        └──────┬──────┘  │
│       │                                                               │         │
│       │                                                               ▼         │
│       │  Tag v*.*.*                                           ┌─────────────┐  │
│       │                                                        │  部署生产   │  │
│       │                                                        │   SSH      │  │
│       │                                                        └──────┬──────┘  │
│       │                                                               │         │
│       │                                                               ▼         │
│       │                                                      ┌─────────────┐  │
│       │                                                      │  健康检查   │  │
│       │                                                      └──────┬──────┘  │
│       │                                                             │         │
│       ▼       ◀────────────────────────────────────────────────────┘         │
│  ┌─────────────┐                                                         │      │
│  │ 创建 Release│◀────────────── 失败 ──────────────── 成功              │      │
│  │   GitHub   │                                                         ▼      │
│  └─────────────┘                                                  ┌─────────────┐  │
│                                                                   │    监控     │  │
│                                                                   └─────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 7.4 实施优先级

| 优先级 | 优化项 | 工作量 | 收益 |
|--------|--------|--------|------|
| **P0** | 创建生产环境 docker-compose.yml | 低 | 高 |
| **P0** | 配置 GitHub Secrets | 低 | 高 |
| **P1** | 添加健康检查 | 中 | 高 |
| **P1** | 添加回滚 Workflow | 中 | 高 |
| **P2** | 添加测试覆盖率报告 | 中 | 中 |
| **P2** | 添加 Slack/钉钉通知 | 低 | 中 |

---

## 8. 总结

### 8.1 当前 CI/CD 流程优势

1. **自动化程度高**：从代码提交到生产部署全自动化
2. **多语言支持**：同时构建 Java、Node.js、Python 三个服务
3. **镜像管理**：使用阿里云 ACR 管理镜像版本
4. **条件触发**：main 分支推送和 Tag 触发不同流程

### 8.2 实施步骤

1. **配置 Secrets**：在 GitHub 仓库设置中添加阿里云和服务器凭据
2. **创建生产配置**：编写 `docker-compose.production.yml`
3. **测试流程**：在测试分支验证 CI/CD 流程
4. **监控告警**：配置服务监控和通知

---

*文档版本: v1.0*  
*更新日期: 2026-02-18*  
*相关文件: .github/workflows/ci-cd.yml*
