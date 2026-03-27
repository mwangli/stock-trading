# 浏览器会话持久化验证清单 - CDP 直连模式

## 环境变量配置检查

- [x] `CHROME_DEBUG_PORT` 环境变量支持（默认 9222）
- [x] `CHROME_USER_DATA_DIR` 环境变量支持
- [x] `directConnect: true` 配置

---

## Chrome 启动检查

### 本地 Chrome

- [x] `scripts/start-local-chrome.ps1` 存在
- [x] 脚本能正确启动 Chrome
- [x] 使用 `--remote-debugging-port=9222`
- [x] 使用 `--user-data-dir` 指定 Profile 目录

### Docker Chrome

- [x] `docker start stock-chrome` 成功启动
- [x] 容器状态为 `Up`
- [x] Chrome 进程正常运行

---

## CDP 连接检查

- [x] `http://localhost:9222/json` 可访问
- [x] 后端 `/api/browser/start` 返回成功
- [x] 日志显示 "使用 CDP 直连模式"

---

## 会话复用检查

- [x] 第二次调用 `/api/browser/start` 显示 "复用现有实例"

---

## 会话持久化检查

- [ ] Profile 目录存在
- [ ] 完成网站登录后 Cookie 保存到 Profile 目录
- [ ] 关闭 Chrome 后重新启动，登录状态保留

---

## 代码质量检查

- [x] `mvn compile` 编译通过
- [x] 无未处理的 Bean 冲突（RestTemplateConfig）
- [x] BrowserSessionManager 日志输出完整

---

## 测试结果记录

| # | 测试项 | 结果 | 备注 |
|---|--------|------|------|
| 1 | 本地 Chrome 启动 | ✅ 通过 | 脚本运行正常 |
| 2 | CDP 端口访问 | ✅ 通过 | http://localhost:9222/json |
| 3 | 后端直连模式 | ✅ 通过 | 日志显示 CDP 直连 |
| 4 | 会话复用 | ✅ 通过 | 复用现有实例 |
| 5 | Docker Chrome | ✅ 通过 | 容器运行正常 |
| 6 | 会话持久化 | ⏳ 待验证 | 需完成实际登录 |

---

## 关键日志检查点

```
[浏览器启动] 使用 CDP 直连模式: http://localhost:9222
[浏览器启动] 浏览器已在运行，复用现有实例
[浏览器启动] 启用浏览器持久化: user-data-dir=xxx
```
