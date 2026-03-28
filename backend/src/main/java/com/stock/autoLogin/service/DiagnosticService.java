package com.stock.autoLogin.service;

import com.stock.autoLogin.exception.BrowserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Service;

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
 * 诊断服务
 * 提供浏览器页面诊断相关功能，包括截图、DOM 诊断、表单状态查询、滑块轮询、
 * 浏览器指纹检查、Frame 信息收集等
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosticService {

    private final CaptchaService captchaService;

    /**
     * 截取全页面截图并保存到 .tmp/ 目录
     *
     * @param driver WebDriver 实例
     * @return 截图文件的绝对路径
     * @throws BrowserException 截图失败时抛出
     */
    public String takeScreenshot(WebDriver driver) {
        try {
            // 1. 切换到顶层 document
            driver.switchTo().defaultContent();

            // 2. 截图并保存
            File screenshotFile = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            Path dest = Paths.get(".tmp", "screenshot_" + timestamp + ".png");
            Files.createDirectories(dest.getParent());
            Files.copy(screenshotFile.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);

            log.info("截图已保存: {}", dest.toAbsolutePath());
            return dest.toAbsolutePath().toString();
        } catch (Exception e) {
            log.error("截图失败", e);
            throw new BrowserException("截图失败: " + e.getMessage(), e);
        }
    }

    /**
     * 在顶层和所有 iframe 中搜索 .yidun 元素，返回每个 frame 的详细检查结果
     *
     * @param driver WebDriver 实例
     * @return 每个 frame 的检查结果列表
     */
    public List<Map<String, Object>> inspectYidunElements(WebDriver driver) {
        List<Map<String, Object>> results = new ArrayList<>();

        // 1. 检查顶层 document
        driver.switchTo().defaultContent();
        results.add(inspectFrameForYidun(driver, "顶层 document"));

        // 2. 遍历所有 iframe 检查
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

        // 3. 切回顶层
        driver.switchTo().defaultContent();
        return results;
    }

    /**
     * 收集当前 frame 中所有 input 和 button 的状态信息
     *
     * @param driver WebDriver 实例
     * @return 包含 inputs 和 buttons 状态列表的 Map
     */
    public Map<String, Object> inspectFormState(WebDriver driver) {
        Map<String, Object> state = new HashMap<>();

        // 1. 收集所有 input 元素的状态
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

        // 2. 收集所有 button 元素的状态
        List<WebElement> buttons = driver.findElements(By.tagName("button"));
        List<Map<String, String>> buttonStates = buttons.stream().map(btn -> {
            Map<String, String> m = new HashMap<>();
            m.put("text", btn.getText());
            m.put("enabled", String.valueOf(btn.isEnabled()));
            m.put("displayed", String.valueOf(btn.isDisplayed()));
            return m;
        }).collect(Collectors.toList());
        state.put("buttons", buttonStates);

        return state;
    }

    /**
     * 持续轮询检测滑块出现，最多持续指定秒数
     *
     * @param driver  WebDriver 实例
     * @param seconds 最大轮询秒数
     * @return 每秒的轮询结果列表
     */
    public List<Map<String, Object>> pollSlider(WebDriver driver, int seconds) {
        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < seconds; i++) {
            Map<String, Object> pollResult = new HashMap<>();
            pollResult.put("second", i + 1);

            // 在所有 frame 中搜索滑块
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

        return results;
    }

    /**
     * 检查浏览器反爬检测指纹状态
     *
     * @param driver WebDriver 实例
     * @return 浏览器指纹信息（webdriver、languages、platform 等）
     */
    public Map<String, Object> checkFingerprint(WebDriver driver) {
        driver.switchTo().defaultContent();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        Map<String, Object> result = new HashMap<>();
        result.put("webdriver", js.executeScript("return navigator.webdriver"));
        result.put("languages", js.executeScript("return navigator.languages"));
        result.put("platform", js.executeScript("return navigator.platform"));
        result.put("hardwareConcurrency", js.executeScript("return navigator.hardwareConcurrency"));
        result.put("userAgent", js.executeScript("return navigator.userAgent"));
        result.put("chromeRuntime", js.executeScript("return typeof window.chrome"));

        return result;
    }

    /**
     * 收集当前页面的 frame 信息
     *
     * @param driver WebDriver 实例
     * @return frame 上下文信息（URL、标题、iframe 列表）
     */
    public Map<String, Object> getFrameInfo(WebDriver driver) {
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

        return info;
    }

    /**
     * 执行任意 JavaScript 脚本（仅调试用）
     *
     * @param driver WebDriver 实例
     * @param script JavaScript 代码
     * @return 脚本执行结果
     * @throws BrowserException JS 执行失败时抛出
     */
    public Object executeJavaScript(WebDriver driver, String script) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            return js.executeScript(script);
        } catch (Exception e) {
            throw new BrowserException("JS 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 在指定 frame 中检查 .yidun 元素的详细信息
     *
     * @param driver    WebDriver 实例
     * @param frameName frame 标识名称
     * @return 检查结果（元素总数、可见数、详情）
     */
    private Map<String, Object> inspectFrameForYidun(WebDriver driver, String frameName) {
        Map<String, Object> result = new HashMap<>();
        result.put("frameName", frameName);

        List<WebElement> yidunElements = driver.findElements(By.cssSelector("[class*='yidun']"));

        // 统计可见和隐藏的元素
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
