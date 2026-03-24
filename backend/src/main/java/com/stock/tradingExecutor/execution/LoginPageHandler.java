package com.stock.tradingExecutor.execution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 登录页元素定位与表单操作，与 {@link BrowserSessionManager} 共用同一 WebDriver。
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginPageHandler {

    private final BrowserSessionManager browserSessionManager;
    private static final int DEFAULT_TIMEOUT = 15;

    private WebDriver requireDriver() {
        if (!browserSessionManager.isRunning()) {
            return null;
        }
        return browserSessionManager.getDriver();
    }

    public boolean inputAccount(String account) {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return false;
        }
        try {
            WebElement accountInput = findAccountInput(driver);
            if (accountInput == null) {
                log.error("[LoginPageHandler] 未找到账号输入框");
                return false;
            }
            humanType(accountInput, account);
            log.info("[LoginPageHandler] 账号输入成功");
            return true;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 账号输入失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean inputPassword(String password) {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return false;
        }
        try {
            WebElement passwordInput = findPasswordInput(driver);
            if (passwordInput == null) {
                log.error("[LoginPageHandler] 未找到密码输入框");
                return false;
            }
            humanType(passwordInput, password);
            log.info("[LoginPageHandler] 密码输入成功");
            return true;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 密码输入失败: {}", e.getMessage());
            return false;
        }
    }

    public String extractCaptchaText() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return null;
        }
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = """
                const elements = document.querySelectorAll('div, span, p');
                for (let element of elements) {
                    const text = element.textContent || element.innerText;
                    if (text && (text.includes('+') || text.includes('-') || text.includes('*') || text.includes('/')) && text.includes('=')) {
                        return text.trim();
                    }
                }
                return null;
            """;
            String captchaText = (String) js.executeScript(script);
            if (captchaText != null) {
                log.info("[LoginPageHandler] 提取到验证码文本: {}", captchaText);
            } else {
                log.warn("[LoginPageHandler] 未找到验证码文本");
            }
            return captchaText;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 提取验证码文本失败: {}", e.getMessage());
            return null;
        }
    }

    public int calculateCaptcha(String captchaText) {
        if (captchaText == null || captchaText.isEmpty()) {
            log.error("[LoginPageHandler] 验证码文本为空");
            return -1;
        }
        try {
            String equation = captchaText.split("=")[0].trim();
            log.info("[LoginPageHandler] 解析算式: {}", equation);
            int result = evaluateExpression(equation);
            log.info("[LoginPageHandler] 计算结果: {} = {}", equation, result);
            return result;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 算式解析失败: {}", e.getMessage());
            return -1;
        }
    }

    private int evaluateExpression(String expression) {
        expression = expression.replaceAll("\\s+", "");
        Pattern pattern = Pattern.compile("(\\d+)([+\\-*/])(\\d+)");
        Matcher matcher = pattern.matcher(expression);
        if (matcher.find()) {
            int a = Integer.parseInt(matcher.group(1));
            String op = matcher.group(2);
            int b = Integer.parseInt(matcher.group(3));
            return switch (op) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> a / b;
                default -> throw new IllegalArgumentException("不支持的运算符: " + op);
            };
        }
        throw new IllegalArgumentException("无法解析表达式: " + expression);
    }

    public boolean inputCaptcha(String captcha) {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return false;
        }
        try {
            WebElement captchaInput = findCaptchaInput(driver);
            if (captchaInput == null) {
                log.error("[LoginPageHandler] 未找到验证码输入框");
                return false;
            }
            humanType(captchaInput, captcha);
            log.info("[LoginPageHandler] 验证码输入成功: {}", captcha);
            return true;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 验证码输入失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean checkAgreement() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return false;
        }
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 勾选所有未选中的 checkbox（《隐私保护条款》和《授权书》）
            String script = """
                const checkboxes = document.querySelectorAll('input[type="checkbox"]');
                let checkedCount = 0;
                for (let checkbox of checkboxes) {
                    if (!checkbox.checked) {
                        checkbox.click();
                        checkbox.dispatchEvent(new Event('change', {bubbles: true}));
                        checkbox.dispatchEvent(new Event('click', {bubbles: true}));
                        checkedCount++;
                    }
                }
                return checkedCount;
            """;
            Long count = (Long) js.executeScript(script);
            if (count != null && count > 0) {
                log.info("[LoginPageHandler] 同意条款勾选成功，勾选了 {} 个复选框", count);
            }
            return true;
        } catch (Exception e) {
            log.error("[LoginPageHandler] 同意条款勾选失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean clickLoginButton() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            log.error("[LoginPageHandler] 驱动未初始化");
            return false;
        }
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String script = """
                const buttons = document.querySelectorAll('button');
                for (let button of buttons) {
                    const text = button.textContent || button.innerText;
                    const className = button.className;
                    if (text.includes('登录') || className.includes('login')) {
                        button.scrollIntoView({behavior: 'smooth', block: 'center'});
                        button.dispatchEvent(new Event('mouseover', {bubbles: true}));
                        button.dispatchEvent(new MouseEvent('mousedown', {bubbles: true, button: 0, buttons: 1}));
                        button.dispatchEvent(new MouseEvent('mouseup', {bubbles: true, button: 0, buttons: 0}));
                        button.click();
                        return true;
                    }
                }
                const divs = document.querySelectorAll('div');
                for (let div of divs) {
                    const text = div.textContent || div.innerText;
                    const className = div.className;
                    if (text.includes('登录') && (className.includes('btn') || className.includes('login'))) {
                        div.scrollIntoView({behavior: 'smooth', block: 'center'});
                        div.click();
                        return true;
                    }
                }
                return false;
            """;
            Boolean result = (Boolean) js.executeScript(script);
            if (Boolean.TRUE.equals(result)) {
                log.info("[LoginPageHandler] 点击登录按钮成功");
                return true;
            } else {
                log.warn("[LoginPageHandler] 未找到登录按钮");
                return false;
            }
        } catch (Exception e) {
            log.error("[LoginPageHandler] 点击登录按钮失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean isOnLoginPage() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            return false;
        }
        String url = driver.getCurrentUrl();
        return url != null && url.contains("login.html");
    }

    public boolean isLoginSuccess() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            return false;
        }
        String url = driver.getCurrentUrl();
        return url != null && !url.contains("login.html") && !url.contains("activePhone");
    }

    private WebElement findAccountInput(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT));
        // 1. 优先：placeholder 含 "资金账号"（实际页面: "请输入资金账号"）
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[placeholder*='资金账号']")));
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 通过'资金账号'占位符查找失败，尝试其他方式");
        }
        // 2. id/name
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[id='account'], input[name='account']")));
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 通过ID/Name查找失败，遍历查找");
        }
        // 3. 遍历 input，匹配 placeholder 含 "账号"
        try {
            List<WebElement> inputs = driver.findElements(By.tagName("input"));
            for (WebElement input : inputs) {
                String placeholder = input.getAttribute("placeholder");
                if (placeholder != null && (placeholder.contains("账号") || placeholder.contains("资金"))) {
                    return input;
                }
            }
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 遍历查找账号输入框失败: {}", e.getMessage());
        }
        return null;
    }

    private WebElement findPasswordInput(WebDriver driver) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(DEFAULT_TIMEOUT));
        // 1. 优先：placeholder 含 "交易密码"（实际页面: "请输入交易密码"）
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[placeholder*='交易密码']")));
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 通过'交易密码'占位符查找失败，尝试type查找");
        }
        // 2. type=password
        try {
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[type='password']")));
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 通过type=password查找失败，遍历查找");
        }
        // 3. 遍历匹配
        try {
            List<WebElement> inputs = driver.findElements(By.tagName("input"));
            for (WebElement input : inputs) {
                String placeholder = input.getAttribute("placeholder");
                String type = input.getAttribute("type");
                if ((placeholder != null && placeholder.contains("密码")) || "password".equals(type)) {
                    return input;
                }
            }
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 遍历查找密码输入框失败: {}", e.getMessage());
        }
        return null;
    }

    private WebElement findCaptchaInput(WebDriver driver) {
        // 1. 优先：placeholder 含 "四则运算"（实际页面: "请输入四则运算的运算结果"）
        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));
            return wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[placeholder*='四则运算'], input[placeholder*='运算结果']")));
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 通过'四则运算'占位符查找失败，遍历查找");
        }
        // 2. 遍历匹配
        try {
            List<WebElement> inputs = driver.findElements(By.tagName("input"));
            for (WebElement input : inputs) {
                String placeholder = input.getAttribute("placeholder");
                if (placeholder != null && (placeholder.contains("运算") || placeholder.contains("结果")
                        || placeholder.contains("验证码"))) {
                    return input;
                }
            }
        } catch (Exception e) {
            log.debug("[LoginPageHandler] 查找验证码输入框失败: {}", e.getMessage());
        }
        return null;
    }

    private void humanType(WebElement element, String text) {
        WebDriver driver = requireDriver();
        if (driver == null) {
            return;
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        // 先用 JS 聚焦
        try {
            js.executeScript("arguments[0].scrollIntoView({block:'center'}); arguments[0].focus(); arguments[0].value = '';", element);
            Thread.sleep(200);
        } catch (Exception ignored) {
        }
        // 尝试 sendKeys
        try {
            element.clear();
            for (int i = 0; i < text.length(); i++) {
                element.sendKeys(String.valueOf(text.charAt(i)));
                js.executeScript("arguments[0].dispatchEvent(new Event('input', {bubbles: true}));", element);
                Thread.sleep(80 + (int) (Math.random() * 60));
            }
            return;
        } catch (Exception e) {
            log.debug("[LoginPageHandler] sendKeys 失败，回退到 JS 输入: {}", e.getMessage());
        }
        // 回退到 JS 输入
        try {
            js.executeScript("arguments[0].value = '';", element);
            for (int i = 0; i < text.length(); i++) {
                String current = text.substring(0, i + 1);
                js.executeScript(
                        "arguments[0].value = arguments[1]; arguments[0].dispatchEvent(new Event('input', {bubbles:true}));",
                        element, current);
                Thread.sleep(80 + (int) (Math.random() * 60));
            }
            js.executeScript("arguments[0].dispatchEvent(new Event('change', {bubbles:true}));", element);
        } catch (Exception ex) {
            log.error("[LoginPageHandler] JS 输入也失败: {}", ex.getMessage());
        }
    }

    public void simulateHumanBehavior() {
        simulateMouseMove();
        browserSessionManager.randomWait(1, 2);
    }

    private void simulateMouseMove() {
        WebDriver driver = requireDriver();
        if (driver == null) {
            return;
        }
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("""
            const startX = Math.random() * window.innerWidth * 0.8 + window.innerWidth * 0.1;
            const startY = Math.random() * window.innerHeight * 0.8 + window.innerHeight * 0.1;
            const endX = Math.random() * window.innerWidth * 0.8 + window.innerWidth * 0.1;
            const endY = Math.random() * window.innerHeight * 0.8 + window.innerHeight * 0.1;
            for (let i = 0; i < 15; i++) {
                const x = startX + (endX - startX) * i / 15 + (Math.random() - 0.5) * 15;
                const y = startY + (endY - startY) * i / 15 + (Math.random() - 0.5) * 15;
                document.dispatchEvent(new MouseEvent('mousemove', {clientX: x, clientY: y, bubbles: true}));
            }
        """);
    }
}
