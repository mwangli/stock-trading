# AI Shopping

AI驱动的智能购物系统 - 全栈应用

## 技术栈

### 后端 (Backend)
- **Java**: 17
- **Spring Boot**: 3.0.x
- **Maven**: 3.6.2
- **数据库**: H2(开发) / MySQL(生产)
- **ORM**: MyBatis-Plus 3.5.5

### 前端 (Frontend)
- **React**: 18.x
- **Ant Design Pro**: 5.x
- **Umi**: 4.x
- **TypeScript**: 5.x

## 项目结构

```
ai-shopping/
├── pom.xml                    # 父项目POM
├── README.md                  # 项目说明
├── backend/                   # 后端模块
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/example/aishopping/
│       │   │       └── AiShoppingApplication.java
│       │   └── resources/
│       │       └── application.yml
│       └── test/
├── frontend/                  # 前端模块
│   ├── pom.xml
│   ├── package.json
│   ├── .umirc.ts
│   ├── tsconfig.json
│   └── src/
│       ├── pages/             # 页面组件
│       ├── services/          # API服务
│       └── app.tsx            # 应用入口
└── doc/                       # 项目文档
    ├── requirements/          # 需求文档
    │   └── PRD-001-商品数据采集系统.md
    └── design/                # 设计文档
        ├── DESIGN-001-系统架构设计.md
        └── DESIGN-002-数据库设计.md
```

## 快速开始

### 环境要求
- Java 17+
- Maven 3.6.2
- Node.js 16+ (前端开发)

### 1. 构建整个项目

```bash
D:\apache-maven-3.6.2\bin\mvn clean install
```

### 2. 仅构建后端

```bash
D:\apache-maven-3.6.2\bin\mvn clean install -pl backend
```

### 3. 启动后端服务

```bash
cd backend
D:\apache-maven-3.6.2\bin\mvn spring-boot:run
```

访问：
- API服务: http://localhost:8080
- H2控制台: http://localhost:8080/h2-console
- 健康检查: http://localhost:8080/actuator/health

### 4. 启动前端开发服务器

```bash
cd frontend
npm install
npm run dev
```

访问：http://localhost:8000

## 开发指南

### 后端开发
1. 在 `backend/src/main/java/com/example/aishopping/` 下创建包
2. 实现业务逻辑 (Controller -> Service -> Repository)
3. 添加单元测试

### 前端开发
1. 在 `frontend/src/pages/` 下创建新页面
2. 在 `.umirc.ts` 中配置路由
3. 在 `frontend/src/services/` 中添加API调用

### API 代理配置
前端开发服务器已配置代理，所有 `/api` 请求会自动转发到 `http://localhost:8080`

## 项目文档

项目文档位于 `doc/` 目录：

- **[需求文档](./doc/requirements/)** - 产品需求文档
  - [PRD-001: 商品数据采集系统](./doc/requirements/PRD-001-商品数据采集系统.md)
- **[设计文档](./doc/design/)** - 系统架构和数据库设计
  - [DESIGN-001: 系统架构设计](./doc/design/DESIGN-001-系统架构设计.md)
  - [DESIGN-002: 数据库设计](./doc/design/DESIGN-002-数据库设计.md)

## 联系方式

如有问题，请联系开发团队。
