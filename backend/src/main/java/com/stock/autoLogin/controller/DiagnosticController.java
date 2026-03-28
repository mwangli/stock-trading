package com.stock.autoLogin.controller;

import com.stock.autoLogin.service.BrowserSessionManager;
import com.stock.autoLogin.service.CaptchaService;
import com.stock.dataCollector.domain.dto.ResponseDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 诊断接口控制器
 * 提供页面类型检测、截图、DOM 诊断、表单状态查询、滑块轮询、指纹检查等调试接口
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@RestController
@RequestMapping("/api/browser/debug")
@RequiredArgsConstructor
public class DiagnosticController {

    private final BrowserSessionManager browserSessionManager;
    private final CaptchaService captchaService;

    /**
     * 检测页面类型（基于 DOM 内容，非 URL）
     *
     * @return 页面类型及检测详情
     */
    @GetMapping("/page-type")
    public ResponseDTO<Map<String, Object>> detectPageType() {
        log.info("检测页面类型");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. 获取页面标题
            result.put("title", driver.getTitle());

            // 2. 检测登录页特征
            List<WebElement> passwordInputs = driver.findElements(
                    By.xpath("//input[@type='password']"));
            boolean hasPassword = !passwordInputs.isEmpty();
            result.put("hasPasswordInput", hasPassword);

            // 3. 检测手机验证页特征
            List<WebElement> phoneInputs = driver.findElements(
                    By.xpath("//input[contains(@placeholder, '手机号')]"));
            boolean hasPhoneInput = !phoneInputs.isEmpty();
            result.put("hasPhoneInput", hasPhoneInput);

            List<WebElement> sendCodeButtons = driver.findElements(
                    By.xpath("//*[contains(text(), '获取验证码')]"));
            boolean hasSendCodeButton = !sendCodeButtons.isEmpty();
            result.put("hasSendCodeButton", hasSendCodeButton);

            // 4. 综合判断页面类型
            String pageType;
            if (hasPhoneInput && hasSendCodeButton) {
                boolean passwordVisible = passwordInputs.stream().anyMatch(WebElement::isDisplayed);
                if (passwordVisible) {
                    pageType = "LOGIN";
                    result.put("description", "登录页（常规登录，含密码输入框）");
                } else {
                    pageType = "PHONE_VERIFY";
                    result.put("description", "手机验证页（首次登录/新设备）");
                }
            } else if (hasPassword) {
                pageType = "LOGIN";
                result.put("description", "登录页（常规登录）");
            } else {
                pageType = "UNKNOWN";
                result.put("description", "未知页面类型");
            }

            result.put("pageType", pageType);
            result.put("currentUrl", driver.getCurrentUrl());
            log.info("页面类型检测结果: {}", pageType);
            return ResponseDTO.success(result);

        } catch (Exception e) {
            log.error("页面类型检测失败: {}", e.getMessage());
            result.put("pageType", "ERROR");
            result.put("error", e.getMessage());
            return ResponseDTO.failure("页面类型检测失败: " + e.getMessage());
        }
    }

    /**
     * 全页面截图
     *
     * @return 截图文件路径
     */
    @GetMapping("/screenshot")
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
    @GetMapping("/dom-inspect")
    public ResponseDTO<List<Map<String, Object>>> domInspect() {
        log.info("DOM 诊断 — 搜索 .yidun 元素");
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. 检查顶层
        driver.switchTo().defaultContent();
        results.add(inspectFrameForYidun(driver, "顶层 document"));

        // 2. 检查所有 iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
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
    @GetMapping("/form-state")
    public ResponseDTO<Map<String, Object>> formState() {
        log.info("查询表单状态");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> state = new HashMap<>();

        List<WebElement> inputs = driver.findElements(By.tagName("input"));
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

        List<WebElement> buttons = driver.findElements(By.tagName("button"));
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
    @GetMapping("/slider-poll")
    public ResponseDTO<List<Map<String, Object>>> sliderPoll(
            @RequestParam(defaultValue = "10") int seconds) {
        log.info("滑块轮询 {} 秒", seconds);
        WebDriver driver = browserSessionManager.getDriver();
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < seconds; i++) {
            Map<String, Object> pollResult = new HashMap<>();
            pollResult.put("second", i + 1);

            String foundIn = captchaService.findYidunInAllFrames(driver);
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
    @GetMapping("/fingerprint")
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
    @GetMapping("/frame-info")
    public ResponseDTO<Map<String, Object>> frameInfo() {
        log.info("查询 frame 信息");
        WebDriver driver = browserSessionManager.getDriver();
        Map<String, Object> info = new HashMap<>();
        info.put("currentUrl", driver.getCurrentUrl());
        info.put("title", driver.getTitle());

        driver.switchTo().defaultContent();
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
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
     * 执行任意 JS 脚本（仅开发调试用，生产环境应禁用）
     *
     * @param script JS 代码
     * @return 执行结果
     */
    @PostMapping("/exec-js")
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

    /**
     * 在指定 frame 中检查 .yidun 元素的详细信息
     *
     * @param driver    WebDriver 实例
     * @param frameName frame 标识名
     * @return 检查结果
     */
    private Map<String, Object> inspectFrameForYidun(WebDriver driver, String frameName) {
        Map<String, Object> result = new HashMap<>();
        result.put("frameName", frameName);

        List<WebElement> yidunElements = driver.findElements(
                By.cssSelector("[class*='yidun']"));

        int visibleCount = 0;
        List<String> elementDetails = new ArrayList<>();
        for (WebElement el : yidunElements) {
            try {
                boolean displayed = el.isDisplayed();
                int width = el.getSize().getWidth();
                int height = el.getSize().getHeight();
                boolean visible = displayed && width > 0 && height > 0;
                if (visible) {
                    visibleCount++;
                }
                elementDetails.add(el.getTagName() + "." + el.getAttribute("class")
                        + " [visible=" + visible + " displayed=" + displayed
                        + " " + width + "x" + height + "]");
            } catch (Exception e) {
                elementDetails.add(el.getTagName() + "." + el.getAttribute("class") + " [error]");
            }
        }

        result.put("totalFound", yidunElements.size());
        result.put("visibleCount", visibleCount);
        result.put("found", visibleCount > 0);
        result.put("details", String.join(", ", elementDetails));

        return result;
    }
}
