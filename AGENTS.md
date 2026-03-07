# 交互语言要求

1. **强制使用中文**：所有回答、思考过程、输出内容必须使用中文
2. **代码除外**：代码本身和特殊专有名词（如类名、方法名、API 名称）使用英文
3. **展示思考过程**：在回答问题时，需要展示分析和推理过程

## 补充强化（必须遵守）

1. **不得直接粘贴英文原始输出**：包括但不限于后台 Agent、外部文档、命令行输出等；如包含英文内容，必须先用中文进行归纳/翻译后再输出。
2. **面向用户的最终输出必须中文**：允许在代码块、类名/方法名/API 名称、URL、HTTP Header 等专有名词处使用英文。
3. **“展示思考过程”的边界**：以“结论 → 证据 → 推理 → 验证步骤 → 规避方案”的结构化方式说明；避免输出无关的内部草稿或逐字推演。

---

# AGENTS.md - AI 股票交易系统开发指南

## 项目结构

本项目采用前后端分离架构，包含以下两个主要部分：

```
stock-trading/
├── backend/                        # Java Spring Boot 单体应用
│   ├── src/main/java/com/stock/    # 源代码
│   │   ├── dataCollector/          # 数据采集包
│   │   ├── modelService/           # 模型服务包
│   │   ├── strategyAnalysis/       # 策略分析包
│   │   └── tradingExecutor/        # 交易执行包
│   └── pom.xml                     # Maven 配置
│
├── frontend-v2/                    # React + Vite 前端应用
│   ├── src/                        # 源代码
│   ├── index.html                  # 入口 HTML
│   ├── package.json                # npm 依赖
│   └── vite.config.ts              # Vite 配置
│
├── docker-compose.yml              # 服务编排
└── pom.xml                         # 根项目 Maven 配置
```

## 构建与运行命令

### Backend (Java/Maven)

工作目录: `D:\ai-stock-trading\backend`

```bash
# 启动应用 (Spring Boot)
mvn spring-boot:run

# 构建打包 (跳过测试)
mvn clean package -DskipTests

# 编译代码
mvn compile
```

### Frontend (React/Vite)

工作目录: `D:\ai-stock-trading\frontend-v2`

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 代码检查 (Lint)
npm run lint
```

## 测试规范

### Backend 测试

后端测试位于 `backend/src/test/java`，使用 JUnit 5 + Spring Boot Test。

```bash
# 运行所有测试
mvn test

# 运行单个测试类 (重要：Agent 常用)
mvn test -Dtest=StockDataServiceTest

# 运行特定测试方法
mvn test -Dtest=StockDataServiceTest#testMethodName
```

**测试原则**：
1. **真实环境优先**：尽量使用嵌入式数据库 (H2/Embedded Mongo) 或 Docker 容器进行集成测试。
2. **谨慎 Mock**：业务逻辑测试尽量避免 Mock，确保真实链路畅通。仅在涉及第三方外部 API（如券商接口）时使用 Mock 或 Simulator。
3. **数据清理**：测试产生的临时数据必须在测试结束后清理 (`@Transactional` 或 `@AfterEach`)。

### Frontend 测试

目前 `frontend-v2` 尚未配置自动化测试脚本 (`npm test` 不可用)。
*Agent 注意：在添加前端测试前，需先安装 Vitest 或 Jest。*

## 代码规范

### Java (Backend)

- **版本**: Java 17, Spring Boot 3.2.x
- **风格**:
  - 类名 `PascalCase`, 方法/变量 `camelCase`, 常量 `UPPER_SNAKE_CASE`
  - 使用 Lombok (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) 简化代码
  - Controller 返回统一泛型对象 `Response<T>`
- **分包策略**: 按业务领域分包 (`dataCollector`, `modelService` 等)，而非按层分包。
- **错误处理**: 使用全局异常处理 (`@ControllerAdvice`)，禁止吞掉异常。

### TypeScript/React (Frontend)

- **框架**: React 19, Vite 7, TailwindCSS 4
- **风格**:
  - **组件**: 函数式组件 + Hooks，文件名 `PascalCase.tsx`
  - **样式**: **强制使用 TailwindCSS** 类名，禁止写行内 style 或传统 CSS 文件（除非必要）
  - **状态**: 使用 Zustand 进行全局状态管理
  - **类型**: 严禁使用 `any`，必须定义 Interface/Type
- **组件结构**:
  ```tsx
  // 推荐写法
  interface Props {
    title: string;
  }
  
  export const MyComponent: React.FC<Props> = ({ title }) => {
    return <div className="p-4 bg-blue-500 text-white">{title}</div>;
  };
  ```

## Git 工作流

1. **原子提交**: 每次 commit 只包含一个逻辑变更。
2. **提交信息**: 遵循 Conventional Commits 规范。
   - `feat: ...` 新功能
   - `fix: ...` 修复 bug
   - `docs: ...` 文档
   - `refactor: ...` 重构
   - `test: ...` 测试
3. **禁止回滚**: 严禁使用 `git reset --hard` 或 `git push -f`，错误提交请使用 `revert` 或新提交修复。
4. **推送**: 提交后必须立即 `git push`，保持远程同步。

## 思考与行动指南 (For Agents)

当接到任务时，请遵循：

1.  **Context (读取)**: 先阅读相关代码，不要臆测。使用 `ls`, `read` 确认文件位置。
2.  **Think (思考)**: 分析影响范围，确定修改方案。
    - "如果是修改后端 API，是否需要同步更新前端 Type 定义？"
    - "如果是修改数据库实体，是否需要数据库迁移？"
3.  **Act (执行)**:
    - 运行单个测试验证修改前的状态（如果存在）。
    - 修改代码。
    - **必须**运行测试或编译命令验证修改。
    - 如果是新增功能，**必须**补充对应的测试用例（后端）。
4.  **Verify (验证)**: 检查 `lsp_diagnostics` 确保无语法错误。

**Agent 特别指令**:
- 如果发现 `frontend-v2` 下缺少测试环境，不要尝试运行 `npm test`，除非你先配置了它。
- 后端是单体应用结构，不要尝试寻找子模块的 `pom.xml` 进行独立构建，始终在 `backend` 目录下操作。
