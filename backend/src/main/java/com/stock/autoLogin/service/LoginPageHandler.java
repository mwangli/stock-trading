package com.stock.autoLogin.service;

import com.stock.autoLogin.exception.BrowserException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 登录页面处理
 * 负责登录表单的元素定位、数据输入、验证码处理等
 * 支持标准登录页（login.html）和手机验证页（activePhone.html）
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginPageHandler {

    private final BrowserSessionManager browserSessionManager;

    // ===== 标准登录页元素定位器 =====

    private static final By ACCOUNT_LOCATOR = By.xpath(
            "//input[contains(@placeholder, '资金账号')] | " +
            "//input[contains(@placeholder, '账号')] | " +
            "//input[contains(@placeholder, '手机号')] | " +
            "//input[@id='account']"
    );

    private static final By PASSWORD_LOCATOR = By.xpath(
            "//input[contains(@placeholder, '交易密码')] | " +
            "//input[@type='password']"
    );

    private static final By CAPTCHA_INPUT_LOCATOR = By.xpath(
            "//input[contains(@placeholder, '四则运算')] | " +
            "//input[contains(@placeholder, '运算结果')] | " +
            "//input[contains(@placeholder, '验证码')]"
    );

    private static final By CAPTCHA_IMAGE_LOCATOR = By.xpath(
            "//img[@width >= 50 and @width <= 250 and " +
            "@height >= 15 and @height <= 80]"
    );

    private static final By PRIVACY_CHECKBOX = By.xpath(
            "(//input[@type='checkbox'])[1]"
    );
    private static final By AUTHORIZATION_CHECKBOX = By.xpath(
            "(//input[@type='checkbox'])[2]"
    );

    private static final By LOGIN_BUTTON = By.xpath(
            "//button[contains(text(), '登录')] | " +
            "//div[contains(text(), '登录')] | " +
            "//span[contains(text(), '登录')]"
    );

    // ===== 手机验证页元素定位器 =====

    private static final By SMS_CODE_BUTTON = By.xpath(
            "//button[contains(., '获取验证码')] | " +
            "//a[contains(., '获取验证码')] | " +
            "//div[contains(., '获取验证码')] | " +
            "//span[contains(., '获取验证码')] | " +
            "//*[contains(., '点击获取验证码')]"
    );

    private static final By SMS_CODE_INPUT = By.xpath(
            "//input[contains(@placeholder, '手机验证码')] | " +
            "//input[contains(@placeholder, '验证码')]"
    );

    private static final By NEXT_STEP_BUTTON = By.xpath(
            "//button[contains(text(), '下一步')] | " +
            "//a[contains(text(), '下一步')] | " +
            "//div[contains(text(), '下一步')] | " +
            "//*[contains(text(), '下一步')]"
    );

    private static final By CONFIRM_BUTTON = By.xpath(
            "//button[contains(text(), '确定')] | " +
            "//a[contains(text(), '确定')] | " +
            "//div[contains(text(), '确定')] | " +
            "//*[contains(text(), '确定')]"
    );

    /**
     * 输入账号/手机号（自动选择可见的输入框）
     */
    public void inputAccount(String account) {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement accountInput = findVisibleElement(driver, ACCOUNT_LOCATOR);
            browserSessionManager.humanLikeInput(accountInput, account);
            log.info("账号输入完成: {}", account);
        } catch (Exception e) {
            log.error("输入账号失败: {}", e.getMessage());
            throw new BrowserException("输入账号失败", e);
        }
    }

    /**
     * 在匹配元素中找到第一个可见的
     * 使用 JS offsetParent 作为可见性判断（比 Selenium isDisplayed 更准确）
     */
    private WebElement findVisibleElement(WebDriver driver, By locator) {
        List<WebElement> elements = driver.findElements(locator);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        for (WebElement el : elements) {
            try {
                // 用 JS 判断可见性：offsetParent != null 且 offsetWidth > 0
                Boolean visible = (Boolean) js.executeScript(
                        "return arguments[0].offsetParent !== null && arguments[0].offsetWidth > 0", el);
                if (Boolean.TRUE.equals(visible)) {
                    return el;
                }
            } catch (Exception ignored) {
            }
        }
        // 兜底：用 Selenium isDisplayed
        for (WebElement el : elements) {
            try {
                if (el.isDisplayed()) {
                    return el;
                }
            } catch (Exception ignored) {
            }
        }
        throw new NoSuchElementException("未找到可见元素: " + locator);
    }

    /**
     * 输入密码
     */
    public void inputPassword(String password) {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement passwordInput = findVisibleElement(driver, PASSWORD_LOCATOR);
            browserSessionManager.humanLikeInput(passwordInput, password);
            log.info("密码输入完成");
        } catch (Exception e) {
            log.error("输入密码失败: {}", e.getMessage());
            throw new BrowserException("输入密码失败", e);
        }
    }

    /**
     * 输入验证码
     */
    public void inputCaptcha(String captcha) {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement captchaInput = findVisibleElement(driver, CAPTCHA_INPUT_LOCATOR);
            browserSessionManager.humanLikeInput(captchaInput, captcha);
            log.info("验证码输入完成: {}", captcha);
        } catch (Exception e) {
            log.error("输入验证码失败: {}", e.getMessage());
            throw new BrowserException("输入验证码失败", e);
        }
    }

    /**
     * 勾选协议（隐私条款 + 授权书）
     * 页面 checkbox 原生 input 被隐藏（display:none），外层 span.icon_check 作为可视化替代。
     * 策略：使用 jQuery trigger 点击 span.icon_check（页面使用 jQuery 绑定事件）
     */
    public void checkAgreements() {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Long checked = (Long) js.executeScript(
                    "var count = 0;" +
                    "var spans = document.querySelectorAll('span.icon_check');" +
                    "for(var i=0; i<spans.length; i++){" +
                    "  var span = spans[i];" +
                    "  var wrapper = span.parentElement;" +
                    "  if(wrapper && wrapper.offsetParent !== null && wrapper.textContent.indexOf('勾选表示同意') >= 0){" +
                    "    if(typeof jQuery !== 'undefined'){" +
                    "      jQuery(span).trigger('click');" +
                    "    } else {" +
                    "      span.dispatchEvent(new MouseEvent('click', {bubbles:true}));" +
                    "    }" +
                    "    count++;" +
                    "  }" +
                    "}" +
                    "return count;"
            );
            log.info("协议勾选完成，共勾选 {} 个", checked);
        } catch (Exception e) {
            log.error("勾选协议失败: {}", e.getMessage());
        }
    }

    /**
     * 提取并计算数学验证码
     */
    public String calculateMathCaptcha() {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement captchaImg = driver.findElement(CAPTCHA_IMAGE_LOCATOR);
            String altText = captchaImg.getAttribute("alt");
            if (altText != null && altText.matches(".*\\d.*[+\\-×÷].*")) {
                String result = evaluateExpression(altText);
                if (result != null) {
                    log.info("数学验证码计算成功: {} = {}", altText, result);
                    return result;
                }
            }

            try {
                WebElement label = captchaImg.findElement(By.xpath(
                        "preceding-sibling::*[contains(text(), '?')] | " +
                        "following-sibling::*[contains(text(), '?')]"
                ));
                if (label != null) {
                    String result = evaluateExpression(label.getText());
                    if (result != null) {
                        log.info("数学验证码计算成功: {} = {}", label.getText(), result);
                        return result;
                    }
                }
            } catch (Exception e) {
                log.debug("未找到验证码标签: {}", e.getMessage());
            }

            log.warn("无法提取数学算式，需要人工识别");
            return null;
        } catch (Exception e) {
            log.error("计算数学验证码失败: {}", e.getMessage());
            return null;
        }
    }

    private String evaluateExpression(String expr) {
        expr = expr.replace('×', '*').replace('÷', '/');
        Matcher matcher = Pattern.compile("(\\d+)\\s*([+\\-*/])\\s*(\\d+)").matcher(expr);
        if (matcher.find()) {
            int a = Integer.parseInt(matcher.group(1));
            char op = matcher.group(2).charAt(0);
            int b = Integer.parseInt(matcher.group(3));
            int result = switch (op) {
                case '+' -> a + b;
                case '-' -> a - b;
                case '*' -> a * b;
                case '/' -> a / b;
                default -> throw new IllegalArgumentException("未知运算符：" + op);
            };
            return String.valueOf(result);
        }
        return null;
    }

    /**
     * 截图保存验证码图片
     */
    public File captureCaptchaImage() {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement captchaImg = driver.findElement(CAPTCHA_IMAGE_LOCATOR);
            File screenshot = captchaImg.getScreenshotAs(OutputType.FILE);
            Path captchaPath = Paths.get(".tmp/captcha_image.png");
            Files.createDirectories(captchaPath.getParent());
            Files.copy(screenshot.toPath(), captchaPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("验证码图片已保存：{}", captchaPath.toAbsolutePath());
            return captchaPath.toFile();
        } catch (Exception e) {
            log.error("截图保存失败: {}", e.getMessage());
            throw new BrowserException("截图保存失败", e);
        }
    }

    /**
     * 点击登录按钮
     * 策略：jQuery trigger → Selenium 原生点击
     * 页面使用 jQuery 绑定事件，优先使用 jQuery trigger 确保事件被正确触发
     */
    public void clickLoginButton() {
        WebDriver driver = browserSessionManager.getDriver();
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

        // 策略1：jQuery trigger
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "if (typeof jQuery !== 'undefined') {" +
                    "  var btn = jQuery('a:contains(登):visible, button:contains(登):visible').first();" +
                    "  if (btn.length > 0) { btn.trigger('click'); return true; }" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("登录按钮已点击（jQuery trigger）");
                return;
            }
        } catch (Exception e) {
            log.warn("jQuery trigger 点击登录按钮失败: {}", e.getMessage());
        }

        // 策略2：Selenium 原生点击（兜底）
        try {
            WebElement loginButton = findVisibleElement(driver, LOGIN_BUTTON);
            browserSessionManager.safeClick(loginButton);
            log.info("登录按钮已点击（Selenium）");
        } catch (Exception e) {
            log.error("点击登录按钮失败: {}", e.getMessage());
            throw new BrowserException("点击登录按钮失败", e);
        }
    }

    /**
     * 点击「获取验证码」按钮（手机验证页）
     * 策略优先级：jQuery trigger → Selenium 原生点击 → 原生 JS dispatchEvent
     * 注意：该页面使用 jQuery 2.1.1 绑定事件，原生 element.click() 无法触发 jQuery handler
     */
    public void clickSendCodeButton() {
        WebDriver driver = browserSessionManager.getDriver();
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

        // 策略1：jQuery trigger（页面使用 jQuery 绑定事件，这是最可靠的方式）
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "var el = document.getElementById('sendMsg');" +
                    "if (el && typeof jQuery !== 'undefined') {" +
                    "  jQuery(el).trigger('click');" +
                    "  return true;" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("获取验证码按钮已点击（jQuery trigger #sendMsg）");
                return;
            }
        } catch (Exception e) {
            log.warn("jQuery trigger 点击失败: {}", e.getMessage());
        }

        // 策略2：jQuery 文本匹配 + trigger
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "if (typeof jQuery !== 'undefined') {" +
                    "  var btn = jQuery('a:contains(获取验证码):visible, span:contains(获取验证码):visible').first();" +
                    "  if (btn.length > 0) { btn.trigger('click'); return true; }" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("获取验证码按钮已点击（jQuery :contains 匹配）");
                return;
            }
        } catch (Exception e) {
            log.warn("jQuery :contains 点击失败: {}", e.getMessage());
        }

        // 策略3：Selenium 原生点击（会模拟真实鼠标事件）
        try {
            WebElement sendButton = findVisibleElement(driver, SMS_CODE_BUTTON);
            browserSessionManager.safeClick(sendButton);
            log.info("获取验证码按钮已点击（Selenium 原生点击）");
            return;
        } catch (Exception e) {
            log.warn("Selenium 原生点击失败: {}", e.getMessage());
        }

        // 策略4：原生 JS dispatchEvent 模拟完整事件链
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "var el = document.getElementById('sendMsg') || document.querySelector('a.activeBtn');" +
                    "if (el) {" +
                    "  var evt = new MouseEvent('click', {bubbles: true, cancelable: true, view: window});" +
                    "  el.dispatchEvent(evt);" +
                    "  return true;" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("获取验证码按钮已点击（dispatchEvent）");
                return;
            }
        } catch (Exception e) {
            log.error("dispatchEvent 点击也失败: {}", e.getMessage());
        }

        throw new BrowserException("点击获取验证码按钮失败：所有策略均未成功");
    }

    /**
     * 输入短信验证码
     */
    public void inputSmsCode(String code) {
        WebDriver driver = browserSessionManager.getDriver();
        try {
            WebElement codeInput = findVisibleElement(driver, SMS_CODE_INPUT);
            browserSessionManager.humanLikeInput(codeInput, code);
            log.info("短信验证码输入完成");
        } catch (Exception e) {
            log.error("输入短信验证码失败: {}", e.getMessage());
            throw new BrowserException("输入短信验证码失败", e);
        }
    }

    /**
     * 点击「下一步」/「登录」按钮（手机验证页）
     * 策略：jQuery trigger → Selenium 原生点击
     */
    public void clickNextStepButton() {
        WebDriver driver = browserSessionManager.getDriver();
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

        // 策略1：jQuery trigger（与页面框架一致）
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "if (typeof jQuery !== 'undefined') {" +
                    "  var btn = jQuery('a:contains(下一步):visible').first();" +
                    "  if (btn.length > 0) { btn.trigger('click'); return true; }" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("下一步按钮已点击（jQuery trigger）");
                return;
            }
        } catch (Exception e) {
            log.warn("jQuery trigger 点击下一步失败: {}", e.getMessage());
        }

        // 策略2：Selenium 原生点击
        try {
            WebElement nextButton = findVisibleElement(driver, NEXT_STEP_BUTTON);
            browserSessionManager.safeClick(nextButton);
            log.info("下一步按钮已点击（Selenium）");
        } catch (Exception e) {
            log.error("点击下一步按钮失败: {}", e.getMessage());
            throw new BrowserException("点击下一步按钮失败", e);
        }
    }

    /**
     * 点击「确定」按钮（滑块验证后的提示弹窗）
     * 该页面使用 xubox layer 插件，确定按钮 id=pop_tip_alert_btn，文字中有多余空格
     * 策略：ID定位 → jQuery trigger → XPath normalize-space → 原始XPath
     */
    public void clickConfirmButton() {
        WebDriver driver = browserSessionManager.getDriver();
        org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

        // 策略1：通过 xubox 弹窗的固定 ID 定位（最可靠）
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "var btn = document.getElementById('pop_tip_alert_btn');" +
                    "if (btn) {" +
                    "  btn.click();" +
                    "  if (typeof jQuery !== 'undefined') jQuery(btn).trigger('click');" +
                    "  return true;" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("确定按钮已点击（xubox #pop_tip_alert_btn）");
                return;
            }
        } catch (Exception e) {
            log.debug("xubox ID 点击失败: {}", e.getMessage());
        }

        // 策略2：jQuery trigger 搜索 xubox_layer 内的链接
        try {
            Boolean clicked = (Boolean) js.executeScript(
                    "if (typeof jQuery !== 'undefined') {" +
                    "  var btn = jQuery('.xubox_layer a:visible').first();" +
                    "  if (btn.length > 0) { btn.trigger('click'); return true; }" +
                    "}" +
                    "return false;"
            );
            if (Boolean.TRUE.equals(clicked)) {
                log.info("确定按钮已点击（jQuery .xubox_layer a）");
                return;
            }
        } catch (Exception e) {
            log.debug("jQuery xubox 点击失败: {}", e.getMessage());
        }

        // 策略3：XPath normalize-space（处理文字中多余空格的情况）
        try {
            WebElement confirmButton = driver.findElement(By.xpath(
                    "//*[normalize-space(text())='确定'] | " +
                    "//a[contains(@id, 'alert_btn')]"
            ));
            browserSessionManager.safeClick(confirmButton);
            log.info("确定按钮已点击（XPath normalize-space）");
            return;
        } catch (NoSuchElementException e) {
            log.debug("XPath normalize-space 未找到确定按钮");
        }

        // 策略4：原始 XPath（兜底）
        try {
            WebElement confirmButton = driver.findElement(CONFIRM_BUTTON);
            browserSessionManager.safeClick(confirmButton);
            log.info("确定按钮已点击（原始 XPath）");
        } catch (NoSuchElementException e) {
            log.info("未找到确定按钮（可能不需要此步骤）");
        } catch (Exception e) {
            log.warn("点击确定按钮失败: {}", e.getMessage());
        }
    }
}
