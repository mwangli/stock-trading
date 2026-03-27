# 浏览器会话持久化方案 - CDP 直连模式

## Why

中信证券平台**每天最多获取8次验证码**，需要浏览器会话持久化避免每次重新登录验证。通过 Chrome DevTools Protocol (CDP) 直连模式实现会话复用。

## What Changes

- 统一使用 CDP 直连模式连接 Chrome
- 支持环境变量 `CHROME_DEBUG_PORT` 配置 CDP 端口
- 支持环境变量 `CHROME_USER_DATA_DIR` 配置持久化目录
- 提供本地 Chrome 启动脚本

## Impact

- Affected specs: 会话持久化
- Affected code:
  - `application.yml` - CDP 直连配置
  - `BrowserSessionManager.java` - CDP 直连实现
  - `docker-compose.yml` - Chrome 容器配置
  - `scripts/start-local-chrome.ps1` - 本地启动脚本

---

## 环境变量配置

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `CHROME_DEBUG_PORT` | `9222` | CDP 调试端口 |
| `CHROME_USER_DATA_DIR` | `` | 持久化目录（空=使用本地） |

---

## 架构设计

### CDP 直连模式

```
┌─────────────────────────────────────┐
│  Chrome 浏览器                       │
│  - 本地: start-local-chrome.ps1     │
│  - Docker: selenium/chrome         │
│  - CDP 端口: 9222                   │
│  - Profile: ~/chrome-sessions/stock │
└─────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────┐
│  后端 BrowserSessionManager         │
│  - debuggerAddress: localhost:9222  │
│  - 直接连接 Chrome CDP              │
│  - 支持会话持久化                   │
└─────────────────────────────────────┘
```

---

## 配置说明

### application.yml

```yaml
chrome:
  directConnect: true
  debugger:
    url: http://localhost:${CHROME_DEBUG_PORT:9222}
  headless: false
  userDataDir: ${CHROME_USER_DATA_DIR:}
```

### 本地启动脚本 (start-local-chrome.ps1)

```powershell
$ProfileDir = "$env:USERPROFILE\chrome-sessions\stock"
$DebugPort = 9222

& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
    --remote-debugging-port=$DebugPort `
    --user-data-dir=$ProfileDir `
    --disable-blink-features=AutomationControlled
```

### Docker 配置 (docker-compose.yml)

```yaml
chrome:
  image: selenium/chrome:latest
  ports:
    - "9222:9222"    # CDP 端口
    - "7900:7900"    # VNC 端口（可选）
  volumes:
    - chrome-userdata:/home/seluser/chrome-data
  command: bash -c "google-chrome --headless=new --remote-debugging-port=9222 --user-data-dir=/home/seluser/chrome-data --disable-blink-features=AutomationControlled --no-sandbox --disable-dev-shm-usage"
```

---

## 启动流程

### 本地开发

```powershell
# 1. 启动本地 Chrome
.\scripts\start-local-chrome.ps1

# 2. 验证 CDP
curl.exe http://localhost:9222/json

# 3. 启动后端（自动检测并连接）
mvn spring-boot:run
```

### Docker 部署

```bash
# 1. 启动 Docker Chrome
docker start stock-chrome

# 2. 验证 CDP
curl.exe http://localhost:9222/json

# 3. 启动后端
docker-compose up -d stock-backend
```

---

## 关键优势

| 特性 | 说明 |
|------|------|
| 会话持久化 | Cookie/LocalStorage 保存在 user-data-dir |
| 调试友好 | 可通过 DevTools 直接调试 |
| 简单可靠 | 无中间代理，直连 Chrome |
| 跨平台 | 本地/Docker 均支持 |
