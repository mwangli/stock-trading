package com.stock.tradingExecutor.execution;

import io.github.bonigarcia.wdm.WebDriverManager;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动化登录集成测试
 *
 * 支持三种运行模式：
 * 1. 常驻浏览器模式：浏览器启动后不关闭，持续运行
 * 2. 测试模式：通过JUnit测试逐步骤执行
 * 3. API触发模式：通过HTTP API触发登录流程
 *
 * 新设备首次登录流程：
 * - 访问login.html -> 自动跳转activePhone.html
 * - 点击获取验证码 -> 等待用户输入验证码 -> 提交验证
 * - 跳转回login.html -> 继续正常登录流程
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag("e2e")
@EnabledIfEnvironmentVariable(named = "AUTO_LOGIN_TEST_USERNAME", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AUTO_LOGIN_TEST_PASSWORD", matches = ".+")
public class AutoLoginIntegrationTest {

    private static final String LOGIN_URL = "https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/login.html";
    private static final String ACTIVE_PHONE_URL = "https://weixin.citicsinfo.com/tztweb/deal/index.html#!/account/activePhone.html";

    private static WebDriver driver;
    private static WebDriverWait wait;
    private static Actions actions;
    private static final Random random = new Random();

    private static boolean browserStarted = false;
    private static boolean needPhoneVerification = false;
    private static boolean phoneVerificationCompleted = false;

    private String username = "";
    private String password = "";

    private static String smsCodeFilePath() {
        return Paths.get(System.getProperty("user.dir"), ".tmp", "sms_code.txt").toString();
    }

    @BeforeAll
    static void setUpAll() {
        log.info("========== [集成测试] 全局初始化 ==========");
        log.info("========== [模式] 测试模式（浏览器会在所有测试结束后关闭）===========");
        try {
            Files.createDirectories(Paths.get(System.getProperty("user.dir"), ".tmp"));
        } catch (Exception e) {
            log.warn("创建 .tmp 目录失败: {}", e.getMessage());
        }
    }

    @BeforeEach
    void loadCredentialsFromEnv() {
        String u = System.getenv("AUTO_LOGIN_TEST_USERNAME");
        String p = System.getenv("AUTO_LOGIN_TEST_PASSWORD");
        username = (u != null && !u.isBlank()) ? u.trim() : "";
        password = (p != null && !p.isBlank()) ? p.trim() : "";
    }

    @AfterAll
    static void tearDownAll() {
        log.info("========== [集成测试] 全局清理 ==========");
        if (driver != null) {
            try {
                driver.quit();
                log.info("[集成测试] Chrome浏览器已关闭");
            } catch (Exception e) {
                log.warn("[集成测试] 关闭浏览器异常: {}", e.getMessage());
            }
        }
    }

    @BeforeEach
    void setUp() {
        if (!browserStarted) {
            log.info("[前置] 启动Chrome浏览器...");
            ChromeOptions options = createChromeOptions();
            try {
                WebDriverManager.chromedriver().setup();
                driver = new ChromeDriver(options);
                wait = new WebDriverWait(driver, Duration.ofSeconds(15));
                actions = new Actions(driver);
                browserStarted = true;
                log.info("[前置] Chrome启动成功");
            } catch (Exception e) {
                log.error("[前置] Chrome启动失败: {}", e.getMessage());
                fail("Chrome启动失败: " + e.getMessage());
            }
        } else {
            log.info("[前置] 复用已有Chrome浏览器（常驻模式）");
            String currentUrl = driver.getCurrentUrl();
            log.info("[前置] 当前URL: {}", currentUrl);
        }
    }

    // ==================== 阶段一：浏览器环境初始化 ====================

    @Test
    @Order(1)
    @DisplayName("步骤1.1: 启动Chrome浏览器")
    void step1_1_startChromeBrowser() {
        log.info("");
        log.info("========== [步骤1.1] 启动Chrome浏览器 ==========");
        assertNotNull(driver, "WebDriver 实例不应为 null");
        if (driver instanceof ChromeDriver) {
            ChromeDriver chromeDriver = (ChromeDriver) driver;
            log.info("[步骤1.1] ✓ WebDriver实例创建成功");
            log.info("[步骤1.1] ✓ 会话ID: {}", chromeDriver.getSessionId().toString());
        } else {
            log.info("[步骤1.1] ✓ WebDriver实例创建成功 (类型: {})", driver.getClass().getSimpleName());
        }
        log.info("========== [步骤1.1] 完成 ==========");
    }

    @Test
    @Order(2)
    @DisplayName("步骤1.2: 访问登录页面")
    void step1_2_visitLoginPage() {
        log.info("");
        log.info("========== [步骤1.2] 访问登录页面 ==========");

        try {
            log.info("[步骤1.2] 正在注入浏览器指纹...");
            injectBrowserFingerprint();
            log.info("[步骤1.2] ✓ 浏览器指纹注入完成");

            log.info("[步骤1.2] 正在访问登录页面: {}", LOGIN_URL);
            driver.get(LOGIN_URL);

            log.info("[步骤1.2] 等待页面加载...");
            randomWait(3, 5);
            ensureLoginFrameForTestDriver();

            String currentUrl = driver.getCurrentUrl();
            log.info("[步骤1.2] ✓ 当前URL: {}", currentUrl);

            String title = driver.getTitle();
            log.info("[步骤1.2] ✓ 页面标题: {}", title);

            log.info("[步骤1.2] 检查页面结构...");
            analyzePageStructure();

            if (currentUrl.contains("activePhone.html")) {
                needPhoneVerification = true;
                log.info("[步骤1.2] ⚠ 检测到新设备/新环境，需要进行手机验证码验证");
            }

            log.info("========== [步骤1.2] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.2] ✗ 访问登录页面失败: {}", e.getMessage());
            fail("访问登录页面失败: " + e.getMessage());
        }
    }

    // ==================== 阶段一点五：手机验证码处理 ====================

    @Test
    @Order(5)
    @DisplayName("步骤1.5.1: 检测是否需要手机验证码")
    void step1_5_1_detectPhoneVerification() {
        log.info("");
        log.info("========== [步骤1.5.1] 检测是否需要手机验证码 ==========");

        try {
            String currentUrl = driver.getCurrentUrl();
            boolean isPhoneVerificationPage = currentUrl.contains("activePhone.html");

            log.info("[步骤1.5.1] 当前URL: {}", currentUrl);
            log.info("[步骤1.5.1] 是否需要手机验证: {}", isPhoneVerificationPage);

            needPhoneVerification = isPhoneVerificationPage;

            if (isPhoneVerificationPage) {
                log.info("[步骤1.5.1] ℹ 检测到activePhone.html页面，需要进行手机验证");
                analyzePageStructure();
            } else {
                log.info("[步骤1.5.1] ℹ 当前已是登录页面，无需手机验证");
            }

            log.info("========== [步骤1.5.1] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.5.1] ✗ 检测手机验证码失败: {}", e.getMessage());
            fail("检测手机验证码失败: " + e.getMessage());
        }
    }

    @Test
    @Order(6)
    @DisplayName("步骤1.5.2: 点击获取验证码按钮")
    void step1_5_2_clickSendCodeButton() {
        log.info("");
        log.info("========== [步骤1.5.2] 点击获取验证码按钮 ==========");

        try {
            String currentUrl = driver.getCurrentUrl();
            boolean isPhonePage = currentUrl.contains("activePhone.html");

            if (!isPhonePage) {
                log.info("[步骤1.5.2] ℹ 当前不是手机验证页面，跳过");
                return;
            }

            log.info("[步骤1.5.2] 检测到手机验证页面，继续执行...");

            log.info("[步骤1.5.2] 等待动态内容加载...");
            randomWait(2, 3);
            ensureActivePhoneFrameForTestDriver();

            if (!username.isBlank()) {
                WebElement phoneInput = findElementSafely(By.cssSelector("input[placeholder*='手机号']"));
                if (phoneInput == null) {
                    for (WebElement el : driver.findElements(By.cssSelector("input[type='text'], input:not([type])"))) {
                        try {
                            if (!el.isDisplayed()) {
                                continue;
                            }
                            String ph = el.getAttribute("placeholder");
                            if (ph != null && ph.contains("手机")) {
                                phoneInput = el;
                                break;
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                if (phoneInput != null) {
                    log.info("[步骤1.5.2] 正在输入手机号（与登录账号一致）…");
                    scrollToElement(phoneInput);
                    humanType(phoneInput, username);
                    randomWait(1, 2);
                } else {
                    log.warn("[步骤1.5.2] ⚠ 未找到手机号输入框");
                }
            }

            log.info("[步骤1.5.2] 打印所有可见按钮元素...");
            List<WebElement> allButtons = driver.findElements(By.tagName("button"));
            for (int i = 0; i < allButtons.size(); i++) {
                WebElement btn = allButtons.get(i);
                log.info("[步骤1.5.2]   button[{}]: text='{}', displayed={}, enabled={}",
                        i, btn.getText(), btn.isDisplayed(), btn.isEnabled());
            }

            List<WebElement> allDivs = driver.findElements(By.cssSelector("div[onclick], div[ng-click], div[ role='button']"));
            log.info("[步骤1.5.2]   可点击div数量: {}", allDivs.size());

            By[] sendCodeLocators = {
                    By.xpath("//button[contains(text(),'点击获取验证码')]"),
                    By.xpath("//button[contains(text(),'获取验证码')]"),
                    By.xpath("//button[contains(text(),'发送验证码')]"),
                    By.xpath("//span[contains(text(),'点击获取验证码')]"),
                    By.xpath("//div[contains(text(),'点击获取验证码')]"),
                    By.xpath("//button[contains(text(),'获取')]"),
                    By.xpath("//div[contains(text(),'获取验证码')]"),
                    By.xpath("//span[contains(text(),'获取验证码')]"),
                    By.cssSelector("button.send-code-btn"),
                    By.cssSelector("[class*='send']"),
                    By.cssSelector("[class*='code']")
            };

            WebElement sendCodeBtn = null;
            for (By locator : sendCodeLocators) {
                try {
                    List<WebElement> buttons = driver.findElements(locator);
                    for (WebElement btn : buttons) {
                        if (btn.isDisplayed() && btn.isEnabled()) {
                            sendCodeBtn = btn;
                            log.info("[步骤1.5.2] ✓ 找到发送验证码按钮: {}", btn.getText());
                            break;
                        }
                    }
                    if (sendCodeBtn != null) break;
                } catch (Exception ignored) {}
            }

            if (sendCodeBtn == null) {
                log.warn("[步骤1.5.2] ⚠ 未找到发送验证码按钮，打印页面源码片段...");
                String pageSource = driver.getPageSource();
                int start = Math.max(0, pageSource.indexOf("验证码") - 100);
                int end = Math.min(pageSource.length(), pageSource.indexOf("验证码") + 200);
                log.info("[步骤1.5.2] 页面包含验证码的位置: {}", pageSource.substring(start, end).replaceAll("\\s+", " "));
            }

            assertNotNull(sendCodeBtn, "获取验证码按钮不应为null");

            scrollToElement(sendCodeBtn);
            waitForElement(sendCodeBtn);

            log.info("[步骤1.5.2] 正在点击获取验证码按钮...");
            sendCodeBtn.click();

            log.info("[步骤1.5.2] ✓ 已点击，等待短信发送...");
            randomWait(2, 3);

            log.info("========== [步骤1.5.2] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.5.2] ✗ 点击获取验证码按钮失败: {}", e.getMessage());
            fail("点击获取验证码按钮失败: " + e.getMessage());
        }
    }

    @Test
    @Order(7)
    @DisplayName("步骤1.5.3: 等待用户输入验证码（人工介入）")
    void step1_5_3_waitForUserInputSmsCode() {
        String currentUrl = driver.getCurrentUrl();
        boolean isPhonePage = currentUrl.contains("activePhone.html");
        if (!isPhonePage) {
            log.info("[步骤1.5.3] ℹ 当前不是手机验证页面，跳过");
            return;
        }

        log.info("");
        log.info("========== [步骤1.5.3] 等待用户输入验证码 ==========");
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
                Path path = Paths.get(smsCodeFilePath());
                if (Files.exists(path)) {
                    String code = Files.readString(path).trim();
                    if (code != null && code.length() == 6 && code.matches("\\d{6}")) {
                        log.info("[步骤1.5.3] ✓ 检测到验证码文件，验证码: {}", code);
                        break;
                    }
                }

                if (waitSeconds % 10 == 0 && waitSeconds > 0) {
                    log.info("[步骤1.5.3] 等待验证码输入... 已等待 {} 秒", waitSeconds);
                }

                Thread.sleep(1000);
                waitSeconds++;
            }

            if (waitSeconds >= maxWaitSeconds) {
                log.warn("[步骤1.5.3] ⚠ 等待验证码超时（{}秒）", maxWaitSeconds);
            }

        } catch (Exception e) {
            log.error("[步骤1.5.3] ✗ 等待验证码异常: {}", e.getMessage());
        }

        log.info("========== [步骤1.5.3] 完成 ==========");
    }

    @Test
    @Order(8)
    @DisplayName("步骤1.5.4: 输入短信验证码")
    void step1_5_4_inputSmsCode() {
        String currentUrl = driver.getCurrentUrl();
        boolean isPhonePage = currentUrl.contains("activePhone.html");
        if (!isPhonePage) {
            log.info("[步骤1.5.4] ℹ 当前不是手机验证页面，跳过");
            return;
        }

        log.info("");
        log.info("========== [步骤1.5.4] 输入短信验证码 ==========");
        ensureActivePhoneFrameForTestDriver();

        try {
            String smsCode = "";
            Path path = Paths.get(smsCodeFilePath());
            if (Files.exists(path)) {
                smsCode = Files.readString(path).trim();
                log.info("[步骤1.5.4] 从文件读取验证码: {}", smsCode);
            }

            if (smsCode.isEmpty()) {
                log.warn("[步骤1.5.4] ⚠ 未检测到验证码，使用默认值: 123456");
                smsCode = "123456";
            }

            WebElement smsInput = findElementSafely(By.cssSelector("input[placeholder*='验证码']"));
            if (smsInput == null) {
                smsInput = findElementSafely(By.xpath("//input[@type='text']"));
            }

            assertNotNull(smsInput, "短信验证码输入框不应为null");

            log.info("[步骤1.5.4] 正在输入验证码: {}", smsCode);
            scrollToElement(smsInput);
            humanType(smsInput, smsCode);

            log.info("[步骤1.5.4] ✓ 验证码输入成功");
            randomWait(1, 2);

            log.info("========== [步骤1.5.4] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.5.4] ✗ 输入验证码失败: {}", e.getMessage());
            fail("输入验证码失败: " + e.getMessage());
        }
    }

    @Test
    @Order(9)
    @DisplayName("步骤1.5.5: 提交手机验证")
    void step1_5_5_submitPhoneVerification() {
        String currentUrl = driver.getCurrentUrl();
        boolean isPhonePage = currentUrl.contains("activePhone.html");
        if (!isPhonePage) {
            log.info("[步骤1.5.5] ℹ 当前不是手机验证页面，跳过");
            return;
        }

        log.info("");
        log.info("========== [步骤1.5.5] 提交手机验证 ==========");

        try {
            ensureActivePhoneFrameForTestDriver();

            List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
            if (checkboxes.size() >= 2) {
                for (int i = 0; i < 2; i++) {
                    WebElement cb = checkboxes.get(i);
                    if (!cb.isSelected()) {
                        scrollToElement(cb);
                        cb.click();
                        randomWait(0, 1);
                    }
                }
                log.info("[步骤1.5.5] ✓ 已勾选隐私条款与授权书");
            }

            By[] submitLocators = {
                    By.xpath("//button[contains(text(),'下一步')]"),
                    By.xpath("//button[contains(text(),'确认')]"),
                    By.xpath("//button[contains(text(),'验证')]"),
                    By.cssSelector("button[type='submit']")
            };

            WebElement submitBtn = null;
            for (By locator : submitLocators) {
                try {
                    List<WebElement> buttons = driver.findElements(locator);
                    for (WebElement btn : buttons) {
                        if (btn.isDisplayed()) {
                            submitBtn = btn;
                            log.info("[步骤1.5.5] ✓ 找到提交按钮: {}", btn.getText());
                            break;
                        }
                    }
                    if (submitBtn != null) break;
                } catch (Exception ignored) {}
            }

            assertNotNull(submitBtn, "提交按钮不应为null");

            scrollToElement(submitBtn);
            waitForElement(submitBtn);

            log.info("[步骤1.5.5] 正在点击提交按钮...");
            submitBtn.click();

            log.info("[步骤1.5.5] ✓ 已提交，等待页面跳转...");
            randomWait(3, 5);

            currentUrl = driver.getCurrentUrl();
            log.info("[步骤1.5.5]   当前URL: {}", currentUrl);

            log.info("========== [步骤1.5.5] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.5.5] ✗ 提交手机验证失败: {}", e.getMessage());
            fail("提交手机验证失败: " + e.getMessage());
        }
    }

    @Test
    @Order(10)
    @DisplayName("步骤1.5.6: 确认跳转回登录页面")
    void step1_5_6_confirmBackToLoginPage() {
        String currentUrl = driver.getCurrentUrl();
        boolean isPhonePage = currentUrl.contains("activePhone.html");
        if (!isPhonePage) {
            log.info("[步骤1.5.6] ℹ 当前不是手机验证页面，跳过");
            return;
        }

        log.info("");
        log.info("========== [步骤1.5.6] 确认跳转回登录页面 ==========");

        try {
            log.info("[步骤1.5.6] 当前URL: {}", currentUrl);

            boolean isOnLoginPage = currentUrl.contains("login.html");
            log.info("[步骤1.5.6] 是否在登录页面: {}", isOnLoginPage);

            if (isOnLoginPage) {
                log.info("[步骤1.5.6] ✓ 成功跳转回登录页面");
                analyzePageStructure();
            } else {
                log.warn("[步骤1.5.6] ⚠ 尚未跳转回登录页面，可能验证未通过");
            }

            needPhoneVerification = false;
            log.info("========== [步骤1.5.6] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤1.5.6] ✗ 确认登录页面失败: {}", e.getMessage());
            fail("确认登录页面失败: " + e.getMessage());
        }
    }

    // ==================== 阶段二：页面元素定位 ====================

    @Test
    @Order(11)
    @DisplayName("步骤2.1: 定位用户名输入框")
    void step2_1_findAccountInput() {
        log.info("");
        log.info("========== [步骤2.1] 定位用户名输入框 ==========");

        try {
            ensureOnLoginPage();

            WebElement accountInput = findElementSafely(By.cssSelector("input[placeholder*='手机号']"));
            if (accountInput == null) {
                accountInput = findElementSafely(By.cssSelector("input[name='username']"));
            }
            if (accountInput == null) {
                accountInput = findElementSafely(By.xpath("//input[@type='text']"));
            }

            assertNotNull(accountInput, "用户名输入框不应为null");
            waitForElement(accountInput);

            log.info("[步骤2.1] ✓ 用户名输入框已定位");
            log.info("[步骤2.1]   - tagName: {}", accountInput.getTagName());
            log.info("[步骤2.1]   - type: {}", accountInput.getAttribute("type"));
            log.info("[步骤2.1]   - placeholder: {}", accountInput.getAttribute("placeholder"));

            log.info("========== [步骤2.1] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.1] ✗ 定位用户名输入框失败: {}", e.getMessage());
            fail("定位用户名输入框失败: " + e.getMessage());
        }
    }

    @Test
    @Order(12)
    @DisplayName("步骤2.2: 定位密码输入框")
    void step2_2_findPasswordInput() {
        log.info("");
        log.info("========== [步骤2.2] 定位密码输入框 ==========");

        try {
            ensureOnLoginPage();

            WebElement passwordInput = findElementSafely(By.cssSelector("input[type='password']"));

            assertNotNull(passwordInput, "密码输入框不应为null");
            waitForElement(passwordInput);

            log.info("[步骤2.2] ✓ 密码输入框已定位");
            log.info("[步骤2.2]   - tagName: {}", passwordInput.getTagName());
            log.info("[步骤2.2]   - type: {}", passwordInput.getAttribute("type"));

            log.info("========== [步骤2.2] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.2] ✗ 定位密码输入框失败: {}", e.getMessage());
            fail("定位密码输入框失败: " + e.getMessage());
        }
    }

    @Test
    @Order(13)
    @DisplayName("步骤2.3: 定位图片验证码")
    void step2_3_findCaptchaImage() {
        log.info("");
        log.info("========== [步骤2.3] 定位图片验证码 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> images = driver.findElements(By.cssSelector("img"));
            log.info("[步骤2.3] 页面中共有 {} 个图片元素", images.size());

            WebElement captchaImg = null;
            for (WebElement img : images) {
                String src = img.getAttribute("src");
                int width = img.getSize().getWidth();
                log.info("[步骤2.3]   - src: {}, width: {}", src != null ? src.substring(0, Math.min(50, src.length())) : "null", width);
                if (width > 50 && src != null && !src.isEmpty() && !src.equals("data:image/png;base64,")) {
                    captchaImg = img;
                    break;
                }
            }

            assertNotNull(captchaImg, "验证码图片不应为null");
            log.info("[步骤2.3] ✓ 验证码图片已定位");
            log.info("[步骤2.3]   - width: {}", captchaImg.getSize().getWidth());
            log.info("[步骤2.3]   - height: {}", captchaImg.getSize().getHeight());

            log.info("========== [步骤2.3] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.3] ✗ 定位验证码图片失败: {}", e.getMessage());
            fail("定位验证码图片失败: " + e.getMessage());
        }
    }

    @Test
    @Order(14)
    @DisplayName("步骤2.4: 定位验证码输入框")
    void step2_4_findCaptchaInput() {
        log.info("");
        log.info("========== [步骤2.4] 定位验证码输入框 ==========");

        try {
            ensureOnLoginPage();

            WebElement captchaInput = findElementSafely(By.cssSelector("input[placeholder*='验证码']"));

            assertNotNull(captchaInput, "验证码输入框不应为null");

            log.info("[步骤2.4] ✓ 验证码输入框已定位");
            log.info("[步骤2.4]   - placeholder: {}", captchaInput.getAttribute("placeholder"));

            log.info("========== [步骤2.4] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.4] ✗ 定位验证码输入框失败: {}", e.getMessage());
            fail("定位验证码输入框失败: " + e.getMessage());
        }
    }

    @Test
    @Order(15)
    @DisplayName("步骤2.5: 定位隐私保护条款复选框")
    void step2_5_findPrivacyCheckbox() {
        log.info("");
        log.info("========== [步骤2.5] 定位隐私保护条款复选框 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
            log.info("[步骤2.5] 页面中共有 {} 个复选框", checkboxes.size());

            for (int i = 0; i < checkboxes.size(); i++) {
                WebElement cb = checkboxes.get(i);
                log.info("[步骤2.5]   复选框[{}]: displayed={}, enabled={}", i, cb.isDisplayed(), cb.isEnabled());
            }

            assertFalse(checkboxes.isEmpty(), "不应没有复选框");

            log.info("[步骤2.5] ✓ 隐私条款复选框已定位");
            log.info("========== [步骤2.5] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.5] ✗ 定位隐私条款复选框失败: {}", e.getMessage());
            fail("定位隐私条款复选框失败: " + e.getMessage());
        }
    }

    @Test
    @Order(16)
    @DisplayName("步骤2.6: 定位授权书复选框")
    void step2_6_findAuthCheckbox() {
        log.info("");
        log.info("========== [步骤2.6] 定位授权书复选框 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
            assertTrue(checkboxes.size() > 1, "复选框数量应大于1");

            log.info("[步骤2.6] ✓ 授权书复选框已定位，数量: {}", checkboxes.size());
            log.info("========== [步骤2.6] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.6] ✗ 定位授权书复选框失败: {}", e.getMessage());
            fail("定位授权书复选框失败: " + e.getMessage());
        }
    }

    @Test
    @Order(17)
    @DisplayName("步骤2.7: 定位登录按钮")
    void step2_7_findLoginButton() {
        log.info("");
        log.info("========== [步骤2.7] 定位登录按钮 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> buttons = driver.findElements(By.tagName("button"));
            log.info("[步骤2.7] 页面中共有 {} 个按钮", buttons.size());

            WebElement loginButton = null;
            for (WebElement btn : buttons) {
                String text = btn.getText();
                log.info("[步骤2.7]   按钮: '{}', displayed={}", text, btn.isDisplayed());
                if (text.contains("登录") || text.contains("下一步")) {
                    loginButton = btn;
                    break;
                }
            }

            if (loginButton == null && !buttons.isEmpty()) {
                loginButton = buttons.get(buttons.size() - 1);
            }

            assertNotNull(loginButton, "登录按钮不应为null");

            log.info("[步骤2.7] ✓ 登录按钮已定位");
            log.info("[步骤2.7]   - text: {}", loginButton.getText());

            log.info("========== [步骤2.7] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤2.7] ✗ 定位登录按钮失败: {}", e.getMessage());
            fail("定位登录按钮失败: " + e.getMessage());
        }
    }

    // ==================== 阶段三：表单填写 ====================

    @Test
    @Order(20)
    @DisplayName("步骤3.1: 输入用户名")
    void step3_1_inputAccount() {
        log.info("");
        log.info("========== [步骤3.1] 输入用户名 ==========");

        try {
            ensureOnLoginPage();

            WebElement accountInput = driver.findElement(By.cssSelector("input[type='text']"));

            log.info("[步骤3.1] 正在输入用户名: {}", username);
            scrollToElement(accountInput);
            waitForElement(accountInput);
            humanType(accountInput, username);

            String actualValue = accountInput.getAttribute("value");
            log.info("[步骤3.1] ✓ 用户名输入成功: {}", actualValue);
            randomWait(1, 2);

            log.info("========== [步骤3.1] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.1] ✗ 输入用户名失败: {}", e.getMessage());
            fail("输入用户名失败: " + e.getMessage());
        }
    }

    @Test
    @Order(21)
    @DisplayName("步骤3.2: 输入密码")
    void step3_2_inputPassword() {
        log.info("");
        log.info("========== [步骤3.2] 输入密码 ==========");

        try {
            ensureOnLoginPage();

            WebElement passwordInput = driver.findElement(By.cssSelector("input[type='password']"));

            log.info("[步骤3.2] 正在输入密码: ****");
            scrollToElement(passwordInput);
            waitForElement(passwordInput);
            humanType(passwordInput, password);

            String actualValue = passwordInput.getAttribute("value");
            assertNotNull(actualValue, "密码输入失败");
            log.info("[步骤3.2] ✓ 密码输入成功");
            randomWait(1, 2);

            log.info("========== [步骤3.2] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.2] ✗ 输入密码失败: {}", e.getMessage());
            fail("输入密码失败: " + e.getMessage());
        }
    }

    @Test
    @Order(22)
    @DisplayName("步骤3.3: 截取验证码图片")
    void step3_3_captureCaptchaImage() {
        log.info("");
        log.info("========== [步骤3.3] 截取验证码图片 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> images = driver.findElements(By.cssSelector("img"));
            WebElement captchaImg = null;
            for (WebElement img : images) {
                int width = img.getSize().getWidth();
                String src = img.getAttribute("src");
                if (width > 50 && src != null && !src.isEmpty() && !src.equals("data:image/png;base64,")) {
                    captchaImg = img;
                    break;
                }
            }

            assertNotNull(captchaImg, "验证码图片不应为null");

            scrollToElement(captchaImg);
            waitForElement(captchaImg);

            log.info("[步骤3.3] 正在截取验证码图片...");
            Path tmpDir = Paths.get(System.getProperty("user.dir"), ".tmp");
            Files.createDirectories(tmpDir);
            String captchaPath = tmpDir.resolve("captcha_" + System.currentTimeMillis() + ".png").toString();

            File screenshot = captchaImg.getScreenshotAs(OutputType.FILE);
            java.nio.file.Files.copy(screenshot.toPath(), new File(captchaPath).toPath());

            assertTrue(new File(captchaPath).exists(), "验证码图片保存失败");
            log.info("[步骤3.3] ✓ 验证码图片已保存: {}", captchaPath);

            BufferedImage img = ImageIO.read(new File(captchaPath));
            log.info("[步骤3.3]   - 图片尺寸: {}x{}", img.getWidth(), img.getHeight());

            log.info("========== [步骤3.3] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.3] ✗ 截取验证码图片失败: {}", e.getMessage());
            fail("截取验证码图片失败: " + e.getMessage());
        }
    }

    @Test
    @Order(23)
    @DisplayName("步骤3.4: 输入验证码")
    void step3_4_inputCaptcha() {
        log.info("");
        log.info("========== [步骤3.4] 输入验证码 ==========");

        try {
            ensureOnLoginPage();

            log.info("");
            log.info("╔════════════════════════════════════════════════════════════════╗");
            log.info("║           【人工介入 Required】                                 ║");
            log.info("║                                                                ║");
            log.info("║  请查看上一步保存的验证码图片，手动识别后写入验证码：           ║");
            log.info("║                                                                ║");
            log.info("║  文件路径: d:/ai-stock-trading/.tmp/captcha_*.png              ║");
            log.info("║                                                                ║");
            log.info("║  验证码写入: echo 验证码 > d:/ai-stock-trading/.tmp/captcha_code.txt  ║");
            log.info("║                                                                ║");
            log.info("╚════════════════════════════════════════════════════════════════╝");
            log.info("");

            String captchaText = "1234";

            Path codePath = Paths.get(System.getProperty("user.dir"), ".tmp", "captcha_code.txt");
            if (Files.exists(codePath)) {
                captchaText = Files.readString(codePath).trim();
                log.info("[步骤3.4] 从文件读取验证码: {}", captchaText);
            } else {
                log.warn("[步骤3.4] ⚠ 未检测到验证码文件，使用默认值: {}", captchaText);
            }

            WebElement captchaInput = findElementSafely(By.cssSelector("input[placeholder*='验证码']"));
            assertNotNull(captchaInput, "验证码输入框不应为null");

            log.info("[步骤3.4] 正在输入验证码: {}", captchaText);
            scrollToElement(captchaInput);
            humanType(captchaInput, captchaText);

            log.info("[步骤3.4] ✓ 验证码输入成功");
            randomWait(1, 2);

            log.info("========== [步骤3.4] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.4] ✗ 输入验证码失败: {}", e.getMessage());
            fail("输入验证码失败: " + e.getMessage());
        }
    }

    @Test
    @Order(24)
    @DisplayName("步骤3.5: 勾选隐私保护条款")
    void step3_5_checkPrivacyAgreement() {
        log.info("");
        log.info("========== [步骤3.5] 勾选隐私保护条款 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
            assertFalse(checkboxes.isEmpty(), "复选框列表为空");

            WebElement privacyCheckbox = checkboxes.get(0);
            scrollToElement(privacyCheckbox);

            if (!privacyCheckbox.isSelected()) {
                waitForElement(privacyCheckbox);
                privacyCheckbox.click();
                randomWait(0, 1);
            }

            assertTrue(privacyCheckbox.isSelected(), "隐私条款未勾选");
            log.info("[步骤3.5] ✓ 隐私条款已勾选");

            log.info("========== [步骤3.5] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.5] ✗ 勾选隐私条款失败: {}", e.getMessage());
            fail("勾选隐私条款失败: " + e.getMessage());
        }
    }

    @Test
    @Order(25)
    @DisplayName("步骤3.6: 勾选授权书")
    void step3_6_checkAuthAgreement() {
        log.info("");
        log.info("========== [步骤3.6] 勾选授权书 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> checkboxes = driver.findElements(By.cssSelector("input[type='checkbox']"));
            assertTrue(checkboxes.size() > 1, "复选框数量不足");

            WebElement authCheckbox = checkboxes.get(1);
            scrollToElement(authCheckbox);

            if (!authCheckbox.isSelected()) {
                waitForElement(authCheckbox);
                authCheckbox.click();
                randomWait(0, 1);
            }

            assertTrue(authCheckbox.isSelected(), "授权书未勾选");
            log.info("[步骤3.6] ✓ 授权书已勾选");

            log.info("========== [步骤3.6] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤3.6] ✗ 勾选授权书失败: {}", e.getMessage());
            fail("勾选授权书失败: " + e.getMessage());
        }
    }

    // ==================== 阶段四：登录提交 ====================

    @Test
    @Order(30)
    @DisplayName("步骤4.1: 点击登录按钮")
    void step4_1_clickLoginButton() {
        log.info("");
        log.info("========== [步骤4.1] 点击登录按钮 ==========");

        try {
            ensureOnLoginPage();

            List<WebElement> buttons = driver.findElements(By.tagName("button"));
            WebElement loginButton = null;
            for (WebElement btn : buttons) {
                if (btn.getText().contains("登录") || btn.getText().contains("下一步")) {
                    loginButton = btn;
                    break;
                }
            }

            if (loginButton == null && !buttons.isEmpty()) {
                loginButton = buttons.get(buttons.size() - 1);
            }

            assertNotNull(loginButton, "登录按钮不应为null");

            log.info("[步骤4.1] 正在点击登录按钮: {}", loginButton.getText());
            scrollToElement(loginButton);
            waitForElement(loginButton);

            actions.moveToElement(loginButton).perform();
            randomWait(1, 2);

            loginButton.click();
            log.info("[步骤4.1] ✓ 已点击登录按钮");

            log.info("[步骤4.1] 等待服务器响应...");
            randomWait(3, 5);

            String currentUrl = driver.getCurrentUrl();
            log.info("[步骤4.1]   当前URL: {}", currentUrl);

            boolean hasSlider = !driver.findElements(By.cssSelector(".nc_wrapper, .slider-captcha")).isEmpty();
            log.info("[步骤4.1]   出现滑块验证码: {}", hasSlider);

            log.info("========== [步骤4.1] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤4.1] ✗ 点击登录按钮失败: {}", e.getMessage());
            fail("点击登录按钮失败: " + e.getMessage());
        }
    }

    // ==================== 阶段五：滑块验证码处理 ====================

    @Test
    @Order(40)
    @DisplayName("步骤5.1: 检测滑块验证码")
    void step5_1_detectSliderCaptcha() {
        log.info("");
        log.info("========== [步骤5.1] 检测滑块验证码 ==========");

        try {
            By[] sliderLocators = {
                    By.cssSelector(".nc_wrapper"),
                    By.cssSelector(".slider-captcha"),
                    By.cssSelector("[class*='slider']"),
                    By.xpath("//div[contains(@class,'slider')]")
            };

            WebElement slider = null;
            for (By locator : sliderLocators) {
                try {
                    List<WebElement> elements = driver.findElements(locator);
                    for (WebElement el : elements) {
                        if (el.isDisplayed()) {
                            slider = el;
                            break;
                        }
                    }
                    if (slider != null) break;
                } catch (Exception ignored) {}
            }

            if (slider != null) {
                log.info("[步骤5.1] ✓ 检测到滑块验证码");
                log.info("[步骤5.1]   - class: {}", slider.getAttribute("class"));
            } else {
                log.info("[步骤5.1] ℹ 未检测到滑块验证码");
            }

            log.info("========== [步骤5.1] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤5.1] ✗ 检测滑块验证码失败: {}", e.getMessage());
            fail("检测滑块验证码失败: " + e.getMessage());
        }
    }

    @Test
    @Order(41)
    @DisplayName("步骤5.2: 定位滑块元素")
    void step5_2_findSliderElements() {
        log.info("");
        log.info("========== [步骤5.2] 定位滑块元素 ==========");

        try {
            By sliderImgLocator = By.cssSelector(".nc_iconfont.btn_slide, .slider-image, [class*='slider'] img");
            By bgImgLocator = By.cssSelector(".nc_bgimg, .bg-image, [class*='bg'] img");

            List<WebElement> sliderImages = driver.findElements(sliderImgLocator);
            List<WebElement> bgImages = driver.findElements(bgImgLocator);

            log.info("[步骤5.2] 滑块图片数量: {}", sliderImages.size());
            log.info("[步骤5.2] 背景图片数量: {}", bgImages.size());

            log.info("========== [步骤5.2] 完成 ==========");

        } catch (Exception e) {
            log.error("[步骤5.2] ✗ 定位滑块元素失败: {}", e.getMessage());
            fail("定位滑块元素失败: " + e.getMessage());
        }
    }

    // ==================== 阶段六：Token ====================

    @Test
    @Order(50)
    @DisplayName("步骤6.1: 解析并校验 Token（Cookie 或 Web Storage）")
    void step6_1_probeToken() {
        log.info("");
        log.info("========== [步骤6.1] 解析 Token ==========");
        switchDriverToDefaultContent();
        randomWait(2, 4);

        String url = driver.getCurrentUrl();
        log.info("[步骤6.1] 当前 URL: {}", url);

        String token = probeTokenFromBrowserForTest();
        if (token != null) {
            int show = Math.min(10, token.length());
            log.info("[步骤6.1] ✓ 已解析 Token 前缀: {}*** (总长度 {})", token.substring(0, show), token.length());
            assertTrue(token.length() >= 8, "Token 长度应合理");
        } else {
            boolean leftLogin = !url.contains("login.html") && !url.contains("activePhone.html");
            if (leftLogin) {
                log.warn("[步骤6.1] ⚠ 已离开登录页但未解析到 Token，请检查站点实际存储方式");
            } else {
                log.info("[步骤6.1] ℹ 仍在登录相关页，未登录则无 Token 属正常");
            }
        }
        log.info("========== [步骤6.1] 完成 ==========");
    }

    // ==================== 辅助方法 ====================

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

    private void injectBrowserFingerprint() {
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

    private void ensureOnLoginPage() {
        String currentUrl = driver.getCurrentUrl();
        if (!currentUrl.contains("login.html")) {
            log.info("[确保] 当前页面不是登录页，正在导航回登录页面...");
            injectBrowserFingerprint();
            driver.get(LOGIN_URL);
            randomWait(3, 5);
            log.info("[确保] 已导航回登录页面: {}", driver.getCurrentUrl());
        }
        ensureLoginFrameForTestDriver();
    }

    private void switchDriverToDefaultContent() {
        try {
            driver.switchTo().defaultContent();
        } catch (Exception e) {
            log.debug("[确保] defaultContent: {}", e.getMessage());
        }
    }

    /**
     * 与 {@link com.stock.tradingExecutor.execution.BrowserSessionManager#ensureLoginFrame()} 对齐，定位含密码框的 iframe。
     */
    private void ensureLoginFrameForTestDriver() {
        try {
            switchDriverToDefaultContent();
            searchLoginFrameRecursiveForTest(0, 8);
        } catch (Exception e) {
            log.warn("[确保] iframe 切换异常: {}", e.getMessage());
            switchDriverToDefaultContent();
        }
    }

    /** 手机验证页表单 iframe（占位含「手机号」） */
    private void ensureActivePhoneFrameForTestDriver() {
        String url = driver.getCurrentUrl();
        if (!url.contains("activePhone.html")) {
            return;
        }
        try {
            switchDriverToDefaultContent();
            searchActivePhoneFrameRecursiveForTest(0, 8);
        } catch (Exception e) {
            log.warn("[确保] 手机验证 iframe 切换异常: {}", e.getMessage());
            switchDriverToDefaultContent();
        }
    }

    private boolean searchActivePhoneFrameRecursiveForTest(int depth, int maxDepth) {
        if (depth > maxDepth) {
            return false;
        }
        if (hasVisiblePhoneNumberInputForTest()) {
            return true;
        }
        List<WebElement> frames = driver.findElements(By.tagName("iframe"));
        for (WebElement frame : frames) {
            try {
                driver.switchTo().frame(frame);
                if (searchActivePhoneFrameRecursiveForTest(depth + 1, maxDepth)) {
                    return true;
                }
                driver.switchTo().parentFrame();
            } catch (Exception e) {
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ignored) {
                    switchDriverToDefaultContent();
                }
            }
        }
        return false;
    }

    private boolean hasVisiblePhoneNumberInputForTest() {
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

    private boolean searchLoginFrameRecursiveForTest(int depth, int maxDepth) {
        if (depth > maxDepth) {
            return false;
        }
        if (hasVisiblePasswordInputForTest()) {
            return true;
        }
        List<WebElement> frames = driver.findElements(By.tagName("iframe"));
        for (WebElement frame : frames) {
            try {
                driver.switchTo().frame(frame);
                if (searchLoginFrameRecursiveForTest(depth + 1, maxDepth)) {
                    return true;
                }
                driver.switchTo().parentFrame();
            } catch (Exception e) {
                try {
                    driver.switchTo().parentFrame();
                } catch (Exception ignored) {
                    switchDriverToDefaultContent();
                }
            }
        }
        return false;
    }

    private boolean hasVisiblePasswordInputForTest() {
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

    private String probeTokenFromBrowserForTest() {
        switchDriverToDefaultContent();
        JavascriptExecutor js = (JavascriptExecutor) driver;
        try {
            Object o = js.executeScript("""
                    function scan(store) {
                      if (!store || !store.length) return null;
                      const prefer = ['token','Token','TOKEN','access_token','accessToken','tk',
                        'sessionId','session_id','Authorization','authToken','userToken'];
                      for (const k of prefer) {
                        const v = store.getItem(k);
                        if (v && String(v).length >= 8) return String(v);
                      }
                      for (let i = 0; i < store.length; i++) {
                        const k = store.key(i);
                        if (!k) continue;
                        const kl = k.toLowerCase();
                        if (kl.includes('token') || kl.includes('session') || kl.includes('auth')) {
                          const v = store.getItem(k);
                          if (v && String(v).length >= 12) return String(v);
                        }
                      }
                      return null;
                    }
                    let r = scan(window.localStorage) || scan(window.sessionStorage);
                    if (!r) {
                      try { r = scan(window.top.localStorage) || scan(window.top.sessionStorage); } catch (e) {}
                    }
                    return r;
                    """);
            if (o instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        } catch (Exception e) {
            log.debug("[Token] 脚本读取失败: {}", e.getMessage());
        }
        for (Cookie c : driver.manage().getCookies()) {
            String n = c.getName().toLowerCase();
            String v = c.getValue();
            if (v == null || v.length() < 12) {
                continue;
            }
            if (n.contains("token") || n.contains("session") || n.contains("sid") || n.contains("auth")) {
                return v.trim();
            }
        }
        return null;
    }

    private void analyzePageStructure() {
        log.info("[分析] === 页面结构分析 ===");

        int iframes = driver.findElements(By.tagName("iframe")).size();
        log.info("[分析] iframe数量: {}", iframes);

        int inputs = driver.findElements(By.tagName("input")).size();
        log.info("[分析] input数量: {}", inputs);

        int buttons = driver.findElements(By.tagName("button")).size();
        log.info("[分析] button数量: {}", buttons);

        int images = driver.findElements(By.tagName("img")).size();
        log.info("[分析] img数量: {}", images);

        int divs = driver.findElements(By.tagName("div")).size();
        log.info("[分析] div数量: {}", divs);

        String pageSource = driver.getPageSource();
        boolean hasLogin = pageSource.contains("登录") || pageSource.contains("login");
        boolean hasCaptcha = pageSource.contains("验证码") || pageSource.contains("captcha");
        boolean hasPhoneCode = pageSource.contains("手机验证码") || pageSource.contains("短信验证");
        log.info("[分析] 页面包含'登录': {}", hasLogin);
        log.info("[分析] 页面包含'验证码': {}", hasCaptcha);
        log.info("[分析] 页面包含'手机验证码': {}", hasPhoneCode);

        log.info("[分析] ======================");
    }

    private WebElement findElementSafely(By by) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            return wait.until(ExpectedConditions.presenceOfElementLocated(by));
        } catch (Exception e) {
            log.debug("[查找] 元素未找到: {}", by);
            return null;
        }
    }

    private void waitForElement(WebElement element) {
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            wait.until(ExpectedConditions.elementToBeClickable(element));
        } catch (Exception e) {
            log.debug("[等待] 元素等待失败: {}", e.getMessage());
        }
    }

    private void scrollToElement(WebElement element) {
        try {
            ((JavascriptExecutor) driver).executeScript(
                "arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});",
                element
            );
            randomWait(0, 1);
        } catch (Exception e) {
            log.debug("[滚动] 滚动失败: {}", e.getMessage());
        }
    }

    private void humanType(WebElement element, String text) {
        try {
            element.clear();
        } catch (Exception e) {
            log.debug("[清空] 清空输入框失败: {}", e.getMessage());
        }
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

    private void randomWait(int minSeconds, int maxSeconds) {
        try {
            int delay = minSeconds * 1000 + random.nextInt((maxSeconds - minSeconds) * 1000);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<int[]> generateSlideTrack(int distance) {
        List<int[]> track = new ArrayList<>();
        int trackSize = 30 + distance / 10;
        double currentX = 0;

        for (int i = 0; i < trackSize; i++) {
            double progress = (double) i / trackSize;
            double easeProgress = calculateEaseProgress(progress);

            currentX = distance * easeProgress;
            double jitter = (random.nextDouble() - 0.5) * 4;
            int y = 100 + (int) (Math.sin(i * 0.3) * 3);

            track.add(new int[]{(int) (currentX + jitter), y});
        }

        return track;
    }

    private double calculateEaseProgress(double progress) {
        if (progress < 0.3) {
            return 2 * progress * progress;
        } else if (progress < 0.7) {
            return -1 + (4 - 2 * progress) * progress;
        } else {
            return 1 - Math.pow(-2 * progress + 2, 2) / 2;
        }
    }
}