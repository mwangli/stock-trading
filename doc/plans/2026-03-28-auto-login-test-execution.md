# 自动登录系统 — 详细测试执行计划

> **For Agent:** REQUIRED SUB-SKILL: Use `executing-plans` 技能 to implement this plan task-by-task.

**Goal:** 对自动登录系统的 98 个测试用例逐步执行验证，每个环节可测试、可验证、可追溯，并记录潜在问题、修复方案和回归测试手段。

**Architecture:** 基于 REST API 驱动的手动测试，按 12 个阶段依次执行。每个步骤通过 curl 命令调用后端 API，结合日志和截图验证结果。测试数据和截图保存在 `.tmp/test-results/` 目录。

**Tech Stack:** Spring Boot 3.2.10 + Selenium CDP + Java 17 / curl / Chrome DevTools Protocol

---

## 测试前准备

### Task 0: 创建测试结果目录与日志收集脚本

**Files:**
- Create: `.tmp/test-results/` 目录
- Create: `.tmp/test-results/test-log.md` 测试日志文件

**Step 1: 创建目录结构**

```bash
mkdir -p .tmp/test-results/screenshots
mkdir -p .tmp/test-results/logs
echo "# 测试执行日志 $(date +%Y-%m-%d)" > .tmp/test-results/test-log.md
echo "" >> .tmp/test-results/test-log.md
echo "| 时间 | 用例编号 | 结果 | 备注 |" >> .tmp/test-results/test-log.md
echo "|------|---------|------|------|" >> .tmp/test-results/test-log.md
```

**Step 2: 确认后端日志文件位置**

```bash
# 后端日志默认输出到控制台，另开终端窗口监控：
cd backend && mvn spring-boot:run 2>&1 | tee ../.tmp/test-results/logs/backend.log
```

---

## 阶段 1: 环境准备 (E-01 ~ E-13)

### Task 1: 基础环境检查 (E-01 ~ E-10)

**目的:** 确认所有依赖服务可用，避免后续测试因环境问题阻塞。

**Step 1: 检查 Java 版本 (E-01)**

```bash
java -version
```

- **预期:** 输出包含 `openjdk 17` 或 `java 17`
- **验证方式:** 目视确认版本号
- **可能问题:**
  - 问题: Java 版本为 8 或 11，非 17
  - 修复: 安装 JDK 17 并设置 `JAVA_HOME` 环境变量指向 JDK 17 目录，更新 `PATH`
  - 重新测试: 重新执行 `java -version`，确认输出 `17.x`
  - 问题: `java` 命令不存在
  - 修复: 安装 JDK 17，配置环境变量
  - 重新测试: 关闭并重开终端，执行 `java -version`

**Step 2: 检查 Maven (E-02)**

```bash
mvn -version
```

- **预期:** 输出 Maven `3.x` 且 Java 版本为 17
- **验证方式:** 确认 Maven version 行和 Java home 行
- **可能问题:**
  - 问题: Maven 使用了错误的 Java 版本
  - 修复: 确认 `JAVA_HOME` 指向 JDK 17，而非其他版本
  - 重新测试: `mvn -version` 检查 Java home 指向

**Step 3: 检查 Chrome 安装 (E-03)**

```bash
dir "C:\Program Files\Google\Chrome\Application\chrome.exe"
```

- **预期:** 文件存在
- **验证方式:** dir 命令返回文件信息
- **可能问题:**
  - 问题: Chrome 安装在非默认路径（如 `C:\Program Files (x86)\Google\...`）
  - 修复: 修改 `BrowserSessionManager.java` 中 `CHROME_PATH` 常量，或创建符号链接
  - 重新测试: 重新执行 dir 命令确认路径可达
  - 问题: Chrome 未安装
  - 修复: 下载安装 Chrome 浏览器（稳定版），安装到默认路径
  - 重新测试: dir 命令确认文件存在

**Step 4: ~~检查 MySQL (E-04)~~ — 已移除，自动登录模块不直接依赖数据库**

**Step 5: ~~检查 MongoDB (E-05)~~ — 已移除**

**Step 6: ~~检查 Redis (E-06)~~ — 已移除**

**Step 7: 编译后端 (E-07)**

```bash
cd backend && mvn compile
```

- **预期:** `BUILD SUCCESS`
- **可能问题:**
  - 问题: 依赖下载失败（网络问题）
  - 修复: 配置 Maven 镜像仓库（如阿里云），或使用代理
  - 重新测试: `mvn compile -U` 强制更新依赖
  - 问题: 编译错误
  - 修复: 查看错误日志，修复代码问题后重新编译
  - 重新测试: `mvn compile`

**Step 8: 启动后端 (E-08)**

```bash
cd backend && mvn spring-boot:run
```

- **预期:** 控制台输出 `Started Application` 且端口 8080 监听
- **验证方式:** `curl http://localhost:8080/actuator/health` 返回 UP，或 `netstat -an | findstr 8080`
- **可能问题:**
  - 问题: 端口 8080 被占用
  - 修复: `netstat -ano | findstr 8080` 找到占用进程，`taskkill /PID <pid> /F` 释放端口
  - 重新测试: 重新启动后端
  - 问题: 数据库连接失败
  - 修复: 检查 `application.yml` 中数据库配置，确认 MySQL/MongoDB/Redis 均已启动
  - 重新测试: 重新启动后端，观察日志无连接错误

**Step 9: 检查 IMAP 可达性 (E-09)**

```bash
# Windows 下可用 PowerShell 测试：
powershell -Command "Test-NetConnection -ComputerName imap.qq.com -Port 993"
```

- **预期:** `TcpTestSucceeded : True`
- **可能问题:**
  - 问题: 端口 993 被防火墙拦截
  - 修复: 添加防火墙出站规则允许 993 端口，或检查企业网络策略
  - 重新测试: 重新执行连接测试
  - 问题: DNS 解析失败
  - 修复: 检查 DNS 设置，尝试 `nslookup imap.qq.com`
  - 重新测试: 重新执行连接测试

**Step 10: 检查环境变量 (E-10)**

```bash
echo $GLOBAL_DB_PASSWORD
# 或 Windows: echo %GLOBAL_DB_PASSWORD%
```

- **预期:** 输出非空字符串
- **可能问题:**
  - 问题: 环境变量未设置
  - 修复: 在系统环境变量中添加 `GLOBAL_DB_PASSWORD`，或在 `application.yml` 中直接配置
  - 重新测试: 重开终端后 echo 确认

---

### Task 2: Chrome 自动启动验证 (E-11 ~ E-13)

**前置条件:** Task 1 全部通过

**Step 1: 确保 Chrome 未运行**

```bash
taskkill /f /im chrome.exe 2>/dev/null
# 等待 2 秒
sleep 2
# 确认无 chrome 进程
tasklist | grep -i chrome
```

- **预期:** 无 chrome.exe 进程
- **可能问题:**
  - 问题: Chrome 进程杀不掉（权限不足）
  - 修复: 以管理员权限运行终端，重新执行 taskkill
  - 重新测试: tasklist 确认无残留进程

**Step 2: 调用自动启动 API (E-11)**

```bash
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
```

- **预期:** `{"success": true, ...}`，后端日志输出 "已启动本地 Chrome 进程"
- **验证方式:**
  1. curl 返回 JSON 中 `success` 为 `true`
  2. 后端日志包含 "Chrome 调试端口不可访问，尝试自动启动" 和 "已启动本地 Chrome 进程"
- **可能问题:**
  - 问题: Chrome 路径与代码硬编码不一致
  - 修复: 检查 `BrowserSessionManager.java` 中 `startLocalChrome()` 方法的路径，修改为实际安装路径
  - 重新测试: 重启后端（代码修改需重新编译），再次调用 `POST /api/browser/start`
  - 问题: 端口 9222 已被其他进程占用
  - 修复: `netstat -ano | findstr 9222` 找到占用进程并结束
  - 重新测试: 再次调用 `POST /api/browser/start`
  - 问题: ProcessBuilder 启动后 Chrome 闪退
  - 修复: 检查 `--user-data-dir` 路径是否存在写权限，手动创建目录 `mkdir %USERPROFILE%\chrome-sessions\stock`
  - 重新测试: 再次调用 API

**Step 3: 验证 CDP 端口 (E-12)**

```bash
curl -s http://localhost:9222/json | python -m json.tool
```

- **预期:** 返回 JSON 数组，包含至少一个 tab 信息
- **可能问题:**
  - 问题: 返回连接拒绝
  - 修复: 等待更长时间（Chrome 启动可能需要 5-10 秒），或检查 Chrome 是否真正启动了
  - 重新测试: `sleep 5` 后再次 curl

**Step 4: 验证会话目录 (E-13)**

```bash
ls -la "$USERPROFILE/chrome-sessions/stock/Default/"
```

- **预期:** 目录存在且包含 Chrome Profile 文件（Cookies, Preferences 等）
- **可能问题:**
  - 问题: 目录不存在
  - 修复: 检查 Chrome 启动参数中 `--user-data-dir` 值，确认路径拼接正确
  - 重新测试: 重启 Chrome 后再次检查

---

## 阶段 2: 浏览器管理 (TC-BSM-01 ~ TC-BSM-06)

### Task 3: 浏览器启动与复用测试

**Step 1: TC-BSM-01 — 首次启动浏览器（已在 Task 2 覆盖，此处复核）**

```bash
# 确认浏览器状态
curl -s http://localhost:8080/api/browser/status | python -m json.tool
```

- **预期:** `running: true`
- **记录:** 记录响应 JSON 到测试日志

**Step 2: TC-BSM-02 — 复用已有实例**

```bash
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
```

- **预期:** `success: true`，后端日志显示 "复用现有浏览器实例"（**不是** "启动新浏览器"）
- **验证方式:**
  1. 检查后端日志关键字
  2. 确认 Chrome 进程数未增加（`tasklist | grep -c chrome`）
- **可能问题:**
  - 问题: 日志显示重新启动了新浏览器（未复用）
  - 修复: 检查 `BrowserSessionManager.isChromeReachable()` 逻辑，确认 HTTP GET `localhost:9222` 的检测是否正常
  - 重新测试: 再次调用 start API，观察日志

**Step 3: TC-BSM-04 — 反爬虫指纹验证**

```bash
# 检查 navigator.webdriver
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return navigator.webdriver"}' | python -m json.tool
```

- **预期:** 返回 `undefined` 或 `null`（**不是** `true`）
- **可能问题:**
  - 问题: 返回 `true`，说明反爬注入未生效
  - 修复: 检查 Chrome 启动参数是否包含 `--disable-blink-features=AutomationControlled`，以及 CDP 连接后是否执行了 `navigator.webdriver = undefined` 的 JS 注入
  - 重新测试: 修复后重启浏览器，再次执行 JS 检查

```bash
# 检查 navigator.languages
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return JSON.stringify(navigator.languages)"}' | python -m json.tool
```

- **预期:** `["zh-CN", "zh"]`

```bash
# 检查 navigator.platform
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return navigator.platform"}' | python -m json.tool
```

- **预期:** `Win32`

**Step 4: TC-BSM-05 — 浏览器关闭与资源释放**

```bash
curl -s -X POST http://localhost:8080/api/browser/quit | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
# 状态应为未运行
curl -s http://localhost:8080/api/browser/status | python -m json.tool
```

- **预期:** `running: false`
- **可能问题:**
  - 问题: quit 后 Chrome 进程仍然存在
  - 修复: 检查 `BrowserSessionManager.quit()` 是否调用了 `driver.quit()` 和进程清理
  - 重新测试: `tasklist | grep chrome` 确认无残留

**Step 5: TC-BSM-06 — 会话持久化验证（关键测试）**

此测试验证 Chrome 关闭重启后 Cookie/localStorage 是否保持。

```bash
# 5a. 重新启动浏览器
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool

# 5b. 导航到登录页
curl -s -X POST http://localhost:8080/api/browser/navigate/login | python -m json.tool

# 5c. 刷新页面（确保完整加载）
sleep 3
curl -s -X POST http://localhost:8080/api/browser/refresh | python -m json.tool
sleep 3

# 5d. 记录当前 Cookie（如果之前有登录过，会有残留）
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return document.cookie"}' | python -m json.tool

# 5e. 记录 localStorage
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return JSON.stringify(localStorage)"}' | python -m json.tool
```

- **验证方式:** 将 5d/5e 的输出保存，后续在完成登录并重启后对比
- **可能问题:**
  - 问题: Cookie 为空（之前未登录过，属正常情况）
  - 处理: 这不是问题，需在完成完整登录后再做一次对比测试
  - 问题: `--user-data-dir` 目录权限不足导致无法写入
  - 修复: 赋予当前用户对该目录的完全控制权限
  - 重新测试: 重启浏览器后再次读取 Cookie

---

### Task 3a: Chrome 模式切换测试 (TC-BSM-03)

> **注意:** 此测试需要 Docker 环境，若不具备 Docker 可跳过 docker 模式部分。

**Step 1: 测试 local 模式（默认）**

```bash
# 确保 Chrome 未运行
taskkill /f /im chrome.exe 2>/dev/null
sleep 2

# 确认 CHROME_MODE 未设置或为 local
echo $CHROME_MODE

# 调用启动
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
```

- **预期:** 日志显示 "已启动本地 Chrome 进程"
- **验证:** `wmic process where "name='chrome.exe'" get CommandLine | findstr 9222`

**Step 2: 测试 docker 模式（可选）**

> 需修改环境变量 `CHROME_MODE=docker` 后重启后端。

```bash
# 如果有 Docker 且已拉取 chrome 镜像：
# 设置 CHROME_MODE=docker
# 重启后端
# curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
```

- **预期:** 日志显示 "已发送 docker start stock-chrome 命令"
- **可能问题:**
  - 问题: Docker 未安装或容器不存在
  - 修复: `docker pull selenium/standalone-chrome` 并创建容器
  - 问题: Docker 模式下轮询超时
  - 修复: 检查容器启动时间，增加轮询次数或等待间隔

---

## 阶段 3: 页面导航与类型检测 (TC-NAV-01 ~ TC-NAV-04)

### Task 4: 页面导航测试

**前置条件:** 浏览器已启动 (Task 3 Step 1 通过)

**Step 1: TC-NAV-01 — 导航到登录页**

```bash
curl -s -X POST http://localhost:8080/api/browser/navigate/login | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
# 等待页面加载
sleep 3

# 截图确认
curl -s http://localhost:8080/api/browser/debug/screenshot
```

- **预期:** 截图显示登录页面内容（包含输入框、按钮等）
- **可能问题:**
  - 问题: 页面加载超时（网络慢或目标站点不可达）
  - 修复: 检查网络连通性 `ping weixin.citicsinfo.com`，确认 DNS 解析正常
  - 重新测试: 重新调用 navigate API
  - 问题: 页面返回 403/503 错误
  - 修复: 可能被反爬拦截，检查 User-Agent 和浏览器指纹注入是否生效
  - 重新测试: 清除 Cookie 后重新导航
  - 问题: 截图为空白页
  - 修复: 增加等待时间，或检查 URL 是否正确

**Step 2: TC-NAV-01a — 页面刷新（关键步骤）**

```bash
curl -s -X POST http://localhost:8080/api/browser/refresh | python -m json.tool
```

- **预期:** `success: true`
- **等待:** 3 秒
- **验证:**

```bash
sleep 3
# 截图确认页面完整渲染
curl -s http://localhost:8080/api/browser/debug/screenshot

# 确认表单状态
curl -s http://localhost:8080/api/browser/debug/formState | python -m json.tool
```

- **预期:** formState 返回包含输入框元素信息
- **可能问题:**
  - 问题: 刷新后页面白屏或 JS 报错
  - 修复: 可能是缓存问题，尝试 `Ctrl+Shift+R` 硬刷新，或通过 execJs 执行 `location.reload(true)`
  - 重新测试: 截图确认页面正常
  - 问题: formState 返回空（元素未加载）
  - 修复: 增加等待时间到 5 秒，或检查 iframe 加载状态
  - 重新测试: 增加 sleep 后重新获取 formState

**Step 3: TC-NAV-02 — 页面类型检测（登录页）**

```bash
curl -s http://localhost:8080/api/browser/debug/pageType | python -m json.tool
```

- **预期:** 返回 `LOGIN`
- **可能问题:**
  - 问题: 返回 `PHONE_VERIFY`（实际是手机验证页）
  - 这不是bug: 说明当前账号需要手机验证，应跳转到阶段 5 手机验证流程
  - 问题: 返回 `UNKNOWN`
  - 修复: 检查 `AutoLoginService.detectPageType()` 的 DOM 选择器是否匹配当前页面结构（可能目标网站更新了 DOM）
  - 重新测试: 使用 `GET /api/browser/debug/domInspect` 查看当前 DOM 结构，对比选择器

**Step 4: TC-NAV-04 — iframe 切换**

```bash
curl -s -X POST http://localhost:8080/api/browser/frame/switch | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
# 确认表单可见
curl -s http://localhost:8080/api/browser/debug/formState | python -m json.tool

# 查看 frame 信息
curl -s http://localhost:8080/api/browser/debug/frameInfo | python -m json.tool
```

- **可能问题:**
  - 问题: iframe 切换失败，返回 "找不到 iframe"
  - 修复: 检查页面中 iframe 的选择器（`id`/`name`/`src`），可能目标网站修改了 iframe 属性
  - 诊断: `curl -s http://localhost:8080/api/browser/debug/domInspect` 查看页面 iframe 列表
  - 重新测试: 更新选择器代码后，重新导航 + 刷新 + 切换
  - 问题: 切换成功但 formState 为空
  - 修复: iframe 内容可能还在加载，增加等待时间
  - 重新测试: `sleep 3` 后重新获取 formState

---

## 阶段 4: 登录页单步测试 (TC-LF-01 ~ TC-LF-07)

### Task 5: 登录表单填写

**前置条件:** Task 4 全部通过，已切换到登录 iframe

**Step 1: TC-LF-01 — 输入资金账号**

```bash
curl -s -X POST "http://localhost:8080/api/browser/login/inputAccount?account=13278828091" | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
# 截图确认
curl -s http://localhost:8080/api/browser/debug/screenshot
```

- **预期:** 截图中账号输入框显示 `13278828091`
- **额外验证:** 后端日志显示逐字输入，每字间隔 80-140ms
- **可能问题:**
  - 问题: 输入框未定位到（选择器失效）
  - 修复: `GET /api/browser/debug/domInspect` 查看当前 DOM，找到正确的账号输入框选择器，更新代码
  - 重新测试: 刷新页面 → 切换 iframe → 重新输入
  - 问题: 输入值不完整（部分字符丢失）
  - 修复: 检查人类仿真输入逻辑中的延迟是否足够，增加每字延迟
  - 重新测试: 清空输入框后重新输入，截图对比
  - 问题: 输入到了错误的输入框
  - 修复: 检查选择器优先级，确保匹配的是账号框而非密码框
  - 重新测试: 刷新页面后重新执行

**Step 2: TC-LF-02 — 输入交易密码**

```bash
curl -s -X POST "http://localhost:8080/api/browser/login/inputPassword?password=132553" | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认密码框显示掩码字符 `●●●●●●`（6个点）
- **可能问题:**
  - 问题: 密码明文显示（非掩码）
  - 原因: 这可能是截图渲染问题，不影响功能。检查 `<input type="password">` 是否正确
  - 问题: 密码输入被安全控件拦截
  - 修复: 某些券商使用安全键盘，需要通过 JS 直接设置 value 而非模拟键盘输入
  - 重新测试: 通过 `execJs` 检查密码框 value 长度

**Step 3: TC-LF-03 — 数学验证码获取**

```bash
curl -s http://localhost:8080/api/browser/login/captureCaptcha | python -m json.tool
```

- **预期:** 返回 JSON 包含 `captchaResult` 字段（数字值）
- **验证:**
  1. `captchaResult` 是合理数字（通常 0-99）
  2. 后端日志输出识别的数学表达式（如 `OCR识别: 3+5=8`）
  3. `.tmp/` 目录下生成 `captcha_debug_*.png` 调试图片
- **可能问题:**
  - 问题: `captchaResult` 为 null 或 -1（OCR 识别失败）
  - 修复方案 A: 检查 `.tmp/captcha_debug_*.png` 调试图片，确认图片质量
  - 修复方案 B: 可能是验证码图片格式变化，检查 `MathCaptchaService.fetchCaptchaBase64()` 的 API 响应
  - 修复方案 C: OCR 分割参数需要调整，检查 `ocrImage()` 方法的分割逻辑
  - 重新测试: 刷新页面获取新验证码，再次调用 captureCaptcha
  - 问题: Base64 数据解析失败
  - 修复: 检查 `fetchCaptchaBase64()` 中 API 返回的 `MESSAGE` 字段格式是否变化
  - 重新测试: 直接调用底层 API 检查原始响应

**Step 4: TC-LF-04 — 输入验证码结果**

> 使用 Step 3 返回的 `captchaResult` 值

```bash
# 假设 Step 3 返回 captchaResult=8
curl -s -X POST "http://localhost:8080/api/browser/login/inputCaptcha?captcha=8" | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认验证码输入框已填充
- **可能问题:**
  - 问题: 验证码输入框定位失败
  - 修复: 检查验证码输入框的 DOM 选择器
  - 重新测试: 刷新页面后重新执行完整流程（输入账号→密码→获取验证码→输入验证码）

**Step 5: TC-LF-05 — 勾选协议**

```bash
curl -s -X POST http://localhost:8080/api/browser/login/checkAgreements | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认协议勾选框已选中
- **可能问题:**
  - 问题: 协议 checkbox 找不到
  - 修复: 检查 DOM 中 checkbox 选择器，可能页面有多个 checkbox
  - 重新测试: 使用 `domInspect` 定位正确元素后修复代码
  - 问题: 已经是勾选状态（之前的会话残留）
  - 处理: 正常情况，不影响后续流程

**Step 6: TC-LF-06 — 提交登录**

```bash
curl -s -X POST http://localhost:8080/api/browser/login/submit | python -m json.tool
```

- **预期:** `success: true`
- **等待:** 2 秒
- **验证:**

```bash
sleep 2
# 截图查看提交后的状态
curl -s http://localhost:8080/api/browser/debug/screenshot
```

- **预期:**
  - 场景 A: 出现滑块验证码弹窗 → 需要执行滑块验证（进入 Task 6）
  - 场景 B: 页面跳转到交易主页 → 登录成功（跳到 Step 7）
  - 场景 C: 页面提示错误（验证码错误/密码错误）→ 需要排查
- **可能问题:**
  - 问题: 提交按钮点击无反应
  - 修复: 检查按钮选择器，可能被覆盖层遮挡
  - 重新测试: 使用 `execJs` 直接触发表单提交
  - 问题: 验证码错误提示
  - 修复: 重新获取验证码（刷新验证码图片），重新 OCR + 输入 + 提交
  - 重新测试: 从 Step 3 重新开始

**Step 7: 滑块验证处理（条件性）**

```bash
# 检查是否出现滑块
curl -s http://localhost:8080/api/browser/phone/sliderStatus | python -m json.tool
```

- **若 `sliderDetected: true`:**

```bash
# 执行滑块验证
curl -s -X POST http://localhost:8080/api/browser/phone/solveSlider | python -m json.tool
```

- **预期:** `verified: true`
- **可能问题:**
  - 问题: 滑块验证失败（距离不准）
  - 修复: 内部有 3 次重试机制，自动重算距离。若 3 次全部失败，检查 `.tmp/` 下滑块截图
  - 重新测试: 刷新页面，从提交登录开始重试
  - 问题: 滑块图片 URL 提取失败
  - 修复: 检查 `CaptchaService.extractYidunImageUrls()` 的 4 种提取策略，可能需要更新选择器
  - 重新测试: 截图 + DOM 分析后修复代码

**Step 8: TC-LF-07 — 检查登录结果**

```bash
curl -s http://localhost:8080/api/browser/login/check | python -m json.tool
```

- **预期:** `loginSuccess: true`, `token` 为非空字符串且长度 > 50
- **验证:**

```bash
# 确认全局 Token 同步
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** `loggedIn: true`, `token` 非空
- **可能问题:**
  - 问题: `loginSuccess: false`（登录页仍在显示）
  - 修复: 截图检查页面状态，可能是验证码错误/密码错误/需要二次验证
  - 重新测试: 从 Task 5 Step 1 重新开始完整流程
  - 问题: Token 为空（登录成功但提取失败）
  - 修复: 检查 `CookieManager.extractToken()` 的提取策略，可能 Token 的 Cookie name 变化
  - 诊断: 通过 `execJs` 执行 `document.cookie` 和 `JSON.stringify(localStorage)` 查看所有存储数据
  - 重新测试: 修复提取逻辑后，再次调用 `login/check`

---

## 阶段 5: 手机验证单步测试 (TC-PV-01 ~ TC-PV-08)

> **触发条件:** 阶段 3 中 `pageType` 检测为 `PHONE_VERIFY` 时才需要执行此阶段

### Task 6: 手机验证流程

**前置条件:** 页面类型为 `PHONE_VERIFY`

**Step 1: TC-PV-02 — 点击发送验证码**

```bash
curl -s -X POST http://localhost:8080/api/browser/phone/sendCode | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认按钮变为倒计时状态
- **可能问题:**
  - 问题: 按钮不可点击（手机号未输入）
  - 修复: 先确认手机号输入框已填充，可能需要手动输入手机号
  - 重新测试: 输入手机号后重新点击发送

**Step 2: TC-PV-03 — 检查滑块弹窗**

```bash
sleep 2
curl -s http://localhost:8080/api/browser/phone/sliderStatus | python -m json.tool
```

- **预期:** `sliderDetected: true`, `sliderType: YIDUN`
- **可能问题:**
  - 问题: 未弹出滑块（直接发送了验证码）
  - 处理: 正常情况，跳过 Step 3-4，直接进入 Step 5

**Step 3: TC-PV-04 — 执行滑块验证**

```bash
curl -s -X POST http://localhost:8080/api/browser/phone/solveSlider | python -m json.tool
```

- **预期:** `verified: true`
- **验证:** 后端日志包含：
  1. `计算滑块距离: XXXpx`
  2. 40+ 轨迹点信息
  3. 拖动执行记录
- **可能问题:**
  - 问题: 滑块验证 3 次重试均失败
  - 修复: 查看 `.tmp/` 下的滑块调试图片，检查 Sobel 边缘检测是否准确
  - 修复方案: 调整 `CaptchaService.calculateSliderDistance()` 中的比例修正参数
  - 重新测试: 刷新页面，重新发送验证码触发新滑块
  - 问题: 被风控识别为机器人
  - 修复: 检查拖动轨迹的随机性，增加抖动和速度变化
  - 重新测试: 等待 5 分钟后重试（避免频率限制）

**Step 4: TC-PV-05 — 点击确定**

```bash
curl -s -X POST http://localhost:8080/api/browser/phone/confirmSlider | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认滑块弹窗已关闭

**Step 5: TC-PV-06 — 获取短信验证码**

```bash
curl -s -X POST http://localhost:8080/api/browser/phone/getCodeEnhanced | python -m json.tool
```

- **预期:** 返回 6 位数字验证码
- **验证:** 后端日志包含：
  1. IMAP 连接 `imap.qq.com:993` 成功
  2. 匹配到含"验证码"/"登录"/"中信"的邮件
  3. 正则提取到 6 位数字
- **可能问题:**
  - 问题: IMAP 连接失败
  - 修复: 检查邮箱配置（`application.yml` 中的邮箱地址和授权码），确认 QQ 邮箱已开启 IMAP 服务
  - 诊断: 使用第三方邮件客户端（如 Foxmail）测试同一 IMAP 账号
  - 重新测试: 修复配置后重新调用 API
  - 问题: 邮件未到达（验证码短信还没转发到邮箱）
  - 修复: 等待更长时间（内部有 3 次重试 + 重发机制），检查短信转发规则
  - 重新测试: 手动检查邮箱确认邮件到达后再调用
  - 问题: 正则匹配失败（邮件格式变化）
  - 修复: 检查 `CaptchaFetchService` 中的邮件主题匹配关键字和正则表达式
  - 重新测试: 修复正则后重新调用
  - 问题: 获取到旧验证码（上次残留）
  - 修复: 检查时效性过滤逻辑，确保只获取最近几分钟内的邮件
  - 重新测试: 清空邮箱旧邮件后重试

**Step 6: TC-PV-07 — 输入短信验证码**

```bash
# 使用 Step 5 获取的验证码
curl -s -X POST "http://localhost:8080/api/browser/phone/inputCode?code=123456" | python -m json.tool
```

- **预期:** `success: true`
- **验证:** 截图确认验证码已填充
- **可能问题:**
  - 问题: 验证码输入框定位失败
  - 修复: 检查手机验证页的 DOM 结构，更新选择器
  - 重新测试: 修复后重新输入

**Step 7: TC-PV-08 — 提交并跳转**

```bash
curl -s -X POST http://localhost:8080/api/browser/phone/submit | python -m json.tool
```

- **预期:** `success: true`，页面跳转到登录页
- **验证:**

```bash
sleep 3
# 确认跳转到登录页
curl -s http://localhost:8080/api/browser/debug/pageType | python -m json.tool
```

- **预期:** 返回 `LOGIN`
- **后续:** 继续执行阶段 4（Task 5）完成登录页流程
- **可能问题:**
  - 问题: 验证码过期
  - 修复: 验证码有时效性（通常 60 秒），需要更快完成流程
  - 重新测试: 重新发送验证码，快速完成输入和提交
  - 问题: 未跳转（停留在手机验证页）
  - 修复: 截图检查是否有错误提示，可能验证码错误
  - 重新测试: 重新获取验证码并输入

---

## 阶段 6: 验证码模块测试

### Task 7: 滑块验证码详细测试 (TC-CS-01 ~ TC-CS-05)

> **说明:** 这些测试多为内部方法测试，需要通过触发场景（登录提交或发送验证码后弹出滑块）来间接验证。可通过后端日志逐步观察。

**Step 1: TC-CS-01/02 — 滑块类型检测和图片提取**

通过触发滑块场景，观察后端日志：

- **预期日志关键字:**
  - `检测到滑块类型: YIDUN`
  - `背景图URL: https://...`
  - `拼图块URL: https://...`
- **可能问题:**
  - 问题: 滑块类型识别错误
  - 修复: 检查 `detectSliderType()` 中的 DOM 特征匹配
  - 问题: 图片 URL 为空
  - 修复: 检查 4 种提取策略的选择器是否适配当前滑块版本

**Step 2: TC-CS-03 — Sobel 距离计算**

观察后端日志：

- **预期:** `计算滑块距离: XXXpx`（30-200 范围）
- **预期:** `renderDistance = bestX * 220.0 / bgWidth`
- **可能问题:**
  - 问题: 距离明显偏大或偏小
  - 修复: 调整比例修正系数，或检查图片下载是否完整
  - 重新测试: 刷新页面触发新滑块

**Step 3: TC-CS-04 — 轨迹仿真验证**

观察后端日志：

- **预期:** 轨迹点数 >= 40，包含加速-匀速-减速-微调各阶段
- **可能问题:**
  - 问题: 轨迹点数太少（被检测为机器人）
  - 修复: 调整 `generateSliderTrajectory()` 参数，增加细分度
  - 重新测试: 触发新滑块验证

---

### Task 8: 数学验证码测试 (TC-MC-01 ~ TC-MC-03)

**Step 1: TC-MC-01 — Base64 图片获取**

已在 Task 5 Step 3 (TC-LF-03) 间接测试。

额外验证：

```bash
# 检查 .tmp 目录下是否生成调试图片
ls -la .tmp/captcha_debug_*.png
```

- **预期:** 存在调试图片文件
- **可能问题:**
  - 问题: 调试图片未生成
  - 修复: 检查 `.tmp` 目录是否存在写权限，检查代码中调试图片保存逻辑

**Step 2: TC-MC-02/03 — OCR 识别与计算**

通过后端日志验证：

- **预期日志:**
  - `图片分割为 6 段`
  - `OCR识别表达式: 3+5`
  - `计算结果: 8`
- **可能问题:**
  - 问题: OCR 将 `8` 识别为 `6` 或 `0`
  - 修复: 调整 OCR 特征匹配阈值，或增加字符模板
  - 重新测试: 多次刷新验证码，统计识别准确率（应 >= 70%）

---

### Task 9: 短信验证码测试 (TC-CF-01 ~ TC-CF-04)

已在 Task 6 Step 5 (TC-PV-06) 覆盖。

**额外测试: TC-CF-04 — 重试机制验证**

```bash
# 在没有新验证码邮件的情况下调用（模拟获取失败）
curl -s -X POST http://localhost:8080/api/browser/phone/getCodeEnhanced | python -m json.tool
```

- **预期:** 日志显示 3 次重试过程（含重发触发）
- **可能问题:**
  - 问题: 重发操作失败（"获取验证码"按钮处于冷却中）
  - 修复: 增加冷却时间检测，等待按钮可点击后再重发
  - 重新测试: 等待 60 秒冷却后重新测试

---

## 阶段 7: Token 管理测试 (TC-TK-01 ~ TC-TK-03)

### Task 10: Token 提取与同步

**前置条件:** 登录成功

**Step 1: TC-TK-01/02 — Token 提取验证**

```bash
# 检查 Cookie 中的 Token
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return document.cookie"}' | python -m json.tool

# 检查 localStorage
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return JSON.stringify(localStorage)"}' | python -m json.tool

# 检查 sessionStorage
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return JSON.stringify(sessionStorage)"}' | python -m json.tool
```

- **预期:** 至少一个存储位置包含 Token 相关数据
- **验证:** Token 长度 > 50 字符
- **可能问题:**
  - 问题: 所有存储位置均无 Token
  - 修复: 检查 `CookieManager` 的提取策略优先级和匹配关键字
  - 诊断: 逐一输出 Cookie name/localStorage key 列表，手动查找 Token
  - 重新测试: 修复匹配逻辑后重新登录

**Step 2: TC-TK-03 — 全局同步验证**

```bash
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** `loggedIn: true`, `token` 非空
- **额外验证:** 调用一个需要认证的交易 API（如查询持仓），确认 Token 生效
- **可能问题:**
  - 问题: status 显示 loggedIn 但 Token 为空
  - 修复: 检查 `syncTokenToZXRequestUtils()` 的反射调用是否成功
  - 诊断: 后端日志查找 `ZXRequestUtils.setGlobalToken` 相关信息
  - 重新测试: 修复同步逻辑后重新登录

---

## 阶段 8: REST API 测试 (TC-API-01 ~ TC-API-05)

### Task 11: REST API 完整性验证

**Step 1: TC-API-01 — 一键登录成功**

> **注意:** 此步骤执行时间较长（20-60秒），需耐心等待

```bash
# 先退出当前登录状态
curl -s -X POST http://localhost:8080/api/autoLogin/logout | python -m json.tool
curl -s -X POST http://localhost:8080/api/browser/quit | python -m json.tool
sleep 2

# 执行一键登录
time curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" | python -m json.tool
```

- **预期:**
  - HTTP 200
  - `success: true`
  - `data.token` 非空，长度 > 50
  - `data.message` 为 "登录成功"
  - 耗时 20-60 秒
- **可能问题:**
  - 问题: 超时无响应（> 120 秒）
  - 修复: 检查各环节日志，定位卡在哪一步（浏览器启动？页面加载？验证码？）
  - 重新测试: 修复卡顿环节后重试
  - 问题: 返回登录失败
  - 修复: 查看后端日志完整流程，根据错误点修复
  - 重新测试: 重新调用一键登录

**Step 2: TC-API-02 — 参数缺失时使用默认值**

```bash
curl -s -X POST "http://localhost:8080/api/autoLogin/login" | python -m json.tool
```

- **预期:** 使用 `application.yml` 中配置的默认账号密码执行登录
- **可能问题:**
  - 问题: 返回参数错误
  - 修复: 检查 Controller 是否有默认值逻辑，确认配置文件中有 `logging.auto-login.account/password`
  - 重新测试: 修复后重新调用

**Step 3: TC-API-03 — 查询登录状态**

```bash
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** `loggedIn: true`, `browserAlive: true`, `token` 非空
- **无需修复:** 纯查询接口

**Step 4: TC-API-04 — 强制退出**

```bash
curl -s -X POST http://localhost:8080/api/autoLogin/logout | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** `loggedIn: false`
- **可能问题:**
  - 问题: logout 后 status 仍显示 loggedIn
  - 修复: 检查 logout 是否清除了内存中的 Token 和状态标记
  - 重新测试: 重新调用 logout + status

**Step 5: TC-API-05 — 关闭浏览器**

```bash
curl -s -X POST http://localhost:8080/api/autoLogin/quit | python -m json.tool
```

- **预期:** `success: true`
- **验证:**

```bash
curl -s http://localhost:8080/api/browser/status | python -m json.tool
```

- **预期:** `running: false`

---

## 阶段 9: 端到端流程测试 (E2E-01 ~ E2E-03)

### Task 12: E2E-01 — 登录页完整流程

**这是最核心的端到端测试，按顺序执行所有步骤。**

> 依赖关系: 每一步都依赖前一步成功。失败则从失败点重试。

```bash
# Step 1: 启动浏览器
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
# 预期: success=true
# 失败处理: 参考 Task 2

# Step 2: 导航到登录页
curl -s -X POST http://localhost:8080/api/browser/navigate/login | python -m json.tool
# 预期: success=true
# 失败处理: 检查网络连通性

# Step 3: 刷新页面
sleep 3
curl -s -X POST http://localhost:8080/api/browser/refresh | python -m json.tool
# 预期: success=true
sleep 3

# Step 4: 截图确认
curl -s http://localhost:8080/api/browser/debug/screenshot
# 预期: 页面完整渲染

# Step 5: 检测页面类型
curl -s http://localhost:8080/api/browser/debug/pageType | python -m json.tool
# 预期: type=LOGIN
# 若返回 PHONE_VERIFY → 转到 Task 13 (E2E-02)

# Step 6: 切换 iframe
curl -s -X POST http://localhost:8080/api/browser/frame/switch | python -m json.tool
# 预期: success=true
# 失败处理: DOM 结构变化，需更新 iframe 选择器

# Step 7: 检查表单状态
curl -s http://localhost:8080/api/browser/debug/formState | python -m json.tool
# 预期: 返回表单元素列表

# Step 8: 输入账号
curl -s -X POST "http://localhost:8080/api/browser/login/inputAccount?account=13278828091" | python -m json.tool
# 预期: success=true

# Step 9: 输入密码
curl -s -X POST "http://localhost:8080/api/browser/login/inputPassword?password=132553" | python -m json.tool
# 预期: success=true

# Step 10: 获取验证码
CAPTCHA_RESULT=$(curl -s http://localhost:8080/api/browser/login/captureCaptcha | python -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('captchaResult',''))")
echo "验证码结果: $CAPTCHA_RESULT"
# 预期: 数字结果
# 失败处理: 刷新验证码重试

# Step 11: 输入验证码
curl -s -X POST "http://localhost:8080/api/browser/login/inputCaptcha?captcha=$CAPTCHA_RESULT" | python -m json.tool
# 预期: success=true

# Step 12: 勾选协议
curl -s -X POST http://localhost:8080/api/browser/login/checkAgreements | python -m json.tool
# 预期: success=true

# Step 13: 提交登录
curl -s -X POST http://localhost:8080/api/browser/login/submit | python -m json.tool
# 预期: success=true

# Step 14: 等待
sleep 2

# Step 15: 检查滑块
SLIDER=$(curl -s http://localhost:8080/api/browser/phone/sliderStatus | python -c "import sys,json; print(json.load(sys.stdin).get('data',{}).get('sliderDetected',False))")
echo "滑块检测: $SLIDER"

# Step 16: 若需要，解决滑块
if [ "$SLIDER" = "True" ]; then
  curl -s -X POST http://localhost:8080/api/browser/phone/solveSlider | python -m json.tool
fi

# Step 17: 检查登录结果
sleep 2
curl -s http://localhost:8080/api/browser/login/check | python -m json.tool
# 预期: loginSuccess=true, token 非空

# Step 18: 查询全局状态
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
# 预期: loggedIn=true
```

**整体可能问题与修复:**

| 失败步骤 | 典型原因 | 修复方案 | 重测起点 |
|---------|---------|---------|---------|
| Step 1 | Chrome 路径/端口问题 | 修改路径或释放端口 | Step 1 |
| Step 2-4 | 网络/DNS 问题 | 检查网络 | Step 2 |
| Step 5 | DOM 选择器失效 | 更新检测逻辑 | Step 2 |
| Step 6 | iframe 结构变化 | 更新 iframe 选择器 | Step 2 |
| Step 8-9 | 输入框定位失败 | 更新表单选择器 | Step 6 |
| Step 10 | OCR 识别失败 | 调整 OCR 参数 | Step 10（重新获取验证码） |
| Step 13 | 验证码错误 | 重新获取验证码 | Step 10 |
| Step 16 | 滑块距离不准 | 调整 Sobel 参数 | Step 13（重新提交触发新滑块） |
| Step 17 | Token 提取失败 | 更新 Cookie 匹配 | 需先手动确认登录成功 |

---

### Task 13: E2E-02 — 手机验证页完整流程

**触发条件:** Task 12 Step 5 返回 `PHONE_VERIFY`

执行顺序: Task 6（手机验证） → Task 5（登录表单） → 完成

**整体可能问题与修复:**

| 失败步骤 | 典型原因 | 修复方案 | 重测起点 |
|---------|---------|---------|---------|
| 发送验证码 | 手机号未输入 | 检查手机号填充逻辑 | 重新导航 |
| 滑块验证 | 距离不准 | 调整 Sobel | 重新发送验证码 |
| 获取短信码 | IMAP 问题 | 检查邮箱配置 | 重新发送验证码 |
| 提交跳转 | 验证码过期 | 加快流程 | 重新发送验证码 |

---

### Task 14: E2E-03 — 一键登录端到端

```bash
# 彻底重置状态
curl -s -X POST http://localhost:8080/api/autoLogin/logout 2>/dev/null
curl -s -X POST http://localhost:8080/api/browser/quit 2>/dev/null
sleep 2

# 一键登录
echo "开始时间: $(date)"
curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" | python -m json.tool
echo "结束时间: $(date)"

# 验证
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** 20-60 秒内完成，返回 Token
- **验证:** status 显示 loggedIn=true
- **可能问题:**
  - 问题: 一键登录封装了全部流程，失败时难以定位
  - 修复: 查看后端完整日志，找到失败环节，按对应模块的修复方案处理
  - 重新测试: 修复后重新调用一键登录

---

## 阶段 10: 异常与边界测试 (EX-01 ~ EX-19)

### Task 15: 浏览器异常测试 (EX-01 ~ EX-05)

**Step 1: EX-01 — Chrome 未启动时自动启动**

```bash
taskkill /f /im chrome.exe 2>/dev/null
sleep 2
curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" | python -m json.tool
```

- **预期:** 自动启动 Chrome 后完成登录
- **验证:** 日志包含 "自动启动" 相关信息

**Step 2: EX-02 — Chrome 崩溃恢复**

```bash
# 先正常启动
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
sleep 2
# 模拟崩溃
taskkill /f /im chrome.exe
sleep 1
# 尝试操作
curl -s -X POST http://localhost:8080/api/browser/navigate/login | python -m json.tool
```

- **预期:** 抛出 BrowserException 或自动重连
- **可能问题:**
  - 问题: 后端卡死（WebDriver 会话无效但未超时）
  - 修复: 添加 WebDriver 操作超时机制
  - 重新测试: 重启后端，重新测试

**Step 3: EX-03 — 端口被占用**

```bash
# 用一个 dummy 进程占用 9222
python -c "import socket; s=socket.socket(); s.bind(('127.0.0.1',9222)); s.listen(1); input('Press Enter to release...')" &
DUMMY_PID=$!
sleep 1
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
kill $DUMMY_PID 2>/dev/null
```

- **预期:** 启动失败，日志报端口占用错误

**Step 4: EX-04/05 — 网络中断和页面超时**

> 这些场景较难模拟，可通过断开网络或使用不存在的 URL 来测试。

---

### Task 16: 验证码异常测试 (EX-06 ~ EX-11)

**Step 1: EX-06 — 滑块验证失败重试**

> 通过正常流程观察日志，确认失败时有重试。

- **验证:** 后端日志包含 "重试第 X 次" 字样
- **可能问题:**
  - 问题: 重试逻辑未触发
  - 修复: 检查 `solveSlider()` 的 try-catch 和重试循环

**Step 2: EX-09 — 邮件验证码未收到**

```bash
# 在未发送验证码的情况下直接获取
curl -s -X POST http://localhost:8080/api/browser/phone/getCodeEnhanced | python -m json.tool
```

- **预期:** 3 次重试后返回失败
- **验证:** 日志显示重试过程

---

### Task 17: 登录异常测试 (EX-12 ~ EX-16)

**Step 1: EX-12 — 错误密码**

```bash
curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=wrong_password" | python -m json.tool
```

- **预期:** 登录失败，返回错误信息
- **注意:** 不要连续多次错误登录，避免账号被锁定

**Step 2: EX-17 — 重复登录请求**

```bash
# 同时发起两次
curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" &
sleep 1
curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" | python -m json.tool
wait
```

- **预期:** 第二次请求等待或被拒绝
- **可能问题:**
  - 问题: 两次请求都执行，导致浏览器冲突
  - 修复: 添加登录锁（synchronized 或 ReentrantLock）
  - 重新测试: 修复后再次并发调用

---

## 阶段 11: 性能与稳定性测试 (PF-01 ~ ST-03)

### Task 18: 性能基准测试

**Step 1: PF-01 — 浏览器启动时间**

```bash
curl -s -X POST http://localhost:8080/api/browser/quit 2>/dev/null
sleep 2
time curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
```

- **基准:** < 10 秒
- **可能问题:**
  - 问题: 启动超过 10 秒
  - 修复: 检查 Chrome 启动参数，考虑减少扩展加载
  - 重新测试: 多次测量取平均值

**Step 2: PF-06 — 完整登录耗时**

```bash
time curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553" | python -m json.tool
```

- **基准:** < 60 秒
- **可能问题:**
  - 问题: 超过 60 秒
  - 修复: 分析各环节耗时，优化瓶颈（通常是验证码获取或滑块验证）

---

### Task 19: 稳定性测试

**Step 1: ST-01 — 连续登录成功率**

```bash
SUCCESS=0
TOTAL=5

for i in $(seq 1 $TOTAL); do
  echo "=== 第 $i 次登录 ==="
  curl -s -X POST http://localhost:8080/api/autoLogin/logout 2>/dev/null
  curl -s -X POST http://localhost:8080/api/browser/quit 2>/dev/null
  sleep 5

  RESULT=$(curl -s -X POST "http://localhost:8080/api/autoLogin/login?username=13278828091&password=132553")
  TOKEN=$(echo $RESULT | python -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('token',''))" 2>/dev/null)

  if [ -n "$TOKEN" ] && [ "$TOKEN" != "None" ]; then
    echo "第 $i 次: 成功"
    SUCCESS=$((SUCCESS+1))
  else
    echo "第 $i 次: 失败"
    echo "$RESULT" >> .tmp/test-results/login-failures.log
  fi

  sleep 10  # 间隔，避免频率限制
done

echo "成功率: $SUCCESS/$TOTAL"
```

- **基准:** >= 80% (4/5)
- **可能问题:**
  - 问题: 成功率低于 80%
  - 修复: 分析失败日志，找到最常见的失败原因（通常是验证码识别或滑块验证）
  - 重新测试: 修复后重新跑 5 次

---

## 阶段 12: 诊断工具验证 (D-01 ~ D-08)

### Task 20: 诊断 API 矩阵

**前置条件:** 浏览器已启动，已导航到登录页

```bash
# D-01: 页面类型
curl -s http://localhost:8080/api/browser/debug/pageType | python -m json.tool

# D-02: 截图
curl -s http://localhost:8080/api/browser/debug/screenshot

# D-03: DOM 检查
curl -s http://localhost:8080/api/browser/debug/domInspect | python -m json.tool

# D-04: 表单状态
curl -s http://localhost:8080/api/browser/debug/formState | python -m json.tool

# D-05: 滑块轮询
curl -s "http://localhost:8080/api/browser/debug/sliderPoll?seconds=5" | python -m json.tool

# D-06: 浏览器指纹
curl -s http://localhost:8080/api/browser/debug/fingerprint | python -m json.tool

# D-07: Frame 信息
curl -s http://localhost:8080/api/browser/debug/frameInfo | python -m json.tool

# D-08: 执行 JS
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return document.title"}' | python -m json.tool
```

- **逐个验证:** 每个 API 应返回 200 且包含有效数据
- **可能问题:**
  - 问题: 某个诊断 API 返回 500
  - 修复: 检查对应 Controller 方法，可能是浏览器状态未就绪
  - 重新测试: 确认浏览器运行后重新调用

---

## 会话持久化回归测试（登录后）

### Task 21: TC-BSM-06 完整验证

**前置条件:** 已完成一次成功登录

**Step 1: 记录当前会话数据**

```bash
# 记录 Cookie
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return document.cookie"}' > .tmp/test-results/cookie-before.txt

# 记录 localStorage
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return JSON.stringify(localStorage)"}' > .tmp/test-results/ls-before.txt

cat .tmp/test-results/cookie-before.txt
```

**Step 2: 关闭并重启浏览器**

```bash
curl -s -X POST http://localhost:8080/api/browser/quit | python -m json.tool
sleep 3
curl -s -X POST http://localhost:8080/api/browser/start | python -m json.tool
sleep 3
```

**Step 3: 导航并对比**

```bash
curl -s -X POST http://localhost:8080/api/browser/navigate/login | python -m json.tool
sleep 3
curl -s -X POST http://localhost:8080/api/browser/refresh | python -m json.tool
sleep 3

# 记录重启后的 Cookie
curl -s -X POST http://localhost:8080/api/browser/debug/execJs \
  -H "Content-Type: application/json" \
  -d '{"script": "return document.cookie"}' > .tmp/test-results/cookie-after.txt

# 对比
diff .tmp/test-results/cookie-before.txt .tmp/test-results/cookie-after.txt
```

- **预期:** Cookie 数据基本一致（部分时间相关字段可能不同）
- **验证:**

```bash
curl -s http://localhost:8080/api/autoLogin/status | python -m json.tool
```

- **预期:** 如果 Cookie 有效，可能直接 `loggedIn: true`（免登录）
- **可能问题:**
  - 问题: Cookie 完全丢失
  - 修复: 检查 `--user-data-dir` 路径和 Chrome 退出时的持久化机制
  - 重新测试: 重新登录并再次对比

---

## 测试结果汇总

### 执行完成后，填写此表

| 类别 | 总数 | 通过 | 失败 | 阻塞 | 通过率 |
|------|------|------|------|------|--------|
| 环境准备 (E-01~E-13) | 13 | | | | |
| 浏览器管理 (TC-BSM-01~06) | 6 | | | | |
| 页面导航 (TC-NAV-01~04) | 5 | | | | |
| 登录页流程 (TC-LF-01~07) | 7 | | | | |
| 手机验证流程 (TC-PV-01~08) | 8 | | | | |
| 滑块验证码 (TC-CS-01~05) | 5 | | | | |
| 数学验证码 (TC-MC-01~03) | 3 | | | | |
| 短信验证码 (TC-CF-01~04) | 4 | | | | |
| Token 管理 (TC-TK-01~03) | 3 | | | | |
| REST API (TC-API-01~05) | 5 | | | | |
| 端到端流程 (E2E-01~03) | 3 | | | | |
| 异常边界 (EX-01~19) | 19 | | | | |
| 性能稳定性 (PF+ST) | 9 | | | | |
| 诊断工具 (D-01~08) | 8 | | | | |
| **合计** | **98** | | | | |

---

## 通用问题排查速查表

| 症状 | 排查步骤 | 常见原因 | 修复方案 |
|------|---------|---------|---------|
| API 返回 500 | 查后端日志 stacktrace | NPE / 数据库异常 | 根据异常修复代码 |
| 浏览器操作无响应 | 检查 Chrome 进程存活 | Chrome 崩溃 | 重启浏览器 |
| 选择器找不到元素 | `domInspect` 查看 DOM | 页面结构变更 | 更新选择器 |
| 验证码识别失败 | 查看 `.tmp/` 调试图片 | OCR 精度不足 | 调整分割/匹配参数 |
| 滑块验证失败 | 查看滑块调试图片 | Sobel 距离偏差 | 调整比例系数 |
| IMAP 获取失败 | telnet 测试连接 | 网络/认证问题 | 检查邮箱配置 |
| Token 为空 | 检查 Cookie/Storage | 提取策略不匹配 | 更新匹配关键字 |
| 登录超时 | 逐步日志分析 | 某环节卡住 | 优化瓶颈环节 |
| 并发冲突 | 检查是否有多个登录 | 缺少并发控制 | 添加登录锁 |
| 会话丢失 | 检查 user-data-dir | Chrome 未正确持久化 | 确认启动参数 |
