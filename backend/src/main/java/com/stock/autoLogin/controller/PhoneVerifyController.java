package com.stock.autoLogin.controller;

import com.stock.autoLogin.service.*;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 手机验证页分步操作控制器
 * 提供手机验证流程的逐步操作接口：发送验证码、滑块验证、输入短信码、提交
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser/phone")
@RequiredArgsConstructor
public class PhoneVerifyController {

    private final BrowserSessionManager browserSessionManager;
    private final LoginPageHandler loginPageHandler;
    private final CaptchaService captchaService;
    private final CaptchaFetchService captchaFetchService;

    /**
     * 点击「获取验证码」按钮，并立即检测弹窗状态
     *
     * @return 弹窗检测结果
     */
    @PostMapping("/send-code")
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

        // 检测弹窗 DOM 结构
        try {
            WebDriver driver = browserSessionManager.getDriver();
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. body 直接子元素列表
            @SuppressWarnings("unchecked")
            List<String> bodyChildren = (List<String>) js.executeScript(
                    "return Array.from(document.body.children).map(e => " +
                    "e.tagName + '#' + e.id + '.' + (e.className || '').substring(0,80) + " +
                    "' display=' + getComputedStyle(e).display + ' pos=' + getComputedStyle(e).position" +
                    ").slice(0, 30);"
            );
            result.put("bodyChildren", bodyChildren);

            // 2. 所有 iframe
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> iframes = (List<Map<String, Object>>) js.executeScript(
                    "return Array.from(document.querySelectorAll('iframe')).map(f => " +
                    "({src: f.src?.substring(0,200), id: f.id, cls: f.className, " +
                    "w: f.offsetWidth, h: f.offsetHeight, display: getComputedStyle(f).display}));"
            );
            result.put("iframes", iframes);

            // 3. window handles 数量
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
     *
     * @return 滑块存在状态及类型
     */
    @GetMapping("/slider-status")
    public ResponseDTO<Map<String, Object>> checkSliderStatus() {
        log.info("检查滑块状态");
        WebDriver driver = browserSessionManager.getDriver();
        com.stock.autoLogin.enums.SliderType sliderType = captchaService.detectSliderType(driver);

        Map<String, Object> status = new HashMap<>();
        status.put("hasSlider", sliderType != com.stock.autoLogin.enums.SliderType.NONE);
        status.put("sliderType", sliderType.name());
        return ResponseDTO.success(status);
    }

    /**
     * 执行滑块验证（增强版：自动重新获取图片，最多 3 次尝试）
     *
     * @return 滑块验证结果
     */
    @PostMapping("/solve-slider")
    public ResponseDTO<Map<String, Object>> solveSlider() {
        log.info("执行滑块验证（增强版：自动重新获取图片）");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = new HashMap<>();

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            log.info("滑块验证第 {}/{} 次尝试", attempt, maxAttempts);

            // 1. 切换到滑块所在 frame
            if (!captchaService.switchToSliderFrame(driver)) {
                log.info("滑块未弹出，尝试重新点击获取验证码按钮触发");
                try {
                    browserSessionManager.ensureLoginFrame();
                    loginPageHandler.clickSendCodeButton();
                    Thread.sleep(3000);
                    if (!captchaService.switchToSliderFrame(driver)) {
                        result.put("success", false);
                        result.put("error", "滑块弹窗未弹出");
                        result.put("attempt", attempt);
                        return ResponseDTO.success(result, "滑块弹窗未弹出");
                    }
                } catch (Exception e) {
                    result.put("success", false);
                    result.put("error", "重新触发滑块失败: " + e.getMessage());
                    result.put("attempt", attempt);
                    return ResponseDTO.success(result, "重新触发滑块失败");
                }
            }

            try {
                // 2. 提取图片 URL
                CaptchaService.ImageUrls urls = captchaService.extractYidunImageUrls(driver);
                result.put("bgUrl", urls.getBgUrl() != null ? "已获取" : "失败");
                result.put("sliderUrl", urls.getSliderUrl() != null ? "已获取" : "失败");

                if (urls.getBgUrl() == null || urls.getSliderUrl() == null) {
                    result.put("success", false);
                    result.put("error", "无法提取滑块图片 URL");
                    result.put("attempt", attempt);
                    return ResponseDTO.success(result, "无法提取滑块图片 URL");
                }

                // 3. 下载图片 + 计算距离
                byte[] bgImage = captchaService.downloadImage(urls.getBgUrl());
                byte[] sliderImage = captchaService.downloadImage(urls.getSliderUrl());
                int distance = captchaService.calculateSliderDistance(bgImage, sliderImage);
                result.put("distance", distance);
                log.info("滑块距离: {}px (第 {}/{} 次)", distance, attempt, maxAttempts);

                if (distance <= 0) {
                    result.put("success", false);
                    result.put("error", "距离计算失败");
                    result.put("attempt", attempt);
                    return ResponseDTO.success(result, "距离计算失败");
                }

                // 4. 执行拖动
                boolean dragSuccess = captchaService.executeSliderDrag(driver, distance);
                result.put("dragSuccess", dragSuccess);

                if (!dragSuccess) {
                    result.put("success", false);
                    result.put("error", "拖动失败");
                    result.put("attempt", attempt);
                    return ResponseDTO.success(result, "拖动失败");
                }

                // 5. 等待验证结果
                boolean verified = captchaService.waitForVerificationResult(driver, 1);
                result.put("verified", verified);

                if (verified) {
                    log.info("滑块验证成功！");
                    result.put("success", true);
                    result.put("attempt", attempt);
                    return ResponseDTO.success(result, "滑块验证成功");
                }

                log.warn("滑块验证失败，页面已刷新，继续第 {} 次尝试", attempt + 1);
                if (attempt < maxAttempts) {
                    Thread.sleep(1500);
                }

            } catch (Exception e) {
                log.error("滑块验证异常 (第 {}/{} 次): {}", attempt, maxAttempts, e.getMessage());
                result.put("success", false);
                result.put("error", e.getMessage());
                result.put("attempt", attempt);
                if (attempt == maxAttempts) {
                    return ResponseDTO.success(result, "滑块验证异常: " + e.getMessage());
                }
            }
        }

        log.error("滑块验证失败：已达最大尝试次数 {}", maxAttempts);
        result.put("success", false);
        result.put("error", "滑块验证失败：已达最大尝试次数 " + maxAttempts);
        return ResponseDTO.success(result, "滑块验证失败");
    }

    /**
     * 点击「确定」按钮（滑块验证后）
     *
     * @return 操作结果
     */
    @PostMapping("/confirm-slider")
    public ResponseDTO<Void> confirmSlider() {
        log.info("点击确定按钮");
        browserSessionManager.ensureLoginFrame();
        loginPageHandler.clickConfirmButton();
        return ResponseDTO.success(null, "操作完成");
    }

    /**
     * 输入短信验证码
     * 如果 code 参数为空，通过邮件获取验证码；否则直接使用传入的值
     *
     * @param code 可选，短信验证码
     * @return 验证码输入结果
     */
    @PostMapping("/input-code")
    public ResponseDTO<Map<String, Object>> inputSmsCode(@RequestParam(required = false) String code) {
        log.info("输入短信验证码");

        if (code == null || code.trim().isEmpty()) {
            log.info("未提供验证码，通过邮件获取");
            code = captchaFetchService.getPhoneCode();
            if (code == null) {
                return ResponseDTO.failure("邮件获取验证码失败");
            }
            log.info("邮件获取验证码成功");
        }

        browserSessionManager.ensureLoginFrame();
        loginPageHandler.inputSmsCode(code);

        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("source", "email");
        return ResponseDTO.success(result, "验证码输入成功");
    }

    /**
     * 增强版获取验证码（含失效重试）
     *
     * @return 验证码获取结果
     */
    @PostMapping("/get-code-enhanced")
    public ResponseDTO<Map<String, Object>> getCodeEnhanced() {
        log.info("增强版获取验证码（含失效重试）");

        String code = captchaFetchService.getPhoneCodeWithRetry(() -> {
            log.info("触发重新发送验证码...");
            browserSessionManager.ensureLoginFrame();
            loginPageHandler.clickSendCodeButton();
            return true;
        });

        Map<String, Object> result = new HashMap<>();
        if (code != null) {
            result.put("code", code);
            result.put("source", "enhanced-retry");
            return ResponseDTO.success(result, "验证码获取成功");
        } else {
            return ResponseDTO.failure("验证码获取失败");
        }
    }

    /**
     * 点击「下一步」/「登录」按钮
     *
     * @return 操作结果
     */
    @PostMapping("/submit")
    public ResponseDTO<Void> submitPhone() {
        log.info("点击下一步/登录按钮");
        loginPageHandler.clickNextStepButton();
        return ResponseDTO.success(null, "已提交");
    }
}
