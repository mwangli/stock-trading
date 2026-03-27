# 会话持久化任务清单 - 直连模式

## 问题背景

中信证券平台**每天最多获取8次验证码**，必须实现会话持久化来避免重复手机验证。

## 方案：直连模式（CDP）

已全面转向直连模式，删除所有 Grid 相关代码和配置。

---

## Task 1: 配置修改

- [x] 1.1 修改 application.yml - 启用 directConnect: true
- [x] 1.2 删除 chrome.remote.url 配置
- [x] 1.3 修改 docker-compose.yml - 删除 Grid 配置，保留 9222 端口

## Task 2: 代码修改

- [x] 2.1 BrowserSessionManager.java - 删除 Grid/RemoteWebDriver 代码
- [x] 2.2 BrowserSessionManager.java - 简化启动逻辑，只保留直连模式
- [x] 2.3 删除不需要的 import（URL、RemoteWebDriver、By）
- [x] 2.4 修改 configureChromeOptions - 移除 Grid 模式专属参数

## Task 3: 文档更新

- [x] 3.1 更新 spec.md - 删除 Grid 方案描述
- [x] 3.2 更新 tasks.md - 删除 Grid 相关任务
- [x] 3.3 更新 checklist.md - 删除 Grid 相关检查项

## Task 4: Docker Chrome 容器配置

- [x] 4.1 Chrome 容器使用独立启动命令（不通过 Grid）
- [x] 4.2 配置 Xvfb 虚拟显示
- [x] 4.3 配置 --remote-debugging-port=9222
- [x] 4.4 配置 --user-data-dir 持久化目录
- [x] 4.5 挂载 chrome-userdata 卷

## Task 5: 验证测试

- [ ] 5.1 重启 Chrome Docker 容器
- [ ] 5.2 重启后端服务
- [ ] 5.3 测试 CDP 直连模式启动
- [ ] 5.4 测试会话复用
- [ ] 5.5 测试会话持久化（完成登录后重启后端，验证无需重新登录）

---

## 配置变更总结

### application.yml

```yaml
# 删除前
chrome:
  directConnect: false
  remote:
    url: http://localhost:4444/wd/hub

# 删除后
chrome:
  directConnect: true
  debugger:
    url: http://localhost:9222
```

### docker-compose.yml

```yaml
# 删除前
chrome:
  ports:
    - "4444:4444"    # Selenium Grid
  environment:
    - SE_NODE_MAX_SESSIONS=1
    - SE_SESSION_REQUEST_TIMEOUT=3600
  command: /opt/bin/entry_point.sh

# 删除后
chrome:
  ports:
    - "9222:9222"    # CDP 直连
  command: bash -c "Xvfb :99 -screen 0 1920x1080x24 & sleep 2 && google-chrome --remote-debugging-port=9222 --user-data-dir=/home/seluser/.config/chromium ..."
```

### BrowserSessionManager.java

- 删除 `chromeRemoteUrl` 字段
- 删除 `if (chromeRemoteUrl != null)` 分支
- 删除 `RemoteWebDriver` import
- 删除 `configureChromeOptionsForDirectConnect()` 方法
- 保留 `configureChromeOptions()` 方法作为唯一配置方式
