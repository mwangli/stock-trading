package com.stock.tradingExecutor.api;

import com.stock.tradingExecutor.execution.BrowserSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/browser")
@RequiredArgsConstructor
public class BrowserApiController {

    private final BrowserSessionManager browserSession;

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startBrowser() {
        log.info("[BrowserApi] 收到启动浏览器请求");
        try {
            browserSession.startBrowser();
            browserSession.visitLoginPage();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "浏览器启动成功");
            result.put("url", browserSession.getCurrentUrl());
            result.put("title", browserSession.getPageTitle());

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[BrowserApi] 启动浏览器失败: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "浏览器启动失败: " + e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", browserSession.isRunning());
        result.put("url", browserSession.getCurrentUrl());
        result.put("title", browserSession.getPageTitle());
        result.put("onActivePhone", browserSession.isOnActivePhonePage());
        result.put("onLoginPage", browserSession.isOnLoginPage());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzePage() {
        log.info("[BrowserApi] 收到页面分析请求");
        try {
            String analysis = browserSession.analyzePageStructure();

            Map<String, Object> result = new HashMap<>();
            result.put("analysis", analysis);
            result.put("success", true);
            result.put("url", browserSession.getCurrentUrl());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[BrowserApi] 页面分析失败: {}", e.getMessage());
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/phone/send-code")
    public ResponseEntity<Map<String, Object>> sendPhoneCode() {
        log.info("[BrowserApi] 收到发送手机验证码请求");
        Map<String, Object> result = new HashMap<>();

        if (!browserSession.isOnActivePhonePage()) {
            result.put("success", false);
            result.put("message", "当前不在手机验证页面");
            return ResponseEntity.ok(result);
        }

        try {
            boolean success = browserSession.clickSendCodeButton();
            result.put("success", success);
            result.put("message", success ? "验证码已发送" : "发送失败");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[BrowserApi] 发送验证码失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @GetMapping("/phone/wait-code")
    public ResponseEntity<Map<String, Object>> waitForPhoneCode() {
        log.info("[BrowserApi] 等待手机验证码输入...");
        String code = browserSession.waitForSmsCode();

        Map<String, Object> result = new HashMap<>();
        if (code != null) {
            result.put("success", true);
            result.put("code", code);
            result.put("message", "验证码已输入");
        } else {
            result.put("success", false);
            result.put("message", "等待验证码超时");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/phone/input-code")
    public ResponseEntity<Map<String, Object>> inputPhoneCode(@RequestParam String code) {
        log.info("[BrowserApi] 收到输入手机验证码请求: {}", code);
        boolean success = browserSession.inputSmsCode(code);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "验证码已输入" : "输入失败");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/phone/submit")
    public ResponseEntity<Map<String, Object>> submitPhoneVerification() {
        log.info("[BrowserApi] 收到提交手机验证请求");
        boolean success = browserSession.submitPhoneVerification();

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("url", browserSession.getCurrentUrl());
        result.put("message", success ? "验证提交成功" : "验证提交失败");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login/input-account")
    public ResponseEntity<Map<String, Object>> inputAccount(@RequestParam String account) {
        log.info("[BrowserApi] 收到输入账号请求: {}", account);
        boolean success = browserSession.inputAccount(account);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "账号输入成功" : "账号输入失败");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login/input-password")
    public ResponseEntity<Map<String, Object>> inputPassword(@RequestParam String password) {
        log.info("[BrowserApi] 收到输入密码请求");
        boolean success = browserSession.inputPassword(password);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "密码输入成功" : "密码输入失败");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/login/capture-captcha")
    public ResponseEntity<Map<String, Object>> captureCaptcha() {
        log.info("[BrowserApi] 收到截取验证码图片请求");
        String path = browserSession.captureCaptchaImage();

        Map<String, Object> result = new HashMap<>();
        if (path != null) {
            result.put("success", true);
            result.put("path", path);
            result.put("message", "验证码图片已保存");
        } else {
            result.put("success", false);
            result.put("message", "验证码图片截取失败");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/login/wait-captcha")
    public ResponseEntity<Map<String, Object>> waitForCaptchaCode() {
        log.info("[BrowserApi] 等待图片验证码输入...");
        String code = browserSession.waitForCaptchaCode();

        Map<String, Object> result = new HashMap<>();
        if (code != null) {
            result.put("success", true);
            result.put("code", code);
            result.put("message", "验证码已输入");
        } else {
            result.put("success", false);
            result.put("message", "等待验证码超时");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login/input-captcha")
    public ResponseEntity<Map<String, Object>> inputCaptcha(@RequestParam String code) {
        log.info("[BrowserApi] 收到输入图片验证码请求: {}", code);
        boolean success = browserSession.inputCaptcha(code);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "验证码输入成功" : "验证码输入失败");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login/check-agreements")
    public ResponseEntity<Map<String, Object>> checkAgreements() {
        log.info("[BrowserApi] 收到勾选协议请求");
        boolean privacy = browserSession.checkPrivacyAgreement();
        boolean auth = browserSession.checkAuthAgreement();

        Map<String, Object> result = new HashMap<>();
        result.put("success", privacy && auth);
        result.put("privacyChecked", privacy);
        result.put("authChecked", auth);
        result.put("message", (privacy && auth) ? "协议勾选成功" : "部分协议勾选失败");
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login/submit")
    public ResponseEntity<Map<String, Object>> submitLogin() {
        log.info("[BrowserApi] 收到提交登录请求");
        boolean success = browserSession.clickLoginButton();

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("url", browserSession.getCurrentUrl());
        result.put("title", browserSession.getPageTitle());
        result.put("loginSuccess", browserSession.isLoginSuccess());
        result.put("message", success ? "登录提交成功" : "登录提交失败");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/login/check")
    public ResponseEntity<Map<String, Object>> checkLoginSuccess() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", browserSession.isLoginSuccess());
        result.put("url", browserSession.getCurrentUrl());
        result.put("title", browserSession.getPageTitle());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/quit")
    public ResponseEntity<Map<String, Object>> quitBrowser() {
        log.info("[BrowserApi] 收到关闭浏览器请求");
        browserSession.quitBrowser();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "浏览器已关闭");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> result = new HashMap<>();
        result.put("title", "中信证券自动登录 API");
        result.put("description", "浏览器常驻模式下的分步登录API");

        Map<String, String> steps = new HashMap<>();
        steps.put("1. POST /api/browser/start", "启动浏览器并访问登录页面");
        steps.put("2. GET /api/browser/status", "查看当前状态");
        steps.put("3. GET /api/browser/analyze", "分析页面结构");
        steps.put("4.1. POST /api/browser/phone/send-code", "发送手机验证码（如需）");
        steps.put("4.2. GET /api/browser/phone/wait-code", "等待验证码输入");
        steps.put("4.3. POST /api/browser/phone/input-code?code=123456", "输入验证码");
        steps.put("4.4. POST /api/browser/phone/submit", "提交验证");
        steps.put("5.1. POST /api/browser/login/input-account?account=xxx", "输入账号");
        steps.put("5.2. POST /api/browser/login/input-password?password=xxx", "输入密码");
        steps.put("5.3. GET /api/browser/login/capture-captcha", "截取验证码图片");
        steps.put("5.4. GET /api/browser/login/wait-captcha", "等待图片验证码");
        steps.put("5.5. POST /api/browser/login/input-captcha?code=xxxx", "输入图片验证码");
        steps.put("5.6. POST /api/browser/login/check-agreements", "勾选协议");
        steps.put("5.7. POST /api/browser/login/submit", "提交登录");
        steps.put("6. GET /api/browser/login/check", "检查登录结果");
        steps.put("7. POST /api/browser/quit", "关闭浏览器");

        result.put("steps", steps);
        result.put("note", "验证码文件路径: d:/ai-stock-trading/.tmp/sms_code.txt (6位数字) 和 captcha_code.txt (4位字符)");
        return ResponseEntity.ok(result);
    }
}