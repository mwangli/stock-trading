package com.stock.autoLogin.service;

import com.stock.autoLogin.dto.LoginResult;
import com.stock.autoLogin.exception.LoginException;
import com.stock.autoLogin.exception.TokenException;
import com.stock.tradingExecutor.execution.CaptchaService;
import com.stock.tradingExecutor.execution.LoginPageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动登录服务
 * 负责编排完整的登录流程，包括浏览器操作、表单填写、验证码处理、Token 提取
 * 支持双流程：
 * 1. 手机验证流程（首次登录/新设备）：手机号 → 滑块验证 → 短信验证码 → 跳转登录页
 * 2. 登录页流程（常规登录）：账号密码 → 数学验证码 → 滑块验证 → Token 获取
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutoLoginService {

    private final BrowserSessionManager browserSessionManager;
    private final LoginPageHandler loginPageHandler;
    private final CaptchaService captchaService;
    private final CookieManager cookieManager;
    private final CaptchaFetchService captchaFetchService;

    @Value("${spring.auto-login.captcha-timeout:300}")
    private int captchaTimeoutSeconds;

    @Value("${spring.auto-login.max-retries:3}")
    private int maxRetries;

    private LocalDateTime lastLoginTime;

    /**
     * 检测页面类型（严格模式）
     * @return "PHONE_VERIFY"（手机验证页）或 "LOGIN"（登录页）
     */
    private String detectPageType(WebDriver driver) {
        try {
            org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

            // 1. 检测手机验证页特征
            // 手机号输入框（placeholder 包含"手机号"）且可见
            List<org.openqa.selenium.WebElement> phoneInputs = driver.findElements(
                    org.openqa.selenium.By.xpath("//input[contains(@placeholder, '手机号')]"));
            boolean hasVisiblePhoneInput = phoneInputs.stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed);

            // "获取验证码"按钮存在且可见
            List<org.openqa.selenium.WebElement> sendCodeButtons = driver.findElements(
                    org.openqa.selenium.By.xpath("//*[contains(text(), '获取验证码')]"));
            boolean hasVisibleSendCode = sendCodeButtons.stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed);

            // 2. 检测登录页特征
            // 密码输入框存在且可见
            List<org.openqa.selenium.WebElement> passwordInputs = driver.findElements(
                    org.openqa.selenium.By.xpath("//input[@type='password']"));
            boolean hasVisiblePassword = passwordInputs.stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed);

            // 资金账号输入框（placeholder 包含"资金账号"或"证券账号"）且可见
            List<org.openqa.selenium.WebElement> accountInputs = driver.findElements(
                    org.openqa.selenium.By.xpath("//input[contains(@placeholder, '资金账号') or contains(@placeholder, '证券账号')]"));
            boolean hasVisibleAccount = accountInputs.stream().anyMatch(org.openqa.selenium.WebElement::isDisplayed);

            // 3. 综合判断
            // 手机验证页：手机号输入框可见 + 获取验证码按钮可见 + 密码输入框不可见
            if (hasVisiblePhoneInput && hasVisibleSendCode && !hasVisiblePassword) {
                log.info("检测到页面类型：PHONE_VERIFY（手机验证页）- hasPhone={}, hasSendCode={}, hasPassword={}",
                        hasVisiblePhoneInput, hasVisibleSendCode, hasVisiblePassword);
                return "PHONE_VERIFY";
            }

            // 登录页：密码输入框可见 或 资金账号输入框可见
            if (hasVisiblePassword || hasVisibleAccount) {
                log.info("检测到页面类型：LOGIN（登录页）- hasPassword={}, hasAccount={}",
                        hasVisiblePassword, hasVisibleAccount);
                return "LOGIN";
            }

            // 如果都检测不到，打印详细信息用于调试
            log.warn("无法确定页面类型，打印页面元素信息：");
            log.warn("  - 手机号输入框: {}个, 可见: {}", phoneInputs.size(), hasVisiblePhoneInput);
            log.warn("  - 获取验证码按钮: {}个, 可见: {}", sendCodeButtons.size(), hasVisibleSendCode);
            log.warn("  - 密码输入框: {}个, 可见: {}", passwordInputs.size(), hasVisiblePassword);
            log.warn("  - 资金账号输入框: {}个, 可见: {}", accountInputs.size(), hasVisibleAccount);

            // 备选判断：如果密码输入框存在（不论可见性），认为是登录页
            if (!passwordInputs.isEmpty()) {
                return "LOGIN";
            }

            return "UNKNOWN";
        } catch (Exception e) {
            log.error("页面类型检测失败: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * 执行手机验证流程（首次登录/新设备）
     * 流程：输入手机号 → 勾选协议 → 获取验证码 → 滑块验证 → 输入短信验证码 → 点击下一步
     *
     * @param phone 手机号
     * @return 是否成功完成手机验证
     */
    private boolean executePhoneVerifyFlow(String phone) throws Exception {
        log.info("========== 开始手机验证流程 ==========");

        // 1. 输入手机号
        loginPageHandler.inputAccount(phone);
        Thread.sleep(500);

        // 2. 勾选协议
        loginPageHandler.checkAgreements();
        Thread.sleep(500);

        // 3. 点击获取验证码按钮
        loginPageHandler.clickSendCodeButton();
        Thread.sleep(2000);

        // 4. 处理滑块验证码
        if (!handleSliderCaptcha()) {
            throw new LoginException("手机验证页滑块验证失败");
        }

        // 5. 点击确定按钮（滑块验证后的弹窗）
        loginPageHandler.clickConfirmButton();
        Thread.sleep(1000);

        // 6. 获取短信验证码（增强版：Redis 3次重试 + 邮箱获取）
        String smsCode = captchaFetchService.getPhoneCodeWithRetry(() -> {
            log.info("验证码可能已失效，重新发送...");
            loginPageHandler.clickSendCodeButton();
            try {
                Thread.sleep(2000);
                handleSliderCaptcha();
                loginPageHandler.clickConfirmButton();
            } catch (Exception e) {
                log.error("重新发送验证码流程异常: {}", e.getMessage());
                return false;
            }
            return true;
        });

        if (smsCode == null) {
            throw new LoginException("获取短信验证码失败");
        }

        // 7. 输入短信验证码
        loginPageHandler.inputSmsCode(smsCode);
        Thread.sleep(500);

        // 8. 点击下一步按钮
        loginPageHandler.clickNextStepButton();
        Thread.sleep(2000);

        log.info("========== 手机验证流程完成，等待跳转登录页 ==========");
        return true;
    }

    /**
     * 执行登录页流程（常规登录）
     * 流程：输入账号密码 → 数学验证码 → 勾选协议 → 点击登录 → 滑块验证 → Token 获取
     *
     * @param account 资金账号
     * @param password 交易密码
     * @return Token 字符串
     */
    private String executeLoginFlow(String account, String password) throws Exception {
        log.info("========== 开始登录页流程 ==========");

        // 1. 切换到登录表单所在的 iframe
        if (!browserSessionManager.ensureLoginFrame()) {
            throw new LoginException("无法切换到登录表单 iframe");
        }

        // 2. 输入账号
        loginPageHandler.inputAccount(account);
        Thread.sleep(300);

        // 3. 输入密码
        loginPageHandler.inputPassword(password);
        Thread.sleep(300);

        // 4. 处理数学验证码
        String captchaResult = handleMathCaptcha();
        if (captchaResult != null) {
            loginPageHandler.inputCaptcha(captchaResult);
            Thread.sleep(300);
        }

        // 5. 勾选协议
        loginPageHandler.checkAgreements();
        Thread.sleep(300);

        // 6. 点击登录按钮
        loginPageHandler.clickLoginButton();
        Thread.sleep(1000);

        // 7. 处理滑块验证码
        if (!handleSliderCaptcha()) {
            throw new LoginException("登录页滑块验证失败");
        }

        // 8. 等待登录成功
        Thread.sleep(2000);

        // 9. 提取 Token
        WebDriver driver = browserSessionManager.getDriver();
        String token = cookieManager.extractToken(driver);
        if (token == null) {
            throw new TokenException("Token 提取失败");
        }

        log.info("========== 登录页流程完成，Token 获取成功 ==========");
        return token;
    }

    /**
     * 执行完整登录流程（自动检测页面类型并选择对应流程）
     *
     * @param account  账号（资金账号）
     * @param password 密码（交易密码）
     * @return 登录结果，包含 Token 和状态
     */
    public LoginResult executeLogin(String account, String password) {
        int attempt = 0;

        while (attempt < maxRetries) {
            attempt++;
            log.info("========== 开始第 {} 次登录尝试 ==========", attempt);

            try {
                // 1. 启动浏览器
                WebDriver driver = browserSessionManager.startBrowser();

                // 2. 访问登录页面
                driver.get("https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html");
                Thread.sleep(3000);

                // 3. 检测页面类型
                String pageType = detectPageType(driver);
                String token;

                if ("PHONE_VERIFY".equals(pageType)) {
                    // 流程一：手机验证页 → 登录页
                    String phone = account; // 手机号即账号
                    if (!browserSessionManager.ensureLoginFrame()) {
                        throw new LoginException("无法切换到登录表单 iframe");
                    }

                    // 执行手机验证流程
                    executePhoneVerifyFlow(phone);

                    // 等待跳转到登录页
                    Thread.sleep(3000);

                    // 检测是否已跳转到登录页
                    String newPageType = detectPageType(driver);
                    if ("LOGIN".equals(newPageType)) {
                        // 继续执行登录页流程
                        token = executeLoginFlow(account, password);
                    } else {
                        throw new LoginException("手机验证完成后未跳转到登录页");
                    }
                } else {
                    // 流程二：直接登录页流程
                    token = executeLoginFlow(account, password);
                }

                // 10. 同步 Token
                cookieManager.syncTokenToZXRequestUtils(token);

                lastLoginTime = LocalDateTime.now();
                log.info("========== 登录成功 ==========");
                return LoginResult.success(token);

            } catch (Exception e) {
                log.error("第 {} 次登录尝试失败: {}", attempt, e.getMessage());

                if (attempt >= maxRetries) {
                    return LoginResult.failure("登录失败：" + e.getMessage());
                }

                browserSessionManager.quitBrowser();
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        return LoginResult.failure("登录失败：已达最大重试次数");
    }

    /**
     * 处理数学验证码
     * @return 验证码结果，如果无法自动计算返回 null
     */
    private String handleMathCaptcha() {
        try {
            String mathResult = loginPageHandler.calculateMathCaptcha();
            if (mathResult != null) {
                log.info("数学验证码自动计算成功: {}", mathResult);
                return mathResult;
            }
            log.info("无法自动计算数学验证码，将跳过（前端应已处理）");
            return null;
        } catch (Exception e) {
            log.warn("数学验证码处理异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 处理滑块验证码
     */
    private boolean handleSliderCaptcha() throws Exception {
        WebDriver driver = browserSessionManager.getDriver();
        Thread.sleep(1000);

        // 1. 切换到滑块所在 frame
        if (!captchaService.switchToSliderFrame(driver)) {
            log.info("未检测到滑块验证码，继续登录流程");
            return true;
        }

        log.info("检测到滑块验证码，开始处理");

        // 2. 提取图片 URL
        CaptchaService.ImageUrls urls = captchaService.extractYidunImageUrls(driver);
        if (urls.getBgUrl() == null || urls.getSliderUrl() == null) {
            log.error("无法提取滑块图片 URL");
            return false;
        }

        // 3. 下载图片 + 计算距离
        byte[] bgImage = captchaService.downloadImage(urls.getBgUrl());
        byte[] sliderImage = captchaService.downloadImage(urls.getSliderUrl());
        int distance = captchaService.calculateSliderDistance(bgImage, sliderImage);
        if (distance <= 0) {
            log.error("滑块距离计算失败");
            return false;
        }

        log.info("滑块距离: {}px", distance);

        // 4. 执行拖动
        boolean success = captchaService.executeSliderDrag(driver, distance);
        if (!success) {
            log.error("滑块拖动失败");
            return false;
        }

        return captchaService.waitForVerificationResult(driver, 3);
    }

    /**
     * 获取上次登录时间
     */
    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    /**
     * 检查当前登录状态
     */
    public boolean isLoggedIn() {
        try {
            Class<?> zxRequestUtilsClass = Class.forName("com.stock.tradingExecutor.execution.ZXRequestUtils");
            java.lang.reflect.Method isTokenValidMethod = zxRequestUtilsClass.getMethod("isTokenValid");
            return (Boolean) isTokenValidMethod.invoke(null);
        } catch (Exception e) {
            log.warn("检查登录状态失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 强制退出登录
     */
    public void forceLogout() {
        try {
            Class<?> zxRequestUtilsClass = Class.forName("com.stock.tradingExecutor.execution.ZXRequestUtils");
            java.lang.reflect.Method clearTokenMethod = zxRequestUtilsClass.getMethod("clearToken");
            clearTokenMethod.invoke(null);
            browserSessionManager.quitBrowser();
            log.info("强制退出登录成功");
        } catch (Exception e) {
            log.error("强制退出登录失败: {}", e.getMessage());
        }
    }
}
