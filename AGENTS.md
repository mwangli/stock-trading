# AGENTS.md - AI 股票交易系统开发指南

## 项目结构

```
stock-trading/
├── backend/              # Spring Boot 3.2 + Java 17 后端服务
│   └── src/main/java/com/stock/
│       ├── config/        # 全局配置
│       ├── dataCollector/       # 数据采集模块
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
| 数据采集 | com.stock.dataCollector | 股票数据获取、新闻采集 |
| AI 模型 | com.stock.modelService | LSTM 预测、情感分析 |
| 交易策略 | com.stock.strategyAnalysis | 决策引擎、股票筛选 |
| 交易执行 | com.stock.tradingExecutor | 订单执行、风险控制 |
| 全局配置 | com.stock.config | 全局配置和 Web 资源处理 |

## 关键端口

- 后端 API: http://localhost:8080
- 前端开发：http://localhost:8000
- API 代理：`/api/*` → `http://localhost:8080`
- Swagger UI: http://localhost:8080/swagger-ui.html

5. **代码检查**: 确保 lint 和 type check 通过
6. **提交代码**: 遵循 Git 提交规范（见下文）

## Git 工作规范

### 代码提交要求

**每次代码修改完成后，必须进行 Git 提交**。

- 完成一个功能模块或修复一个问题后，应立即提交代码
- 提交信息应清晰描述本次修改的内容
- 遵循 Git 提交规范（见下文）
- 提交前确保代码编译通过且无严重问题

### 禁止回滚规则

**严禁使用以下命令回滚任何代码和文件**：

- `git checkout`（用于文件恢复）
- `git revert`
- `git reset`（含 --hard, --soft 等参数）
- 任何其他回滚命令

**原因**：
- 回滚会丢失代码修改历史，影响团队协作
- 导致代码审查和问题追踪困难
- 破坏代码完整性

**正确的处理方式**：
- 如果需要撤销修改，直接删除或重新编辑相关代码
- 如果提交了错误的代码，应该创建一个新的提交来修复
- 遇到问题时，先分析原因再决定如何处理，而不是盲目回滚

### Git 提交规范

## 测试规范

### 测试数据要求

- **禁止使用 Mock 数据**：所有测试必须使用真实数据
- **真实环境测试**：测试流程必须连接真实数据库和服务
- **数据准备**：测试前应准备充分的测试数据
- **数据清理**：测试后应清理测试数据，保持环境干净
- **集成测试优先**：优先编写集成测试而非单元测试

### 原因说明

1. Mock 测试无法验证真实业务逻辑
2. Mock 测试容易遗漏边界条件
3. 真实数据测试能发现集成问题
4. 确保代码在生产环境中正常工作

### 例外情况

仅在以下情况允许使用 Mock：
- 外部依赖不可用（如第三方 API）
- 测试成本过高（如需要大量时间）
- 核心逻辑已通过集成测试验证

### 测试覆盖要求

- 核心业务逻辑：100% 集成测试覆盖
- 数据处理流程：使用真实数据验证
- API 接口：通过真实请求测试

示例：feat(dataCollector): add stock price collection
```

### 强制提交场景

以下情况**必须**提交代码：
- 完成任何功能模块的实现
- 修复任何 Bug
- 更新文档
- 修改配置文件
- 进行任何代码修改后

### 提交检查清单

在提交前请确认：
- [ ] 代码编译通过
- [ ] 相关功能已测试（如适用）

### 代码推送要求

**每次提交代码后，必须推送到远程仓库**。

- 保持本地和远程代码同步
- 确保团队成员可以获取最新代码
- 避免因本地提交未推送导致的代码丢失风险

**推送命令**：
```bash
git push
```


