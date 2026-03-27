# 会话持久化方案 - 直连模式

## Why

中信证券平台**每天最多获取8次验证码**，频繁重启浏览器会导致：
1. 每次都需重新进行手机验证
2. 验证码次数迅速耗尽
3. 无法完成自动化交易流程

## 方案选择

采用 **直连模式（CDP Direct Connect）** 实现会话持久化：

| 方案 | 持久化能力 | 复杂度 | 推荐度 |
|------|-----------|--------|--------|
| 直连模式（CDP） | ✅ 完全支持 | 低 | ⭐⭐⭐⭐⭐ |

**选择直连模式的原因**：
1. 直接控制 Chrome 实例，`--user-data-dir` 完全生效
2. 浏览器配置（Cookie、Session）持久化保存
3. 不依赖 Selenium Grid

---

## 架构原理

```
┌─────────────────────────────────────────────────────────────┐
│  Chrome 容器/进程                                           │
│  ┌─────────────────────────────────────────────────────┐  │
│  │ Chrome 实例                                          │  │
│  │  --remote-debugging-port=9222                      │  │
│  │  --remote-debugging-address=0.0.0.0                 │  │
│  │  --user-data-dir=/home/seluser/.config/chromium    │  │
│  │                                                      │  │
│  │  CDP WebSocket: ws://0.0.0.0:9222/devtools/...      │  │
│  └─────────────────────────────────────────────────────┘  │
│                              ▲                            │
│                              │                            │
└──────────────────────────────┼─────────────────────────────┘
                               │
┌──────────────────────────────┼─────────────────────────────┐
│  后端 BrowserSessionManager  │                            │
│                              │                            │
│  1. debuggerAddress=localhost:9222                        │
│  2. 创建 ChromeDriver 直连到 Chrome                        │
│  3. 复用同一 Chrome 实例                                   │
└──────────────────────────────┴─────────────────────────────┘
```

---

## 环境要求

### 直连模式要求

1. **Chrome 必须监听 `0.0.0.0:9222`**（不是 `127.0.0.1`）
2. 后端需要能够访问 Chrome 的 CDP 端口

### Docker 环境配置

```yaml
chrome:
  image: selenium/standalone-chrome:latest
  ports:
    - "9222:9222"
  command: bash -c "Xvfb :99 -screen 0 1920x1080x24 &
    google-chrome
    --remote-debugging-address=0.0.0.0
    --remote-debugging-port=9222
    --user-data-dir=/home/seluser/.config/chromium
    ..."
```

**注意**：Windows Docker Desktop 环境下，Chrome 可能无法正确绑定到 `0.0.0.0`，始终绑定到 `127.0.0.1`。这是 Docker 网络限制导致的。

### 推荐部署方式

| 环境 | 推荐方式 | 说明 |
|------|---------|------|
| Linux 服务器 | 直接运行 Chrome | 最简单的直连模式 |
| Linux Docker | 独立 Chrome 容器 | 需要配置 `--network=host` 或 `--remote-debugging-address=0.0.0.0` |
| Windows Docker | 不推荐 | Chrome 无法绑定到 `0.0.0.0` |

---

## 配置说明

### application.yml

```yaml
chrome:
  directConnect: true
  debugger:
    url: http://localhost:9222
  userDataDir: /home/seluser/.config/chromium
  headless: false
```

### BrowserSessionManager.java

使用 `debuggerAddress` 方式直连：

```java
ChromeOptions options = new ChromeOptions();
options.setExperimentalOption("debuggerAddress", "localhost:9222");
WebDriver driver = new ChromeDriver(options);
```

---

## 验证方法

| 验证点 | 验证方式 | 预期结果 |
|--------|---------|----------|
| 1. Chrome CDP 可访问 | `curl http://localhost:9222/json` | 返回 Chrome 信息 |
| 2. CDP 连接成功 | 调用 `/api/browser/start` | 日志显示 "使用 CDP 直连模式" |
| 3. 会话复用 | 调用 `/api/browser/start` 两次 | 第二次显示 "复用现有实例" |
| 4. Cookie 持久化 | 完成登录后重启后端 | 无需重新登录 |

---

## 关键配置项

| 配置 | 值 | 说明 |
|------|-----|------|
| `chrome.directConnect` | `true` | 启用直连模式 |
| `chrome.debugger.url` | `http://localhost:9222` | CDP 调试端口 |
| `chrome.userDataDir` | `/home/seluser/.config/chromium` | 持久化目录 |
| `--remote-debugging-address` | `0.0.0.0` | 必须绑定到所有接口 |

---

## Windows Docker 环境限制

在 Windows Docker Desktop 环境下，Chrome 无法绑定到 `0.0.0.0`，始终绑定到 `127.0.0.1`。这导致宿主机无法访问 Chrome 的 CDP 端口。

**解决方案**：
1. 在 Linux 服务器上部署（推荐）
2. 使用 Windows 本地安装的 Chrome（不使用 Docker）
3. 继续使用 Grid 模式（但不支持真正的会话持久化）
