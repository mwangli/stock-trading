# 邮件验证码获取验证清单

## CaptchaFetchService 重构检查

- [ ] Redis 导入已移除
- [ ] RedisTemplate 字段已移除
- [ ] Redis 相关方法已删除：
  - [ ] `tryGetFromRedisWithRetry()`
  - [ ] `fetchFromRedis()`
  - [ ] `peekRedisCode()`
  - [ ] `clearRedisCode()`
- [ ] `getPhoneCode()` 直接调用邮件获取
- [ ] `getPhoneCodeWithRetry()` 方法签名保持不变

## 配置文件检查

- [ ] `application.yml` 中无 Redis 相关配置
- [ ] `需求.md` 包含 QQ 邮箱配置（1325533186@qq.com）
- [ ] `设计.md` 架构说明已更新

## 编译检查

- [ ] `mvn compile` 通过
- [ ] 无未使用的导入警告

## 端到端测试检查

### 环境准备

- [ ] 本地 Chrome 已启动（`start-local-chrome.ps1`）
- [ ] CDP 端口 9222 可访问
- [ ] 后端服务已启动

### 手机验证流程测试

- [ ] 1. 启动浏览器
- [ ] 2. 导航到登录页
- [ ] 3. 检测到手机验证页（PHONE_VERIFY）
- [ ] 4. 输入手机号
- [ ] 5. 勾选协议
- [ ] 6. 点击获取验证码
- [ ] 7. 滑块验证通过
- [ ] 8. 邮件验证码获取成功
- [ ] 9. 输入验证码
- [ ] 10. 点击下一步，跳转到登录页

### 测试结果记录

| # | 测试项 | 结果 | 备注 |
|---|--------|------|------|
| 1 | Chrome 启动 | ⏳ | |
| 2 | CDP 连接 | ⏳ | |
| 3 | 后端启动 | ⏳ | |
| 4 | 手机验证流程 | ⏳ | |
| 5 | 邮件验证码获取 | ⏳ | |
