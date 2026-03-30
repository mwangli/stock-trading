# 数字计算表达式验证码识别测试方案

## Why

当前 `MathCaptchaService` 的验证码识别功能缺乏系统性测试覆盖，需要设计一套完整的自动化测试方案，通过代码调用识别接口并结合截图验证，确保识别功能的可用性与准确性。

## What Changes

- 新增验证码识别测试服务 `CaptchaRecognitionTestService`
- 新增验证码识别测试控制器 `CaptchaTestController`
- 新增测试报告生成功能，包含识别结果、截图证据、准确率分析
- 支持批量测试用例执行，自动计算准确率、错误率等指标
- 提供测试结果持久化，记录每次识别的原始图像和分析结果

## Impact

- Affected specs: 数学验证码识别
- Affected code:
  - `CaptchaRecognitionTestService.java` - 验证码识别测试服务
  - `CaptchaTestController.java` - 验证码测试接口
  - `CaptchaTestReport.java` - 测试报告数据模型
  - `CaptchaTestCase.java` - 测试用例数据模型

---

## 功能需求

### Requirement: 验证码识别接口调用

系统 SHALL 提供通过代码调用验证码识别接口的能力，支持传入测试用例并获取识别结果。

#### Scenario: 成功调用识别接口
- **WHEN** 测试服务调用 `MathCaptchaService.getCaptchaResult(account)`
- **THEN** 返回包含 `success`、`expression`、`result`、`method` 字段的 Map 对象

#### Scenario: 识别失败场景
- **WHEN** 识别过程中发生异常
- **THEN** 返回 `success=false` 和 `error` 错误信息

---

### Requirement: 验证码原始图像截图捕获

系统 SHALL 提供截图捕获机制，获取验证码接口返回的原始图像数据并持久化保存。

#### Scenario: 截图保存成功
- **WHEN** 调用截图捕获方法
- **THEN** 将验证码原始图像保存至 `.tmp/captcha-test/{timestamp}/` 目录
- **AND** 文件命名为 `{caseId}_{timestamp}.png` 格式

---

### Requirement: 测试用例管理

系统 SHALL 提供测试用例的管理能力，支持预定义测试用例集和动态测试用例添加。

#### Scenario: 预定义测试用例执行
- **WHEN** 执行预定义测试用例集
- **THEN** 依次执行每个测试用例并记录结果
- **AND** 每个测试用例包含：`caseId`、`expectedAnswer`、`description`

#### Scenario: 动态测试用例添加
- **WHEN** 调用添加测试用例接口
- **THEN** 将新测试用例添加至测试用例池
- **AND** 支持指定预期答案用于后续对比验证

---

### Requirement: 识别结果对比与准确率计算

系统 SHALL 提供识别结果与预期答案的对比功能，自动计算识别准确率、错误率等关键指标。

#### Scenario: 识别结果正确
- **WHEN** 识别结果与预期答案一致
- **THEN** 标记该测试用例为 `passed=true`

#### Scenario: 识别结果错误
- **WHEN** 识别结果与预期答案不一致
- **THEN** 标记该测试用例为 `passed=false`
- **AND** 记录 `expectedAnswer` 和 `actualAnswer`

#### Scenario: 准确率计算
- **WHEN** 测试集执行完成
- **THEN** 计算并返回以下指标：
  - `totalCount`: 总测试用例数
  - `passedCount`: 通过数
  - `failedCount`: 失败数
  - `accuracy`: 准确率 (passedCount / totalCount)
  - `errorRate`: 错误率 (failedCount / totalCount)

---

### Requirement: 测试报告生成

系统 SHALL 提供完整的测试报告生成功能，包含测试用例详情、接口响应数据、截图证据及准确率分析。

#### Scenario: 生成测试报告
- **WHEN** 测试集执行完成后调用报告生成接口
- **THEN** 返回包含以下内容的测试报告：
  - `reportId`: 报告唯一标识
  - `generatedAt`: 报告生成时间
  - `testCases`: 测试用例执行结果列表
  - `summary`: 准确率统计摘要
  - `screenshots`: 截图文件路径列表

#### Scenario: 报告持久化
- **WHEN** 测试报告生成
- **THEN** 将报告以 JSON 格式保存至 `.tmp/captcha-test/reports/{reportId}.json`

---

## 接口设计

### 测试控制器接口

| 接口路径 | 方法 | 说明 |
|---------|------|------|
| `/api/test/captcha/run` | POST | 执行测试用例集 |
| `/api/test/captcha/add-case` | POST | 添加测试用例 |
| `/api/test/captcha/report/{reportId}` | GET | 获取测试报告 |
| `/api/test/captcha/reports` | GET | 获取所有测试报告列表 |
| `/api/test/captcha/screenshot/{caseId}` | GET | 获取指定用例的截图 |

### 请求/响应模型

```java
// 执行测试请求
public class CaptchaTestRunRequest {
    private String account;           // 资金账号
    private List<String> caseIds;      // 指定用例ID（可选，空则执行全部）
    private boolean saveScreenshots;   // 是否保存截图
}

// 测试报告响应
public class CaptchaTestReport {
    private String reportId;           // 报告ID
    private LocalDateTime generatedAt; // 生成时间
    private String account;            // 测试账号
    private int totalCount;            // 总用例数
    private int passedCount;           // 通过数
    private int failedCount;           // 失败数
    private double accuracy;            // 准确率
    private double errorRate;          // 错误率
    private List<CaptchaTestCaseResult> caseResults;  // 用例结果列表
    private List<String> screenshotPaths;  // 截图路径列表
}

// 单个测试用例结果
public class CaptchaTestCaseResult {
    private String caseId;             // 用例ID
    private String expression;         // 识别的表达式
    private String expectedAnswer;      // 预期答案
    private String actualAnswer;       // 实际识别结果
    private boolean passed;            // 是否通过
    private String method;             // 识别方法
    private String errorMessage;       // 错误信息（若有）
    private String screenshotPath;      // 截图路径
    private long elapsedTimeMs;        // 识别耗时
}
```

---

## 数据流设计

```
┌─────────────────────────────────────────────────────────────────┐
│                      测试执行流程                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────┐    ┌───────────────┐    ┌──────────────────┐      │
│  │ 测试用例集 │ ──▶ │ CaptchaTest  │ ──▶ │ MathCaptchaService │     │
│  │          │    │ Service       │    │ getCaptchaResult    │     │
│  └──────────┘    └───────────────┘    └──────────────────┘      │
│                          │                      │                 │
│                          ▼                      ▼                 │
│                  ┌───────────────┐    ┌──────────────────┐      │
│                  │ 截图捕获      │    │ 识别结果          │      │
│                  │ (原始图像)    │    │ (expression/result)│     │
│                  └───────────────┘    └──────────────────┘      │
│                          │                      │                 │
│                          ▼                      ▼                 │
│                  ┌───────────────────────────────────────┐      │
│                  │         结果对比与准确率计算             │      │
│                  │  expected vs actual → passed/failed    │      │
│                  └───────────────────────────────────────┘      │
│                                    │                              │
│                                    ▼                              │
│                  ┌───────────────────────────────────────┐      │
│                  │           测试报告生成                   │      │
│                  │  report.json + screenshots/            │      │
│                  └───────────────────────────────────────┘      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 目录结构

```
.tmp/captcha-test/
├── cases/                      # 测试用例配置
│   └── test-cases.json
├── screenshots/                 # 截图保存目录
│   └── {reportId}/
│       ├── {caseId}_raw.png     # 原始验证码图片
│       └── ...
├── reports/                     # 测试报告保存目录
│   └── {reportId}.json
└── logs/                        # 测试日志
    └── test-run-{timestamp}.log
```

---

## 测试用例设计

### 预定义测试用例集

| caseId | 描述 | 难度 | 干扰程度 |
|--------|------|------|---------|
| TC001 | 两位数加法 (如 23+45) | 低 | 低 |
| TC002 | 两位数减法 (如 67-32) | 低 | 低 |
| TC003 | 两位数加减混合 (如 85-29) | 中 | 低 |
| TC004 | 三位数加法 (如 123+456) | 中 | 中 |
| TC005 | 三位数减法 (如 789-234) | 中 | 中 |
| TC006 | 三位数混合运算 (如 456+123) | 高 | 高 |
| TC007 | 结果为负数 (如 23-85) | 中 | 中 |
| TC008 | 大数字运算 (如 999+999) | 高 | 高 |

---

## 验收标准

### 验收标准 1: 接口调用成功

- [ ] 测试服务能成功调用 `MathCaptchaService.getCaptchaResult`
- [ ] 能正确解析并返回识别结果（表达式、答案、方法）

### 验收标准 2: 截图捕获功能

- [ ] 能获取验证码原始图像
- [ ] 截图文件正确保存至指定目录
- [ ] 文件命名格式正确且唯一

### 验收标准 3: 结果对比准确

- [ ] 能正确对比预期答案与实际结果
- [ ] 准确率和错误率计算正确
- [ ] 通过/失败状态标记准确

### 验收标准 4: 测试报告完整

- [ ] 报告包含所有测试用例详情
- [ ] 报告包含准确率统计摘要
- [ ] 报告包含截图证据路径
- [ ] 报告能正确持久化保存

### 验收标准 5: 批量测试支持

- [ ] 能执行多个测试用例
- [ ] 测试结果互不干扰
- [ ] 整体执行时间合理（每个用例 < 2秒）
