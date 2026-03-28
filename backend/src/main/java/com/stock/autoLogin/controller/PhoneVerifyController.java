package com.stock.autoLogin.controller;

import com.stock.autoLogin.dto.PopupDetectionResult;
import com.stock.autoLogin.dto.SliderVerificationResult;
import com.stock.autoLogin.service.*;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriver;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 手机验证页分步操作控制器
 * 提供手机验证流程的逐步操作接口：发送验证码、滑块验证、输入短信码、提交
 * 所有业务逻辑委托给对应的 Service 处理，Controller 仅负责参数接收和响应封装
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
    private final SliderVerificationService sliderVerificationService;
    private final PopupDetectionService popupDetectionService;

    /**
     * 点击「获取验证码」按钮，并检测弹窗状态
     * 点击后自动等待并分析 DOM 变化，检测是否出现滑块验证弹窗
     *
     * @return 弹窗检测结果（DOM 子元素、iframe、验证码组件特征）
     */
    @PostMapping("/send-code")
    public ResponseDTO<Map<String, Object>> sendSmsCode() {
        log.info("点击获取验证码按钮");
        // 1. 点击获取验证码按钮
        loginPageHandler.clickSendCodeButton();
        // 2. 检测弹窗状态
        WebDriver driver = browserSessionManager.getDriver();
        PopupDetectionResult result = popupDetectionService.detectPopupAfterAction(driver);
        return ResponseDTO.success(result.toMap(), "已点击获取验证码按钮");
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
     * 完整流程委托给 SliderVerificationService 编排
     *
     * @return 滑块验证结果（成功状态、距离、尝试次数）
     */
    @PostMapping("/solve-slider")
    public ResponseDTO<Map<String, Object>> solveSlider() {
        log.info("执行滑块验证");
        WebDriver driver = browserSessionManager.getDriver();
        SliderVerificationResult result = sliderVerificationService.solveWithRetry(driver);
        String message = result.isSuccess() ? "滑块验证成功" : "滑块验证失败";
        return ResponseDTO.success(result.toMap(), message);
    }

    /**
     * 点击「确定」按钮（滑块验证后的提示弹窗）
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
     * 如果 code 参数为空，通过邮件自动获取验证码；否则直接使用传入的值
     *
     * @param code 可选，短信验证码（6位数字）
     * @return 验证码输入结果
     */
    @PostMapping("/input-code")
    public ResponseDTO<Map<String, Object>> inputSmsCode(@RequestParam(required = false) String code) {
        log.info("输入短信验证码");
        // 1. 自动获取验证码（如未提供）
        String source = "manual";
        if (code == null || code.trim().isEmpty()) {
            log.info("未提供验证码，通过邮件获取");
            code = captchaFetchService.getPhoneCode();
            if (code == null) {
                return ResponseDTO.failure("邮件获取验证码失败");
            }
            source = "email";
            log.info("邮件获取验证码成功");
        }
        // 2. 输入验证码
        browserSessionManager.ensureLoginFrame();
        loginPageHandler.inputSmsCode(code);

        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("source", source);
        return ResponseDTO.success(result, "验证码输入成功");
    }

    /**
     * 增强版获取验证码（含失效自动重发）
     * 如果首次获取失败，自动重新发送验证码并再次尝试获取
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
