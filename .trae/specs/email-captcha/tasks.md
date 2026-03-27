# 邮件验证码获取 - 任务清单

## Task 1: CaptchaFetchService 重构

- [ ] 1.1 移除 Redis 相关导入和字段
- [ ] 1.2 删除 Redis 相关方法（`tryGetFromRedisWithRetry`, `fetchFromRedis`, `peekRedisCode`, `clearRedisCode`）
- [ ] 1.3 简化 `getPhoneCode()` 方法，直接调用 `fetchLatestEmail()`
- [ ] 1.4 保留 `fetchLatestEmail()` 方法，优化邮件搜索逻辑
- [ ] 1.5 移除 `@PostConstruct` 中的 Redis 可用性检查
- [ ] 1.6 保留邮件配置字段（`emailHost`, `emailPort`, `emailUsername`, `emailPassword`）

## Task 2: AutoLoginService 简化

- [ ] 2.1 移除 `CaptchaFetchService` 中 Redis 重试逻辑调用（实际上调用方式不变，但内部实现已简化）
- [ ] 2.2 确保 `getPhoneCodeWithRetry()` 方法签名不变，兼容现有调用

## Task 3: 配置文件清理

- [ ] 3.1 从 `application.yml` 移除 Redis 相关配置（如果有）
- [ ] 3.2 更新 `需求.md` 中的邮箱配置信息
- [ ] 3.3 更新 `设计.md` 中的架构说明

## Task 4: 编译验证

- [ ] 4.1 执行 `mvn compile` 确认编译通过
- [ ] 4.2 确认无未使用的导入警告

## Task 5: 端到端测试（CDP 直连模式）

- [ ] 5.1 启动本地 Chrome（`start-local-chrome.ps1`）
- [ ] 5.2 启动后端服务
- [ ] 5.3 执行完整手机验证流程测试
- [ ] 5.4 记录测试结果

---

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `CaptchaFetchService.java` | 修改 | 移除 Redis，保留邮件获取 |
| `AutoLoginService.java` | 修改 | 调用方式不变 |
| `application.yml` | 修改 | 移除 Redis 配置 |
| `docs/05-自动登录/需求.md` | 修改 | 更新邮箱配置 |
| `docs/05-自动登录/设计.md` | 修改 | 更新架构说明 |

---

## 验证命令

```powershell
# 编译验证
mvn compile

# 启动 Chrome
.\scripts\start-local-chrome.ps1

# 启动后端
mvn spring-boot:run

# 测试邮件获取（手动验证）
curl.exe http://localhost:8080/api/browser/phone/code-from-redis
```
