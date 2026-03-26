package com.stock.tradingExecutor.execution;

import com.stock.autoLogin.enums.SliderType;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务
 * 提供滑块验证码的距离计算、轨迹生成和验证执行
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@Service
public class CaptchaService {

    /**
     * 检测滑块验证码类型（跨 frame 搜索）
     * 策略：当前 frame → 顶层 document → 遍历所有 iframe
     *
     * @param driver WebDriver 实例
     * @return 滑块类型枚举
     */
    public SliderType detectSliderType(WebDriver driver) {
        try {
            // 策略 1: 当前 frame 查找
            if (hasYidunElement(driver)) {
                log.info("在当前 frame 中检测到网易云盾滑块");
                return SliderType.YIDUN;
            }

            // 策略 2: 切到顶层查找
            driver.switchTo().defaultContent();
            if (hasYidunElement(driver)) {
                log.info("在顶层 document 中检测到网易云盾滑块");
                return SliderType.YIDUN;
            }

            // 策略 3: 遍历顶层 iframe 查找
            List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
            for (int i = 0; i < iframes.size(); i++) {
                try {
                    driver.switchTo().defaultContent();
                    driver.switchTo().frame(i);
                    if (hasYidunElement(driver)) {
                        log.info("在第 {} 个 iframe 中检测到网易云盾滑块", i);
                        return SliderType.YIDUN;
                    }
                } catch (Exception e) {
                    log.debug("切换到第 {} 个 iframe 失败: {}", i, e.getMessage());
                }
            }

        } catch (Exception e) {
            log.debug("滑块检测异常: {}", e.getMessage());
        }

        log.debug("未检测到滑块验证码");
        return SliderType.NONE;
    }

    /**
     * 切换到滑块所在的 frame 上下文
     *
     * @param driver WebDriver 实例
     * @return 是否成功找到并切换
     */
    public boolean switchToSliderFrame(WebDriver driver) {
        // 1. 检查当前 frame
        if (hasYidunElement(driver)) {
            log.info("滑块在当前 frame 中");
            return true;
        }

        // 2. 检查顶层
        driver.switchTo().defaultContent();
        if (hasYidunElement(driver)) {
            log.info("滑块在顶层 document 中");
            return true;
        }

        // 3. 遍历 iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                if (hasYidunElement(driver)) {
                    log.info("滑块在第 {} 个 iframe 中", i);
                    return true;
                }
            } catch (Exception e) {
                log.debug("切换到第 {} 个 iframe 失败", i);
            }
        }

        log.warn("未找到滑块元素所在 frame");
        return false;
    }

    /**
     * 在所有 frame 中搜索 .yidun 元素（诊断用）
     *
     * @param driver WebDriver 实例
     * @return 找到滑块的 frame 名称，未找到返回 null
     */
    public String findYidunInAllFrames(WebDriver driver) {
        // 检查当前 frame
        if (hasYidunElement(driver)) {
            return "当前 frame";
        }

        // 检查顶层
        driver.switchTo().defaultContent();
        if (hasYidunElement(driver)) {
            return "顶层 document";
        }

        // 遍历 iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                if (hasYidunElement(driver)) {
                    return "iframe[" + i + "]";
                }
            } catch (Exception ignored) {
            }
        }

        // 切回顶层
        driver.switchTo().defaultContent();
        return null;
    }

    /**
     * 检查当前 frame 中是否存在可见的 .yidun 滑块面板
     * 仅检测实际弹出的面板（yidun_panel, yidun_popup, yidun_slider），
     * 排除隐藏的 yidun_input
     */
    private boolean hasYidunElement(WebDriver driver) {
        // 优先检测可见的滑块面板
        List<WebElement> panels = driver.findElements(
                By.cssSelector(".yidun_panel, .yidun_popup, .yidun_slider, .yidun_bgimg"));
        for (WebElement panel : panels) {
            try {
                if (panel.isDisplayed() && panel.getSize().getWidth() > 0) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        // 兜底：检测包含可见图片的 yidun 容器
        List<WebElement> imgs = driver.findElements(By.cssSelector("[class*='yidun'] img[src]"));
        for (WebElement img : imgs) {
            try {
                String src = img.getAttribute("src");
                if (src != null && !src.isEmpty() && img.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * 提取网易云盾背景图和拼图块图片 URL
     * 多策略：img src → CSS background-image → 全局搜索
     */
    public ImageUrls extractYidunImageUrls(WebDriver driver) {
        try {
            org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;

            // 策略 1: 从 img 标签 src 提取（多种选择器）
            String bgUrl = (String) js.executeScript(
                    "return document.querySelector('.yidun_bg-img img')?.src || " +
                            "document.querySelector('.yidun_bgimg img')?.src || " +
                            "document.querySelector('.yidun_bgimg')?.src || " +
                            "document.querySelector('[class*=\"yidun\"][class*=\"bg\"] img')?.src || " +
                            "document.querySelector('.yidun_panel img')?.src"
            );
            String sliderUrl = (String) js.executeScript(
                    "return document.querySelector('.yidun_jigsaw img')?.src || " +
                            "document.querySelector('.yidun_slider__icon')?.src || " +
                            "document.querySelector('[class*=\"yidun\"][class*=\"jigsaw\"] img')?.src"
            );

            // 策略 2: 从 CSS background-image 提取
            if (bgUrl == null) {
                bgUrl = (String) js.executeScript(
                        "var selectors = ['.yidun_bg-img', '.yidun_bgimg', '[class*=\"yidun_bg\"]'];" +
                                "for (var i = 0; i < selectors.length; i++) {" +
                                "  var el = document.querySelector(selectors[i]);" +
                                "  if (el) {" +
                                "    var bg = getComputedStyle(el).backgroundImage;" +
                                "    if (bg && bg !== 'none') return bg.replace(/url\\([\"']?(.+?)[\"']?\\)/, '$1');" +
                                "  }" +
                                "}" +
                                "return null;"
                );
            }

            // 策略 3: 从所有 yidun 容器内的 img 中筛选（按尺寸区分背景/拼图）
            if (bgUrl == null) {
                @SuppressWarnings("unchecked")
                java.util.List<java.util.Map<String, Object>> allImgs = (java.util.List<java.util.Map<String, Object>>) js.executeScript(
                        "return Array.from(document.querySelectorAll('[class*=\"yidun\"] img, #captcha img')).map(img => " +
                                "({src: img.src, w: img.naturalWidth || img.width, h: img.naturalHeight || img.height, cls: img.className, pCls: img.parentElement?.className}));"
                );
                if (allImgs != null) {
                    log.info("策略3：在 yidun/captcha 中找到 {} 张图片", allImgs.size());
                    for (java.util.Map<String, Object> img : allImgs) {
                        String src = (String) img.get("src");
                        Long w = img.get("w") instanceof Long ? (Long) img.get("w") : 0L;
                        if (src != null && !src.isEmpty()) {
                            if (w > 200 && bgUrl == null) {
                                bgUrl = src;
                            } else if (w <= 200 && w > 0 && sliderUrl == null) {
                                sliderUrl = src;
                            }
                        }
                    }
                }
            }

            log.info("网易云盾图片提取：bgUrl={}, sliderUrl={}",
                    bgUrl != null ? "已获取(" + bgUrl.substring(0, Math.min(50, bgUrl.length())) + "...)" : "失败",
                    sliderUrl != null ? "已获取" : "失败"
            );
            return new ImageUrls(bgUrl, sliderUrl);
        } catch (Exception e) {
            log.error("提取图片 URL 失败: {}", e.getMessage());
            return new ImageUrls(null, null);
        }
    }

    /**
     * 下载图片
     */
    public byte[] downloadImage(String imageUrl) throws IOException {
        if (imageUrl == null || imageUrl.isEmpty()) {
            throw new IOException("图片 URL 为空");
        }
        try (InputStream in = new URL(imageUrl).openStream()) {
            byte[] data = in.readAllBytes();
            log.info("滑块图片下载成功：{} 字节", data.length);
            return data;
        }
    }

    /**
     * 计算滑块距离
     * 策略：在背景图上检测拼图缺口的垂直边缘位置。
     * 拼图缺口在背景图中表现为突然的色差边缘（左侧边缘），
     * 通过逐列计算灰度跳变次数来找到缺口位置。
     *
     * @param bgImage     背景图字节数据
     * @param sliderImage 拼图块字节数据（用于获取拼图宽度做修正）
     * @return 滑块需要滑动的距离（像素）
     */
    public int calculateSliderDistance(byte[] bgImage, byte[] sliderImage) throws IOException {
        BufferedImage bg = ImageIO.read(new ByteArrayInputStream(bgImage));
        BufferedImage slider = ImageIO.read(new ByteArrayInputStream(sliderImage));

        if (bg == null || slider == null) {
            log.error("图片解码失败：bg={}, slider={}", bg != null, slider != null);
            return 0;
        }

        int bgWidth = bg.getWidth();
        int bgHeight = bg.getHeight();
        int sliderWidth = slider.getWidth();

        log.info("图片尺寸：bg={}x{}, slider={}x{}", bgWidth, bgHeight, sliderWidth, slider.getHeight());

        // 1. 将背景图转为灰度矩阵
        int[][] gray = new int[bgWidth][bgHeight];
        for (int y = 0; y < bgHeight; y++) {
            for (int x = 0; x < bgWidth; x++) {
                Color c = new Color(bg.getRGB(x, y));
                gray[x][y] = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            }
        }

        // 2. 逐列计算灰度跳变强度（Sobel 水平边缘）
        // 跳过前10%区域（滑块起始位置通常不在最左侧）
        int startX = bgWidth / 10;
        int endX = bgWidth - sliderWidth / 2;
        int bestX = 0;
        int maxEdgeSum = 0;

        for (int x = startX; x < endX; x++) {
            int edgeSum = 0;
            for (int y = 1; y < bgHeight - 1; y++) {
                // 水平 Sobel：检测垂直边缘
                int left = x > 0 ? gray[x - 1][y] : gray[x][y];
                int right = x < bgWidth - 1 ? gray[x + 1][y] : gray[x][y];
                int diff = Math.abs(right - left);
                if (diff > 30) { // 阈值：明显的灰度跳变
                    edgeSum += diff;
                }
            }
            if (edgeSum > maxEdgeSum) {
                maxEdgeSum = edgeSum;
                bestX = x;
            }
        }

        // 3. 距离修正：页面渲染宽度 / 图片原始宽度
        // NECaptcha 背景图原始 320px，渲染 220px
        // 返回的距离需要按渲染比例换算
        // 注意：调用方会直接用这个距离来拖动，所以要返回渲染后的距离
        double renderRatio = 220.0 / bgWidth;
        int renderDistance = (int) (bestX * renderRatio);

        log.info("滑块距离计算：原始={}px, 渲染={}px（ratio={}, maxEdge={}）",
                bestX, renderDistance, String.format("%.2f", renderRatio), maxEdgeSum);

        return renderDistance;
    }

    /**
     * 生成 S 曲线缓动滑动轨迹
     */
    public List<int[]> generateSliderTrajectory(int distance) {
        List<int[]> trajectory = new ArrayList<>();
        int pointCount = 30 + distance / 10;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < pointCount; i++) {
            double t = (double) i / pointCount;
            double easing;
            if (t < 0.3) {
                easing = 2 * t * t;
            } else if (t < 0.7) {
                easing = -2 * t * t + 2 * t - 0.2;
            } else {
                easing = 1 - Math.pow(1 - t, 2);
            }

            int x = (int) (easing * distance) + random.nextInt(-2, 3);
            int y = (int) (Math.sin(i * 0.3) * 3);
            trajectory.add(new int[]{x, y});
        }

        log.debug("生成滑动轨迹：点数={}, 距离={}px", trajectory.size(), distance);
        return trajectory;
    }

    /**
     * 使用 Selenium Actions 执行滑块拖动
     * 轨迹为绝对位置，转为增量执行 moveByOffset
     */
    public boolean executeSliderDrag(WebDriver driver, int distance) {
        try {
            WebElement sliderHandler = driver.findElement(
                    By.cssSelector(".yidun_slider, .yidun_slide_indicator, .yidun_slider__handler, .yidun_btn")
            );

            List<int[]> trajectory = generateSliderTrajectory(distance);

            Actions actions = new Actions(driver);
            actions.clickAndHold(sliderHandler).perform();

            int prevX = 0, prevY = 0;
            for (int[] point : trajectory) {
                int dx = point[0] - prevX;
                int dy = point[1] - prevY;
                actions.moveByOffset(dx, dy).perform();
                prevX = point[0];
                prevY = point[1];
                Thread.sleep(ThreadLocalRandom.current().nextInt(15, 26));
            }

            actions.release().perform();
            log.info("滑块拖动执行完成：距离={}px", distance);
            return true;

        } catch (Exception e) {
            log.error("滑块拖动失败", e);
            return false;
        }
    }

    /**
     * 等待滑块验证结果
     */
    public boolean waitForVerificationResult(WebDriver driver, int maxRetries) {
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

                // 检测 1: 成功提示文本
                try {
                    WebElement successTip = wait.until(ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".yidun_tips__text")));
                    if (successTip.getText().contains("成功") || successTip.getText().contains("通过")) {
                        log.info("网易云盾滑块验证通过（提示文本检测）");
                        return true;
                    }
                } catch (Exception ignored) {
                }

                // 检测 2: 滑块面板消失
                try {
                    wait.until(ExpectedConditions.invisibilityOfElementLocated(
                            By.cssSelector(".yidun_panel, .yidun")));
                    log.info("滑块已消失，视为通过（面板消失检测）");
                    return true;
                } catch (Exception ignored) {
                }

                // 检测 3: 页面 URL 跳转
                String currentUrl = driver.getCurrentUrl();
                if (!currentUrl.contains("login") && !currentUrl.contains("activePhone")) {
                    log.info("网易云盾滑块验证通过（URL 跳转检测）");
                    return true;
                }

                if (attempt < maxRetries) {
                    log.warn("滑块验证失败，准备第 {} 次重试", attempt + 1);
                    Thread.sleep(1000);
                }

            } catch (Exception e) {
                log.error("验证结果检测异常", e);
            }
        }

        log.error("滑块验证失败：已达最大重试次数 {}", maxRetries);
        return false;
    }

    /**
     * 计算滑块距离（兼容 ZXRequestUtils 调用）
     */
    public int calculateDistance(byte[] bgImage, byte[] sliderImage) {
        try {
            return calculateSliderDistance(bgImage, sliderImage);
        } catch (IOException e) {
            log.error("计算滑块距离失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 生成滑块拖动轨迹（兼容 ZXRequestUtils 调用，返回 List<Integer>）
     */
    public List<Integer> generateSlideTrack(int distance) {
        List<Integer> track = new ArrayList<>();
        if (distance <= 0) {
            return track;
        }
        int current = 0;
        double acceleration = 0.3;
        int step = 0;
        while (current < distance) {
            if (step < 10) {
                acceleration = 0.3 + step * 0.05;
            } else if (step > distance / 5) {
                acceleration = 2.0 - (step - distance / 5.0) / (distance / 10.0);
            }
            int move = (int) (Math.random() * 3 + 1 + acceleration);
            current += Math.min(move, distance - current);
            track.add(current);
            step++;
            if (step > 1000) break;
        }
        while (track.size() < 30 && track.size() < distance) {
            track.add(distance);
        }
        return track;
    }

    /**
     * 图片 URL 数据类
     */
    public static class ImageUrls {
        private final String bgUrl;
        private final String sliderUrl;

        public ImageUrls(String bgUrl, String sliderUrl) {
            this.bgUrl = bgUrl;
            this.sliderUrl = sliderUrl;
        }

        public String getBgUrl() {
            return bgUrl;
        }

        public String getSliderUrl() {
            return sliderUrl;
        }
    }
}
