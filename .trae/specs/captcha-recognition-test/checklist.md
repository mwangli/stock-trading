# Checklist - 验证码识别测试方案验收检查清单

## 数据模型检查

- [ ] `CaptchaTestCaseResult.java` 类存在且字段完整
- [ ] `CaptchaTestReport.java` 类存在且字段完整
- [ ] `CaptchaTestCase.java` 类存在且字段完整
- [ ] `CaptchaTestRunRequest.java` 类存在且字段完整
- [ ] 所有 DTO 类添加了 Javadoc 注释
- [ ] 所有字段添加了 Javadoc 注释

## 服务层检查

- [ ] `CaptchaRecognitionTestService.java` 服务类存在
- [ ] 服务类添加了 `@Slf4j` 注解
- [ ] 服务类添加了 `@Service` 注解
- [ ] `executeTestCase(account, testCase)` 方法实现正确
- [ ] `captureScreenshot(account)` 方法实现正确
- [ ] 服务正确注入了 `MathCaptchaService` 依赖

## 控制器层检查

- [ ] `CaptchaTestController.java` 控制器类存在
- [ ] 控制器添加了 `@RestController` 注解
- [ ] 控制器添加了 `@RequestMapping("/api/test/captcha")` 注解
- [ ] 控制器添加了 `@Slf4j` 注解
- [ ] `POST /api/test/captcha/run` 接口存在且实现正确
- [ ] `POST /api/test/captcha/add-case` 接口存在且实现正确
- [ ] `GET /api/test/captcha/report/{reportId}` 接口存在且实现正确
- [ ] `GET /api/test/captcha/reports` 接口存在且实现正确
- [ ] `GET /api/test/captcha/screenshot/{caseId}` 接口存在且实现正确

## 测试用例管理检查

- [ ] 预定义 8 个测试用例正确加载
- [ ] 测试用例如以下表所示：

| caseId | 描述 | 预期答案格式 |
|--------|------|------------|
| TC001 | 两位数加法 | X+X |
| TC002 | 两位数减法 | X-X |
| TC003 | 两位数混合 | X±X |
| TC004 | 三位数加法 | XXX+XXX |
| TC005 | 三位数减法 | XXX-XXX |
| TC006 | 三位数混合 | XXX±XXX |
| TC007 | 结果负数 | X-X(负) |
| TC008 | 大数字运算 | XXX+XXX |

- [ ] `saveTestCase()` 能正确保存用例到 JSON
- [ ] `getAllTestCases()` 能正确加载所有用例
- [ ] `getTestCaseById()` 能根据ID获取用例

## 截图功能检查

- [ ] 截图保存目录 `.tmp/captcha-test/screenshots/` 正确创建
- [ ] 截图文件命名格式 `{caseId}_{timestamp}.png` 正确
- [ ] `fetchCaptchaImage()` 能正确获取验证码图片
- [ ] `saveScreenshot()` 能正确保存图片到文件
- [ ] `getScreenshotPath()` 能正确返回截图路径

## 结果对比与统计检查

- [ ] `compareResult(actual, expected)` 对比逻辑正确
- [ ] 考虑 trim 和空字符串情况
- [ ] `calculateStatistics()` 统计计算正确
- [ ] `accuracy = passedCount / totalCount` 计算正确
- [ ] `errorRate = failedCount / totalCount` 计算正确

## 报告生成检查

- [ ] `generateReport()` 生成唯一 reportId
- [ ] 报告包含所有必需字段
- [ ] `saveReport()` 保存到 `.tmp/captcha-test/reports/{reportId}.json`
- [ ] `loadReport()` 能从文件加载报告
- [ ] `getAllReports()` 返回所有报告列表

## 接口功能检查

- [ ] 执行测试接口能正确调用 MathCaptchaService
- [ ] 添加用例接口能正确保存新用例
- [ ] 报告查询接口能正确返回报告数据
- [ ] 截图接口能正确返回图片文件

## 目录结构检查

- [ ] `.tmp/captcha-test/cases/` 目录存在
- [ ] `.tmp/captcha-test/screenshots/` 目录存在
- [ ] `.tmp/captcha-test/reports/` 目录存在
- [ ] `.tmp/captcha-test/logs/` 目录存在

## 异常处理检查

- [ ] 识别失败时返回 `success=false`
- [ ] 异常情况有错误日志记录
- [ ] 截图保存失败不影响主流程
- [ ] 报告生成失败有降级处理

## 代码质量检查

- [ ] 所有 public 方法有 Javadoc 注释
- [ ] 关键业务逻辑有行内注释
- [ ] 日志记录完整（INFO/ERROR 级别）
- [ ] 无硬编码配置值（使用 @Value 注入）
- [ ] 代码遵循现有项目规范
