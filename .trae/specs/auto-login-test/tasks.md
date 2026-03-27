# 浏览器会话持久化任务清单 - CDP 直连模式

## 问题背景

通过 CDP 直连模式实现浏览器会话持久化，删除所有 Selenium Grid 相关配置。

---

## Task 1: 环境变量配置

- [x] 1.1 application.yml 支持 `CHROME_DEBUG_PORT` 环境变量
- [x] 1.2 application.yml 支持 `CHROME_USER_DATA_DIR` 环境变量
- [x] 1.3 `directConnect: true` 配置

## Task 2: 本地 Chrome 启动脚本

- [x] 2.1 创建 `scripts/start-local-chrome.ps1`
- [x] 2.2 使用 `--remote-debugging-port=9222`
- [x] 2.3 使用 `--user-data-dir` 指定 Profile 目录

## Task 3: Docker Chrome 配置

- [x] 3.1 docker-compose.yml 配置 Chrome 容器
- [x] 3.2 配置 `--remote-debugging-port=9222`
- [x] 3.3 配置 `--user-data-dir` 持久化
- [x] 3.4 配置 chrome-userdata volume

## Task 4: BrowserSessionManager 实现

- [x] 4.1 CDP 直连模式实现
- [x] 4.2 调试日志输出

## Task 5: 验证测试

### CDP 连接验证

- [x] 5.1 本地 Chrome 启动脚本运行
- [x] 5.2 CDP 端口 9222 可访问
- [x] 5.3 后端直连模式启动成功

### 会话持久化验证

- [ ] 5.4 Profile 目录存在
- [ ] 5.5 完成网站登录后 Cookie 保存
- [ ] 5.6 关闭 Chrome 后重新启动，登录状态保留

---

## 文件变更清单

| 文件 | 变更类型 | 说明 |
|------|---------|------|
| `application.yml` | 已完成 | CDP 直连配置 |
| `BrowserSessionManager.java` | 已完成 | CDP 直连实现 |
| `docker-compose.yml` | 已完成 | Chrome 容器配置 |
| `scripts/start-local-chrome.ps1` | 已完成 | 本地 Chrome 启动脚本 |

---

## 验证命令

### 本地模式

```powershell
# 启动 Chrome
.\scripts\start-local-chrome.ps1

# 验证 CDP
curl.exe http://localhost:9222/json

# 启动后端
mvn spring-boot:run

# 测试 API
curl.exe -X POST http://localhost:8080/api/browser/start
```

### Docker 模式

```bash
# 启动 Docker Chrome
docker start stock-chrome

# 验证 CDP
curl.exe http://localhost:9222/json

# 启动后端
mvn spring-boot:run
```
