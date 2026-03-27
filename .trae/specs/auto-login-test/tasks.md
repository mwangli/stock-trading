# 自动登录测试验证任务清单

## 前置准备

- [ ] 确认 Chrome Docker 容器运行中（`localhost:4444`）
- [ ] 确认后端服务运行中（`localhost:8080`）
- [ ] 确认 VNC 可视化可用（`localhost:7900`）

## Task 1: 页面类型检测接口验证

- [x] 1.1 启动浏览器：`POST /api/browser/start`
- [x] 1.2 访问登录页：`POST /api/browser/navigate/login`
- [x] 1.3 检测页面类型：`GET /api/browser/debug/page-type`
  - 预期返回 `pageType=PHONE_VERIFY` 或 `pageType=LOGIN`
- [x] 1.4 截图确认页面状态：`GET /api/browser/debug/screenshot`

## Task 2: 手机验证页流程测试（如页面类型为 PHONE_VERIFY）

- [x] 2.1 切入 iframe：`POST /api/browser/frame/switch`
- [x] 2.2 输入手机号：`POST /api/browser/login/input-account?account=13278828091`
- [x] 2.3 勾选协议：`POST /api/browser/login/check-agreements`
- [x] 2.4 确认表单状态：`GET /api/browser/debug/form-state`
- [x] 2.5 点击获取验证码：`POST /api/browser/phone/send-code`
- [x] 2.6 检测滑块状态：`GET /api/browser/phone/slider-status`
  - 预期 `hasSlider=true`, `sliderType=YIDUN`
- [ ] 2.7 执行滑块验证：`POST /api/browser/phone/solve-slider`
  - ⚠️ 滑块验证成功率不稳定，需优化轨迹算法
- [ ] 2.8 点击确定：`POST /api/browser/phone/confirm-slider`
- [ ] 2.9 获取短信验证码：增强版逻辑
  - 优先从 Redis 获取（3 次重试：10s, 20s, 30s）
  - Redis 失败则从邮箱获取
  - 失效则清除 Redis → 重新发送 → 继续等待
- [ ] 2.10 输入短信验证码：`POST /api/browser/phone/input-code`
- [ ] 2.11 点击下一步：`POST /api/browser/phone/submit`
- [ ] 2.12 等待跳转后检测页面类型：`GET /api/browser/debug/page-type`
  - 预期 `pageType=LOGIN`

## Task 6: 增强版验证码获取服务测试

- [x] 6.1 CaptchaFetchService 服务已创建
  - Redis 3 次重试（10s, 20s, 30s）
  - 退阶邮件获取（IMAP）
- [x] 6.2 新增 API 接口
  - `GET /api/browser/phone/code-from-redis` - 查询 Redis 验证码
  - `POST /api/browser/phone/clear-redis` - 清除 Redis 验证码
  - `POST /api/browser/phone/get-code-enhanced` - 增强版获取（含失效重试）
  - `POST /api/browser/phone/input-code` - 输入验证码（自动从 Redis 获取）
- [ ] 6.3 测试增强获取逻辑：`POST /api/browser/phone/get-code-enhanced`
  - 需实际收到短信验证码后测试

## Task 3: 登录页流程测试

- [x] 3.0 前置：手机验证页由用户手动完成
- [x] 3.1 确认页面类型为 LOGIN：`GET /api/browser/debug/page-type`
- [x] 3.2 切入 iframe：`POST /api/browser/frame/switch`
- [x] 3.3 输入账号：`POST /api/browser/login/input-account?account=13278828091`
- [x] 3.4 输入密码：`POST /api/browser/login/input-password?password=132553`
- [ ] 3.5 获取数学验证码：`GET /api/browser/login/capture-captcha`
  - ⚠️ 验证码图片定位问题，需调整 XPath
- [ ] 3.6 输入验证码：`POST /api/browser/login/input-captcha?captcha=计算结果`
- [ ] 3.7 勾选协议：`POST /api/browser/login/check-agreements`
- [ ] 3.8 点击登录：`POST /api/browser/login/submit`
- [ ] 3.9 等待滑块弹出后检测：`GET /api/browser/phone/slider-status`
- [ ] 3.10 执行滑块验证：`POST /api/browser/phone/solve-slider`（如需要）
- [ ] 3.11 检查登录结果：`GET /api/browser/login/check`
  - 预期 `hasToken=true`, `isSuccess=true`

## Task 4: AutoLoginService 一键登录测试

- [ ] 4.1 关闭现有浏览器：`POST /api/browser/quit`
- [ ] 4.2 调用一键登录：`POST /api/auto-login/login?username=13278828091&password=132553`
- [ ] 4.3 检查登录状态：`GET /api/auto-login/status`
  - 预期 `isLoggedIn=true`

## Task 5: 代码优化记录

### 滑块验证增强版（已完成）
- [x] 修复 solve-slider API：验证失败后自动重新获取图片和距离
- [x] 修复前的 bug：验证失败后页面刷新，但代码用旧距离值重试

### 页面类型严格检测（已完成）
- [x] 基于 DOM 可见性多条件判断
- [x] 手机验证页：手机号输入框可见 + 获取验证码按钮可见 + 密码输入框不可见
- [x] 登录页：密码输入框可见 或 资金账号输入框可见

### 浏览器持久化配置（已完成）
- [x] 在 application.yml 添加 `chrome.userDataDir` 配置
- [x] 注意：Selenium Grid 模式下 `--user-data-dir` 不直接生效

### 浏览器会话长期运行模式（方案C）- 2026-03-27

- [x] 评估 4G 内存运行 Docker Chrome 的可行性
  - Chrome Headless 模式：~1.5GB 内存足够
  - 推荐配置：mem_limit=1500m, headless=true
- [x] 修改 docker-compose.yml
  - 添加 mem_limit: 1500m 内存限制
  - 添加 SE_NODE_MAX_SESSIONS=1 单会话限制
  - 添加 SE_SESSION_REQUEST_TIMEOUT=3600 超长超时
  - 添加 CHROME_HEADLESS=true 启用无头模式
  - 调整持久化卷路径为 /tmp/chrome-user-data
  - 添加 9222 端口支持 CDP 直连模式
- [ ] 修改 BrowserSessionManager.java 支持直连模式（待代码实现）
- [ ] 更新 docs/05-自动登录/设计.md 文档（已完成）
- [ ] 验证容器重启后会话恢复功能

## Task Dependencies

- Task 2 依赖 Task 1 的页面类型检测结果
- Task 3 依赖 Task 2 的跳转结果（或直接页面类型为 LOGIN）
- Task 4 需要所有前置步骤完成

## 关键日志检查点

| # | 日志关键字 | 预期 |
|---|-----------|------|
| 1 | `浏览器指纹注入完成` | 每次启动必出现 |
| 2 | `登录表单在顶层` 或 `在第X个 iframe 中找到登录表单` | iframe 切换成功 |
| 3 | `获取验证码按钮已点击（jQuery trigger）` | 按钮点击成功 |
| 4 | `在 XXX 中检测到网易云盾滑块` | 滑块检测成功 |
| 5 | `滑块距离: Xpx` | X 在 20-250px |
| 6 | `滑块拖动执行完成` | 拖动成功 |
| 7 | `网易云盾滑块验证通过` 或 `滑块已消失` | 验证通过 |
| 8 | `Token 提取成功` | Token 获取成功 |
