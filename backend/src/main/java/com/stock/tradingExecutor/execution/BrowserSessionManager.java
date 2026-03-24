package com.stock.tradingExecutor.execution;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * 常驻浏览器会话管理：与自动登录、Cookie 读取共用同一 {@link WebDriver}，避免多实例导致 Token 与操作不一致。
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Component
public class BrowserSessionManager {

    private static final String LOGIN_URL = "https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html";
    /**
     * 远程 Selenium Grid / standalone-chrome 地址，例如 http://chrome:4444/wd/hub；为空则使用本地 Chrome。
     */
    @Value("${chrome.remote.url:}")
    private String chromeRemoteUrl;

    /**
     * 是否在本地启动无头 Chrome（远程模式下忽略）。
     */
    @Value("${chrome.headless:false}")
    private boolean chromeHeadless;

    /**
     * 短信验证码、图片验证码人工介入文件的目录，默认项目根下 .tmp。
     */
    @Value("${stock.auto-login.tmp-dir:}")
    private String configuredTmpDir;

    private WebDriver driver;
    private WebDriverWait wait;
    private Actions actions;
    private final Random random = new Random();

    private boolean isRunning = false;
    private String currentUsername;
    private String currentPassword;
    private String currentPhoneCode;

    public synchronized void startBrowser() {
        if (isRunning && driver != null) {
            log.info("[BrowserSession] 浏览器已在运行中，复用现有会话");
            return;
        }
        // 确保关闭之前残留的 Selenium 会话（仅关闭自己创建的 driver）
        if (driver != null) {
            try {
                driver.quit();
                log.info("[BrowserSession] 已关闭残留的旧浏览器会话");
            } catch (Exception ignored) {
            }
            driver = null;
            isRunning = false;
        }

        log.info("[BrowserSession] 启动 Chrome...");
        ChromeOptions options = createChromeOptions();

        try {
            if (chromeRemoteUrl != null && !chromeRemoteUrl.isBlank()) {
                URL gridUrl = new URL(chromeRemoteUrl.trim());
                driver = new RemoteWebDriver(gridUrl, options);
                log.info("[BrowserSession] 已连接远程 Chrome: {}", gridUrl);
            } else {
                if (chromeHeadless) {
                    options.addArguments("--headless=new");
                    options.addArguments("--window-size=1920,1080");
                }
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(options);
                log.info("[BrowserSession] 本地 Chrome 启动成功, headless={}", chromeHeadless);
            }
            wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            actions = new Actions(driver);
            isRunning = true;
        } catch (MalformedURLException e) {
            log.error("[BrowserSession] chrome.remote.url 无效: {}", e.getMessage());
            throw new RuntimeException("chrome.remote.url 配置无效", e);
        } catch (Exception e) {
            log.error("[BrowserSession] Chrome 启动失败: {}", e.getMessage());
            throw new RuntimeException("Chrome启动失败", e);
        }
    }

    /**
     * 人工介入文件所在目录（短信码、图片验证码等），便于测试与部署统一配置。
     *
     * @return 已确保存在的绝对路径
     */
    public Path getAutoLoginTmpDir() {
        return resolveTmpDirPath();
    }

    private Path resolveTmpDirPath() {
        try {
            String base = (configuredTmpDir != null && !configuredTmpDir.isBlank())
                    ? configuredTmpDir
                    : Paths.get(System.getProperty("user.dir"), ".tmp").toString();
            Path p = Paths.get(base).toAbsolutePath().normalize();
            Files.createDirectories(p);
            return p;
        } catch (Exception e) {
            throw new IllegalStateException("无法创建自动登录临时目录", e);
        }
    }

    private Path smsCodeFilePath() {
        return resolveTmpDirPath().resolve("sms_code.txt");
    }

    private Path captchaCodeFilePath() {
        return resolveTmpDirPath().resolve("captcha_code.txt");
    }

    /**
     * 模拟人工间隔等待（秒），供登录重试、点击前后使用。
     *
     * @param minSeconds 最小秒数（含）
     * @param maxSeconds 最大秒数（含）
     */
    public void randomWait(int minSeconds, int maxSeconds) {
        sleepRandomSeconds(minSeconds, maxSeconds);
    }

    public synchronized void quitBrowser() {
        if (driver != null) {
            try {
                // 先关闭所有窗口
                for (String handle : driver.getWindowHandles()) {
                    try {
                        driver.switchTo().window(handle);
                        driver.close();
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ignored) {
            }
            try {
                driver.quit();
                log.info("[BrowserSession] Chrome 浏览器已关闭");
            } catch (Exception e) {
                log.warn("[BrowserSession] 关闭浏览器异常: {}", e.getMessage());
            }
            driver = null;
            wait = null;
            actions = null;
            isRunning = false;
        }
    }

    public boolean isRunning() {
        return isRunning && driver != null;
    }

    public WebDriver getDriver() {
        return driver;
    }

    private ChromeOptions createChromeOptions() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--disable-gpu");
        options.addArguments("--lang=zh-CN");
        options.addArguments("--time-zone=Asia/Shanghai");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);
        return options;
    }

    /**
     * 注入浏览器指纹，隐藏 Selenium 自动化特征。
     * <p>Chrome 146+ 不允许通过 JS 重定义 navigator.webdriver，改用 CDP 命令在页面加载前注入。</p>
     */
    public void injectFingerprint() {
        if (driver == null) return;
        try {
            // 方式1：使用 CDP 命令在每个新文档加载前自动执行（推荐，兼容 Chrome 146+）
            if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver chromiumDriver) {
                chromiumDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument",
                        java.util.Map.of("source", """
                            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                            Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en-US', 'en']});
                            Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});
                            Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
                            Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});
                            Object.defineProperty(navigator, 'cookieEnabled', {get: () => true});
                            Object.defineProperty(navigator, 'onLine', {get: () => true});
                            Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 10});
                            Object.defineProperty(navigator, 'language', {get: () => 'zh-CN'});
                        """));
                log.debug("[BrowserSession] 已通过 CDP 注入浏览器指纹");
                return;
            }
        } catch (Exception e) {
            log.debug("[BrowserSession] CDP 注入失败，回退到 JS 注入: {}", e.getMessage());
        }
        // 方式2：回退到 JS 直接执行（远程 WebDriver 或旧版 Chrome）
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("""
                try { Object.defineProperty(navigator, 'webdriver', {get: () => undefined}); } catch(e) {}
                try { Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en-US', 'en']}); } catch(e) {}
                try { Object.defineProperty(navigator, 'platform', {get: () => 'Win32'}); } catch(e) {}
                try { Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8}); } catch(e) {}
                try { Object.defineProperty(navigator, 'deviceMemory', {get: () => 8}); } catch(e) {}
            """);
        } catch (Exception e) {
            log.warn("[BrowserSession] JS 指纹注入也失败: {}", e.getMessage());
        }
    }

    public void visitLoginPage() {
        log.info("[BrowserSession] 访问登录页面: {}", LOGIN_URL);
        // CDP 指纹注入必须在 driver.get() 之前，确保首次页面加载时生效
        injectFingerprint();
        driver.get(LOGIN_URL);
        waitForPageLoad();
        ensureLoginFrame();
    }

    /**
     * 切回顶层文档，便于读取同源 Cookie、执行顶层 localStorage 脚本。
     */
    public void switchToDefaultContent() {
        if (driver != null) {
            try {
                driver.switchTo().defaultContent();
            } catch (Exception e) {
                log.debug("[BrowserSession] switchToDefaultContent: {}", e.getMessage());
            }
        }
    }

    /**
     * 若登录表单在 iframe（含嵌套）内，切换至包含可见密码框的 frame；否则留在顶层。
     */
    public void ensureLoginFrame() {
        if (driver == null) {
            return;
        }
        try {
            switchToDefaultContent();
            if (searchLoginFrameRecursive(0, 8)) {
                log.debug("[BrowserSession] 已切换至含密码框的文档上下文");
            } else {
                switchToDefaultContent();
            }
        } catch (Exception e) {
            log.warn("[BrowserSession] ensureLoginFrame 异常: {}", e.getMessage());
            switchToDefaultContent();
        }
    }

    private boolean searchLoginFrameRecursive(int depth, int maxDepth) {
        if (depth > maxDepth) {
            return false;
        }
        if (hasVisiblePasswordInput()) {
            return true;
        }
        List<WebElement> frames = driver.findElements(By.tagName("iframe"));
        for (WebElement frame : frames) {
            try {
                driver.switchTo().frame(frame);
                if (searchLoginFrameRecursive(depth + 1, maxDepth)) {
                    return true;
                }
                driver.switchTo().parentFrame();
            } catch (Exception e) {
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ignored) {
                    switchToDefaultContent();
                }
            }
        }
        return false;
    }

    private boolean hasVisiblePasswordInput() {
        try {
            for (WebElement el : driver.findElements(By.cssSelector("input[type='password']"))) {
                if (el.isDisplayed()) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 手机验证页（activePhone.html）表单可能在 iframe 内：切换至含「请输入手机号」占位输入框的文档。
     */
    public void ensureActivePhoneFrame() {
        if (driver == null || !isOnActivePhonePage()) {
            return;
        }
        try {
            switchToDefaultContent();
            if (searchActivePhoneFrameRecursive(0, 8)) {
                log.debug("[BrowserSession] 已切换至手机验证表单所在 frame");
            } else {
                switchToDefaultContent();
            }
        } catch (Exception e) {
            log.warn("[BrowserSession] ensureActivePhoneFrame 异常: {}", e.getMessage());
            switchToDefaultContent();
        }
    }

    private boolean searchActivePhoneFrameRecursive(int depth, int maxDepth) {
        if (depth > maxDepth) {
            return false;
        }
        if (hasVisiblePhoneNumberInputForVerification()) {
            return true;
        }
        List<WebElement> frames = driver.findElements(By.tagName("iframe"));
        for (WebElement frame : frames) {
            try {
                driver.switchTo().frame(frame);
                if (searchActivePhoneFrameRecursive(depth + 1, maxDepth)) {
                    return true;
                }
                driver.switchTo().parentFrame();
            } catch (Exception e) {
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ignored) {
                    switchToDefaultContent();
                }
            }
        }
        return false;
    }

    private boolean hasVisiblePhoneNumberInputForVerification() {
        try {
            for (WebElement el : driver.findElements(By.cssSelector("input"))) {
                if (!el.isDisplayed()) {
                    continue;
                }
                String ph = el.getAttribute("placeholder");
                if (ph != null && ph.contains("手机号")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * 进入手机验证页后，定位 iframe 并准备操作表单。
     */
    public void prepareActivePhonePage() {
        if (!isOnActivePhonePage()) {
            return;
        }
        sleepRandomSeconds(2, 3);
        ensureActivePhoneFrame();
    }

    /**
     * 在手机验证页填写手机号（与登录账号一致）。
     *
     * @param phone 手机号
     * @return 是否成功填入可见输入框
     */
    public boolean inputPhoneNumberForVerification(String phone) {
        if (driver == null || phone == null || phone.isBlank()) {
            log.warn("[BrowserSession] 手机号为空，跳过输入");
            return false;
        }
        ensureActivePhoneFrame();
        WebElement input = findElementSafely(By.cssSelector("input[placeholder*='手机号']"));
        if (input == null) {
            for (WebElement el : driver.findElements(By.cssSelector("input[type='text'], input:not([type])"))) {
                try {
                    if (!el.isDisplayed()) {
                        continue;
                    }
                    String ph = el.getAttribute("placeholder");
                    if (ph != null && ph.contains("手机")) {
                        input = el;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (input == null) {
            log.warn("[BrowserSession] 未找到手机验证页手机号输入框");
            return false;
        }
        scrollToElement(input);
        humanType(input, phone);
        this.currentUsername = phone;
        log.info("[BrowserSession] ✓ 手机验证页已填写手机号");
        return true;
    }

    /**
     * 在当前文档中勾选第一条协议（隐私保护条款），不跳转登录 URL。
     *
     * @return 是否已勾选
     */
    public boolean checkPrivacyAgreementOnCurrentForm() {
        if (driver == null) {
            return false;
        }
        List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
        if (checkboxes.isEmpty()) {
            log.warn("[BrowserSession] 当前页未找到协议复选框");
            return false;
        }
        WebElement checkbox = checkboxes.get(0);
        scrollToElement(checkbox);
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
        log.info("[BrowserSession] ✓ 隐私条款已勾选（当前表单）");
        return checkbox.isSelected();
    }

    /**
     * 在当前文档中勾选第二条协议（授权书）。
     *
     * @return 是否已勾选
     */
    public boolean checkAuthAgreementOnCurrentForm() {
        if (driver == null) {
            return false;
        }
        List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
        if (checkboxes.size() < 2) {
            log.warn("[BrowserSession] 当前页未找到第二条协议复选框");
            return false;
        }
        WebElement checkbox = checkboxes.get(1);
        scrollToElement(checkbox);
        if (!checkbox.isSelected()) {
            checkbox.click();
        }
        log.info("[BrowserSession] ✓ 授权书已勾选（当前表单）");
        return checkbox.isSelected();
    }

    public String getCurrentUrl() {
        return driver != null ? driver.getCurrentUrl() : "";
    }

    public String getPageTitle() {
        return driver != null ? driver.getTitle() : "";
    }

    private void waitForPageLoad() {
        sleepRandomSeconds(3, 5);
    }

    /**
     * 检测当前是否在手机验证页面。
     * <p>SPA 路由可能不更新 URL hash，因此同时检测 URL 和 DOM 内容。</p>
     */
    public boolean isOnActivePhonePage() {
        if (getCurrentUrl().contains("activePhone.html")) {
            return true;
        }
        // SPA 情况下 URL 可能仍显示 login.html，但 DOM 内容已切换到手机验证页
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Boolean result = (Boolean) js.executeScript("""
                var inputs = document.querySelectorAll('input');
                for (var i = 0; i < inputs.length; i++) {
                    var ph = inputs[i].placeholder || '';
                    if (ph.includes('手机号') || ph.includes('手机验证码')) return true;
                }
                var texts = document.body ? document.body.innerText : '';
                return texts.includes('手机验证') && texts.includes('获取验证码');
            """);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检测当前是否在标准登录页面（非手机验证页）。
     * <p>通过检测 DOM 中是否存在「资金账号」或「交易密码」输入框来判断。</p>
     */
    public boolean isOnLoginPage() {
        if (driver == null) return false;
        // 先排除手机验证页
        if (isOnActivePhonePage()) return false;
        if (getCurrentUrl().contains("login.html")) {
            // 进一步确认 DOM 中有登录表单元素
            try {
                JavascriptExecutor js = (JavascriptExecutor) driver;
                Boolean result = (Boolean) js.executeScript("""
                    var inputs = document.querySelectorAll('input');
                    for (var i = 0; i < inputs.length; i++) {
                        var ph = inputs[i].placeholder || '';
                        if (ph.includes('资金账号') || ph.includes('交易密码') || inputs[i].type === 'password') return true;
                    }
                    return false;
                """);
                return Boolean.TRUE.equals(result);
            } catch (Exception e) {
                return true; // URL 匹配时默认认为是登录页
            }
        }
        return false;
    }

    /**
     * 分析当前页面结构，打印所有输入框、按钮、图片信息用于调试。
     *
     * @return 页面结构分析结果的 JSON 字符串
     */
    public String analyzePageStructure() {
        log.info("[分析] === 页面结构分析 ===");
        log.info("[分析] 当前URL: {}", getCurrentUrl());

        JavascriptExecutor js = (JavascriptExecutor) driver;
        String script = """
                (function() {
                    var result = {inputs: [], buttons: [], images: [], iframes: 0};
                    result.iframes = document.querySelectorAll('iframe').length;
                    document.querySelectorAll('input').forEach(function(el, i) {
                        result.inputs.push({
                            index: i, type: el.type || '', placeholder: el.placeholder || '',
                            name: el.name || '', id: el.id || '',
                            displayed: el.offsetParent !== null, width: el.offsetWidth, height: el.offsetHeight
                        });
                    });
                    document.querySelectorAll('button, div[class*="btn"], a[class*="btn"]').forEach(function(el, i) {
                        var text = (el.textContent || '').trim().substring(0, 30);
                        if (text) result.buttons.push({index: i, tag: el.tagName, text: text, displayed: el.offsetParent !== null});
                    });
                    document.querySelectorAll('img').forEach(function(el, i) {
                        result.images.push({
                            index: i, src: (el.src || '').substring(0, 80),
                            width: el.naturalWidth || el.offsetWidth, height: el.naturalHeight || el.offsetHeight,
                            displayed: el.offsetParent !== null
                        });
                    });
                    return JSON.stringify(result);
                })()
                """;
        try {
            String result = (String) js.executeScript(script);
            log.info("[分析] 页面元素: {}", result);
            return result;
        } catch (Exception e) {
            log.error("[分析] 分析失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 点击"获取验证码"按钮。
     * <p>使用 JS 定位按钮坐标后，通过 Selenium Actions 执行物理级别点击（而非 JS 事件派发），
     * 确保 Angular/Vue 等框架能正确响应，触发滑块验证弹出。</p>
     */
    public boolean clickSendCodeButton() {
        log.info("[BrowserSession] 查找发送验证码按钮...");
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 1. JS 获取按钮在页面视口中的坐标
            String script = """
                var all = document.querySelectorAll('button, div, span, a, p');
                var target = null;
                for (var i = 0; i < all.length; i++) {
                    var text = (all[i].textContent || all[i].innerText || '').trim();
                    if ((text.includes('获取验证码') || text.includes('发送验证码')) && all[i].children.length < 5) {
                        target = all[i];
                        break;
                    }
                }
                if (!target) return null;
                target.scrollIntoView({behavior: 'instant', block: 'center'});
                var rect = target.getBoundingClientRect();
                return JSON.stringify({text: target.textContent.trim().substring(0, 30), tag: target.tagName,
                    x: Math.round(rect.left + rect.width / 2),
                    y: Math.round(rect.top + rect.height / 2)});
            """;
            String result = (String) js.executeScript(script);
            if (result == null) {
                log.warn("[BrowserSession] 未找到发送验证码按钮");
                return false;
            }
            log.info("[BrowserSession] 定位到发送验证码按钮: {}", result);

            // 2. 仅 CDP 物理点击
            com.alibaba.fastjson2.JSONObject info = com.alibaba.fastjson2.JSONObject.parseObject(result);
            int x = info.getIntValue("x");
            int y = info.getIntValue("y");
            if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver cd && x > 0 && y > 0) {
                cdpClick(cd, x, y);
                log.info("[BrowserSession] ✓ CDP 物理点击发送验证码按钮 ({}, {})", x, y);
            } else {
                log.warn("[BrowserSession] 非 Chromium 驱动，无法使用 CDP");
                return false;
            }

            sleepRandomSeconds(2, 3);
            return true;
        } catch (Exception e) {
            log.error("[BrowserSession] 点击发送验证码按钮失败: {}", e.getMessage());
            return false;
        }
    }

    public String waitForSmsCode() {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║           【人工介入 Required】                                 ║");
        log.info("║                                                                ║");
        log.info("║  请查收短信验证码，并将验证码写入以下文件：                      ║");
        log.info("║                                                                ║");
        log.info("║  文件路径: {}  ", smsCodeFilePath());
        log.info("║                                                                ║");
        log.info("║  示例命令: echo 123456 > \"{}\"  ", smsCodeFilePath());
        log.info("║                                                                ║");
        log.info("║  验证码文件格式：只包含6位数字，如：123456                      ║");
        log.info("║                                                                ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
        log.info("");

        try {
            int waitSeconds = 0;
            int maxWaitSeconds = 300;

            while (waitSeconds < maxWaitSeconds) {
                Path path = smsCodeFilePath();
                if (Files.exists(path)) {
                    String code = Files.readString(path).trim();
                    if (code != null && code.length() == 6 && code.matches("\\d{6}")) {
                        log.info("[BrowserSession] ✓ 检测到验证码: {}", code);
                        return code;
                    }
                }

                if (waitSeconds % 10 == 0 && waitSeconds > 0) {
                    log.info("[BrowserSession] 等待验证码输入... 已等待 {} 秒", waitSeconds);
                }

                Thread.sleep(1000);
                waitSeconds++;
            }

            log.warn("[BrowserSession] ⚠ 等待验证码超时（{}秒）", maxWaitSeconds);
        } catch (Exception e) {
            log.error("[BrowserSession] 等待验证码异常: {}", e.getMessage());
        }
        return null;
    }

    public boolean inputSmsCode(String code) {
        // 1. 优先匹配: "请输入手机验证码"
        WebElement input = findElementSafely(By.cssSelector("input[placeholder*='手机验证码']"));
        // 2. 备选: placeholder 含 "验证码"（手机验证页上下文）
        if (input == null) {
            input = findElementSafely(By.cssSelector("input[placeholder*='验证码']"));
        }
        // 3. 最后回退
        if (input == null) {
            input = findElementSafely(By.xpath("//input[@type='text']"));
        }

        if (input == null) {
            log.warn("[BrowserSession] 未找到短信验证码输入框");
            return false;
        }

        scrollToElement(input);
        humanType(input, code);
        log.info("[BrowserSession] ✓ 短信验证码输入成功: {}", code);
        return true;
    }

    public boolean submitPhoneVerification() {
        log.info("[BrowserSession] 查找「下一步」/确认按钮...");
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = """
                var all = document.querySelectorAll('button, div, a');
                for (var i = 0; i < all.length; i++) {
                    var text = (all[i].textContent || all[i].innerText || '').trim();
                    if (text.includes('下一步') || text === '确认' || text === '验证' || text === '提交') {
                        all[i].scrollIntoView({behavior: 'smooth', block: 'center'});
                        all[i].click();
                        return 'clicked: ' + text;
                    }
                }
                return null;
            """;
            String result = (String) js.executeScript(script);
            if (result != null) {
                log.info("[BrowserSession] ✓ {}", result);
                sleepRandomSeconds(3, 5);

                if (isOnLoginPage()) {
                    log.info("[BrowserSession] ✓ 成功跳转回登录页面");
                    return true;
                } else {
                    log.warn("[BrowserSession] ⚠ 尚未跳转回登录页面，当前URL: {}", getCurrentUrl());
                    return false;
                }
            }
            log.warn("[BrowserSession] 未找到下一步/确认按钮");
            return false;
        } catch (Exception e) {
            log.error("[BrowserSession] 提交手机验证失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean ensureOnLoginPage() {
        if (!isOnLoginPage()) {
            log.info("[BrowserSession] 导航回登录页面...");
            injectFingerprint();
            driver.get(LOGIN_URL);
            waitForPageLoad();
        }
        ensureLoginFrame();
        return true;
    }

    /**
     * 智能输入账号/手机号。
     * <p>使用 CDP 点击输入框聚焦 + CDP 键盘逐字输入，确保 Angular ng-model 正确绑定。</p>
     *
     * @param account 账号或手机号
     * @return 是否输入成功
     */
    public boolean inputAccount(String account) {
        if (driver == null) return false;
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 1. JS 获取第一个可见文本输入框的坐标
            String script = """
                var inputs = document.querySelectorAll('input');
                var target = null;
                var keywords = ['资金账号', '手机号', '账号'];
                for (var k = 0; k < keywords.length; k++) {
                    for (var i = 0; i < inputs.length; i++) {
                        var ph = inputs[i].placeholder || '';
                        if (ph.includes(keywords[k]) && inputs[i].offsetParent !== null) { target = inputs[i]; break; }
                    }
                    if (target) break;
                }
                if (!target) {
                    for (var i = 0; i < inputs.length; i++) {
                        var t = inputs[i].type || 'text';
                        if ((t === 'text' || t === 'tel' || t === 'number') && inputs[i].offsetParent !== null) { target = inputs[i]; break; }
                    }
                }
                if (!target) return null;
                target.scrollIntoView({block: 'center'});
                var rect = target.getBoundingClientRect();
                return JSON.stringify({ph: target.placeholder || '', x: Math.round(rect.left + 50), y: Math.round(rect.top + rect.height / 2)});
            """;
            String info = (String) js.executeScript(script);
            if (info == null) {
                log.warn("[BrowserSession] 未找到账号/手机号输入框");
                return false;
            }
            com.alibaba.fastjson2.JSONObject pos = com.alibaba.fastjson2.JSONObject.parseObject(info);
            log.info("[BrowserSession] 找到输入框: {}", info);

            if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver cd) {
                // 2. CDP 三击输入框全选内容
                int ix = pos.getIntValue("x");
                int iy = pos.getIntValue("y");
                cdpClick(cd, ix, iy);
                Thread.sleep(200);
                cdpClick(cd, ix, iy);
                Thread.sleep(50);
                cdpClick(cd, ix, iy);
                Thread.sleep(200);
                // 3. 删除选中内容
                cd.executeCdpCommand("Input.dispatchKeyEvent",
                        java.util.Map.of("type", "rawKeyDown", "windowsVirtualKeyCode", 8, "nativeVirtualKeyCode", 8, "key", "Backspace"));
                cd.executeCdpCommand("Input.dispatchKeyEvent",
                        java.util.Map.of("type", "keyUp", "windowsVirtualKeyCode", 8, "nativeVirtualKeyCode", 8, "key", "Backspace"));
                Thread.sleep(200);
                // 4. CDP insertText 一次性插入（类似粘贴，Angular 能正确感知）
                cd.executeCdpCommand("Input.insertText", java.util.Map.of("text", account));
                Thread.sleep(100);
                // 5. 触发 Tab 让 Angular blur 事件生效
                cd.executeCdpCommand("Input.dispatchKeyEvent",
                        java.util.Map.of("type", "rawKeyDown", "windowsVirtualKeyCode", 9, "key", "Tab"));
                cd.executeCdpCommand("Input.dispatchKeyEvent",
                        java.util.Map.of("type", "keyUp", "windowsVirtualKeyCode", 9, "key", "Tab"));
            }
            this.currentUsername = account;
            log.info("[BrowserSession] ✓ 账号/手机号 CDP 键盘输入成功: {}", account);
            return true;
        } catch (Exception e) {
            log.error("[BrowserSession] 账号输入失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean inputPassword(String password) {
        // 仅在登录页调用 ensureOnLoginPage，手机验证页不需要
        if (isOnLoginPage()) ensureOnLoginPage();
        WebElement input = findElementSafely(By.cssSelector("input[type='password']"));
        if (input == null) {
            log.warn("[BrowserSession] 未找到密码输入框");
            return false;
        }

        scrollToElement(input);
        humanType(input, password);
        this.currentPassword = password;
        log.info("[BrowserSession] ✓ 密码输入成功");
        return true;
    }

    public String captureCaptchaImage() {
        // 不强制导航，截取当前页面截图
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 1. 用 JS 查找验证码图片的 src（在当前 frame 上下文中执行）
            String script = """
                var imgs = document.querySelectorAll('img');
                var result = [];
                for (var i = 0; i < imgs.length; i++) {
                    var img = imgs[i];
                    var w = img.naturalWidth || img.offsetWidth;
                    var h = img.naturalHeight || img.offsetHeight;
                    var src = img.src || '';
                    result.push({src: src.substring(0, 200), w: w, h: h, visible: img.offsetParent !== null});
                }
                return JSON.stringify(result);
            """;
            String imgInfoJson = (String) js.executeScript(script);
            log.info("[BrowserSession] 页面图片列表: {}", imgInfoJson);

            // 2. 全页面截图保存
            Path tmp = resolveTmpDirPath();
            String captchaPath = tmp.resolve("captcha_" + System.currentTimeMillis() + ".png").toString();
            File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            java.nio.file.Files.copy(screenshot.toPath(), new File(captchaPath).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            log.info("[BrowserSession] ✓ 全页面截图已保存: {}", captchaPath);
            return captchaPath;
        } catch (Exception e) {
            log.error("[BrowserSession] 截取验证码图片失败: {}", e.getMessage());
            return null;
        }
    }

    public String waitForCaptchaCode() {
        return waitForCaptchaCode(300);
    }

    /**
     * 等待人工将图片验证码写入 {@code captcha_code.txt}，最长等待指定秒数。
     *
     * @param maxWaitSeconds 最长等待秒数
     * @return 识别到的验证码；超时为 null
     */
    public String waitForCaptchaCode(int maxWaitSeconds) {
        log.info("");
        log.info("╔════════════════════════════════════════════════════════════════╗");
        log.info("║           【人工介入 Required】                                 ║");
        log.info("║                                                                ║");
        log.info("║  请查看上一步保存的验证码图片，手动识别后写入验证码：           ║");
        log.info("║                                                                ║");
        log.info("║  图片目录: {}  ", resolveTmpDirPath());
        log.info("║                                                                ║");
        log.info("║  验证码写入文件: {}  ", captchaCodeFilePath());
        log.info("║                                                                ║");
        log.info("╚════════════════════════════════════════════════════════════════╝");
        log.info("");

        try {
            int waitSeconds = 0;

            while (waitSeconds < maxWaitSeconds) {
                Path path = captchaCodeFilePath();
                if (Files.exists(path)) {
                    String code = Files.readString(path).trim();
                    if (code != null && code.length() >= 4) {
                        log.info("[BrowserSession] ✓ 检测到图片验证码: {}", code);
                        return code;
                    }
                }

                if (waitSeconds % 10 == 0 && waitSeconds > 0) {
                    log.info("[BrowserSession] 等待图片验证码输入... 已等待 {} 秒", waitSeconds);
                }

                Thread.sleep(1000);
                waitSeconds++;
            }
        } catch (Exception e) {
            log.error("[BrowserSession] 等待图片验证码异常: {}", e.getMessage());
        }
        return null;
    }

    public boolean inputCaptcha(String captcha) {
        // 1. 优先匹配: "请输入四则运算的运算结果"
        WebElement input = findElementSafely(By.cssSelector("input[placeholder*='运算']"));
        // 2. 备选: placeholder 含 "结果"
        if (input == null) {
            input = findElementSafely(By.cssSelector("input[placeholder*='结果']"));
        }
        // 3. 备选: placeholder 含 "验证码"
        if (input == null) {
            input = findElementSafely(By.cssSelector("input[placeholder*='验证码']"));
        }
        if (input == null) {
            log.warn("[BrowserSession] 未找到验证码输入框");
            return false;
        }

        scrollToElement(input);
        humanType(input, captcha);
        this.currentPhoneCode = captcha;
        log.info("[BrowserSession] ✓ 验证码输入成功: {}", captcha);
        return true;
    }

    public boolean checkPrivacyAgreement() {
        // 不再调用 ensureOnLoginPage()，避免在手机验证页时被导航到登录页导致状态丢失
        return jsCheckAllAgreements();
    }

    public boolean checkAuthAgreement() {
        // 已在 checkPrivacyAgreement 中一并处理所有 checkbox
        return true;
    }

    /**
     * 通过 CDP 物理点击勾选所有协议 checkbox。
     * <p>checkbox 本身可能被 CSS 隐藏（width/height=0），实际可点击区域是其父容器或相邻 label。
     * 因此查找 checkbox 的父元素或包含"勾选"文字的容器来获取可点击坐标。</p>
     */
    private boolean jsCheckAllAgreements() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 获取所有协议勾选区域的坐标（checkbox 本身、父元素或 label）
            String script = """
                var checkboxes = document.querySelectorAll('input[type="checkbox"]');
                var result = [];
                for (var i = 0; i < checkboxes.length; i++) {
                    var cb = checkboxes[i];
                    if (cb.checked) continue;
                    var rect = cb.getBoundingClientRect();
                    // 如果 checkbox 自身有可见尺寸，直接用它
                    if (rect.width >= 5 && rect.height >= 5) {
                        result.push({x: Math.round(rect.left + rect.width / 2), y: Math.round(rect.top + rect.height / 2),
                            src: 'checkbox', w: Math.round(rect.width), h: Math.round(rect.height)});
                        continue;
                    }
                    // 否则用父容器（通常是 label 或包含 checkbox 的 div）
                    var parent = cb.parentElement;
                    if (parent) {
                        rect = parent.getBoundingClientRect();
                        if (rect.width > 0 && rect.height > 0) {
                            result.push({x: Math.round(rect.left + Math.min(rect.width, 30) / 2),
                                y: Math.round(rect.top + rect.height / 2),
                                src: 'parent:' + parent.tagName, w: Math.round(rect.width), h: Math.round(rect.height)});
                            continue;
                        }
                    }
                    // 最后回退：用 for 属性关联的 label
                    var id = cb.id;
                    if (id) {
                        var label = document.querySelector('label[for="' + id + '"]');
                        if (label) {
                            rect = label.getBoundingClientRect();
                            result.push({x: Math.round(rect.left + 10), y: Math.round(rect.top + rect.height / 2),
                                src: 'label', w: Math.round(rect.width), h: Math.round(rect.height)});
                        }
                    }
                }
                return JSON.stringify(result);
            """;
            String coordsJson = (String) js.executeScript(script);
            log.info("[BrowserSession] checkbox 坐标诊断: {}", coordsJson);

            @SuppressWarnings("unchecked")
            java.util.List<com.alibaba.fastjson2.JSONObject> coords =
                    com.alibaba.fastjson2.JSON.parseArray(coordsJson, com.alibaba.fastjson2.JSONObject.class);

            if (coords == null || coords.isEmpty()) {
                log.info("[BrowserSession] 所有协议已勾选或未找到 checkbox");
                return true;
            }

            if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver cd) {
                for (com.alibaba.fastjson2.JSONObject coord : coords) {
                    int x = coord.getIntValue("x");
                    int y = coord.getIntValue("y");
                    cdpClick(cd, x, y);
                    log.info("[BrowserSession] ✓ CDP 点击 checkbox ({}, {}) via {}", x, y, coord.getString("src"));
                    Thread.sleep(300);
                }
            }
            log.info("[BrowserSession] ✓ 协议勾选完成，点击了 {} 个", coords.size());
            return true;
        } catch (Exception e) {
            log.error("[BrowserSession] 协议勾选失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * CDP 级别的鼠标点击（mousePressed + mouseReleased），模拟真实用户点击。
     *
     * @param cd ChromiumDriver
     * @param x  视口 X 坐标
     * @param y  视口 Y 坐标
     */
    private void cdpClick(org.openqa.selenium.chromium.ChromiumDriver cd, int x, int y) throws InterruptedException {
        cd.executeCdpCommand("Input.dispatchMouseEvent",
                java.util.Map.of("type", "mousePressed", "x", x, "y", y, "button", "left", "clickCount", 1));
        Thread.sleep(50 + random.nextInt(50));
        cd.executeCdpCommand("Input.dispatchMouseEvent",
                java.util.Map.of("type", "mouseReleased", "x", x, "y", y, "button", "left", "clickCount", 1));
    }

    /**
     * 点击登录/下一步按钮。
     * <p>仅使用 CDP 物理点击（不使用 JS click/Angular triggerHandler，避免触发错误行为）。
     * 先用 JS 获取按钮坐标，再用 CDP Input.dispatchMouseEvent 执行浏览器级别点击。</p>
     */
    public boolean clickLoginButton() {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 1. JS 仅获取按钮坐标（不触发任何点击）
            String script = """
                var buttons = document.querySelectorAll('button, div[class*="btn"], a[class*="btn"]');
                var target = null;
                for (var i = 0; i < buttons.length; i++) {
                    var text = (buttons[i].textContent || buttons[i].innerText || '').trim();
                    if ((text.includes('登') && text.includes('录')) || text === '下一步') {
                        target = buttons[i];
                        break;
                    }
                }
                if (!target) return null;
                target.scrollIntoView({behavior: 'instant', block: 'center'});
                var rect = target.getBoundingClientRect();
                return JSON.stringify({text: target.textContent.trim().substring(0, 20),
                    x: Math.round(rect.left + rect.width / 2), y: Math.round(rect.top + rect.height / 2)});
            """;
            String result = (String) js.executeScript(script);
            if (result == null) {
                log.warn("[BrowserSession] 未找到登录/下一步按钮");
                return false;
            }
            log.info("[BrowserSession] 定位到登录按钮: {}", result);

            // 2. 仅 CDP 物理点击
            com.alibaba.fastjson2.JSONObject info = com.alibaba.fastjson2.JSONObject.parseObject(result);
            int x = info.getIntValue("x");
            int y = info.getIntValue("y");
            if (driver instanceof org.openqa.selenium.chromium.ChromiumDriver cd && x > 0 && y > 0) {
                cd.executeCdpCommand("Input.dispatchMouseEvent",
                        java.util.Map.of("type", "mousePressed", "x", x, "y", y, "button", "left", "clickCount", 1));
                Thread.sleep(80 + random.nextInt(50));
                cd.executeCdpCommand("Input.dispatchMouseEvent",
                        java.util.Map.of("type", "mouseReleased", "x", x, "y", y, "button", "left", "clickCount", 1));
                log.info("[BrowserSession] ✓ CDP 物理点击登录按钮 ({}, {})", x, y);
            } else {
                // 非 Chromium 回退
                js.executeScript("""
                    var buttons = document.querySelectorAll('button, div[class*="btn"]');
                    for (var i = 0; i < buttons.length; i++) {
                        var text = (buttons[i].textContent || '').trim();
                        if (text.includes('登') && text.includes('录')) { buttons[i].click(); break; }
                    }
                """);
            }
            sleepRandomSeconds(3, 5);
            return true;
        } catch (Exception e) {
            log.error("[BrowserSession] 点击登录按钮失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean isLoginSuccess() {
        String url = getCurrentUrl();
        return !url.contains("login.html") && !url.contains("activePhone.html") && !url.contains("captcha");
    }

    /**
     * 获取当前所在 iframe 相对于顶层文档的偏移量。
     * <p>需在 switchToDefaultContent() 后调用。遍历所有 iframe 找到之前所在的 frame。</p>
     *
     * @param js JavascriptExecutor
     * @return [offsetX, offsetY]；非 iframe 页面返回 null
     */
    private Long[] getIframeOffset(JavascriptExecutor js) {
        try {
            // 在顶层文档中查找所有可见的 iframe，返回第一个可见 iframe 的偏移
            @SuppressWarnings("unchecked")
            java.util.List<Object> offsets = (java.util.List<Object>) js.executeScript("""
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    var rect = iframes[i].getBoundingClientRect();
                    if (rect.width > 100 && rect.height > 100) {
                        return [Math.round(rect.left), Math.round(rect.top)];
                    }
                }
                return null;
            """);
            if (offsets != null && offsets.size() == 2) {
                return new Long[]{(Long) offsets.get(0), (Long) offsets.get(1)};
            }
        } catch (Exception e) {
            log.debug("[BrowserSession] 获取 iframe 偏移失败: {}", e.getMessage());
        }
        return null;
    }

    private WebElement findVisibleElement(By[] locators) {
        for (By locator : locators) {
            try {
                List<WebElement> elements = driver.findElements(locator);
                for (WebElement el : elements) {
                    if (el.isDisplayed() && el.isEnabled()) {
                        return el;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private WebElement findElementSafely(By by) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            return wait.until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (Exception e) {
            return null;
        }
    }

    private void scrollToElement(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                element
            );
            sleepRandomSeconds(0, 1);
        } catch (Exception ignored) {}
    }

    private void humanType(WebElement element, String text) {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // 先用 JS 聚焦并清空
        try {
            js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus(); arguments[0].value = '';", element);
            Thread.sleep(200);
        } catch (Exception ignored) {
        }
        // 尝试 sendKeys 逐字符输入
        try {
            element.clear();
            for (int i = 0; i < text.length(); i++) {
                element.sendKeys(String.valueOf(text.charAt(i)));
                Thread.sleep(50 + random.nextInt(50));
            }
            return;
        } catch (Exception e) {
            log.debug("[BrowserSession] sendKeys 失败，回退到 JS 输入: {}", e.getMessage());
        }
        // 回退：使用 JS 逐字符设置值并触发事件
        try {
            js.executeScript("arguments[0].value = '';", element);
            for (int i = 0; i < text.length(); i++) {
                String current = text.substring(0, i + 1);
                js.executeScript(
                        "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));",
                        element, current);
                Thread.sleep(50 + random.nextInt(50));
            }
            // 触发 change 事件确保框架感知
            js.executeScript("arguments[0].dispatchEvent(new Event('change', {bubbles:true}));", element);
        } catch (Exception ex) {
            log.error("[BrowserSession] JS 输入也失败: {}", ex.getMessage());
        }
    }

    private void sleepRandomSeconds(int minSeconds, int maxSeconds) {
        try {
            int span = Math.max(0, maxSeconds - minSeconds);
            int delay = minSeconds * 1000 + (span > 0 ? random.nextInt(span * 1000) : 0);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}