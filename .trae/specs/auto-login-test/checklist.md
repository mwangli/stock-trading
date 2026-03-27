# 会话持久化验证清单 - 直连模式

## 问题背景

中信证券平台**每天最多获取8次验证码**，核心目标是实现会话持久化来避免重复手机验证。

***

## 直连模式配置检查

### application.yml

* [x] `chrome.directConnect: true`

* [x] `chrome.debugger.url: http://localhost:9222`

* [x] `chrome.userDataDir: /home/seluser/.config/chromium`

* [ ] `chrome.headless: false`（可选）

### docker-compose.yml

* [x] 移除 4444 端口（Grid）

* [x] 保留 9222:9222（CDP）

* [x] 配置 Xvfb 虚拟显示

* [x] Chrome 启动命令包含 `--remote-debugging-port=9222`

* [x] Chrome 启动命令包含 `--user-data-dir=/home/seluser/.config/chromium`

* [x] chrome-userdata 卷挂载

### BrowserSessionManager.java

* [x] 删除 `chromeRemoteUrl` 字段

* [x] 删除 `RemoteWebDriver` import

* [x] 删除 Grid 模式分支逻辑

* [x] 只保留直连模式（CDP）逻辑

* [x] `configureChromeOptions()` 只配置直连模式参数

***

## 直连模式功能验证

### 启动验证

* [ ] 调用 `/api/browser/start` 成功

* [ ] 日志显示 "使用 CDP 直连模式: <http://localhost:9222>"

* [ ] 日志显示 "启用浏览器持久化: user-data-dir=..."

### 会话复用验证

* [ ] 第二次调用 `/api/browser/start` 显示 "复用现有实例"

* [ ] 多次调用不会创建新的 Chrome 实例

### Chrome 实例验证

* [ ] Chrome 容器保持运行

* [ ] CDP 端口 9222 可访问

* [ ] VNC 端口 7900 可访问（可选）

***

## 会话持久化验证

### Cookie/Session 持久化

* [ ] 完成登录后，`user-data-dir` 中保存了 Cookie

* [ ] 重启后端服务

* [ ] 重新连接后 Cookie 仍然有效

* [ ] 无需重新进行手机验证

### 验证码次数控制

* [ ] 首次登录使用 1 次验证码

* [ ] 后续操作无需再次验证（验证次数保持为 1）

***

## 登录流程验证

### 手机验证页流程（PHONE\_VERIFY）

* [ ] 页面类型检测正确

* [ ] iframe 切换成功

* [ ] 手机号输入成功

* [ ] 协议勾选成功

* [ ] 滑块检测成功

* [ ] 滑块验证成功

* [ ] 短信验证码获取成功

* [ ] 页面跳转到登录页

### 登录页流程（LOGIN）

* [ ] 页面类型检测为 LOGIN

* [ ] 账号输入成功

* [ ] 密码输入成功

* [ ] 数学验证码计算

* [ ] 协议勾选成功

* [ ] 登录提交成功

* [ ] Token 获取成功

***

## 代码质量检查

* [ ] `mvn compile` 编译通过

* [ ] 无未使用的 import

* [ ] 关键方法有 Javadoc 注释

* [ ] 日志输出符合规范

***

## 测试结果记录

| # | 测试项        | 结果  | 备注 |
| - | ---------- | --- | -- |
| 1 | CDP 直连启动   | 待测试 | -  |
| 2 | 会话复用       | 待测试 | -  |
| 3 | Cookie 持久化 | 待测试 | -  |
| 4 | 后端重启后会话保持  | 待测试 | -  |

***

## 关键日志检查点

```
[浏览器启动] 使用 CDP 直连模式: http://localhost:9222
[浏览器启动] 启用浏览器持久化: user-data-dir=/home/seluser/.config/chromium
[浏览器启动] 浏览器已在运行，复用现有实例
```

