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

后端采用 **Maven 多模块项目结构**，每个模块可独立启动，也可聚合启动：

stock-trading/
├── backend/                        # Spring Boot 后端服务 (多模块)
│   ├── pom.xml                    # 父 POM (依赖管理)
│   ├── data-collector/            # 数据采集模块 (端口: 8081)
│   │   ├── pom.xml
│   │   └── src/main/java/com/stock/dataCollector/
│   │       └── DataCollectorApplication.java
│   ├── model-service/             # AI 模型模块 (端口: 8082)
│   │   ├── pom.xml
│   │   └── src/main/java/com/stock/modelService/
│   │       └── ModelServiceApplication.java
│   ├── strategy-analysis/         # 策略分析模块 (端口: 8083)
│   │   ├── pom.xml
│   │   └── src/main/java/com/stock/strategyAnalysis/
│   │       └── StrategyAnalysisApplication.java
│   ├── trading-executor/          # 交易执行模块 (端口: 8084)
│   │   ├── pom.xml
│   │   └── src/main/java/com/stock/tradingExecutor/
│   │       └── TradingExecutorApplication.java
│   └── app-starter/               # 主应用启动模块 (聚合启动, 端口: 8080)
│       ├── pom.xml
│       └── src/main/java/com/stock/app/
│           └── AppStarterApplication.java
├── frontend/                      # React 前端应用
│   └── src/
│       ├── pages/                 # 页面组件
│       ├── components/            # 通用组件
│       └── services/              # API 服务
│
└── documents/                     # 设计文档

## 构建命令

### Backend (Maven)

```bash
# 构建所有模块 (在 backend 目录下)
cd backend
mvn clean package

# ============ 模块启动方式 ============

# 方式一：聚合启动 (推荐，启动所有模块)
mvn spring-boot:run -pl app-starter

# 方式二：独立启动各模块 (用于开发调试)

# 启动数据采集模块 (端口: 8081)
mvn spring-boot:run -pl data-collector
# 启动模型服务模块 (端口: 8082)
mvn spring-boot:run -pl model-service

# 启动策略分析模块 (端口: 8083)
mvn spring-boot:run -pl strategy-analysis

# 启动交易执行模块 (端口: 8084)
mvn spring-boot:run -pl trading-executor

# 启动主应用启动模块 (端口: 8080，聚合所有模块)
mvn spring-boot:run -pl app-starter
mvn spring-boot:run -pl stock-app
# ============ 其他命令 ============

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=ClassName

# 跳过测试打包
mvn clean package -DskipTests

# 单独构建某个模块
mvn clean package -pl data-collector
mvn clean package -pl model-service
mvn clean package -pl strategy-analysis
mvn clean package -pl trading-executor

# ============ 模块独立编译验证 ============
# 修改某个模块后，只需编译当前模块验证，无需编译所有模块
# 这样可以支持多模块并行开发

# 编译验证 data-collector 模块
mvn compile -pl data-collector

# 编译验证 model-service 模块
mvn compile -pl model-service

# 编译验证 strategy-analysis 模块
mvn compile -pl strategy-analysis

# 编译验证 trading-executor 模块
mvn compile -pl trading-executor
```

### 多模块并行开发规范

**重要原则**：修改某个模块后，只需编译当前模块验证，无需编译所有模块。

```bash
# 示例：修改了 data-collector 模块
cd backend
mvn compile -pl data-collector    # 只编译当前模块

# 编译通过后即可提交，不需要等待其他模块
```

**原因**：
- 各模块相对独立，可并行开发
- 避免其他模块的问题阻塞当前模块的提交
- 提高开发效率

**注意事项**：
- 修改了公共依赖（如父 pom.xml）时，需要编译所有模块
- 修改了模块间接口时，需要同步编译相关模块

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

## 模块架构

| 模块 | Maven ArtifactId | 包路径 | 功能 | 端口 |
|------|------------------|--------|------|------|
| 数据采集 | data-collector | com.stock.dataCollector | 股票数据获取、新闻采集 | 8081 |
| AI 模型 | model-service | com.stock.modelService | LSTM 预测、情感分析 | 8082 |
| 交易策略 | strategy-analysis | com.stock.strategyAnalysis | 决策引擎、股票筛选 | 8083 |
| 交易执行 | trading-executor | com.stock.tradingExecutor | 订单执行、风险控制 | 8084 |
| 主应用启动 | app-starter | com.stock.app | 聚合启动所有模块 | 8080 |
## 模块启动说明

### 聚合启动 (生产环境推荐)

```bash
# 启动所有模块，端口 8080
mvn spring-boot:run -pl app-starter
mvn spring-boot:run -pl stock-app

聚合启动后，所有功能通过单一端口访问：
- 数据采集 API: `http://localhost:8080/api/stock-data/*`
- 模型服务 API: `http://localhost:8080/api/lstm/*`, `http://localhost:8080/api/sentiment/*`

### 独立启动 (开发调试)

```bash
# 终端1: 启动数据采集模块
mvn spring-boot:run -pl data-collector
# 访问: http://localhost:8081

# 终端2: 启动模型服务模块
mvn spring-boot:run -pl model-service
# 访问: http://localhost:8082

# 终端3: 启动策略分析模块
mvn spring-boot:run -pl strategy-analysis
# 访问: http://localhost:8083
```

## 关键端口

| 服务 | 端口 | 说明 |
|------|------|------|
| app-starter (聚合) | 8080 | 所有模块聚合启动 |
| data-collector | 8081 | 数据采集模块独立启动 |
| model-service | 8082 | 模型服务模块独立启动 |
| strategy-analysis | 8083 | 策略分析模块独立启动 |
| trading-executor | 8084 | 交易执行模块独立启动 |
| frontend | 8000 | React 前端开发服务器 |

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

```
提交类型(模块): 提交说明

提交类型：
- feat: 新功能
- fix: Bug 修复
- docs: 文档更新
- style: 代码格式调整
- refactor: 重构
- test: 测试相关
- chore: 构建/工具链
- perf: 性能优化

示例：
- feat(data-collector): 添加股票价格采集功能
- fix(model-service): 修复模型训练内存泄漏
- docs: 更新数据采集设计文档
```

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

### 强制要求：禁止 Mock 测试

**严禁在测试中使用 Mock 数据或模拟实现！**

- 如果数据不足：**跳过测试**（使用 `Assumptions.assumeTrue()`）
- 如果服务不可用：**测试失败**（抛出异常）
- 如果环境未就绪：**跳过测试**（记录原因）

**不要写无意义的模拟测试！宁愿测试失败或跳过，也不要用假数据欺骗自己。**

```java
// 正确做法：数据不足时跳过测试
Assumptions.assumeTrue(dataAvailable, "跳过测试：MongoDB中没有足够的历史数据");

// 错误做法：使用Mock数据
@Mock
private PriceRepository mockRepository; // 禁止！
```

### 例外情况

**无例外！** 所有测试都必须使用真实数据和服务。

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

### Git 工作流程规范

**每次有代码变更或文档修改时，必须遵循以下完整流程：**

1. **本地开发与测试**
   - 完成代码修改或文档更新
   - 确保代码编译通过 (`mvn compile` 或 `npm run tsc`)
   - 运行相关测试确保功能正常
   - 修复所有lint错误和警告

2. **Git状态检查**
   ```bash
   # 查看当前工作区状态
   git status
   
   # 查看具体修改内容
   git diff
   ```

3. **添加变更到暂存区**
   ```bash
   # 添加特定文件
   git add <file-path>
   
   # 或添加所有变更
   git add .
   ```

4. **创建Git提交**
   - 使用规范的提交信息格式：`类型(模块): 描述`
   - 提交前再次确认变更内容的正确性
   ```bash
   git commit -m "feat(module): add new feature"
   ```

5. **推送到远程仓库**
   ```bash
   # 推送当前分支到远程
   git push origin <branch-name>
   
   # 如果是新分支，设置上游跟踪
   git push -u origin <branch-name>
   ```

6. **验证推送成功**
   - 确认远程仓库已包含最新提交
   - 在GitHub/GitLab等平台上确认代码已更新

**重要原则：**
- **绝不允许**本地有未推送的提交超过24小时
- **绝不允许**在没有提交的情况下进行新的代码修改
- **绝不允许**跳过测试和编译验证直接提交
- **每个提交**必须是原子性的，只包含相关联的变更
- **文档修改**与代码修改同等重要，必须同样遵循此流程
