# 验证码获取方式优化 - 邮件直连模式

## Why

中信证券平台每天最多获取8次短信验证码，Redis 作为缓存层增加了系统复杂度和失败环节。当前 Redis 获取存在不稳定问题（连接超时、键值异常），需要简化为纯邮件获取方式。

## What Changes

- 移除 `CaptchaFetchService` 中所有 Redis 相关代码和依赖
- 验证码获取直接通过邮件 IMAP 协议读取
- 配置项简化为单一邮箱配置
- 更新 `AutoLoginService` 直接使用邮件获取
- 更新所有相关文档

## Impact

- Affected specs: 手机验证流程
- Affected code:
  - `CaptchaFetchService.java` - 移除 Redis，重构为纯邮件获取
  - `AutoLoginService.java` - 简化验证码获取调用
  - `application.yml` - 移除 Redis 相关配置
  - `需求.md` - 更新邮箱配置信息
  - `设计.md` - 更新架构说明

## 技术方案

### 邮箱配置（QQ 邮箱）

| 配置项 | 值 |
|--------|-----|
| 协议 | IMAPS |
| 主机 | imap.qq.com |
| 端口 | 993 |
| 用户名 | 1325533186@qq.com |
| 密码 | QQ 邮箱授权码 |

### 验证码获取流程

```
1. 触发发送验证码（点击"获取验证码"按钮）
2. 等待 3-5 秒邮件到达
3. 连接 QQ 邮箱 IMAP
4. 搜索最新邮件（主题包含"验证码"/"中信"/"登录"）
5. 提取邮件正文中的 6 位数字验证码
6. 输入验证码到页面
```

### 邮件搜索策略

1. 获取最新 10 封邮件
2. 按时间倒序搜索主题包含以下关键词的邮件：
   - "验证码"
   - "中信"
   - "登录"
3. 匹配成功后提取正文中的 6 位数字

## 配置项变更

### application.yml 变更

| 旧配置 | 新配置 | 说明 |
|--------|--------|------|
| `spring.email.*` | `spring.email.*` | 保持不变 |
| Redis 相关 | **删除** | 移除所有 Redis 配置 |

### 需求.md 邮箱配置

```yaml
spring:
  email:
    host: imap.qq.com
    port: 993
    username: 1325533186@qq.com
    password: <QQ邮箱授权码>
```

## 代码变更

### CaptchaFetchService 重构

```java
// 移除的内容
- private RedisTemplate<String, Object> redisTemplate;
- @Autowired RedisTemplate
- tryGetFromRedisWithRetry()
- fetchFromRedis()
- peekRedisCode()
- clearRedisCode()

// 保留/优化的内容
+ fetchLatestEmail()       // 优化搜索策略
+ extractCodeFromEmail()   // 保留
+ isValidSmsCode()         // 保留
```

### AutoLoginService 简化

```java
// 之前：Redis 3次重试 + 邮件备选
String smsCode = captchaFetchService.getPhoneCodeWithRetry(...);

// 简化后：直接邮件获取 + 3次重发
String smsCode = captchaFetchService.getPhoneCodeWithRetry(...);
```

## 测试验证

| 测试项 | 验证方式 |
|--------|---------|
| 邮件连接 | IMAP 连接成功 |
| 邮件搜索 | 找到目标邮件 |
| 验证码提取 | 正确提取 6 位数字 |
| 重试机制 | 3 次自动重发 |
| CDP 集成 | 全流程端到端测试 |
