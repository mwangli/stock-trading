# 自动登录流程测试验证 Spec

## Why

需要验证中信证券自动化登录系统的完整流程，包括手机验证页（首次登录）和登录页（常规登录）的端到端测试。

## What Changes

- 基于设计文档 `05-自动登录/设计.md` 执行分步测试验证
- 验证 `BrowserApiController` 新增的 `/api/browser/debug/page-type` 接口
- 验证 `AutoLoginService` 双流程编排（手机验证流程 + 登录页流程）
- 端到端测试：手机验证 → 滑块验证 → 短信验证 → 跳转登录页 → 账号密码登录 → Token 获取

## Impact

- 影响代码：`BrowserApiController.java`、`AutoLoginService.java`
- 依赖环境：Chrome Docker 容器（`localhost:4444`）
- 验证方式：API 接口调用 + 截图确认

## ADDED Requirements

### Requirement: 页面类型检测接口验证

系统应能正确检测当前页面类型（手机验证页或登录页），基于 DOM 内容而非 URL。

#### Scenario: 检测手机验证页
- **WHEN** 调用 `GET /api/browser/debug/page-type`
- **THEN** 返回 `pageType=PHONE_VERIFY`，包含 `hasPhoneInput=true` 和 `hasSendCodeButton=true`

#### Scenario: 检测登录页
- **WHEN** 调用 `GET /api/browser/debug/page-type`
- **THEN** 返回 `pageType=LOGIN`，包含 `hasPasswordInput=true`

### Requirement: 手机验证页流程验证

系统应能完成手机验证页完整流程：

1. 启动浏览器 → 访问登录页
2. 检测页面类型为 `PHONE_VERIFY`
3. 输入手机号 → 勾选协议
4. 点击获取验证码 → 滑块验证
5. 关闭弹窗 → 输入短信验证码
6. 点击下一步 → 跳转登录页

#### Scenario: 滑块检测
- **WHEN** 点击获取验证码后调用 `GET /api/browser/phone/slider-status`
- **THEN** 返回 `hasSlider=true`, `sliderType=YIDUN`

#### Scenario: 滑块验证
- **WHEN** 调用 `POST /api/browser/phone/solve-slider`
- **THEN** 滑块验证成功，距离在 20-250px 范围内

### Requirement: 登录页流程验证

系统应能完成登录页完整流程：

1. 检测页面类型为 `LOGIN`
2. 输入账号 → 输入密码
3. 数学验证码自动计算
4. 勾选协议 → 点击登录
5. 滑块验证（如果出现）
6. 检查 Token

#### Scenario: 数学验证码自动计算
- **WHEN** 调用 `GET /api/browser/login/capture-captcha`
- **THEN** 返回数学算式的计算结果

#### Scenario: Token 获取
- **WHEN** 登录流程完成后调用 `GET /api/browser/login/check`
- **THEN** 返回 `hasToken=true`, `isSuccess=true`

### Requirement: AutoLoginService 双流程编排验证

系统应能根据页面类型自动选择对应流程：

- 检测到 `PHONE_VERIFY` → 执行手机验证流程 → 等待跳转 → 执行登录页流程
- 检测到 `LOGIN` → 直接执行登录页流程

#### Scenario: 一键登录
- **WHEN** 调用 `POST /api/auto-login/login`
- **THEN** 自动完成整个登录流程并返回 Token

## MODIFIED Requirements

### Requirement: 已有接口验证

验证以下已有接口正常工作：

| 接口 | 功能 |
|------|------|
| `POST /api/browser/start` | 启动浏览器 |
| `POST /api/browser/navigate/login` | 访问登录页 |
| `POST /api/browser/frame/switch` | 切入 iframe |
| `POST /api/browser/login/input-account` | 输入账号 |
| `POST /api/browser/login/input-password` | 输入密码 |
| `POST /api/browser/login/check-agreements` | 勾选协议 |
| `POST /api/browser/login/submit` | 点击登录 |
| `GET /api/browser/phone/slider-status` | 滑块检测 |
| `POST /api/browser/phone/solve-slider` | 滑块验证 |

### Requirement: 验证码失效处理

系统应能处理短信验证码失效的情况：

1. 检测验证码是否失效（如提示"验证码已过期"或"验证码错误"）
2. 清除 Redis 中的旧验证码
3. 重新触发短信验证码发送
4. 继续等待邮箱接收新验证码

#### Scenario: 验证码失效处理
- **WHEN** 验证码验证失败，提示失效
- **THEN** 清除 Redis (`PHONE_CODE`) → 调用 `send-code` 重新发送 → 等待新验证码

### Requirement: 增强版验证码获取逻辑

系统应按以下优先级获取短信验证码：

1. **优先 Redis 获取**（3 次重试）
   - 第 1 次：等待 10 秒后获取
   - 第 2 次：等待 20 秒后获取
   - 第 3 次：等待 30 秒后获取

2. **退阶邮件获取**（Redis 3 次都失败）
   - 连接邮箱（IMAP）
   - 扫描最近 10 封邮件
   - 查找标题包含"验证码"/"登录"/"中信"的邮件
   - 从邮件正文中通过正则 `\d{6}` 提取 6 位数字验证码

3. **失效重试循环**
   - 如果验证码失效，清除 Redis → 重新发送 → 继续等待

#### Scenario: Redis 3 次重试
- **WHEN** 调用获取验证码接口
- **THEN** 先尝试 Redis，等待 10s → 20s → 30s

#### Scenario: 退阶邮件获取
- **WHEN** Redis 3 次都获取失败
- **THEN** 连接邮箱，扫描邮件，提取验证码

### Requirement: 滑块验证增强版（失败后重新获取）

系统应能在滑块验证失败后自动重新获取图片和距离：

1. 执行滑块验证（获取图片 → 计算距离 → 拖动 → 等待结果）
2. 如果验证失败，页面会刷新成新的滑块
3. 自动重新执行：获取新图片 → 计算新距离 → 拖动 → 等待结果
4. 最多重试 3 次

#### Scenario: 滑块验证失败重试
- **WHEN** 滑块验证失败（页面已刷新）
- **THEN** 自动重新获取图片和距离，继续验证

### Requirement: 页面类型严格检测

系统应使用严格的多条件判断检测页面类型：

**手机验证页判断条件**（需同时满足）：
- `手机号输入框可见`（placeholder 包含"手机号"）
- `获取验证码按钮可见`
- `密码输入框不可见`

**登录页判断条件**（满足其一即可）：
- `密码输入框可见`
- `资金账号输入框可见`（placeholder 包含"资金账号"或"证券账号"）

#### Scenario: 严格模式页面检测
- **WHEN** 调用 `GET /api/browser/debug/page-type`
- **THEN** 基于可见性多条件判断返回正确的页面类型

### Requirement: 浏览器持久化配置

系统应支持浏览器持久化配置：

1. 在 `application.yml` 中配置 `chrome.userDataDir`
2. Docker Chrome 已配置 `chrome-userdata` 卷挂载
3. Selenium Grid 模式下需注意：远程 Chrome 不直接支持 `--user-data-dir`

#### Scenario: 浏览器配置持久化
- **WHEN** 配置了 `chrome.userDataDir`
- **THEN** 浏览器启动时使用配置的 user-data-dir

### Requirement: 浏览器会话长期运行模式（方案C）

为解决 Selenium Grid 模式下浏览器会话无法持久化的问题，系统采用**长期运行容器 + 单会话复用**模式：

#### 架构设计

| 组件 | 配置 | 说明 |
|------|------|------|
| 容器内存限制 | `mem_limit: 1500m` | 适配4G服务器，Chrome headless 模式 |
| Session 超时 | `SE_SESSION_REQUEST_TIMEOUT=3600` | 1小时等待短信验证码 |
| 实际 Session 超时 | `SE_NODE_SESSION_TIMEOUT=7200` | 2小时总超时 |
| 最大会话数 | `SE_NODE_MAX_SESSIONS=1` | 单会话确保复用 |
| 持久化卷 | `chrome-userdata:/tmp/chrome-user-data` | 容器重启后会话恢复 |
| DevTools 端口 | `9222:9222` | 支持 CDP 直连模式 |

#### 工作原理

1. **容器设计为无状态但会话可恢复**
   - `chrome-userdata` 卷持久化 Chrome 用户配置
   - 容器重启后，原有 Cookie、LocalStorage 仍然有效
   - 无需重新进行手机验证（首次登录后）

2. **单会话限制**
   - `SE_NODE_MAX_SESSIONS=1` 确保同时只有一个 WebDriver 连接
   - 避免多会话竞争导致会话丢失

3. **长超时配置**
   - 短信验证码输入可能需要较长时间
   - `SE_SESSION_REQUEST_TIMEOUT=3600` 确保不会因等待验证码而超时

#### 4G内存可行性评估

| 组件 | 内存占用 | 说明 |
|------|---------|------|
| Chrome Headless | 200-300MB | 基础开销 |
| 渲染进程 | 50-150MB | 取决于页面复杂度 |
| 系统其他 | 500-800MB | SSH、systemd等 |
| **总计** | **~1.5GB** | 分配 `mem_limit: 1500m` |

**结论：4G内存完全可行，推荐使用 headless 模式。**

#### Scenario: 长期运行容器验证
- **WHEN** 容器启动后完成首次登录
- **THEN** 容器重启后仍保持登录状态，无需重新手机验证

### Requirement: 直连模式 CDP 连接（备选方案）

如需完全控制浏览器会话，可切换到 CDP 直连模式：

#### 配置变更

```yaml
# docker-compose.yml
chrome:
  ports:
    - "9222:9222"  # Chrome DevTools Protocol
```

#### Java 连接代码

```java
// 通过 CDP 直连已有浏览器
ChromeOptions options = new ChromeOptions();
options.setExperimentalOption("debuggerAddress", "chrome:9222");
WebDriver driver = new RemoteWebDriver(
    new URL("http://localhost:9222/wd/hub"),
    options
);
```

#### 优势
- 完全支持 `--user-data-dir` 持久化
- 复用浏览器配置、Cookie、LocalStorage

#### 劣势
- 失去 Grid 的负载均衡能力
- 需要手动管理容器生命周期

#### Scenario: CDP 直连模式
- **WHEN** 使用 `debuggerAddress` 连接 Chrome
- **THEN** 复用已有浏览器实例和会话

## REMOVED Requirements

无

---

## 测试环境要求

| 组件 | 地址 | 说明 |
|------|------|------|
| Chrome | `localhost:4444` | Docker Selenium 容器 |
| 后端 | `localhost:8080` | Spring Boot 应用 |
| VNC | `localhost:7900` | 可见化调试（密码: secret） |

## 测试数据

- 手机号：`13278828091`
- 交易密码：`132553`
- 资金账号：`13278828091`（同手机号）
