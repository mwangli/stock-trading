# Tasks - 验证码识别测试方案

## 任务概述

实现数字计算表达式验证码识别功能的完整测试方案，包括测试服务、控制器、报告生成等功能。

---

## 任务列表

- [ ] Task 1: 创建测试报告数据模型 `CaptchaTestReport.java` 和 `CaptchaTestCaseResult.java`

- [ ] Task 2: 创建测试用例数据模型 `CaptchaTestCase.java` 和 `CaptchaTestRunRequest.java`

- [ ] Task 3: 实现 `CaptchaRecognitionTestService` 测试服务核心逻辑

- [ ] Task 4: 实现 `CaptchaTestController` 测试接口控制器

- [ ] Task 5: 实现测试用例管理功能（添加、加载、存储）

- [ ] Task 6: 实现截图捕获与保存功能

- [ ] Task 7: 实现识别结果对比与准确率计算

- [ ] Task 8: 实现测试报告生成与持久化

- [ ] Task 9: 配置测试接口路由和安全设置

---

## Task 1: 创建测试报告数据模型

### 任务描述

创建验证码识别测试的报告数据模型，包含测试报告和用例结果两个核心类。

### 具体步骤

1. 创建 `CaptchaTestCaseResult.java` - 单个测试用例结果
   - 字段：`caseId`、`expression`、`expectedAnswer`、`actualAnswer`、`passed`、`method`、`errorMessage`、`screenshotPath`、`elapsedTimeMs`
   - 添加 Javadoc 注释

2. 创建 `CaptchaTestReport.java` - 测试报告主类
   - 字段：`reportId`、`generatedAt`、`account`、`totalCount`、`passedCount`、`failedCount`、`accuracy`、`errorRate`、`caseResults`、`screenshotPaths`
   - 添加 Javadoc 注释

### 依赖

无

### 交付物

- `CaptchaTestCaseResult.java`
- `CaptchaTestReport.java`

---

## Task 2: 创建测试用例数据模型

### 任务描述

创建测试用例相关的数据模型，包括测试用例定义和测试执行请求。

### 具体步骤

1. 创建 `CaptchaTestCase.java` - 测试用例定义
   - 字段：`caseId`、`description`、`expectedAnswer`、`difficulty`、`interferenceLevel`
   - 添加 Javadoc 注释

2. 创建 `CaptchaTestRunRequest.java` - 测试执行请求
   - 字段：`account`、`caseIds`、`saveScreenshots`
   - 添加 Javadoc 注释

### 依赖

Task 1

### 交付物

- `CaptchaTestCase.java`
- `CaptchaTestRunRequest.java`

---

## Task 3: 实现 CaptchaRecognitionTestService 测试服务核心逻辑

### 任务描述

实现验证码识别测试服务的核心业务逻辑，包括调用识别接口、截图捕获、结果对比等功能。

### 具体步骤

1. 创建 `CaptchaRecognitionTestService.java`
2. 注入 `MathCaptchaService` 依赖
3. 实现 `executeTestCase(account, testCase)` 方法
   - 调用 `mathCaptchaService.getCaptchaResult(account)`
   - 记录识别耗时
   - 对比预期答案与实际结果
   - 返回 `CaptchaTestCaseResult`
4. 实现 `captureScreenshot(account)` 方法
   - 调用验证码接口获取图片
   - 保存截图到 `.tmp/captcha-test/screenshots/`
5. 添加 `@Slf4j` 和 `@Service` 注解

### 依赖

Task 1, Task 2

### 交付物

- `CaptchaRecognitionTestService.java`

---

## Task 4: 实现 CaptchaTestController 测试接口控制器

### 任务描述

实现验证码测试的 REST 接口控制器，提供测试执行、报告查询等功能。

### 具体步骤

1. 创建 `CaptchaTestController.java`
2. 实现接口：
   - `POST /api/test/captcha/run` - 执行测试用例集
   - `POST /api/test/captcha/add-case` - 添加测试用例
   - `GET /api/test/captcha/report/{reportId}` - 获取测试报告
   - `GET /api/test/captcha/reports` - 获取所有测试报告列表
   - `GET /api/test/captcha/screenshot/{caseId}` - 获取截图
3. 注入 `CaptchaRecognitionTestService` 依赖
4. 添加类和方法 Javadoc 注释

### 依赖

Task 3

### 交付物

- `CaptchaTestController.java`

---

## Task 5: 实现测试用例管理功能

### 任务描述

实现测试用例的加载、存储和管理功能，支持预定义用例集和动态添加。

### 具体步骤

1. 在 `CaptchaRecognitionTestService` 中添加测试用例管理方法
2. 实现 `loadPredefinedTestCases()` - 加载预定义测试用例集（8个用例）
3. 实现 `saveTestCase(testCase)` - 保存测试用例到 JSON 文件
4. 实现 `getAllTestCases()` - 获取所有测试用例
5. 实现 `getTestCaseById(caseId)` - 根据ID获取单个用例

### 依赖

Task 2, Task 3

### 交付物

- 测试用例 JSON 文件存储在 `.tmp/captcha-test/cases/`

---

## Task 6: 实现截图捕获与保存功能

### 任务描述

实现验证码原始图像的截图捕获和持久化保存功能。

### 具体步骤

1. 实现 `fetchCaptchaImage(account)` - 从 MathCaptchaService 获取验证码图片
2. 实现 `saveScreenshot(caseId, reportId, image)` - 保存截图到指定目录
3. 实现 `getScreenshotPath(caseId, reportId)` - 获取截图文件路径
4. 确保目录创建和文件命名规范

### 依赖

Task 3

### 交付物

- 截图保存功能实现
- 截图存储目录结构

---

## Task 7: 实现识别结果对比与准确率计算

### 任务描述

实现测试结果的对比分析和统计计算功能。

### 具体步骤

1. 实现 `compareResult(actual, expected)` - 对比实际结果与预期答案
2. 实现 `calculateStatistics(results)` - 计算准确率、错误率等统计指标
3. 实现 `generateSummary(results)` - 生成测试结果摘要
4. 确保结果对比逻辑正确（考虑负数、字符串trim等边界情况）

### 依赖

Task 4

### 交付物

- 结果对比逻辑
- 统计计算功能

---

## Task 8: 实现测试报告生成与持久化

### 任务描述

实现测试报告的生成和持久化保存功能。

### 具体步骤

1. 实现 `generateReport(testResults, account)` - 生成测试报告
   - 生成唯一 reportId（时间戳+UUID）
   - 计算统计指标
   - 收集截图路径列表
2. 实现 `saveReport(report)` - 保存报告到 JSON 文件
   - 目录：`.tmp/captcha-test/reports/`
   - 文件名：`{reportId}.json`
3. 实现 `loadReport(reportId)` - 从文件加载报告
4. 实现 `getAllReports()` - 获取所有报告列表

### 依赖

Task 1, Task 7

### 交付物

- 报告生成功能
- 报告持久化功能

---

## Task 9: 配置测试接口路由和安全设置

### 任务描述

在 Controller 中添加 `@RequestMapping` 注解配置路由，并考虑接口安全。

### 具体步骤

1. 在 `CaptchaTestController` 添加 `@RequestMapping("/api/test/captcha")`
2. 考虑是否需要添加测试接口的认证（建议测试环境开放，生产环境关闭）
3. 可通过配置文件控制测试接口的启用/禁用

### 依赖

Task 4

### 交付物

- 路由配置
- 安全配置（可选）

---

## 任务依赖关系

```
Task 1 ─┬─▶ Task 2 ─┬─▶ Task 3 ─┬─▶ Task 4 ───▶ Task 9
        │           │           │
        │           │           └─▶ Task 5
        │           │           │
        │           │           └─▶ Task 6
        │           │           │
        │           │           └─▶ Task 7 ──▶ Task 8
        │           │
        └───────────┘
```

---

## 验收标准

1. 所有接口能正常调用并返回预期格式
2. 测试用例能正确执行并记录结果
3. 截图能正确保存并可访问
4. 报告能正确生成和持久化
5. 准确率计算正确
