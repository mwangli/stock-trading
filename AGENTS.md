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
│   ├── src/main/java/com/stock/
│   │   ├── config/                # 全局配置
│   │   ├── dataCollector/        # 数据采集
│   │   ├── modelService/         # AI 模型（LSTM、情感分析）
│   │   ├── strategyAnalysis/    # 策略分析
│   │   ├── tradingExecutor/     # 交易执行（含 job 调度）
│   │   ├── event/、handler/、logging/、service/
│   │   └── ...
│   └── pom.xml
│
├── frontend-v2/                    # React + Vite 前端
│   ├── src/pages/、components/、layouts/、store/、locales/
│   ├── package.json、vite.config.ts
│   └── ...
│
├── models/sentiment/               # 情感模型（TorchScript，Git LFS）
├── docs/                           # 文档（00~04 模块需求与设计）
├── .tmp/                           # 临时文件（不提交）
├── docker-compose.yml
└── pom.xml
```

**临时文件约定**：项目根目录下的 `.tmp/` 用于存放所有临时文件和中间脚本。Agent 生成的一次性脚本、临时输出、中间结果等均应放在此目录，且该目录已加入 `.gitignore`，不纳入版本控制。

## 构建与运行命令

### Backend (Java/Maven)

工作目录: `backend/`

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

#### 定时任务规范（强制）

1. **统一任务调度中心**：所有定时任务必须通过 `tradingExecutor.job` 模块中的统一调度体系完成，禁止在业务类中直接使用 `@Scheduled` 注解。
2. **任务注册方式**：
   - 在 `job_config` 表中配置任务的 `jobName` / `beanName` / `methodName` / `cronExpression`；
   - 或在 `JobBootstrap` 中添加默认任务配置，由 `JobSchedulerService` 统一加载。
3. **任务执行入口**：
   - 任务执行逻辑应放在独立的 `Scheduler` / `Job` 类中（如 `DataSyncScheduler`、`ModelTrainingRecordSyncJob`）；
   - 方法为无参 `public void methodName()`，供调度中心通过反射调用。
4. **Agent 约束**：后续新增或修改任何后台任务时：
   - **禁止**在任意类上新增 `@Scheduled`；
   - 必须通过 `JobConfig + JobSchedulerService` 的方式接入统一调度体系。

#### 接口日志规范（强制）

1. **全局入口日志**：所有 `@RestController` 接口方法必须在入口打印一行访问日志，用于排查“接口未返回/超时/参数异常”等问题。  
   - 已通过全局 AOP 切面 `com.stock.logging.ApiLoggingAspect` 统一实现，记录控制器类名、方法名和关键参数。
2. **控制器级业务日志**：对于关键接口（如分页查询、大批量操作、模型训练/同步等），在 Controller 方法内 **显式增加入口业务日志**，说明：
   - 调用的业务含义（例如“分页查询模型训练记录”）
   - 关键请求参数（如 `keyword`、`current`、`pageSize` 等）
3. **Agent 约束**：后续新增任何后端接口时，必须遵守：
   - Controller 类使用 `@Slf4j`；
   - 在每个 `public` 接口方法入口增加一条 `log.info(...)` 业务日志（除全局切面外的补充信息），便于在日志中快速定位该接口的调用与入参。

#### 接口入参与返回值规范（强制）

1. **统一使用 DTO 封装**：
   - 所有 `@RestController` 方法的请求体入参和响应体返回值必须使用明确的 DTO/VO 类进行封装；
   - **禁止**在接口层直接使用 `Map`、`List<Map>`、原始 `Object` 作为入参或返回值类型（包括 `ResponseEntity<Map<...>>`、`@RequestBody Map` 等）。
2. **请求参数规范**：
   - 复杂请求体必须定义独立的 `*Request` / `*Param` DTO 类（如 `SentimentTrainingRequest`）；
   - 简单查询参数可继续使用 `@RequestParam` 标量类型（如 `String keyword`、`int page`），但一旦字段超过 3 个，建议封装为请求 DTO。
3. **响应结果规范**：
   - 统一使用封装的响应泛型（如 `ApiResponse<T>` / `PageResult<T>`），`T` 必须是具体的 DTO/VO 类；
   - 对于列表、分页等结构，DTO 内部再包含集合字段，而不是在 Controller 中返回原始 `Map` 拼装结构。
4. **Agent 约束**：
   - 后续新增或修改任何接口时，如果发现使用了 `Map` 作为接口层入参/出参，必须先补齐/重构为对应的 DTO，再进行业务实现；
   - 如需在内部使用 `Map` 做临时计算，必须限制在 Service 层或更下游，且最终对外接口仍然是 DTO。

#### 接口路径规范（强制）

1. **路径命名风格**：
   - 统一使用**小写驼峰式单词组合**（lower camel words），单词之间采用连字符 `-` 分隔，例如：
     - `/api/stockData`、`/api/stock-data` 统一收敛为：`/api/stockData` 或 `/api/stock-data` 中的一种，全项目保持一致；
     - 推荐对资源名使用小写+连字符（REST 风格），如：`/api/model-training-records`、`/api/stock-data`。
2. **版本化与前缀**：
   - 所有业务接口必须以 `/api/` 为统一前缀，后续如需版本化可扩展为 `/api/v1/...`。
3. **避免混用风格**：
   - 禁止在同一项目中同时存在 `/api/stockInfo` 与 `/api/stock-info` 这种混用情况；
   - 新增接口时必须对齐已有约定的命名风格，若需调整旧路径，须保留一段时间的兼容映射。

#### 接口路径参数位置规范（强制）

1. **路径参数放在末尾**：
   - 所有包含路径参数的接口，必须将路径参数段放在 URL 的最后位置；
   - 例如：`/api/jobs/{id}/status` 必须重构为 `/api/jobs/status/{id}`，`/api/modelInfo/{code}/details` 必须重构为 `/api/modelInfo/details/{code}`。
2. **Agent 约束**：
   - 后续新增任何带路径参数的接口时，禁止在参数段后再追加其它路径片段；
   - 如发现旧接口不符合约定，需在不破坏前端的前提下，通过增加新路径 + 兼容映射的方式逐步迁移。

#### DTO 字段注释规范（强制）

1. **字段级别注释必填**：
   - 所有 DTO/VO 类中的每一个字段，必须在字段声明前添加 Javadoc 或行级注释，清晰说明字段含义、单位、取值范围（如适用）；
   - 示例：
     ```java
     /**
      * 股票代码，如 600519
      */
     private String stockCode;
     ```
2. **适用范围**：
   - 新增 DTO 必须严格按照规范编写字段注释；
   - 修改已有 DTO 时，如发现缺失字段注释，需一并补齐。

#### 类注释与作者信息规范（强制）

1. **统一作者信息**：
   - 所有新建或修改的 Java 类，类级 Javadoc 注释中的作者统一使用 `@author mwangli`
   - 不再使用其他作者标识。
2. **创建时间必填**：
   - 类级 Javadoc 必须包含创建时间字段，例如：
     ```java
     /**
      * 股票数据服务类
      *
      * @author mwangli
      * @since 2026-03-10
      */
     ```
3. **Agent 约束**：
   - 新增类时必须按照上述模板补充类注释；
   - 修改已有类时，如原有注释不符合规范，应在不破坏历史信息的前提下补充或更正作者与时间信息。

#### 统一响应封装规范（强制）

1. **统一使用 ResponseDTO 封装返回值**：
   - 所有后端接口的返回体必须封装为统一的 `ResponseDTO<T>` 泛型对象；
   - `ResponseDTO<T>` 至少包含以下字段：
     - `boolean success`：是否成功
     - `String message`：提示信息（错误或成功说明）
     - `T data`：具体业务数据
2. **控制器返回签名约定**：
   - Controller 方法返回类型应为 `ResponseEntity<ResponseDTO<具体DTO>>` 或直接返回 `ResponseDTO<具体DTO>`（由全局配置统一包装）；
   - 禁止在 Controller 层直接返回裸 DTO 或 Map，所有业务数据必须放入 `ResponseDTO.data` 中。
3. **与现有 ApiResponse 的关系**：
   - 历史上存在的 `ApiResponse<T>`、`PageResult<T>` 等可逐步迁移为基于 `ResponseDTO<T>` 的实现；
   - 新增接口必须直接使用 `ResponseDTO<T>`，禁止再引入新的响应包装类型。

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
