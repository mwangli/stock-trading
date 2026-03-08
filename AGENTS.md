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
│   │   ├── Application.java       # 主启动类
│   │   ├── config/                # 全局配置
│   │   ├── dataCollector/         # 数据采集包
│   │   ├── modelService/          # 模型服务包
│   │   ├── strategyAnalysis/      # 策略分析包
│   │   ├── tradingExecutor/       # 交易执行包
│   │   ├── job/                   # 动态任务包
│   │   ├── event/                 # 事件处理
│   │   ├── handler/               # WebSocket 处理器
│   │   └── service/               # 通用服务
│   └── pom.xml                    # Maven 配置
│
├── frontend/                   # React + Vite 前端应用
│   ├── src/                       # 源代码
│   │   ├── pages/                 # 页面组件
│   │   ├── components/           # 通用组件
│   │   ├── layouts/              # 布局组件
│   │   ├── store/                # 状态管理
│   │   └── locales/              # 国际化
│   ├── index.html
│   ├── package.json
│   └── vite.config.ts
│
├── docs/                          # 项目文档
├── .tmp/                          # 临时文件与中间脚本（不提交 Git）
├── docker-compose.yml              # 服务编排
└── pom.xml                         # 根项目 Maven 配置
```

**临时文件约定**：项目根目录下的 `.tmp/` 用于存放所有临时文件和中间脚本。Agent 生成的一次性脚本、临时输出、中间结果等均应放在此目录，且该目录已加入 `.gitignore`，不纳入版本控制。

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

工作目录: `D:\ai-stock-trading\frontend`

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

## 测试说明

本项目**不维护自动化测试**（无单元测试、集成测试）。原因与说明见 [README - 关于测试](./README.md#关于测试)。构建时使用 `mvn package -DskipTests` 跳过测试。

## 代码规范

### Java (Backend)

- **版本**: Java 17, Spring Boot 3.2.x
- **风格**:
  - 类名 `PascalCase`, 方法/变量 `camelCase`, 常量 `UPPER_SNAKE_CASE`
  - 使用 Lombok (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) 简化代码
  - Controller 返回统一泛型对象 `Response<T>`
- **分包策略**: 按业务领域分包 (`dataCollector`, `modelService` 等)，而非按层分包。
- **错误处理**: 使用全局异常处理 (`@ControllerAdvice`)，禁止吞掉异常。

### Java 代码注释规范 (强制)

生成 Java 代码时，必须遵循以下注释规范：

#### 1. 类注释

每个类必须添加类级文档注释，说明类的业务职责。

```java
/**
 * 股票数据服务类
 * 负责股票的实时数据采集、历史数据存储和数据查询
 * 
 * @author AI Assistant
 * @since 1.0
 */
public class StockDataService {
    // ...
}
```

#### 2. 方法注释

**每个public/protected方法必须添加 Javadoc 注释**，包含以下要素：

```java
/**
 * 同步股票历史数据
 * 
 * 根据指定的股票代码和日期范围，从外部API获取历史K线数据，
 * 并增量更新到本地数据库。对于已存在的数据会自动跳过，
 * 确保数据不重复。
 *
 * @param symbol       股票代码，如 "AAPL" 或 "600519"
 * @param startDate    起始日期，格式 "yyyy-MM-dd"
 * @param endDate      结束日期，格式 "yyyy-MM-dd"
 * @return             同步结果，包含成功条数和失败条数
 * @throws StockDataException 当外部API调用失败或数据解析异常
 * @see #queryHistoryPrices(String, String, String)
 * @see StockPriceEntity
 */
public SyncResult syncHistoryData(String symbol, String startDate, String endDate) {
    // 业务逻辑
}
```

#### 3. 业务注释

在方法内部的业务逻辑关键节点，必须添加业务说明注释：

```java
/**
 * 执行股票买入操作
 */
public void executeBuy(String symbol, int quantity) {
    // 1. 检查持仓是否超过单只股票仓位上限 (默认30%)
    if (currentPosition * currentPrice >= totalAssets * 0.30) {
        throw new BusinessException("单只股票仓位已超过30%上限");
    }
    
    // 2. 检查账户可用资金是否充足
    BigDecimal requiredAmount = price.multiply(BigDecimal.valueOf(quantity));
    if (availableCash.compareTo(requiredAmount) < 0) {
        throw new BusinessException("可用资金不足，当前可用: " + availableAmount);
    }
    
    // 3. 调用券商API下单
    OrderResult result = brokerApi.submitOrder(symbol, quantity, OrderType.BUY);
    
    // 4. 更新本地持仓记录
    positionService.updatePosition(symbol, quantity, price);
}
```

#### 4. 注释禁用规则

- **禁止**添加无意义的注释，如：
  ```java
  // 定义变量 (BAD)
  int count = 0;
  
  // 返回结果 (BAD)
  return result;
  ```

- **禁止**用注释解释简单逻辑：
  ```java
  // 如果列表不为空，遍历列表 (BAD - 冗余)
  for (Order order : orders) {
      process(order);
  }
  ```

#### 5. 注释要点总结

| 位置 | 注释类型 | 必须包含内容 |
|------|----------|--------------|
| 类声明前 | Javadoc | 类职责、业务场景、版本 |
| public/protected方法 | Javadoc | 功能说明、参数含义、返回值、异常、关联方法 |
| private方法 | 行内注释 | 仅当业务逻辑复杂或非自明时添加 |
| 关键业务节点 | 行内注释 | 业务判断逻辑、数据流转、状态变更 |
| 复杂算法 | 行内注释 | 算法思路、关键变量含义 |

#### 6. 快速模板

生成代码时可使用以下快速模板：

```java
/**
 * [方法简短描述]
 * 
 * [详细业务说明，包括业务场景、调用时机、预期效果]
 *
 * @param [参数名] [参数含义]
 * @return [返回值含义]
 * @throws [可能抛出的业务异常]
 */
public [返回值类型] [methodName]([参数列表]) {
    // 1. [业务步骤1说明]
    // 2. [业务步骤2说明]
    // 3. [关键判断/计算说明]
    return [结果];
}
```

**Agent 强制要求**：后续生成的所有 Java 代码必须严格遵循此注释规范，类注释和方法 Javadoc 注释为必填项。

### TypeScript/React (Frontend)

- **框架**: React 19, Vite 7, TailwindCSS 4
- **风格**:
  - **组件**: 函数式组件 + Hooks，文件名 `PascalCase.tsx`
  - **样式**: **强制使用 TailwindCSS** 类名，禁止写行内 style 或传统 CSS 文件（除非必要）
  - **状态**: 使用 Zustand 进行全局状态管理
  - **类型**: 严禁使用 `any`，必须定义 Interface/Type
  - **数据兜底**: 前端表格或组件优先使用接口数据。当接口返回不可用（或未实现）时，**必须**展示 Mock 数据以保持页面美观，不得留空或报错。
- **组件结构**:
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
    - 修改代码。
    - **必须**运行编译命令验证修改（后端 `mvn compile` 或 `mvn package -DskipTests`，前端 `npm run build`）。
4.  **Verify (验证)**: 检查 `lsp_diagnostics` 确保无语法错误。
5.  **Build Check (编译检查)**: 每次修改前端代码后，**必须**在 `frontend` 目录下运行 `npm run tsc` (或 `npm run build`) 以确保无编译错误。这是强制约定。

**Agent 特别指令**:
- 本项目无自动化测试，不要生成或要求补充测试用例；质量依赖代码审查与手工验证。
- 后端是单体应用结构，不要尝试寻找子模块的 `pom.xml` 进行独立构建，始终在 `backend` 目录下操作。
