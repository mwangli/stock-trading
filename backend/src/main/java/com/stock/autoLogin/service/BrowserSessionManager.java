package com.stock.autoLogin.service;

import com.stock.autoLogin.exception.BrowserException;
import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 浏览器会话管理器
 * 负责 WebDriver 的生命周期管理、浏览器指纹注入、iframe 切换、元素交互
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Service
@Slf4j
public class BrowserSessionManager implements DisposableBean {

    @Value("${chrome.remote.url:}")
    private String chromeRemoteUrl;

    @Value("${chrome.headless:false}")
    private boolean chromeHeadless;

    @Value("${chrome.userDataDir:}")
    private String chromeUserDataDir;

    private WebDriver driver;

    /**
     * 启动浏览器
     */
    public synchronized WebDriver startBrowser() {
        if (isBrowserAlive()) {
            log.info("浏览器已在运行，复用现有实例");
            return driver;
        }

        try {
            log.info("开始启动 Chrome 浏览器...");
            ChromeOptions options = configureChromeOptions();

            if (chromeRemoteUrl != null && !chromeRemoteUrl.isEmpty()) {
                log.info("使用 Remote Chrome: {}", chromeRemoteUrl);
                driver = new RemoteWebDriver(new URL(chromeRemoteUrl), options);
            } else {
                log.info("使用本地 Chrome");
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(options);
            }

            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            injectAntiDetection();
            log.info("浏览器启动成功");
            return driver;

        } catch (Exception e) {
            log.error("浏览器启动失败", e);
            throw new BrowserException("浏览器启动失败：" + e.getMessage(), e);
        }
    }

    /**
     * 配置 ChromeOptions
     */
    private ChromeOptions configureChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-extensions");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // 浏览器持久化配置（保持登录状态，避免每次重新手机验证）
        if (chromeUserDataDir != null && !chromeUserDataDir.isEmpty()) {
            options.addArguments("--user-data-dir=" + chromeUserDataDir);
            log.info("启用浏览器持久化: user-data-dir={}", chromeUserDataDir);
        }

        if (chromeHeadless) {
            options.addArguments("--headless=new");
        }
        return options;
    }

    /**
     * 注入浏览器指纹，覆盖 Selenium 特征
     */
    private void injectAntiDetection() {
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                    "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});" +
                    "Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh']});" +
                    "Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});" +
                    "Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});" +
                    "Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});"
            );
            log.info("浏览器指纹注入完成");
        } catch (Exception e) {
            log.warn("浏览器指纹注入失败：{}", e.getMessage());
        }
    }

    /**
     * 关闭浏览器
     */
    public synchronized void quitBrowser() {
        if (driver != null) {
            try {
                driver.quit();
                log.info("浏览器已关闭");
            } catch (Exception e) {
                log.warn("关闭浏览器时出错：{}", e.getMessage());
            } finally {
                driver = null;
            }
        }
    }

    /**
     * 检查浏览器是否存活
     * 区分临时网络异常和真正的会话死亡，避免误判导致会话丢失
     *
     * @return 浏览器是否可用
     */
    public boolean isBrowserAlive() {
        if (driver == null) {
            return false;
        }
        // 多次重试，避免临时网络抖动导致误判
        for (int i = 0; i < 3; i++) {
            try {
                driver.getCurrentUrl();
                return true;
            } catch (org.openqa.selenium.NoSuchSessionException e) {
                // 会话已销毁，确认死亡
                log.warn("浏览器会话已销毁: {}", e.getMessage());
                driver = null;
                return false;
            } catch (org.openqa.selenium.WebDriverException e) {
                // 可能是临时异常，重试
                log.debug("浏览器存活检测第 {} 次异常: {}", i + 1, e.getMessage());
                if (i < 2) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            } catch (Exception e) {
                log.warn("浏览器存活检测未知异常: {}", e.getMessage());
                driver = null;
                return false;
            }
        }
        // 3 次都失败，判定死亡
        log.error("浏览器存活检测连续 3 次失败，判定会话已死亡");
        driver = null;
        return false;
    }

    /**
     * 获取 WebDriver 实例
     *
     * @return WebDriver，不可用时抛出 BrowserException
     */
    public WebDriver getDriver() {
        if (driver == null) {
            throw new BrowserException("浏览器未启动");
        }
        return driver;
    }

    /**
     * 切换到登录表单所在的 iframe
     * 支持两种页面类型：
     * - login.html: 查找包含 password 输入框的 iframe
     * - activePhone.html: 查找包含"获取验证码"按钮或手机号输入框的 iframe
     *
     * @return 是否成功切入
     */
    public boolean ensureLoginFrame() {
        try {
            WebDriver d = getDriver();
            d.switchTo().defaultContent();

            // 1. 先检查顶层是否包含目标元素
            if (hasFormElements(d)) {
                log.info("登录表单在顶层 document");
                return true;
            }

            // 2. 递归遍历所有 iframe
            if (findLoginFrame(d, 0)) {
                return true;
            }

            log.warn("未找到登录表单所在的 iframe");
            return false;

        } catch (Exception e) {
            log.error("iframe 切换失败", e);
            return false;
        }
    }

    /**
     * 判断当前 frame 是否包含登录/验证表单元素
     * 支持标准登录页和手机验证页
     */
    private boolean hasFormElements(WebDriver d) {
        // 标准登录页：有 password 输入框
        if (!d.findElements(By.xpath("//input[@type='password']")).isEmpty()) {
            return true;
        }
        // 手机验证页：有"获取验证码"按钮或"手机号"输入框
        if (!d.findElements(By.xpath(
                "//*[contains(text(), '获取验证码')] | " +
                "//input[contains(@placeholder, '手机号')]"
        )).isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 递归查找包含登录表单的 iframe
     */
    private boolean findLoginFrame(WebDriver d, int depth) {
        if (depth > 3) {
            return false;
        }

        List<WebElement> iframes = d.findElements(By.tagName("iframe"));
        log.debug("第 {} 层，找到 iframe 数量：{}", depth, iframes.size());

        for (int i = 0; i < iframes.size(); i++) {
            try {
                d.switchTo().defaultContent();
                // 如果是多层嵌套，需要逐层切入
                d.switchTo().frame(i);

                if (hasFormElements(d)) {
                    log.info("在第{}层第{}个 iframe 中找到登录表单", depth, i);
                    return true;
                }

                // 递归子 iframe
                if (findLoginFrame(d, depth + 1)) {
                    return true;
                }

            } catch (Exception e) {
                log.debug("切换到第{}个 iframe 失败：{}", i, e.getMessage());
                d.switchTo().defaultContent();
            }
        }

        d.switchTo().defaultContent();
        return false;
    }

    /**
     * 模拟人类打字（80-140ms/字符）
     * 先尝试 Selenium 原生方式，失败则用 JS 方式
     */
    public void humanLikeInput(WebElement element, String text) {
        // 策略 1：Selenium 原生方式
        try {
            element.clear();
            for (char c : text.toCharArray()) {
                element.sendKeys(String.valueOf(c));
                Thread.sleep(ThreadLocalRandom.current().nextInt(80, 141));
            }
            log.debug("模拟人类输入完成（Selenium），字符数={}", text.length());
            return;
        } catch (Exception e) {
            log.warn("Selenium 输入失败，改用 JS: {}", e.getMessage());
        }

        // 策略 2：JS + jQuery 方式（兼容 readonly/hidden 场景，确保 jQuery 事件同步）
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript(
                    "arguments[0].value = ''; arguments[0].focus();" +
                    "arguments[0].value = arguments[1];" +
                    "arguments[0].dispatchEvent(new Event('input', {bubbles: true}));" +
                    "arguments[0].dispatchEvent(new Event('change', {bubbles: true}));" +
                    "if(typeof jQuery !== 'undefined') {" +
                    "  jQuery(arguments[0]).val(arguments[1]).trigger('input').trigger('change');" +
                    "}",
                    element, text
            );
            log.debug("模拟人类输入完成（JS+jQuery），字符数={}", text.length());
        } catch (Exception e) {
            throw new BrowserException("输入失败（Selenium+JS 均失败）：" + e.getMessage(), e);
        }
    }

    /**
     * 安全点击元素（带重试）
     */
    public void safeClick(WebElement element) {
        for (int i = 0; i < 3; i++) {
            try {
                element.click();
                log.debug("点击成功");
                return;
            } catch (Exception e) {
                log.warn("第 {} 次点击失败：{}", i + 1, e.getMessage());
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new BrowserException("点击失败：已达最大重试次数");
    }

    @Override
    public void destroy() {
        quitBrowser();
    }
}
