package com.stock.tradingExecutor.execution;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Random;

/**
 * 中信证券 Web 登录自动化编排：手机验证页、账号密码、算术/图片验证码、条款、滑块与 Token 读取。
 *
 * @author mwangli
 * @since 2026-03-22
 */
@Slf4j
@Service
public class AutoLoginService {

    private static final int MAX_LOGIN_RETRY = 3;
    /** 图片验证码人工输入重试（重新截图并等待文件） */
    private static final int MAX_IMAGE_CAPTCHA_RETRY = 5;
    private static final int MAX_SLIDER_RETRY = 3;
    private static final int CAPTCHA_WAIT_FIRST_SEC = 300;
    private static final int CAPTCHA_WAIT_RETRY_SEC = 120;

    private final BrowserSessionManager browserSessionManager;
    private final LoginPageHandler loginPageHandler;
    private final CookieManager cookieManager;
    private final CaptchaService captchaService;
    private final ZXRequestUtils requestUtils;
    private final Random random = new Random();

    public AutoLoginService(BrowserSessionManager browserSessionManager,
                            LoginPageHandler loginPageHandler,
                            CookieManager cookieManager,
                            CaptchaService captchaService,
                            ZXRequestUtils requestUtils) {
        this.browserSessionManager = browserSessionManager;
        this.loginPageHandler = loginPageHandler;
        this.cookieManager = cookieManager;
        this.captchaService = captchaService;
        this.requestUtils = requestUtils;
    }

    /**
     * 执行完整登录流程，失败时按配置重试。
     *
     * @param username 账号/手机号
     * @param password 密码（勿记录到日志）
     * @return 是否登录成功
     */
    public boolean login(String username, String password) {
        log.info("[AutoLoginService] 开始自动化登录流程");
        for (int attempt = 0; attempt < MAX_LOGIN_RETRY; attempt++) {
            log.info("[AutoLoginService] 第 {} 次登录尝试", attempt + 1);
            if (performLogin(username, password)) {
                log.info("[AutoLoginService] 登录成功");
                return true;
            }
            log.warn("[AutoLoginService] 第 {} 次登录失败", attempt + 1);
            browserSessionManager.randomWait(2, 3);
        }
        log.error("[AutoLoginService] 登录失败，已达到最大重试次数");
        return false;
    }

    private boolean performLogin(String username, String password) {
        try {
            browserSessionManager.startBrowser();

            browserSessionManager.injectFingerprint();
            browserSessionManager.visitLoginPage();
            Thread.sleep(3000 + (long) (Math.random() * 4000));
            simulateHumanBrowse();

            Thread.sleep(4000 + (long) (Math.random() * 5000));

            loginPageHandler.simulateHumanBehavior();

            if (browserSessionManager.isOnActivePhonePage()) {
                log.info("[AutoLoginService] 检测到新设备/新环境，需要进行手机验证码验证");
                if (!handlePhoneVerification(username)) {
                    log.error("[AutoLoginService] 手机验证码验证失败");
                    return false;
                }
                Thread.sleep(3000 + (long) (Math.random() * 5000));
            }

            if (!browserSessionManager.inputAccount(username)) {
                log.error("[AutoLoginService] 账号输入失败");
                return false;
            }
            Thread.sleep(1000 + (long) (Math.random() * 2000));
            loginPageHandler.simulateHumanBehavior();

            if (!browserSessionManager.inputPassword(password)) {
                log.error("[AutoLoginService] 密码输入失败");
                return false;
            }
            Thread.sleep(1000 + (long) (Math.random() * 2000));
            loginPageHandler.simulateHumanBehavior();

            if (!handleCaptchaStep()) {
                log.warn("[AutoLoginService] 验证码步骤未通过");
            }
            Thread.sleep(1000 + (long) (Math.random() * 2000));
            loginPageHandler.simulateHumanBehavior();

            if (!browserSessionManager.checkPrivacyAgreement() || !browserSessionManager.checkAuthAgreement()) {
                log.warn("[AutoLoginService] 同意条款勾选失败，尝试继续");
            }
            Thread.sleep(1000 + (long) (Math.random() * 2000));

            if (!browserSessionManager.clickLoginButton()) {
                log.error("[AutoLoginService] 登录按钮点击失败");
                return false;
            }
            Thread.sleep(4000 + (long) (Math.random() * 5000));

            if (browserSessionManager.isLoginSuccess()) {
                log.info("[AutoLoginService] 登录成功检测(URL)");
                return completeSuccessfulLogin();
            }

            if (handleSliderCaptcha()) {
                browserSessionManager.randomWait(3, 4);
            }

            syncTokenFromBrowser();
            boolean byUrl = loginPageHandler.isLoginSuccess() || browserSessionManager.isLoginSuccess();
            String tok = cookieManager.getToken();
            boolean byToken = tok != null && !tok.isBlank();
            if (byUrl || byToken) {
                if (byToken) {
                    requestUtils.setToken(tok);
                }
                log.info("[AutoLoginService] 登录成功(URL={}, Token={})", byUrl, byToken);
                return completeSuccessfulLogin();
            }
            return false;
        } catch (Exception e) {
            log.error("[AutoLoginService] 登录流程异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 登录成功收尾：多次尝试从浏览器解析 Token 并写入 {@link ZXRequestUtils}，供后续 HTTP 接口复用。
     */
    private boolean completeSuccessfulLogin() {
        syncTokenFromBrowser();
        return true;
    }

    private void syncTokenFromBrowser() {
        browserSessionManager.switchToDefaultContent();
        for (int i = 0; i < 8; i++) {
            String t = cookieManager.getToken();
            if (t != null && !t.isBlank()) {
                requestUtils.setToken(t);
                log.info("[AutoLoginService] Token 已同步到 ZXRequestUtils，长度={}", t.length());
                return;
            }
            try {
                Thread.sleep(600);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("[AutoLoginService] 登录已成功但暂未解析到 Token，可检查 Cookie 命名或稍后重试 status 接口");
    }

    /**
     * 优先处理页面上的四则运算验证码；否则走截图 + 人工写入 captcha_code.txt。
     *
     * @return 是否认为验证码步骤成功（无图片验证码时视为成功）
     */
    private boolean handleCaptchaStep() {
        try {
            if (!browserSessionManager.isOnLoginPage()) {
                log.info("[AutoLoginService] 不在登录页面，跳过验证码处理");
                return true;
            }

            String mathText = loginPageHandler.extractCaptchaText();
            if (mathText != null && mathText.contains("=")) {
                int result = loginPageHandler.calculateCaptcha(mathText);
                if (result >= 0) {
                    boolean ok = loginPageHandler.inputCaptcha(String.valueOf(result));
                    log.info("[AutoLoginService] 四则运算验证码 {}", ok ? "成功" : "失败");
                    return ok;
                }
            }

            for (int attempt = 0; attempt < MAX_IMAGE_CAPTCHA_RETRY; attempt++) {
                String captchaPath = browserSessionManager.captureCaptchaImage();
                if (captchaPath == null) {
                    log.info("[AutoLoginService] 未检测到图片验证码，可能不需要");
                    return true;
                }
                int waitSec = attempt == 0 ? CAPTCHA_WAIT_FIRST_SEC : CAPTCHA_WAIT_RETRY_SEC;
                String captchaCode = browserSessionManager.waitForCaptchaCode(waitSec);
                if (captchaCode == null || captchaCode.isEmpty()) {
                    log.warn("[AutoLoginService] 验证码输入超时或为空");
                    return false;
                }
                if (browserSessionManager.inputCaptcha(captchaCode)) {
                    log.info("[AutoLoginService] 图片验证码处理成功");
                    return true;
                }
                log.warn("[AutoLoginService] 图片验证码输入失败，重试 {}/{}", attempt + 1, MAX_IMAGE_CAPTCHA_RETRY);
                try {
                    java.nio.file.Files.deleteIfExists(browserSessionManager.getAutoLoginTmpDir().resolve("captcha_code.txt"));
                } catch (Exception ignored) {
                }
            }
            return false;
        } catch (Exception e) {
            log.error("[AutoLoginService] 验证码处理异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 登录提交后的滑块：无滑块则视为不需要。
     */
    private boolean handleSliderCaptcha() {
        log.info("[AutoLoginService] 检查滑块验证码（登录流程）");
        browserSessionManager.switchToDefaultContent();
        WebDriver driver = browserSessionManager.getDriver();
        if (driver == null) {
            return false;
        }
        if (!isSliderCaptchaPresent()) {
            log.info("[AutoLoginService] 未检测到滑块验证码");
            return true;
        }
        return runSliderCaptchaSolveLoop();
    }

    /**
     * 手机验证页点击「获取验证码」后会出现滑块：必须等待出现并完成拖动。
     */
    private boolean handleSliderCaptchaMandatoryForPhonePage() throws InterruptedException {
        log.info("[AutoLoginService] 等待获取验证码后的滑块出现…");
        long deadline = System.currentTimeMillis() + 18_000;
        boolean seen = false;
        while (System.currentTimeMillis() < deadline) {
            browserSessionManager.switchToDefaultContent();
            if (isSliderCaptchaPresent()) {
                seen = true;
                break;
            }
            browserSessionManager.ensureActivePhoneFrame();
            if (isSliderCaptchaPresent()) {
                seen = true;
                break;
            }
            Thread.sleep(400);
        }
        if (!seen) {
            log.error("[AutoLoginService] 超时未检测到滑块（获取验证码后）");
            return false;
        }
        browserSessionManager.switchToDefaultContent();
        if (isSliderCaptchaPresent() && runSliderCaptchaSolveLoop()) {
            return true;
        }
        log.info("[AutoLoginService] 在顶层未完成滑块，切换到手机验证表单上下文重试");
        browserSessionManager.ensureActivePhoneFrame();
        if (isSliderCaptchaPresent()) {
            return runSliderCaptchaSolveLoop();
        }
        log.info("[AutoLoginService] 滑块层已关闭，继续短信验证流程");
        return true;
    }

    /**
     * 在滑块已存在的前提下，多次尝试计算距离并拖动直至消失或次数用尽。
     */
    /**
     * 在滑块已存在的前提下，多次尝试计算距离并拖动直至消失或次数用尽。
     * 支持网易云盾距离修正（页面渲染尺寸 vs 图片原始尺寸）和验证结果检测。
     */
    private boolean runSliderCaptchaSolveLoop() {
        WebDriver driver = browserSessionManager.getDriver();
        if (driver == null) {
            return false;
        }
        for (int retry = 0; retry < MAX_SLIDER_RETRY; retry++) {
            try {
                if (!isSliderCaptchaPresent()) {
                    log.info("[AutoLoginService] 滑块已消失，视为通过");
                    return true;
                }

                SliderCaptchaData captchaData = extractSliderCaptchaData();
                if (captchaData == null) {
                    log.warn("[AutoLoginService] 滑块验证码数据提取失败");
                    Thread.sleep(2000 + (long) (Math.random() * 3000));
                    continue;
                }

                // 1. 计算原始距离
                int distance = captchaService.calculateDistance(captchaData.bgImage, captchaData.sliderImage);
                if (distance <= 0) {
                    distance = captchaService.calculateDistanceByEdge(captchaData.bgImage);
                }
                if (distance <= 0) {
                    log.warn("[AutoLoginService] 滑块距离计算失败: {}", distance);
                    Thread.sleep(2000 + (long) (Math.random() * 3000));
                    continue;
                }

                // 2. 网易云盾距离修正：页面渲染宽度 vs 图片原始宽度
                int correctedDistance = distance;
                if (captchaData.isYidun && captchaData.renderWidth > 0) {
                    int imageNativeWidth = captchaService.getImageWidth(captchaData.bgImage);
                    if (imageNativeWidth > 0 && imageNativeWidth != captchaData.renderWidth) {
                        correctedDistance = (int) Math.round(
                                (double) distance * captchaData.renderWidth / imageNativeWidth);
                        log.info("[AutoLoginService] 网易云盾距离修正: 原始={}px, 修正后={}px (渲染宽度={}, 图片宽度={})",
                                distance, correctedDistance, captchaData.renderWidth, imageNativeWidth);
                    }
                }

                int dragDistance = Math.max(1, correctedDistance - 10);
                log.info("[AutoLoginService] 滑块距离: {}px → 拖动距离: {}px (yidun={})",
                        correctedDistance, dragDistance, captchaData.isYidun);

                if (executeSliderDrag(driver, dragDistance)) {
                    log.info("[AutoLoginService] 滑块滑动已执行");
                    Thread.sleep(2000 + (long) (Math.random() * 2000));

                    // 3. 检测验证结果
                    if (captchaData.isYidun && checkYidunResult(driver)) {
                        log.info("[AutoLoginService] 网易云盾滑块验证通过");
                        return true;
                    }
                    if (!isSliderCaptchaPresent()) {
                        log.info("[AutoLoginService] 滑块已消失，视为通过");
                        return true;
                    }
                }

                log.warn("[AutoLoginService] 第 {} 次滑块验证未通过，等待重试", retry + 1);
                // 等待网易云盾刷新图片
                Thread.sleep(2000 + (long) (Math.random() * 3000));
            } catch (Exception e) {
                log.error("[AutoLoginService] 滑块验证码处理异常: {}", e.getMessage());
            }
        }
        return false;
    }

    /**
     * 检测网易云盾滑块验证结果。
     * 依次检查：提示文本 → 面板消失 → URL 跳转。
     *
     * @param driver WebDriver 实例
     * @return 验证是否通过
     */
    private boolean checkYidunResult(WebDriver driver) {
        try {
            // 方式1：检查网易云盾的提示文本
            List<WebElement> tipsList = driver.findElements(
                    By.cssSelector(".yidun_tips__text, [class*='yidun_tips'] [class*='text']"));
            for (WebElement tips : tipsList) {
                try {
                    String text = tips.getText();
                    if (text != null && (text.contains("成功") || text.contains("通过"))) {
                        return true;
                    }
                } catch (Exception ignored) {
                }
            }

            // 方式2：检查云盾面板是否消失或不可见
            List<WebElement> panels = driver.findElements(
                    By.cssSelector(".yidun_panel, .yidun, [class*='yidun']"));
            boolean allHidden = true;
            for (WebElement panel : panels) {
                try {
                    if (panel.isDisplayed()) {
                        allHidden = false;
                        break;
                    }
                } catch (Exception ignored) {
                    // 元素已从 DOM 移除，视为消失
                }
            }
            if (panels.isEmpty() || allHidden) {
                return true;
            }

            // 方式3：检查 URL 是否已跳转（登录成功）
            String url = driver.getCurrentUrl();
            return url != null && !url.contains("login.html");
        } catch (Exception e) {
            log.debug("[AutoLoginService] 检测网易云盾验证结果异常: {}", e.getMessage());
            // 异常时检查 URL 作为最后手段
            try {
                return !driver.getCurrentUrl().contains("login.html");
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private boolean executeSliderDrag(WebDriver driver, int distance) {
        WebElement handle = findSliderHandle(driver);
        if (handle == null) {
            log.warn("[AutoLoginService] 未找到滑块拖动元素");
            return false;
        }
        List<int[]> cumulative = captchaService.generateEaseSlideTrack(distance, random);
        Actions chain = new Actions(driver);
        chain.moveToElement(handle).pause(Duration.ofMillis(200 + random.nextInt(200)));
        chain.clickAndHold(handle);
        int lastX = 0;
        int lastY = 0;
        for (int[] pt : cumulative) {
            int dx = pt[0] - lastX;
            int dy = pt[1] - lastY;
            chain.moveByOffset(dx, dy).pause(Duration.ofMillis(15 + random.nextInt(11)));
            lastX = pt[0];
            lastY = pt[1];
        }
        chain.release().perform();
        return true;
    }

    /**
     * 定位滑块拖动手柄元素。
     * 优先检测网易云盾（yidun）选择器，兼容通用滑块。
     *
     * @param driver WebDriver 实例
     * @return 可拖动的滑块手柄元素；未找到返回 null
     */
    private WebElement findSliderHandle(WebDriver driver) {
        By[] locators = {
                // 网易云盾（yidun）选择器 — 优先
                By.cssSelector(".yidun_slider__handler"),
                By.cssSelector(".yidun_btn"),
                By.cssSelector("[class*='yidun'] [class*='handler']"),
                By.cssSelector("[class*='yidun'] [class*='btn']"),
                // 通用滑块选择器 — 兼容
                By.cssSelector(".nc_iconfont.btn_slide"),
                By.cssSelector(".slider-handle"),
                By.cssSelector("[class*='slider'] [class*='handle']"),
                By.cssSelector(".nc_scale span"),
                By.xpath("//div[contains(@class,'nc_scale')]//span[contains(@class,'btn')]")
        };
        for (By locator : locators) {
            List<WebElement> list = driver.findElements(locator);
            for (WebElement el : list) {
                try {
                    if (el.isDisplayed() && el.getSize().getWidth() > 0) {
                        log.debug("[AutoLoginService] 找到滑块手柄: {}", locator);
                        return el;
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    /**
     * 新设备手机验证页（activePhone.html）完整流程：
     * 填写手机号 → 点击「点击获取验证码」→ 等待并完成滑块 → 短信码 → 勾选协议 → 「下一步」。
     *
     * @param mobile 与登录一致的手机号
     */
    private boolean handlePhoneVerification(String mobile) {
        log.info("[AutoLoginService] 开始手机验证页流程（填手机号→获取验证码→滑块→短信→条款→下一步）");

        try {
            browserSessionManager.prepareActivePhonePage();
            browserSessionManager.randomWait(1, 2);

            if (!browserSessionManager.inputPhoneNumberForVerification(mobile)) {
                log.error("[AutoLoginService] 手机验证页填写手机号失败");
                return false;
            }
            browserSessionManager.randomWait(1, 2);
            loginPageHandler.simulateHumanBehavior();

            if (!browserSessionManager.clickSendCodeButton()) {
                log.error("[AutoLoginService] 点击获取验证码失败");
                return false;
            }

            browserSessionManager.randomWait(1, 2);
            if (!handleSliderCaptchaMandatoryForPhonePage()) {
                log.error("[AutoLoginService] 获取验证码后的滑块验证失败");
                return false;
            }

            String smsCode = browserSessionManager.waitForSmsCode();
            if (smsCode == null || smsCode.isEmpty()) {
                log.error("[AutoLoginService] 手机验证码输入超时或为空");
                return false;
            }

            browserSessionManager.ensureActivePhoneFrame();
            if (!browserSessionManager.inputSmsCode(smsCode)) {
                log.error("[AutoLoginService] 手机验证码输入失败");
                return false;
            }

            if (!browserSessionManager.checkPrivacyAgreementOnCurrentForm()) {
                log.warn("[AutoLoginService] 隐私条款勾选失败，尝试继续");
            }
            if (!browserSessionManager.checkAuthAgreementOnCurrentForm()) {
                log.warn("[AutoLoginService] 授权书勾选失败，尝试继续");
            }
            browserSessionManager.randomWait(1, 2);

            if (!browserSessionManager.submitPhoneVerification()) {
                log.error("[AutoLoginService] 手机验证「下一步」失败");
                return false;
            }

            log.info("[AutoLoginService] 手机验证页流程完成");
            return true;

        } catch (Exception e) {
            log.error("[AutoLoginService] 手机验证码处理异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 检测滑块验证码是否存在（优先检测网易云盾，兼容通用滑块）
     *
     * @return 是否存在可见的滑块验证码
     */
    private boolean isSliderCaptchaPresent() {
        try {
            WebDriver driver = browserSessionManager.getDriver();
            if (driver == null) {
                return false;
            }
            JavascriptExecutor js = (JavascriptExecutor) driver;
            // 1. 优先检测网易云盾（yidun）特征
            String script = """
                    var yidun = document.querySelector('.yidun_panel, .yidun, [class*="yidun"]');
                    if (yidun) {
                        var style = window.getComputedStyle(yidun);
                        if (style.display !== 'none' && style.visibility !== 'hidden') {
                            return 'yidun';
                        }
                    }
                    var elements = document.querySelectorAll('div');
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        var cn = el.className || '';
                        var id = el.id || '';
                        var st = window.getComputedStyle(el);
                        if ((cn.includes('slider') || cn.includes('captcha') ||
                             cn.includes('verify') || cn.includes('nc_') ||
                             id.includes('slider') || id.includes('captcha')) &&
                            st.width && parseInt(st.width, 10) > 200 &&
                            st.display !== 'none' && st.visibility !== 'hidden') {
                            return 'generic';
                        }
                    }
                    return null;
                    """;
            String result = (String) js.executeScript(script);
            if (result != null) {
                log.debug("[AutoLoginService] 检测到滑块验证码类型: {}", result);
            }
            return result != null;
        } catch (Exception e) {
            log.debug("[AutoLoginService] 检测滑块验证码异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 提取滑块验证码图片数据。
     * 优先尝试网易云盾（yidun）特有的 DOM 结构，回退到通用检测逻辑。
     *
     * @return 包含背景图、拼图图片及渲染尺寸的数据对象；提取失败返回 null
     */
    private SliderCaptchaData extractSliderCaptchaData() {
        try {
            WebDriver driver = browserSessionManager.getDriver();
            if (driver == null) {
                return null;
            }
            JavascriptExecutor js = (JavascriptExecutor) driver;

            // 1. 优先尝试网易云盾（yidun）图片提取
            SliderCaptchaData data = extractYidunCaptchaData(js);

            // 2. 回退到通用检测逻辑
            if (data == null) {
                data = extractGenericCaptchaData(js);
            }

            if (data == null) {
                log.debug("[AutoLoginService] 未提取到滑块验证码图片URL");
                return null;
            }

            // 3. 下载图片
            if (data.bgUrl != null) {
                data.bgImage = requestUtils.getCaptchaImage(data.bgUrl);
            }
            if (data.sliderUrl != null) {
                data.sliderImage = requestUtils.getCaptchaImage(data.sliderUrl);
            }

            if (data.bgImage == null || data.bgImage.length == 0
                    || data.sliderImage == null || data.sliderImage.length == 0) {
                log.warn("[AutoLoginService] 滑块图片下载失败 (bgUrl={}, sliderUrl={})", data.bgUrl, data.sliderUrl);
                return null;
            }

            log.info("[AutoLoginService] 滑块图片下载成功: bg={} bytes, slider={} bytes, renderWidth={}",
                    data.bgImage.length, data.sliderImage.length, data.renderWidth);
            return data;
        } catch (Exception e) {
            log.error("[AutoLoginService] 提取滑块验证码数据异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从网易云盾（yidun）DOM 结构提取背景图和拼图图片 URL。
     * 支持 img src 和 CSS background-image 两种方式。
     *
     * @param js JavascriptExecutor
     * @return 提取到的数据；无 yidun 元素时返回 null
     */
    private SliderCaptchaData extractYidunCaptchaData(JavascriptExecutor js) {
        String script = """
                (function() {
                    var result = {bg: null, slider: null, renderWidth: 0};

                    // 方式1：从 img 标签的 src 属性获取
                    var bgImgSelectors = [
                        '.yidun_bg-img img', '.yidun_bgimg img',
                        '.yidun--jigsaw .yidun_bg-img img',
                        '[class*="yidun_bg"] img', '[class*="yidun_bgimg"] img'
                    ];
                    var sliderImgSelectors = [
                        '.yidun_jigsaw img', '.yidun_slider__icon img',
                        '.yidun--jigsaw .yidun_jigsaw img',
                        '[class*="yidun_jigsaw"] img', '[class*="yidun_front"] img'
                    ];

                    for (var i = 0; i < bgImgSelectors.length; i++) {
                        var el = document.querySelector(bgImgSelectors[i]);
                        if (el && el.src) { result.bg = el.src; break; }
                    }
                    for (var i = 0; i < sliderImgSelectors.length; i++) {
                        var el = document.querySelector(sliderImgSelectors[i]);
                        if (el && el.src) { result.slider = el.src; break; }
                    }

                    // 方式2：从 CSS background-image 获取
                    if (!result.bg) {
                        var bgDivSelectors = [
                            '.yidun_bg-img', '.yidun_bgimg',
                            '[class*="yidun_bg"]', '[class*="yidun_bgimg"]'
                        ];
                        for (var i = 0; i < bgDivSelectors.length; i++) {
                            var el = document.querySelector(bgDivSelectors[i]);
                            if (el) {
                                var bg = getComputedStyle(el).backgroundImage;
                                if (bg && bg !== 'none') {
                                    var m = bg.match(/url\\(["']?([^"']+)["']?\\)/);
                                    if (m && m[1]) { result.bg = m[1]; break; }
                                }
                            }
                        }
                    }
                    if (!result.slider) {
                        var sliderDivSelectors = [
                            '.yidun_jigsaw', '.yidun_slider__icon',
                            '[class*="yidun_jigsaw"]', '[class*="yidun_front"]'
                        ];
                        for (var i = 0; i < sliderDivSelectors.length; i++) {
                            var el = document.querySelector(sliderDivSelectors[i]);
                            if (el) {
                                var bg = getComputedStyle(el).backgroundImage;
                                if (bg && bg !== 'none') {
                                    var m = bg.match(/url\\(["']?([^"']+)["']?\\)/);
                                    if (m && m[1]) { result.slider = m[1]; break; }
                                }
                            }
                        }
                    }

                    // 获取渲染宽度，用于距离修正
                    var bgContainer = document.querySelector(
                        '.yidun_bg-img, .yidun_bgimg, [class*="yidun_bg"]'
                    );
                    if (bgContainer) {
                        result.renderWidth = bgContainer.offsetWidth || 0;
                    }

                    if (!result.bg && !result.slider) return null;
                    return JSON.stringify(result);
                })()
                """;
        try {
            String dataStr = (String) js.executeScript(script);
            if (dataStr == null || "null".equals(dataStr)) {
                return null;
            }
            JSONObject json = JSONObject.parseObject(dataStr);
            SliderCaptchaData data = new SliderCaptchaData();
            data.bgUrl = json.getString("bg");
            data.sliderUrl = json.getString("slider");
            data.renderWidth = json.getIntValue("renderWidth");
            data.isYidun = true;
            if (data.bgUrl != null || data.sliderUrl != null) {
                log.info("[AutoLoginService] 网易云盾图片提取成功: bgUrl={}, sliderUrl={}, renderWidth={}",
                        data.bgUrl != null ? "已获取" : "null",
                        data.sliderUrl != null ? "已获取" : "null",
                        data.renderWidth);
                return data;
            }
        } catch (Exception e) {
            log.debug("[AutoLoginService] 网易云盾图片提取异常: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 通用滑块图片提取逻辑（非网易云盾场景的回退方案）。
     *
     * @param js JavascriptExecutor
     * @return 提取到的数据；无匹配元素时返回 null
     */
    private SliderCaptchaData extractGenericCaptchaData(JavascriptExecutor js) {
        String script = """
                (function() {
                    var result = {bg: null, slider: null};
                    var elements = document.querySelectorAll('div, img');
                    for (var i = 0; i < elements.length; i++) {
                        var el = elements[i];
                        var cn = el.className || '';
                        var st = window.getComputedStyle(el);
                        if ((cn.includes('bg') || cn.includes('background') || cn.includes('block'))
                            && st.width && parseInt(st.width, 10) > 200) {
                            var bgUrl = el.style.backgroundImage || el.src
                                        || el.getAttribute('data-bg');
                            if (bgUrl && bgUrl.startsWith('url')) {
                                var m = bgUrl.match(/url\\(["']?([^"']+)["']?\\)/);
                                if (m && m[1]) { result.bg = m[1]; }
                            } else if (bgUrl && (bgUrl.includes('.jpg') || bgUrl.includes('.png'))) {
                                result.bg = bgUrl;
                            }
                        }
                        if (cn.includes('slider') || cn.includes('front')
                            || cn.includes('target') || cn.includes('piece')) {
                            var sUrl = el.style.backgroundImage || el.src
                                       || el.getAttribute('data-slider');
                            if (sUrl && sUrl.startsWith('url')) {
                                var m = sUrl.match(/url\\(["']?([^"']+)["']?\\)/);
                                if (m && m[1]) { result.slider = m[1]; }
                            } else if (sUrl && (sUrl.includes('.jpg') || sUrl.includes('.png'))) {
                                result.slider = sUrl;
                            }
                        }
                    }
                    if (!result.bg && !result.slider) {
                        var imgs = document.querySelectorAll('img');
                        for (var i = 0; i < imgs.length; i++) {
                            var src = imgs[i].src; var alt = imgs[i].alt || '';
                            if (alt.includes('bg') || src.includes('bg')) result.bg = src;
                            if (alt.includes('slider') || alt.includes('front')
                                || src.includes('slider') || src.includes('front')) result.slider = src;
                        }
                    }
                    if (result.bg || result.slider) return JSON.stringify(result);
                    return null;
                })()
                """;
        try {
            String dataStr = (String) js.executeScript(script);
            if (dataStr == null || "null".equals(dataStr)) {
                return null;
            }
            JSONObject json = JSONObject.parseObject(dataStr);
            SliderCaptchaData data = new SliderCaptchaData();
            data.bgUrl = json.getString("bg");
            data.sliderUrl = json.getString("slider");
            data.isYidun = false;
            return data;
        } catch (Exception e) {
            log.debug("[AutoLoginService] 通用滑块图片提取异常: {}", e.getMessage());
            return null;
        }
    }

    private void simulateHumanBrowse() {
        try {
            WebDriver driver = browserSessionManager.getDriver();
            if (driver == null) {
                return;
            }
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("""
                    const scrollSteps = Math.floor(Math.random() * 4) + 2;
                    for (let i = 0; i < scrollSteps; i++) {
                        window.scrollBy(0, Math.random() * window.innerHeight * 0.6);
                    }
                    window.scrollTo(0, 0);
                    """);
            Thread.sleep(1000 + (long) (Math.random() * 2000));
        } catch (Exception e) {
            log.debug("[AutoLoginService] 模拟浏览行为异常: {}", e.getMessage());
        }
    }

    /**
     * 是否已登录：以浏览器 URL 与 Cookie 中 Token 为准。
     *
     * @return 是否已登录
     */
    public boolean isLoggedIn() {
        return browserSessionManager.isRunning() && browserSessionManager.isLoginSuccess()
                || cookieManager.hasValidToken();
    }

    /**
     * 获取当前会话 Token（Cookie 或 Web Storage）。
     *
     * @return Token；无则 null
     */
    public String getLoginToken() {
        String t = cookieManager.getToken();
        if (t != null && !t.isBlank()) {
            return t;
        }
        String cached = requestUtils.getToken();
        return cached != null && !cached.isBlank() ? cached : null;
    }

    /**
     * 打印当前登录与浏览器诊断信息。
     */
    public void printLoginStatus() {
        try {
            log.info("[AutoLoginService] ===== 登录状态 =====");
            if (browserSessionManager.isRunning()) {
                log.info("[AutoLoginService] 当前URL: {}", browserSessionManager.getCurrentUrl());
                log.info("[AutoLoginService] 页面标题: {}", browserSessionManager.getPageTitle());
                log.info("[AutoLoginService] 浏览器运行状态: {}", browserSessionManager.isRunning());
                log.info("[AutoLoginService] 是否已登录: {}", browserSessionManager.isLoginSuccess());
            } else {
                log.info("[AutoLoginService] 浏览器未启动");
            }
            log.info("[AutoLoginService] Token: {}", getLoginToken() != null ? "已获取" : "未获取");
            cookieManager.printAllCookies();
            log.info("[AutoLoginService] =====================");
        } catch (Exception e) {
            log.warn("[AutoLoginService] 获取登录状态失败: {}", e.getMessage());
        }
    }

    /**
     * 滑块验证码图片数据
     */
    private static class SliderCaptchaData {
        /** 背景图 URL */
        String bgUrl;
        /** 拼图图片 URL */
        String sliderUrl;
        /** 背景图字节数据 */
        byte[] bgImage;
        /** 拼图图片字节数据 */
        byte[] sliderImage;
        /** 背景图在页面中的渲染宽度（px），用于距离修正 */
        int renderWidth;
        /** 是否为网易云盾类型 */
        boolean isYidun;
    }
}
