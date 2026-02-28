# AGENTS.md - AI 股票交易系统

## 项目结构

```
stock-trading/
├── backend/              # Spring Boot 3.2 + Java 17 后端服务
│   └── src/main/java/com/stock/
│       ├── config/        # 全局配置
│       ├── databus/       # 数据采集模块
│       │   └── controller # 前端路由控制器
│       ├── models/        # AI模型模块
│       │   └── controller # AI模型相关控制器
│       ├── strategy/      # 交易策略模块
│       ├── executor/      # 交易执行模块
│       └── (根包)         # 主启动类
│
├── frontend/            # React + Ant Design Pro 前端
│   └── src/
│       ├── pages/       # 页面组件
│       ├── components/  # 通用组件
│       └── services/    # API服务
│
├── mobile/              # React Native 移动端
│
└── documents/           # 设计文档
```

## 构建命令

### Backend

```bash
cd backend

# 编译并打包
mvn clean package

# 启动应用
mvn spring-boot:run

# 运行测试
mvn test

# 跳过测试打包
mvn clean package -DskipTests
```

### Frontend

```bash
cd frontend

# 安装依赖
npm install
# 或使用 pnpm
pnpm install

# 启动开发服务器
npm start

# 构建生产版本
npm run build

# 代码检查
npm run lint

# 修复代码问题
npm run lint:fix
```

## 代码规范

### Java (Backend)

- **Java 版本**: 17 (Spring Boot 3.2.2)
- **命名**: CamelCase 类/方法, UPPER_SNAKE 常量
- **Lombok**: `@Data`, `@Slf4j`, `@RequiredArgsConstructor`
- **导入**: 无通配符, 静态导入放最后
- **错误处理**: `@ControllerAdvice` + `Response<T>` 封装
- **API**: RESTful, `@RequestBody` POST/PUT, `@PathVariable` ID

### TypeScript/React (Frontend)

- **框架**: React 18, Ant Design Pro 5, UmiJS 4
- **命名**: PascalCase 组件, camelCase hooks (use* 前缀)
- **类型**: 严格模式, 定义接口不使用 `any`
- **组件**: 函数式组件 + Hooks
- **状态**: useState, useRef, useEffect

## 模块架构

| 模块 | 包路径 | 功能 |
|------|--------|------|
| 数据采集 | com.stock.databus | 股票数据获取、新闻采集 |
| AI模型 | com.stock.models | LSTM预测、情感分析 |
| 交易策略 | com.stock.strategy | 决策引擎、股票筛选 |
| 交易执行 | com.stock.executor | 订单执行、风险控制 |
| 全局配置 | com.stock.config |全局配置和Web资源处理 |

## 关键端口

- 后端 API: http://localhost:8080
- 前端: http://localhost:8000
- 代理: `/api/*` → `http://localhost:8080`

## 开发流程

1. 修改代码前先更新设计文档
2. 编写测试用例
3. 实现代码
4. 运行测试验证
5. 确保 lint 通过
6. 提交代码

## Git 提交规范

```
type(scope): subject

类型: feat, fix, docs, style, refactor, test, chore
示例: feat(databus): add stock price collection
```

## 环境要求

- Java: 17+
- Maven: 3.6+
- Node.js: 16+
- npm: 8+ 或 pnpm