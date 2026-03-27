# 混合方案 - 本地/Docker Chrome 会话持久化

## 方案概述

| 环境 | Chrome 来源 | 会话存储 | 适用场景 |
|------|------------|----------|----------|
| 开发环境 | 本地 Chrome | `C:\Users\<用户名>\chrome-sessions\stock` | 调试方便，会话持久化 |
| 生产环境 | Docker Chrome | `chrome-userdata` volume | 服务器部署，自动运维 |

---

## 开发环境使用步骤

### 1. 启动本地 Chrome

```powershell
# PowerShell 中执行
cd d:\ai-stock-trading
.\scripts\start-local-chrome.ps1
```

或者手动启动：
```powershell
& "C:\Program Files\Google\Chrome\Application\chrome.exe" `
    --remote-debugging-port=9222 `
    --user-data-dir="$env:USERPROFILE\chrome-sessions\stock" `
    --disable-blink-features=AutomationControlled
```

### 2. 配置后端

确保 `application.yml` 中 userDataDir 为空（使用本地 Chrome 时不需要）：

```yaml
chrome:
  userDataDir: ${CHROME_USER_DATA_DIR:}
```

### 3. 启动后端

```bash
cd d:\ai-stock-trading\backend
mvn spring-boot:run
```

### 4. 执行登录测试

```bash
# 1. 启动浏览器
POST http://localhost:8080/api/browser/start

# 2. 导航到登录页
POST http://localhost:8080/api/browser/navigate/login

# 3. 后续登录流程...
```

### 5. 验证会话持久化

登录完成后：
1. **关闭本地 Chrome**
2. **重新启动本地 Chrome**（使用相同 `--user-data-dir`）
3. 访问登录页，验证 Cookie 是否保留

---

## 生产环境使用步骤

### 1. 配置环境变量（可选）

在服务器上设置：
```bash
export CHROME_USER_DATA_DIR=/home/seluser/.config/chromium
```

### 2. 启动 Docker Chrome

```bash
cd d:\ai-stock-trading
docker-compose up -d chrome
```

### 3. 启动后端

```bash
docker-compose up -d stock-backend
```

---

## 快速切换脚本

创建切换脚本 `switch-chrome-env.ps1`：

```powershell
# 切换到本地 Chrome
$env:CHROME_USER_DATA_DIR = ""
Write-Host "已切换到本地 Chrome" -ForegroundColor Green

# 切换到 Docker Chrome
$env:CHROME_USER_DATA_DIR = "/home/seluser/.config/chromium"
Write-Host "已切换到 Docker Chrome" -ForegroundColor Green
```

---

## 常见问题

### Q1: 端口 9222 被占用？

```powershell
# 检查占用端口的进程
netstat -ano | findstr :9222

# 或者运行脚本自动检测并连接
.\scripts\start-local-chrome.ps1
```

### Q2: 如何确认使用的是哪个 Chrome 实例？

检查日志输出：
```
[浏览器启动] 使用 CDP 直连模式: http://localhost:9222
[浏览器启动] 启用浏览器持久化: user-data-dir=...
```

### Q3: 本地 Chrome 和 Docker Chrome 冲突？

确保同一时间只运行一个 Chrome 实例。

---

## 会话持久化验证清单

- [ ] 本地 Chrome 启动成功
- [ ] 后端直连模式启动成功
- [ ] 完成网站登录
- [ ] 关闭 Chrome
- [ ] 重新启动 Chrome
- [ ] 访问登录页，Cookie 仍然有效
