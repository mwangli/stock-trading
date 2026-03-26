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

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * 访问登录页面（等待页面加载完成后返回）
     */
    @PostMapping("/navigate/login")
    public ResponseDTO<Void> navigateToLogin() {
        log.info("访问登录页面");
        WebDriver driver = browserSessionManager.getDriver();
        driver.get("https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html");
        // 等待页面中出现表单元素（手机号或密码输入框）
        try {
            new org.openqa.selenium.support.ui.WebDriverWait(driver, java.time.Duration.ofSeconds(10))
                    .until(d -> !d.findElements(org.openqa.selenium.By.xpath(
                            "//input[contains(@placeholder, '手机号')] | //input[@type='password']"
                    )).isEmpty());
            log.info("登录页面加载完成");
        } catch (Exception e) {
            log.warn("等待页面加载超时，继续执行: {}", e.getMessage());
        }
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
     * 如果滑块面板不在，尝试重新点击获取验证码按钮触发
     */
    @PostMapping("/phone/solve-slider")
    public ResponseDTO<Map<String, Object>> solveSlider() {
        log.info("执行滑块验证");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = new HashMap<>();

        // 1. 切换到滑块所在 frame（如果不在，尝试重新触发）
        if (!captchaService.switchToSliderFrame(driver)) {
            log.info("滑块未弹出，尝试重新点击获取验证码按钮触发");
            try {
                // 切回表单 frame 点击按钮
                browserSessionManager.ensureLoginFrame();
                loginPageHandler.clickSendCodeButton();
                Thread.sleep(3000);  // 等待滑块弹窗加载
                // 再次尝试切换到滑块 frame
                if (!captchaService.switchToSliderFrame(driver)) {
                    result.put("success", false);
                    result.put("error", "滑块弹窗未弹出（重试后仍未检测到）");
                    return ResponseDTO.success(result, "滑块弹窗未弹出");
                }
            } catch (Exception e) {
                result.put("success", false);
                result.put("error", "重新触发滑块失败: " + e.getMessage());
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

    // ==================== 诊断接口 ====================

    /**
     * 全页面截图
     *
     * @return 截图文件路径
     */
    @GetMapping("/debug/screenshot")
    public ResponseDTO<String> screenshot() {
        log.info("截取页面截图");
        try {
            WebDriver driver = browserSessionManager.getDriver();
            driver.switchTo().defaultContent();

            File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            Path dest = Paths.get(".tmp", "screenshot_" + timestamp + ".png");
            Files.createDirectories(dest.getParent());
            Files.copy(screenshotFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            log.info("截图已保存: {}", dest.toAbsolutePath());
            return ResponseDTO.success(dest.toAbsolutePath().toString(), "截图已保存");
        } catch (Exception e) {
            log.error("截图失败", e);
            return ResponseDTO.failure("截图失败: " + e.getMessage());
        }
    }

    /**
     * DOM 诊断 — 在顶层和所有 iframe 中搜索 .yidun 元素
     *
     * @return 每个 frame 的搜索结果
     */
    @GetMapping("/debug/dom-inspect")
    public ResponseDTO<List<Map<String, Object>>> domInspect() {
        log.info("DOM 诊断 — 搜索 .yidun 元素");
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. 检查顶层
        driver.switchTo().defaultContent();
        results.add(inspectFrameForYidun(driver, "顶层 document"));

        // 2. 检查所有 iframe
        List<org.openqa.selenium.WebElement> iframes = driver.findElements(
                org.openqa.selenium.By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                results.add(inspectFrameForYidun(driver, "iframe[" + i + "]"));
            } catch (Exception e) {
                Map<String, Object> errResult = new HashMap<>();
                errResult.put("frameName", "iframe[" + i + "]");
                errResult.put("found", false);
                errResult.put("error", e.getMessage());
                results.add(errResult);
            }
        }

        driver.switchTo().defaultContent();
        return ResponseDTO.success(results);
    }

    /**
     * 表单状态 — 返回当前 frame 中所有 input/button 的状态
     *
     * @return 表单元素状态
     */
    @GetMapping("/debug/form-state")
    public ResponseDTO<Map<String, Object>> formState() {
        log.info("查询表单状态");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> state = new HashMap<>();

        List<org.openqa.selenium.WebElement> inputs = driver.findElements(
                org.openqa.selenium.By.tagName("input"));
        List<Map<String, String>> inputStates = inputs.stream().map(input -> {
            Map<String, String> m = new HashMap<>();
            m.put("type", input.getAttribute("type"));
            m.put("placeholder", input.getAttribute("placeholder"));
            m.put("value", input.getAttribute("value"));
            m.put("checked", String.valueOf(input.isSelected()));
            m.put("displayed", String.valueOf(input.isDisplayed()));
            return m;
        }).collect(Collectors.toList());
        state.put("inputs", inputStates);

        List<org.openqa.selenium.WebElement> buttons = driver.findElements(
                org.openqa.selenium.By.tagName("button"));
        List<Map<String, String>> buttonStates = buttons.stream().map(btn -> {
            Map<String, String> m = new HashMap<>();
            m.put("text", btn.getText());
            m.put("enabled", String.valueOf(btn.isEnabled()));
            m.put("displayed", String.valueOf(btn.isDisplayed()));
            return m;
        }).collect(Collectors.toList());
        state.put("buttons", buttonStates);

        return ResponseDTO.success(state);
    }

    /**
     * 滑块轮询 — 持续 N 秒在所有 frame 中轮询 .yidun 元素
     *
     * @param seconds 轮询秒数
     * @return 每秒检测结果
     */
    @GetMapping("/debug/slider-poll")
    public ResponseDTO<List<Map<String, Object>>> sliderPoll(
            @RequestParam(defaultValue = "10") int seconds) {
        log.info("滑块轮询 {} 秒", seconds);
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < seconds; i++) {
            Map<String, Object> pollResult = new HashMap<>();
            pollResult.put("second", i + 1);

            // 在所有 frame 中查找
            String foundIn = findYidunInAllFrames(driver);
            pollResult.put("found", foundIn != null);
            pollResult.put("foundIn", foundIn);

            results.add(pollResult);

            if (foundIn != null) {
                log.info("第 {} 秒在 {} 中检测到滑块", i + 1, foundIn);
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        return ResponseDTO.success(results);
    }

    /**
     * 指纹检查 — 检查浏览器反爬检测状态
     *
     * @return 浏览器指纹信息
     */
    @GetMapping("/debug/fingerprint")
    public ResponseDTO<Map<String, Object>> fingerprint() {
        log.info("检查浏览器指纹");
        WebDriver driver = browserSessionManager.getDriver();
        driver.switchTo().defaultContent();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Map<String, Object> result = new HashMap<>();
        result.put("webdriver", js.executeScript("return navigator.webdriver"));
        result.put("languages", js.executeScript("return navigator.languages"));
        result.put("platform", js.executeScript("return navigator.platform"));
        result.put("hardwareConcurrency", js.executeScript("return navigator.hardwareConcurrency"));
        result.put("userAgent", js.executeScript("return navigator.userAgent"));
        result.put("chromeRuntime", js.executeScript("return typeof window.chrome"));

        return ResponseDTO.success(result);
    }

    /**
     * 当前 frame 信息
     *
     * @return frame 上下文信息
     */
    @GetMapping("/debug/frame-info")
    public ResponseDTO<Map<String, Object>> frameInfo() {
        log.info("查询 frame 信息");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> info = new HashMap<>();
        info.put("currentUrl", driver.getCurrentUrl());
        info.put("title", driver.getTitle());

        driver.switchTo().defaultContent();
        List<org.openqa.selenium.WebElement> iframes = driver.findElements(
                org.openqa.selenium.By.tagName("iframe"));
        info.put("iframeCount", iframes.size());

        List<Map<String, String>> iframeDetails = iframes.stream().map(iframe -> {
            Map<String, String> detail = new HashMap<>();
            detail.put("src", iframe.getAttribute("src"));
            detail.put("id", iframe.getAttribute("id"));
            detail.put("name", iframe.getAttribute("name"));
            return detail;
        }).collect(Collectors.toList());
        info.put("iframes", iframeDetails);

        return ResponseDTO.success(info);
    }

    /**
     * 执行任意 JS 脚本（仅调试用）
     *
     * @param script JS 代码
     * @return 执行结果
     */
    @PostMapping("/debug/exec-js")
    public ResponseDTO<Object> execJs(@RequestBody String script) {
        log.info("执行 JS 脚本");
        WebDriver driver = browserSessionManager.getDriver();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            Object result = js.executeScript(script);
            return ResponseDTO.success(result);
        } catch (Exception e) {
            return ResponseDTO.failure("JS 执行失败: " + e.getMessage());
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 在指定 frame 中检查 .yidun 元素
     */
    private Map<String, Object> inspectFrameForYidun(WebDriver driver, String frameName) {
        Map<String, Object> result = new HashMap<>();
        result.put("frameName", frameName);

        List<org.openqa.selenium.WebElement> yidunElements = driver.findElements(
                org.openqa.selenium.By.cssSelector("[class*='yidun']"));
        boolean found = !yidunElements.isEmpty();
        result.put("found", found);

        if (found) {
            String details = yidunElements.stream()
                    .map(e -> e.getTagName() + "." + e.getAttribute("class"))
                    .collect(Collectors.joining(", "));
            result.put("details", details);
        }

        return result;
    }

    /**
     * 在顶层和所有 iframe 中搜索 .yidun 元素
     *
     * @return 找到的 frame 名称，未找到返回 null
     */
    private String findYidunInAllFrames(WebDriver driver) {
        // 检查顶层
        driver.switchTo().defaultContent();
        if (!driver.findElements(org.openqa.selenium.By.cssSelector("[class*='yidun']")).isEmpty()) {
            return "顶层 document";
        }

        // 检查所有 iframe
        List<org.openqa.selenium.WebElement> iframes = driver.findElements(
                org.openqa.selenium.By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                if (!driver.findElements(org.openqa.selenium.By.cssSelector("[class*='yidun']")).isEmpty()) {
                    return "iframe[" + i + "]";
                }
            } catch (Exception ignored) {
            }
        }

        driver.switchTo().defaultContent();
        return null;
    }

}
