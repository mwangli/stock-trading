# 会话持久化验证方案

## 验证目标

验证 Docker Chrome 会话持久化功能是否真正有效：
- 登录网站后重启浏览器/容器，用户登录信息是否保留
- Cookie/Session 是否正确保存到 volume

## 前置条件

1. Docker Desktop 已启动
2. Chrome 容器正在运行：`docker ps | grep stock-chrome`
3. CDP 端口可访问：`curl http://localhost:9222/json`

---

## 验证步骤

### 步骤 1：验证 CDP 直连模式

```bash
# 检查 Chrome CDP 是否可访问
curl http://localhost:9222/json

# 预期：返回 Chrome DevTools 信息
```

### 步骤 2：启动浏览器会话

```bash
# 调用后端 API 启动浏览器
POST http://localhost:8080/api/browser/start

# 检查日志
# 预期：显示 "使用 CDP 直连模式: http://localhost:9222"
```

### 步骤 3：执行网站登录

以中信证券为例：

```bash
# 1. 导航到登录页
POST http://localhost:8080/api/browser/navigate/login

# 2. 切换到 iframe
POST http://localhost:8080/api/browser/frame/switch

# 3. 输入账号密码
POST http://localhost:8080/api/browser/login/input-account?account=13278828091
POST http://localhost:8080/api/browser/login/input-password?password=132553

# 4. 获取并计算数学验证码
GET http://localhost:8080/api/browser/login/capture-captcha

# 5. 输入验证码
POST http://localhost:8080/api/browser/login/input-captcha?captcha=计算结果

# 6. 勾选协议
POST http://localhost:8080/api/browser/login/check-agreements

# 7. 提交登录
POST http://localhost:8080/api/browser/login/submit

# 8. 处理滑块验证
GET http://localhost:8080/api/browser/phone/slider-status
POST http://localhost:8080/api/browser/phone/solve-slider

# 9. 检查登录结果
GET http://localhost:8080/api/browser/login/check
# 预期：success=true, hasToken=true
```

### 步骤 4：验证会话持久化 - 重启容器

```bash
# 1. 记录当前 Cookie 状态
docker exec stock-chrome ls -la /home/seluser/.config/chromium/Default/Cookies
# 预期：文件存在且有内容

# 2. 重启 Chrome 容器
docker-compose restart chrome

# 3. 等待容器恢复（约10秒）
sleep 10

# 4. 重新连接会话
POST http://localhost:8080/api/browser/start
# 预期：日志显示 "浏览器已在运行，复用现有实例"

# 5. 导航到登录页
POST http://localhost:8080/api/browser/navigate/login

# 6. 检查页面类型（应该直接进入 login.html，跳过手机验证）
GET http://localhost:8080/api/browser/debug/page-type
# 预期：pageType=LOGIN（而非 PHONE_VERIFY）
```

### 步骤 5：验证会话持久化 - 重启后端

```bash
# 1. 完成登录后，重启后端服务
# Linux: systemctl restart stock-backend
# Docker: docker-compose restart stock-backend

# 2. 重新连接浏览器
POST http://localhost:8080/api/browser/start

# 3. 导航到登录页
POST http://localhost:8080/api/browser/navigate/login

# 4. 检查是否保持登录状态
GET http://localhost:8080/api/browser/login/check
# 预期：hasToken=true（无需重新登录）
```

---

## 简化验证方案（无需完整登录）

如果只想验证会话持久化机制，不需要实际登录网站：

### 方案 A：使用测试网站

```bash
# 1. 启动浏览器
POST http://localhost:8080/api/browser/start

# 2. 访问测试网站（httpbin.org）
POST http://localhost:8080/api/browser/navigate/login
# URL: http://httpbin.org/cookies/set/testcookie testvalue

# 3. 截图保存当前状态
GET http://localhost:8080/api/browser/debug/screenshot

# 4. 重启容器
docker-compose restart chrome

# 5. 重新连接并访问同一 URL
POST http://localhost:8080/api/browser/start
POST http://localhost:8080/api/browser/navigate/login

# 6. 检查 Cookie 是否保留
# 执行 JS: document.cookie
```

### 方案 B：检查 Volume 挂载

```bash
# 1. 查看 chrome-userdata 卷的实际路径
docker volume inspect ai-stock-trading_chrome-userdata

# 2. 检查容器内目录
docker exec stock-chrome ls -la /home/seluser/.config/chromium/

# 3. 写入测试文件
docker exec stock-chrome touch /home/seluser/.config/chromium/test_file.txt

# 4. 重启容器
docker-compose restart chrome

# 5. 检查文件是否保留
docker exec stock-chrome ls -la /home/seluser/.config/chromium/test_file.txt
# 预期：文件存在
```

---

## 验证清单

| # | 验证项 | 预期结果 | 实际结果 |
|---|--------|---------|---------|
| 1 | CDP 端口可访问 | 返回 Chrome 信息 | 待测试 |
| 2 | 直连模式启动成功 | 日志显示 CDP 地址 | 待测试 |
| 3 | 登录后 Cookie 保存 | Cookies 文件有内容 | 待测试 |
| 4 | 容器重启后会话保持 | 复用现有实例 | 待测试 |
| 5 | 后端重启后会话保持 | 无需重新登录 | 待测试 |

---

## 关键日志检查点

```
[浏览器启动] 使用 CDP 直连模式: http://localhost:9222
[浏览器启动] 启用浏览器持久化: user-data-dir=/home/seluser/.config/chromium
[浏览器启动] 浏览器已在运行，复用现有实例
```

---

## 常见问题排查

### 问题 1：CDP 端口无法访问

```bash
# 检查容器是否运行
docker ps | grep stock-chrome

# 检查端口映射
docker port stock-chrome

# 检查 Chrome 进程
docker exec stock-chrome ps aux | grep chrome
```

### 问题 2：会话不持久化

可能原因：
- `user-data-dir` 未正确挂载
- Volume 权限问题
- Chrome 容器使用了 `rm` 参数删除容器

解决方案：
```bash
# 检查卷挂载
docker inspect stock-chrome | grep -A5 Mounts

# 检查目录权限
docker exec stock-chrome ls -la /home/seluser/.config/chromium
```

### 问题 3：每次都是新会话

可能原因：
- `directConnect: false`（使用了 Grid 模式）
- Chrome 启动参数中 `--user-data-dir` 被覆盖

解决方案：
- 确认 `application.yml` 中 `directConnect: true`
- 检查 `BrowserSessionManager.java` 中 `debuggerAddress` 是否设置
