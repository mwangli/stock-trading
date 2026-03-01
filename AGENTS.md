# AGENTS.md - AI 股票交易系统开发指南

## 项目结构

```
stock-trading/
├── backend/              # Spring Boot 3.2 + Java 17 后端服务
│   └── src/main/java/com/stock/
│       ├── config/        # 全局配置
│       ├── databus/       # 数据采集模块
│       ├── models/        # AI 模型模块
│       ├── strategy/      # 交易策略模块
│       ├── executor/      # 交易执行模块
│       └── (根包)         # 主启动类
│
├── frontend/            # React + Ant Design Pro 前端
│   └── src/
│       ├── pages/       # 页面组件
│       ├── components/  # 通用组件
│       └── services/    # API 服务
│
└── documents/           # 设计文档
```

## 构建命令

### Backend (Maven)

```bash
cd backend

# 编译并打包
mvn clean package

# 启动应用
mvn spring-boot:run

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 跳过测试打包
mvn clean package -DskipTests
```

### Frontend (npm/pnpm)

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm start

# 构建生产版本
npm run build

# 代码检查
npm run lint

# 修复代码问题
npm run lint:fix

# 类型检查
npm run tsc

# 运行测试
npm test
```

## 代码规范

### Java (Backend)

- **Java 版本**: 17 (Spring Boot 3.2.2)
- **命名规范**: 
  - 类/方法：CamelCase
  - 常量：UPPER_SNAKE_CASE
  - 包名：全小写
- **Lombok**: `@Data`, `@Slf4j`, `@RequiredArgsConstructor`
- **导入规则**: 
  - 禁止通配符导入
  - 静态导入放最后
  - 按组组织（标准库、第三方、项目内部）
- **错误处理**: 
  - 统一使用 `@ControllerAdvice` + `Response<T>` 封装
  - 记录详细日志，返回友好错误信息
- **API 设计**: 
  - RESTful 风格
  - POST/PUT 使用 `@RequestBody`
  - **测试**: JUnit 5 + Mockito
- **ORM**: Spring Data JPA (自动建库建表，无需 SQL 脚本)

### TypeScript/React (Frontend)

- **框架**: React 18, Ant Design Pro 5, UmiJS 4
- **命名规范**:
  - 组件：PascalCase
  - Hooks：camelCase, use* 前缀
  - 类型接口：PascalCase, 禁止使用 `any`
- **组件规范**:
  - 使用函数式组件 + Hooks
  - 状态管理：useState, useRef, useEffect
- **代码格式**:
  - 单引号 (singleQuote: true)
  - 尾随逗号 (trailingComma: 'all')
  - 行宽 100 字符 (printWidth: 100)
  - LF 换行 (endOfLine: 'lf')
- **编辑器配置** (.editorconfig):
  - 缩进：2 空格
  - 字符集：UTF-8
  - 删除行尾空格
  - 文件末尾空行
- **路径别名**:
  - `@/*` → `./src/*`
  - `@@/*` → `./src/.umi/*`

## 模块架构

| 模块 | 包路径 | 功能 |
|------|--------|------|
| 数据采集 | com.stock.databus | 股票数据获取、新闻采集 |
| AI 模型 | com.stock.models | LSTM 预测、情感分析 |
| 交易策略 | com.stock.strategy | 决策引擎、股票筛选 |
| 交易执行 | com.stock.executor | 订单执行、风险控制 |
| 全局配置 | com.stock.config | 全局配置和 Web 资源处理 |

## 关键端口

- 后端 API: http://localhost:8080
- 前端开发：http://localhost:8000
- API 代理：`/api/*` → `http://localhost:8080`
- Swagger UI: http://localhost:8080/swagger-ui.html

## 开发流程

1. **需求分析**: 更新 `documents/requirements/` 下的需求文档
2. **设计评审**: 更新 `documents/design/` 下的设计文档
3. **代码实现**: 按照本规范编写代码
4. **测试验证**: 编写单元测试和集成测试
5. **代码检查**: 确保 lint 和 type check 通过
6. **提交代码**: 遵循 Git 提交规范

## Git 提交规范

```
type(scope): subject

类型：
- feat: 新功能
- fix: Bug 修复
- docs: 文档更新
- style: 代码格式调整
- refactor: 重构
- test: 测试相关
- chore: 构建/工具链

示例：feat(databus): add stock price collection
```


