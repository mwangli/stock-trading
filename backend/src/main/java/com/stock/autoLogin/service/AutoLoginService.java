package com.stock.autoLogin.service;

import com.stock.autoLogin.dto.LoginResult;
import com.stock.autoLogin.enums.SliderType;
import com.stock.autoLogin.exception.CaptchaException;
import com.stock.autoLogin.exception.LoginException;
import com.stock.autoLogin.exception.TokenException;
import com.stock.tradingExecutor.execution.CaptchaService;
import com.stock.tradingExecutor.execution.LoginPageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自动登录服务
 * 负责编排完整的登录流程，包括浏览器操作、表单填写、验证码处理、Token 提取
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

    @Value("${spring.auto-login.captcha-timeout:300}")
    private int captchaTimeoutSeconds;

    @Value("${spring.auto-login.max-retries:3}")
    private int maxRetries;

    private LocalDateTime lastLoginTime;

    /**
     * 执行完整登录流程
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

                // 3. 切换到登录表单所在的 iframe
                if (!browserSessionManager.ensureLoginFrame()) {
                    throw new LoginException("无法切换到登录表单 iframe");
                }

                // 4. 输入账号（人类行为模拟）
                loginPageHandler.inputAccount(account);

                // 5. 输入密码
                loginPageHandler.inputPassword(password);

                // 6. 处理验证码
                String captchaResult = handleCaptcha(driver);
                if (captchaResult != null) {
                    loginPageHandler.inputCaptcha(captchaResult);
                }

                // 7. 勾选协议
                loginPageHandler.checkAgreements();

                // 8. 点击登录按钮
                loginPageHandler.clickLoginButton();

                // 9. 处理网易云盾滑块验证码
                if (!handleSliderCaptcha(driver)) {
                    throw new LoginException("滑块验证失败");
                }

                // 10. 等待登录成功
                Thread.sleep(2000);

                // 11. 提取并同步 Token
                String token = cookieManager.extractToken(driver);
                if (token == null) {
                    throw new TokenException("Token 提取失败");
                }
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
     * 处理验证码（数学验证码或图片验证码）
     */
    private String handleCaptcha(WebDriver driver) throws Exception {
        String mathResult = loginPageHandler.calculateMathCaptcha();
        if (mathResult != null) {
            return mathResult;
        }

        log.info("检测到图片验证码，需要人工识别");
        File captchaImage = loginPageHandler.captureCaptchaImage();
        return handleImageCaptcha();
    }

    /**
     * 图片验证码人工介入处理
     */
    private String handleImageCaptcha() throws Exception {
        Path captchaCodePath = Paths.get(".tmp/captcha_code.txt");

        log.info("请打开 .tmp/captcha_image.png 查看验证码图片");
        log.info("并在 {} 文件中输入验证码", captchaCodePath.toAbsolutePath());

        long deadline = System.currentTimeMillis() + captchaTimeoutSeconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(captchaCodePath)) {
                List<String> lines = Files.readAllLines(captchaCodePath);
                if (!lines.isEmpty() && !lines.get(0).trim().isEmpty()) {
                    String code = lines.get(0).trim();
                    Files.write(captchaCodePath, "".getBytes());
                    log.info("验证码输入完成: {}", code);
                    return code;
                }
            }
            Thread.sleep(2000);
        }

        throw new CaptchaException("等待验证码输入超时 (" + captchaTimeoutSeconds + "秒)");
    }

    /**
     * 处理滑块验证码
     */
    private boolean handleSliderCaptcha(WebDriver driver) throws Exception {
        Thread.sleep(1000);

        SliderType sliderType = captchaService.detectSliderType(driver);
        if (sliderType == SliderType.NONE) {
            log.info("未检测到滑块验证码，继续登录流程");
            return true;
        }

        log.info("检测到滑块验证码，开始处理");

        // 切换到滑块所在 frame
        if (!captchaService.switchToSliderFrame(driver)) {
            log.error("无法切换到滑块所在 frame");
            return false;
        }

        CaptchaService.ImageUrls imageUrls = captchaService.extractYidunImageUrls(driver);
        if (imageUrls.getBgUrl() == null || imageUrls.getSliderUrl() == null) {
            log.error("无法提取滑块图片 URL");
            return false;
        }

        byte[] bgImage = captchaService.downloadImage(imageUrls.getBgUrl());
        byte[] sliderImage = captchaService.downloadImage(imageUrls.getSliderUrl());
        int distance = captchaService.calculateSliderDistance(bgImage, sliderImage);

        if (distance <= 0) {
            log.error("滑块距离计算失败");
            return false;
        }

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
