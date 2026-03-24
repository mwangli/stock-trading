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
                driver.quit();
                log.info("[BrowserSession] Chrome浏览器已关闭");
            } catch (Exception e) {
                log.warn("[BrowserSession] 关闭浏览器异常: {}", e.getMessage());
            }
            driver = null;
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

    public void injectFingerprint() {
        if (driver == null) return;
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("""
            Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
            Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en-US', 'en']});
            Object.defineProperty(navigator, 'platform', {get: () => 'Win32'});
            Object.defineProperty(navigator, 'hardwareConcurrency', {get: () => 8});
            Object.defineProperty(navigator, 'deviceMemory', {get: () => 8});
            Object.defineProperty(navigator, 'cookieEnabled', {get: () => true});
            Object.defineProperty(navigator, 'onLine', {get: () => true});
            Object.defineProperty(navigator, 'maxTouchPoints', {get: () => 10});
            Object.defineProperty(navigator, 'language', {get: () => 'zh-CN'});
        """);
    }

    public void visitLoginPage() {
        log.info("[BrowserSession] 访问登录页面: {}", LOGIN_URL);
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

    public boolean isOnActivePhonePage() {
        return getCurrentUrl().contains("activePhone.html");
    }

    public boolean isOnLoginPage() {
        return getCurrentUrl().contains("login.html");
    }

    public void analyzePageStructure() {
        log.info("[分析] === 页面结构分析 ===");
        log.info("[分析] 当前URL: {}", getCurrentUrl());

        int iframes = driver.findElements(By.tagName("iframe")).size();
        int inputs = driver.findElements(By.tagName("input")).size();
        int buttons = driver.findElements(By.tagName("button")).size();
        int images = driver.findElements(By.tagName("img")).size();
        int divs = driver.findElements(By.tagName("div")).size();

        log.info("[分析] iframe: {}, input: {}, button: {}, img: {}, div: {}", iframes, inputs, buttons, images, divs);

        log.info("[分析] 打印所有可见按钮:");
        List<WebElement> allButtons = driver.findElements(By.tagName("button"));
        for (int i = 0; i < allButtons.size(); i++) {
            WebElement btn = allButtons.get(i);
            log.info("[分析]   button[{}]: text='{}', displayed={}, enabled={}", i, btn.getText(), btn.isDisplayed(), btn.isEnabled());
        }
        log.info("[分析] ======================");
    }

    public boolean clickSendCodeButton() {
        log.info("[BrowserSession] 查找发送验证码按钮...");

        By[] locators = {
                By.xpath("//button[contains(text(),'点击获取验证码')]"),
                By.xpath("//button[contains(text(),'获取验证码')]"),
                By.xpath("//button[contains(text(),'发送验证码')]"),
                By.xpath("//span[contains(text(),'点击获取验证码')]"),
                By.xpath("//div[contains(text(),'点击获取验证码')]"),
                By.xpath("//button[contains(text(),'获取')]"),
                By.xpath("//button[contains(text(),'发送')]"),
                By.xpath("//div[contains(text(),'获取验证码')]"),
                By.xpath("//span[contains(text(),'获取验证码')]")
        };

        WebElement button = findVisibleElement(locators);
        if (button == null) {
            log.warn("[BrowserSession] 未找到发送验证码按钮");
            analyzePageStructure();
            return false;
        }

        scrollToElement(button);
        button.click();
        log.info("[BrowserSession] ✓ 已点击发送验证码按钮");
        sleepRandomSeconds(2, 3);
        return true;
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

        By[] locators = {
                By.xpath("//button[contains(text(),'下一步')]"),
                By.xpath("//button[contains(text(),'确认')]"),
                By.xpath("//button[contains(text(),'验证')]"),
                By.xpath("//button[contains(text(),'提交')]"),
                By.cssSelector("button[type='submit']")
        };

        WebElement button = findVisibleElement(locators);
        if (button == null) {
            log.warn("[BrowserSession] 未找到下一步/确认按钮");
            return false;
        }

        scrollToElement(button);
        button.click();
        log.info("[BrowserSession] ✓ 已点击按钮: {}", button.getText());

        sleepRandomSeconds(3, 5);

        if (isOnLoginPage()) {
            log.info("[BrowserSession] ✓ 成功跳转回登录页面");
            return true;
        } else {
            log.warn("[BrowserSession] ⚠ 尚未跳转回登录页面，当前URL: {}", getCurrentUrl());
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

    public boolean inputAccount(String account) {
        ensureOnLoginPage();
        // 1. 优先匹配实际 placeholder: "请输入资金账号"
        WebElement input = findElementSafely(By.cssSelector("input[placeholder*='资金账号']"));
        // 2. 备选：placeholder 含 "账号"
        if (input == null) {
            input = findElementSafely(By.cssSelector("input[placeholder*='账号']"));
        }
        // 3. 备选：id/name
        if (input == null) {
            input = findElementSafely(By.cssSelector("input[id='account'], input[name='account']"));
        }
        // 4. 最后回退：第一个可见的 text 输入框
        if (input == null) {
            List<WebElement> inputs = driver.findElements(By.cssSelector("input[type='text'], input:not([type])"));
            for (WebElement el : inputs) {
                try {
                    if (el.isDisplayed()) {
                        input = el;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
        }

        if (input == null) {
            log.warn("[BrowserSession] 未找到账号输入框");
            return false;
        }

        scrollToElement(input);
        humanType(input, account);
        this.currentUsername = account;
        log.info("[BrowserSession] ✓ 账号输入成功: {}", account);
        return true;
    }

    public boolean inputPassword(String password) {
        ensureOnLoginPage();
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
        ensureOnLoginPage();

        List<WebElement> images = driver.findElements(By.cssSelector("img"));
        WebElement captchaImg = null;

        // 1. 优先查找验证码图片：宽度在 60~200 之间，且不是 logo 等大图
        for (WebElement img : images) {
            try {
                int width = img.getSize().getWidth();
                int height = img.getSize().getHeight();
                String src = img.getAttribute("src");
                if (width > 50 && width < 250 && height > 15 && height < 80
                        && src != null && !src.isEmpty() && !src.equals("data:image/png;base64,")) {
                    captchaImg = img;
                    break;
                }
            } catch (Exception ignored) {
            }
        }

        if (captchaImg == null) {
            log.warn("[BrowserSession] 未找到验证码图片");
            return null;
        }

        try {
            scrollToElement(captchaImg);
            Path tmp = resolveTmpDirPath();
            String captchaPath = tmp.resolve("captcha_" + System.currentTimeMillis() + ".png").toString();

            File screenshot = captchaImg.getScreenshotAs(OutputType.FILE);
            java.nio.file.Files.copy(screenshot.toPath(), new File(captchaPath).toPath());

            log.info("[BrowserSession] ✓ 验证码图片已保存: {}", captchaPath);

            BufferedImage img = ImageIO.read(new File(captchaPath));
            log.info("[BrowserSession]   图片尺寸: {}x{}", img.getWidth(), img.getHeight());

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
        ensureOnLoginPage();
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
        ensureOnLoginPage();
        List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
        if (checkboxes.isEmpty()) {
            log.warn("[BrowserSession] 未找到隐私条款复选框");
            return false;
        }

        WebElement checkbox = checkboxes.get(0);
        scrollToElement(checkbox);

        if (!checkbox.isSelected()) {
            checkbox.click();
        }

        log.info("[BrowserSession] ✓ 隐私条款已勾选");
        return true;
    }

    public boolean checkAuthAgreement() {
        ensureOnLoginPage();
        List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
        if (checkboxes.size() < 2) {
            log.warn("[BrowserSession] 未找到授权书复选框");
            return false;
        }

        WebElement checkbox = checkboxes.get(1);
        scrollToElement(checkbox);

        if (!checkbox.isSelected()) {
            checkbox.click();
        }

        log.info("[BrowserSession] ✓ 授权书已勾选");
        return true;
    }

    public boolean clickLoginButton() {
        ensureOnLoginPage();

        List<WebElement> buttons = driver.findElements(By.tagName("button"));
        WebElement loginButton = null;

        for (WebElement btn : buttons) {
            String text = btn.getText();
            if ((text.contains("登录") || text.contains("下一步")) && btn.isDisplayed()) {
                loginButton = btn;
                break;
            }
        }

        if (loginButton == null && !buttons.isEmpty()) {
            loginButton = buttons.get(buttons.size() - 1);
        }

        if (loginButton == null) {
            log.warn("[BrowserSession] 未找到登录按钮");
            return false;
        }

        scrollToElement(loginButton);
        actions.moveToElement(loginButton).perform();
        sleepRandomSeconds(1, 2);

        loginButton.click();
        log.info("[BrowserSession] ✓ 已点击登录按钮");

        sleepRandomSeconds(3, 5);
        return true;
    }

    public boolean isLoginSuccess() {
        String url = getCurrentUrl();
        return !url.contains("login.html") && !url.contains("activePhone.html") && !url.contains("captcha");
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
        try {
            element.clear();
        } catch (Exception ignored) {}

        for (int i = 0; i < text.length(); i++) {
            element.sendKeys(String.valueOf(text.charAt(i)));
            try {
                Thread.sleep(50 + random.nextInt(50));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
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