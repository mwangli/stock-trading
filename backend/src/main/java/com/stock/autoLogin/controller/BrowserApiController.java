package com.stock.autoLogin.controller;

import com.stock.autoLogin.enums.SliderType;
import com.stock.autoLogin.service.BrowserSessionManager;
import com.stock.autoLogin.service.CookieManager;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import com.stock.tradingExecutor.execution.CaptchaService;
import com.stock.tradingExecutor.execution.LoginPageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 浏览器 API 控制器
 * 提供分步调试接口、手机验证页接口、诊断接口
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
public class BrowserApiController {

    private final BrowserSessionManager browserSessionManager;
    private final LoginPageHandler loginPageHandler;
    private final CaptchaService captchaService;
    private final CookieManager cookieManager;

    // ==================== 通用操作 ====================

    /**
     * 启动浏览器
     */
    @PostMapping("/start")
    public ResponseDTO<Void> startBrowser() {
        log.info("启动浏览器");
        browserSessionManager.startBrowser();
        return ResponseDTO.success(null, "浏览器启动成功");
    }

    /**
     * 访问登录页面
     */
    @PostMapping("/navigate/login")
    public ResponseDTO<Void> navigateToLogin() {
        log.info("访问登录页面");
        WebDriver driver = browserSessionManager.getDriver();
        driver.get("https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html");
        return ResponseDTO.success(null, "已访问登录页面");
    }

    /**
     * 切换到登录 iframe
     */
    @PostMapping("/frame/switch")
    public ResponseDTO<Boolean> switchToLoginFrame() {
        log.info("切换到登录 iframe");
        boolean success = browserSessionManager.ensureLoginFrame();
        return ResponseDTO.success(success, success ? "已切换到登录 iframe" : "未找到登录 iframe");
    }

    /**
     * 浏览器状态
     */
    @GetMapping("/status")
    public ResponseDTO<Map<String, Object>> browserStatus() {
        log.info("查询浏览器状态");
        Map<String, Object> status = new HashMap<>();
        boolean alive = browserSessionManager.isBrowserAlive();
        status.put("alive", alive);
        if (alive) {
            WebDriver driver = browserSessionManager.getDriver();
            status.put("currentUrl", driver.getCurrentUrl());
            status.put("title", driver.getTitle());
        }
        return ResponseDTO.success(status);
    }

    /**
     * 关闭浏览器
     */
    @PostMapping("/quit")
    public ResponseDTO<Void> quitBrowser() {
        log.info("关闭浏览器");
        browserSessionManager.quitBrowser();
        return ResponseDTO.success(null, "浏览器已关闭");
    }

    // ==================== 登录页分步操作 ====================

    /**
     * 输入账号
     */
    @PostMapping("/login/input-account")
    public ResponseDTO<Void> inputAccount(@RequestParam String account) {
        log.info("输入账号: {}", account);
        loginPageHandler.inputAccount(account);
        return ResponseDTO.success(null, "账号输入成功");
    }

    /**
     * 输入密码
     */
    @PostMapping("/login/input-password")
    public ResponseDTO<Void> inputPassword(@RequestParam String password) {
        log.info("输入密码");
        loginPageHandler.inputPassword(password);
        return ResponseDTO.success(null, "密码输入成功");
    }

    /**
     * 截取验证码（自动计算或截图）
     */
    @GetMapping("/login/capture-captcha")
    public ResponseDTO<String> captureCaptcha() {
        log.info("截取验证码");
        String mathResult = loginPageHandler.calculateMathCaptcha();
        if (mathResult != null) {
            return ResponseDTO.success(mathResult, "数学验证码自动计算完成");
        }
        File captchaImage = loginPageHandler.captureCaptchaImage();
        return ResponseDTO.success(
                captchaImage.getAbsolutePath(),
                "验证码图片已保存，请人工识别后调用 input-captcha 接口"
        );
    }

    /**
     * 输入验证码
     */
    @PostMapping("/login/input-captcha")
    public ResponseDTO<Void> inputCaptcha(@RequestParam String captcha) {
        log.info("输入验证码: {}", captcha);
        loginPageHandler.inputCaptcha(captcha);
        return ResponseDTO.success(null, "验证码输入成功");
    }

    /**
     * 勾选协议
     */
    @PostMapping("/login/check-agreements")
    public ResponseDTO<Void> checkAgreements() {
        log.info("勾选协议");
        loginPageHandler.checkAgreements();
        return ResponseDTO.success(null, "协议勾选完成");
    }

    /**
     * 点击登录按钮
     */
    @PostMapping("/login/submit")
    public ResponseDTO<Void> submitLogin() {
        log.info("点击登录按钮");
        loginPageHandler.clickLoginButton();
        return ResponseDTO.success(null, "登录按钮已点击");
    }

    /**
     * 检查登录结果
     */
    @GetMapping("/login/check")
    public ResponseDTO<Map<String, Object>> checkLoginResult() {
        log.info("检查登录结果");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = new HashMap<>();

        String currentUrl = driver.getCurrentUrl();
        result.put("currentUrl", currentUrl);
        result.put("isSuccess", !currentUrl.contains("login") && !currentUrl.contains("activePhone"));

        String token = cookieManager.extractToken(driver);
        result.put("hasToken", token != null);
        result.put("token", token != null ? token.substring(0, Math.min(10, token.length())) + "***" : null);

        SliderType sliderType = captchaService.detectSliderType(driver);
        result.put("sliderStatus", sliderType == SliderType.NONE ? "未检测到滑块" : "滑块存在: " + sliderType.name());

        return ResponseDTO.success(result);
    }

    // ==================== 手机验证页分步操作 ====================

    /**
     * 点击「获取验证码」按钮，并立即检测弹窗状态
     *
     * @return 弹窗检测结果（body 子元素、iframe、弹窗关键词等）
     */
    @PostMapping("/phone/send-code")
    public ResponseDTO<Map<String, Object>> sendSmsCode() {
        log.info("点击获取验证码按钮");
        loginPageHandler.clickSendCodeButton();
        Map<String, Object> result = new HashMap<>();
        result.put("clicked", true);

        // 等待弹窗出现
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 立即检测弹窗 DOM 结构
        try {
            WebDriver driver = browserSessionManager.getDriver();
            org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

            // 1. body 直接子元素列表（查找新注入的弹窗层）
            @SuppressWarnings("unchecked")
            List<String> bodyChildren = (List<String>) js.executeScript(
                    "return Array.from(document.body.children).map(e => " +
                    "e.tagName + '#' + e.id + '.' + (e.className || '').substring(0,80) + " +
                    "' display=' + getComputedStyle(e).display + ' pos=' + getComputedStyle(e).position" +
                    ").slice(0, 30);"
            );
            result.put("bodyChildren", bodyChildren);

            // 2. 所有 iframe（全局搜索）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> iframes = (List<Map<String, Object>>) js.executeScript(
                    "return Array.from(document.querySelectorAll('iframe')).map(f => " +
                    "({src: f.src?.substring(0,200), id: f.id, cls: f.className, " +
                    "w: f.offsetWidth, h: f.offsetHeight, display: getComputedStyle(f).display}));"
            );
            result.put("iframes", iframes);

            // 3. window handles 数量（检查新窗口）
            result.put("windowHandles", driver.getWindowHandles().size());

            // 4. body innerHTML 关键词检测
            String bodyHtml = (String) js.executeScript(
                    "return document.body.innerHTML.substring(0, 8000);"
            );
            result.put("hasYidunPopup", bodyHtml.contains("yidun_popup") || bodyHtml.contains("yidun_modal"));
            result.put("hasNECaptcha", bodyHtml.contains("NECaptcha") || bodyHtml.contains("necaptcha"));
            result.put("hasCaptchaDialog", bodyHtml.contains("安全验证") || bodyHtml.contains("拼图") || bodyHtml.contains("滑块"));

        } catch (Exception e) {
            result.put("detectError", e.getMessage());
        }

        return ResponseDTO.success(result, "已点击获取验证码按钮");
    }

    /**
     * 检查滑块状态（跨 frame 检测）
     */
    @GetMapping("/phone/slider-status")
    public ResponseDTO<Map<String, Object>> checkSliderStatus() {
        log.info("检查滑块状态");
        WebDriver driver = browserSessionManager.getDriver();
        SliderType sliderType = captchaService.detectSliderType(driver);

        Map<String, Object> status = new HashMap<>();
        status.put("hasSlider", sliderType != SliderType.NONE);
        status.put("sliderType", sliderType.name());
        return ResponseDTO.success(status);
    }

    /**
     * 执行滑块验证
     */
    @PostMapping("/phone/solve-slider")
    public ResponseDTO<Map<String, Object>> solveSlider() {
        log.info("执行滑块验证");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = new HashMap<>();

        // 1. 切换到滑块所在 frame
        if (!captchaService.switchToSliderFrame(driver)) {
            result.put("success", false);
            result.put("error", "未找到滑块元素");
            return ResponseDTO.success(result, "未找到滑块元素");
        }

        try {
            // 2. 提取图片 URL
            CaptchaService.ImageUrls urls = captchaService.extractYidunImageUrls(driver);
            result.put("bgUrl", urls.getBgUrl() != null ? "已获取" : "失败");
            result.put("sliderUrl", urls.getSliderUrl() != null ? "已获取" : "失败");

            if (urls.getBgUrl() == null || urls.getSliderUrl() == null) {
                result.put("success", false);
                result.put("error", "无法提取滑块图片 URL");
                return ResponseDTO.success(result, "无法提取滑块图片 URL");
            }

            // 3. 下载图片 + 计算距离
            byte[] bgImage = captchaService.downloadImage(urls.getBgUrl());
            byte[] sliderImage = captchaService.downloadImage(urls.getSliderUrl());
            int distance = captchaService.calculateSliderDistance(bgImage, sliderImage);
            result.put("distance", distance);
            log.info("滑块距离: {}px", distance);

            if (distance <= 0) {
                result.put("success", false);
                result.put("error", "距离计算失败");
                return ResponseDTO.success(result, "距离计算失败");
            }

            // 4. 执行拖动
            boolean dragSuccess = captchaService.executeSliderDrag(driver, distance);
            result.put("dragSuccess", dragSuccess);

            if (!dragSuccess) {
                result.put("success", false);
                result.put("error", "拖动失败");
                return ResponseDTO.success(result, "拖动失败");
            }

            // 5. 等待验证结果
            boolean verified = captchaService.waitForVerificationResult(driver, 3);
            result.put("success", verified);
            return ResponseDTO.success(result, verified ? "滑块验证成功" : "滑块验证失败");

        } catch (Exception e) {
            log.error("滑块验证异常", e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseDTO.success(result, "滑块验证异常: " + e.getMessage());
        }
    }

    /**
     * 点击「确定」按钮（滑块验证后）
     */
    @PostMapping("/phone/confirm-slider")
    public ResponseDTO<Void> confirmSlider() {
        log.info("点击确定按钮");
        // 可能需要切回表单 iframe
        browserSessionManager.ensureLoginFrame();
        loginPageHandler.clickConfirmButton();
        return ResponseDTO.success(null, "操作完成");
    }

    /**
     * 输入短信验证码
     */
    @PostMapping("/phone/input-code")
    public ResponseDTO<Void> inputSmsCode(@RequestParam String code) {
        log.info("输入短信验证码");
        // 可能需要切回表单 iframe
        browserSessionManager.ensureLoginFrame();
        loginPageHandler.inputSmsCode(code);
        return ResponseDTO.success(null, "验证码输入成功");
    }

    /**
     * 点击「下一步」/「登录」按钮
     */
    @PostMapping("/phone/submit")
    public ResponseDTO<Void> submitPhone() {
        log.info("点击下一步/登录按钮");
        loginPageHandler.clickNextStepButton();
        return ResponseDTO.success(null, "已提交");
    }

}
