# 自动登录测试验证 Checklist

## 环境检查

- [x] Chrome Docker 容器运行在 `localhost:4444`
- [x] 后端服务运行在 `localhost:8080`
- [ ] VNC 可视化可访问 `localhost:7900`（密码: secret）

## 页面类型检测接口

- [x] `GET /api/browser/debug/page-type` 接口已实现
- [x] 接口返回正确的 `pageType` 值（PHONE_VERIFY 或 LOGIN）
- [x] 接口返回 `hasPasswordInput`、`hasPhoneInput`、`hasSendCodeButton` 等详情
- [x] 增加可见性检查，只有 password 输入框可见时才判定为 LOGIN

## 增强版验证码获取服务

- [x] `CaptchaFetchService` 服务已创建
- [x] Redis 3 次重试（10s, 20s, 30s）
- [x] 退阶邮件获取（IMAP）
- [x] 失效自动重发功能
- [x] `GET /api/browser/phone/code-from-redis` 查询 Redis 验证码（不删除）
- [x] `POST /api/browser/phone/clear-redis` 清除 Redis 验证码
- [x] `POST /api/browser/phone/get-code-enhanced` 增强版获取（含失效重试）

## 手机验证页流程（如适用）

- [x] `POST /api/browser/phone/send-code` 点击获取验证码成功
- [x] `GET /api/browser/phone/slider-status` 正确检测到滑块 (hasSlider=true, YIDUN)
- [x] `POST /api/browser/phone/solve-slider` 滑块距离计算成功 (77px, 83px)
- [x] 拖动执行成功 (dragSuccess=true)
- [x] `POST /api/browser/phone/confirm-slider` 弹窗关闭成功
- [ ] 页面正确跳转到登录页（待完整测试）

## 登录页流程

- [ ] 待验证（需要手机验证页完成后跳转）

## 滑块验证

- [x] `GET /api/browser/phone/slider-status` 滑块检测正确
- [x] `POST /api/browser/phone/solve-slider` 滑块距离计算在合理范围（50-106px）
- [x] 拖动执行成功 (dragSuccess=true)
- [x] solve-slider API 已增强：验证失败后自动重新获取图片和距离
- [ ] 滑块验证成功率待提高（当前约 20-30%）

## 浏览器持久化

- [x] application.yml 添加 `chrome.userDataDir` 配置
- [x] BrowserSessionManager 正确使用配置
- [ ] 注意：Selenium Grid 模式下需 Token 持久化方案

## Token 获取

- [ ] 待验证

## AutoLoginService 双流程编排

- [x] `executePhoneVerifyFlow()` 方法存在且逻辑正确
- [x] `executeLoginFlow()` 方法存在且逻辑正确
- [x] `detectPageType()` 方法正确识别页面类型
- [ ] `POST /api/auto-login/login` 一键登录成功（待验证）

## 代码质量

- [x] `mvn compile` 编译通过
- [x] 无未使用的 import
- [x] 关键方法有 Javadoc 注释
- [x] 日志输出符合规范

---

## 测试结果记录（2026-03-27）

### API 测试结果

| # | 接口 | 结果 | 备注 |
|---|------|------|------|
| 1 | POST /api/browser/start | ✅ 通过 | 浏览器启动成功 |
| 2 | POST /api/browser/navigate/login | ✅ 通过 | 导航到登录页 |
| 3 | GET /api/browser/debug/page-type | ✅ 通过 | 返回 PHONE_VERIFY |
| 4 | POST /api/browser/frame/switch | ✅ 通过 | 切入 iframe 成功 |
| 5 | POST /api/browser/login/input-account | ✅ 通过 | 手机号 13278828091 |
| 6 | POST /api/browser/login/check-agreements | ✅ 通过 | 协议勾选成功 |
| 7 | POST /api/browser/phone/send-code | ✅ 通过 | 滑块弹窗弹出 |
| 8 | GET /api/browser/phone/slider-status | ✅ 通过 | hasSlider=true, YIDUN |
| 9 | POST /api/browser/phone/solve-slider | ⚠️ 部分成功 | 距离77px/73px，拖动成功，验证失败 |
| 10 | POST /api/browser/phone/confirm-slider | ✅ 通过 | 弹窗关闭 |
| 11 | GET /api/browser/debug/screenshot | ✅ 通过 | 截图已保存 |
| 12 | GET /api/browser/debug/dom-inspect | ✅ 通过 | 确认54个可见yidun元素 |

### DOM 元素确认（通过 dom-inspect）

可见元素：
- `div.yidun_popup--light` - 滑块弹窗 (937x947)
- `img.yidun_bg-img` - 背景图 (220x110)
- `img.yidun_jigsaw` - 拼图块 (42x110)
- `div.yidun_slider` - 滑块手柄 (40x38)
- `div.yidun_panel` - 面板 (220x125)

### 待优化

滑块验证失败原因分析：
1. 距离计算使用 Sobel 边缘检测，可能不够精确
2. 拖动轨迹 S 曲线可能与服务器端验证不匹配
3. 建议尝试其他距离计算算法（如模板匹配、SIFT/ORB）
