package com.stock.autoLogin.controller;

import com.stock.autoLogin.enums.SliderType;
import com.stock.autoLogin.service.*;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 登录页分步操作控制器
 * 提供登录表单的逐步操作接口：输入账号、密码、验证码、勾选协议、提交登录、检查结果
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser/login")
@RequiredArgsConstructor
public class LoginStepController {

    private final BrowserSessionManager browserSessionManager;
    private final LoginPageHandler loginPageHandler;
    private final CaptchaService captchaService;
    private final CookieManager cookieManager;
    private final MathCaptchaService mathCaptchaService;

    /**
     * 输入账号
     *
     * @param account 资金账号/手机号
     * @return 操作结果
     */
    @PostMapping("/input-account")
    public ResponseDTO<Void> inputAccount(@RequestParam String account) {
        log.info("输入账号: {}", account);
        loginPageHandler.inputAccount(account);
        return ResponseDTO.success(null, "账号输入成功");
    }

    /**
     * 输入密码
     *
     * @param password 交易密码
     * @return 操作结果
     */
    @PostMapping("/input-password")
    public ResponseDTO<Void> inputPassword(@RequestParam String password) {
        log.info("输入密码");
        loginPageHandler.inputPassword(password);
        return ResponseDTO.success(null, "密码输入成功");
    }

    /**
     * 截取验证码（通过 API 获取图片 Base64 进行 OCR 识别）
     *
     * @param account 可选，资金账号/手机号
     * @return 验证码识别结果
     */
    @GetMapping("/capture-captcha")
    public ResponseDTO<Map<String, Object>> captureCaptcha(
            @RequestParam(required = false) String account) {
        log.info("通过API获取验证码图片并进行OCR识别");

        if (account == null || account.isEmpty()) {
            account = "13278828091";
        }

        Map<String, Object> result = mathCaptchaService.getCaptchaResult(account);

        if ((boolean) result.getOrDefault("success", false)) {
            Map<String, Object> data = new HashMap<>();
            data.put("expression", result.get("expression"));
            data.put("result", result.get("result"));
            return ResponseDTO.success(data, "验证码识别成功");
        } else {
            return ResponseDTO.failure("验证码识别失败: " + result.getOrDefault("error", "未知错误"));
        }
    }

    /**
     * 输入验证码
     *
     * @param captcha 验证码答案
     * @return 操作结果
     */
    @PostMapping("/input-captcha")
    public ResponseDTO<Void> inputCaptcha(@RequestParam String captcha) {
        log.info("输入验证码: {}", captcha);
        loginPageHandler.inputCaptcha(captcha);
        return ResponseDTO.success(null, "验证码输入成功");
    }

    /**
     * 勾选协议
     *
     * @return 操作结果
     */
    @PostMapping("/check-agreements")
    public ResponseDTO<Void> checkAgreements() {
        log.info("勾选协议");
        loginPageHandler.checkAgreements();
        return ResponseDTO.success(null, "协议勾选完成");
    }

    /**
     * 点击登录按钮
     *
     * @return 操作结果
     */
    @PostMapping("/submit")
    public ResponseDTO<Void> submitLogin() {
        log.info("点击登录按钮");
        loginPageHandler.clickLoginButton();
        return ResponseDTO.success(null, "登录按钮已点击");
    }

    /**
     * 检查登录结果
     *
     * @return 登录状态信息（URL、Token、滑块状态）
     */
    @GetMapping("/check")
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
}
