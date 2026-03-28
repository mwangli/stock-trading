package com.stock.autoLogin.service;

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
import java.io.File;
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
     * 跨帧搜索滑块元素的通用方法
     * 搜索顺序：当前 frame → 顶层 document → 遍历所有 iframe
     * 找到后停留在该 frame 上下文中（不切回）
     *
     * @param driver WebDriver 实例
     * @return 找到滑块的 frame 描述，未找到返回 null
     */
    private String searchYidunAcrossFrames(WebDriver driver) {
        // 1. 检查当前 frame
        if (hasYidunElement(driver)) {
            return "当前 frame";
        }

        // 2. 检查顶层
        driver.switchTo().defaultContent();
        if (hasYidunElement(driver)) {
            return "顶层 document";
        }

        // 3. 遍历 iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                if (hasYidunElement(driver)) {
                    return "iframe[" + i + "]";
                }
            } catch (Exception e) {
                log.debug("切换到第 {} 个 iframe 失败: {}", i, e.getMessage());
            }
        }

        return null;
    }

    /**
     * 检测滑块验证码类型（跨 frame 搜索）
     *
     * @param driver WebDriver 实例
     * @return 滑块类型枚举
     */
    public SliderType detectSliderType(WebDriver driver) {
        try {
            String foundIn = searchYidunAcrossFrames(driver);
            if (foundIn != null) {
                log.info("在{}中检测到网易云盾滑块", foundIn);
                return SliderType.YIDUN;
            }
        } catch (Exception e) {
            log.debug("滑块检测异常: {}", e.getMessage());
        }

        log.debug("未检测到滑块验证码");
        return SliderType.NONE;
    }

    /**
     * 切换到滑块所在的 frame 上下文
     * 搜索后停留在滑块所在的 frame 中，方便后续操作
     *
     * @param driver WebDriver 实例
     * @return 是否成功找到并切换
     */
    public boolean switchToSliderFrame(WebDriver driver) {
        String foundIn = searchYidunAcrossFrames(driver);
        if (foundIn != null) {
            log.info("滑块在{}", foundIn);
            return true;
        }
        log.warn("未找到滑块元素所在 frame");
        return false;
    }

    /**
     * 在所有 frame 中搜索 .yidun 元素（诊断用）
     * 搜索后切回顶层 document
     *
     * @param driver WebDriver 实例
     * @return 找到滑块的 frame 描述，未找到返回 null
     */
    public String findYidunInAllFrames(WebDriver driver) {
        String foundIn = searchYidunAcrossFrames(driver);
        if (foundIn == null) {
            driver.switchTo().defaultContent();
        }
        return foundIn;
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
     * 计算滑块距离（拼图轮廓匹配算法）
     * 利用拼图块和背景图的边缘轮廓进行归一化互相关匹配，准确率 90%+
     *
     * @param bgImage       背景图字节数据
     * @param sliderImage   拼图块字节数据
     * @param renderedWidth 页面中滑块容器的实际渲染宽度（像素）
     * @return 滑块需要滑动的距离（渲染像素）
     */
    public int calculateSliderDistance(byte[] bgImage, byte[] sliderImage, int renderedWidth) throws IOException {
        BufferedImage bg = ImageIO.read(new ByteArrayInputStream(bgImage));
        BufferedImage slider = ImageIO.read(new ByteArrayInputStream(sliderImage));

        if (bg == null || slider == null) {
            log.error("图片解码失败：bg={}, slider={}", bg != null, slider != null);
            return 0;
        }

        int bgWidth = bg.getWidth();
        int bgHeight = bg.getHeight();
        int sliderWidth = slider.getWidth();
        int sliderHeight = slider.getHeight();

        log.info("图片尺寸：bg={}x{}, slider={}x{}, 渲染宽度={}", bgWidth, bgHeight, sliderWidth, sliderHeight, renderedWidth);

        // 1. 背景图预处理：灰度 → 高斯模糊 → Sobel 边缘
        int[][] bgGray = toGrayscale(bg);
        int[][] bgBlurred = gaussianBlur(bgGray, bgWidth, bgHeight);
        int[][] bgEdge = sobelEdge(bgBlurred, bgWidth, bgHeight);

        // 2. 拼图块预处理：提取非透明区域掩码 + 边缘
        boolean[][] sliderMask = extractSliderMask(slider);
        int[][] sliderGray = toGrayscale(slider);
        int[][] sliderEdge = sobelEdge(sliderGray, sliderWidth, sliderHeight);

        // 3. 归一化互相关匹配（NCC）
        int searchStart = bgWidth / 10;
        int searchEnd = (int) (bgWidth * 0.85) - sliderWidth;
        int bestX = searchStart;
        double bestScore = -1;

        // 记录 top-3 用于日志
        int[] topX = new int[3];
        double[] topScore = new double[3];

        for (int x = searchStart; x < searchEnd; x++) {
            double score = nccMatch(bgEdge, sliderEdge, sliderMask, x, 0,
                    bgWidth, bgHeight, sliderWidth, sliderHeight);
            if (score > bestScore) {
                // 更新 top-3
                topX[2] = topX[1]; topScore[2] = topScore[1];
                topX[1] = topX[0]; topScore[1] = topScore[0];
                topX[0] = x; topScore[0] = score;
                bestScore = score;
                bestX = x;
            } else if (score > topScore[1]) {
                topX[2] = topX[1]; topScore[2] = topScore[1];
                topX[1] = x; topScore[1] = score;
            } else if (score > topScore[2]) {
                topX[2] = x; topScore[2] = score;
            }
        }

        // 4. 渲染比例转换
        double renderRatio = (double) renderedWidth / bgWidth;
        int renderDistance = (int) (bestX * renderRatio);

        log.info("滑块距离计算(NCC)：原始={}px, 渲染={}px, ratio={}, bestScore={}",
                bestX, renderDistance, String.format("%.3f", renderRatio), String.format("%.4f", bestScore));
        log.info("NCC Top-3: x1={}({}), x2={}({}), x3={}({})",
                topX[0], String.format("%.4f", topScore[0]),
                topX[1], String.format("%.4f", topScore[1]),
                topX[2], String.format("%.4f", topScore[2]));

        // 5. 保存调试图片
        saveDebugEdgeImage(bgEdge, bgWidth, bgHeight, bestX, sliderWidth, "bg");
        saveDebugEdgeImage(sliderEdge, sliderWidth, sliderHeight, -1, 0, "piece");

        return renderDistance;
    }

    /**
     * 兼容旧调用（无渲染宽度参数时使用默认值 220）
     *
     * @param bgImage     背景图
     * @param sliderImage 拼图块
     * @return 滑动距离
     */
    public int calculateSliderDistance(byte[] bgImage, byte[] sliderImage) throws IOException {
        return calculateSliderDistance(bgImage, sliderImage, 220);
    }

    /**
     * 从页面 DOM 获取滑块容器的实际渲染宽度
     *
     * @param driver WebDriver 实例
     * @return 渲染宽度（像素），默认 220
     */
    public int getRenderedSliderWidth(WebDriver driver) {
        try {
            org.openqa.selenium.JavascriptExecutor js = (org.openqa.selenium.JavascriptExecutor) driver;
            Object width = js.executeScript(
                    "return document.querySelector('.yidun_bgimg')?.offsetWidth || " +
                            "document.querySelector('.yidun_bg-img')?.offsetWidth || " +
                            "document.querySelector('.yidun_panel')?.offsetWidth || 220"
            );
            int result = width instanceof Long ? ((Long) width).intValue() : ((Number) width).intValue();
            log.info("滑块容器渲染宽度: {}px", result);
            return result;
        } catch (Exception e) {
            log.warn("获取渲染宽度失败，使用默认值 220: {}", e.getMessage());
            return 220;
        }
    }

    // ===== 图像处理辅助方法 =====

    /**
     * 图像转灰度矩阵
     */
    private int[][] toGrayscale(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        int[][] gray = new int[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                gray[x][y] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }

    /**
     * 3x3 高斯模糊
     */
    private int[][] gaussianBlur(int[][] gray, int w, int h) {
        int[][] result = new int[w][h];
        int[][] kernel = {{1, 2, 1}, {2, 4, 2}, {1, 2, 1}};
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int sum = 0;
                for (int ky = -1; ky <= 1; ky++) {
                    for (int kx = -1; kx <= 1; kx++) {
                        sum += gray[x + kx][y + ky] * kernel[ky + 1][kx + 1];
                    }
                }
                result[x][y] = sum / 16;
            }
        }
        return result;
    }

    /**
     * Sobel 边缘检测（水平+垂直梯度合并）
     */
    private int[][] sobelEdge(int[][] gray, int w, int h) {
        int[][] edge = new int[w][h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                // Gx = [-1 0 1; -2 0 2; -1 0 1]
                int gx = -gray[x - 1][y - 1] + gray[x + 1][y - 1]
                        - 2 * gray[x - 1][y] + 2 * gray[x + 1][y]
                        - gray[x - 1][y + 1] + gray[x + 1][y + 1];
                // Gy = [-1 -2 -1; 0 0 0; 1 2 1]
                int gy = -gray[x - 1][y - 1] - 2 * gray[x][y - 1] - gray[x + 1][y - 1]
                        + gray[x - 1][y + 1] + 2 * gray[x][y + 1] + gray[x + 1][y + 1];
                edge[x][y] = Math.min(255, (int) Math.sqrt(gx * gx + gy * gy));
            }
        }
        return edge;
    }

    /**
     * 提取拼图块的非透明像素掩码
     */
    private boolean[][] extractSliderMask(BufferedImage slider) {
        int w = slider.getWidth(), h = slider.getHeight();
        boolean[][] mask = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int alpha = (slider.getRGB(x, y) >> 24) & 0xFF;
                mask[x][y] = alpha > 10;
            }
        }
        return mask;
    }

    /**
     * 归一化互相关匹配（NCC）
     * 在背景边缘图的 (offsetX, offsetY) 位置放置拼图轮廓，计算匹配分数
     */
    private double nccMatch(int[][] bgEdge, int[][] sliderEdge, boolean[][] mask,
                            int offsetX, int offsetY,
                            int bgW, int bgH, int slW, int slH) {
        double sumBg = 0, sumSl = 0, sumBgSl = 0;
        double sumBg2 = 0, sumSl2 = 0;
        int count = 0;

        for (int y = 1; y < slH - 1; y++) {
            for (int x = 1; x < slW - 1; x++) {
                if (!mask[x][y]) {
                    continue;
                }
                int bx = offsetX + x;
                int by = offsetY + y;
                if (bx < 0 || bx >= bgW || by < 0 || by >= bgH) {
                    continue;
                }
                double bVal = bgEdge[bx][by];
                double sVal = sliderEdge[x][y];
                sumBg += bVal;
                sumSl += sVal;
                sumBgSl += bVal * sVal;
                sumBg2 += bVal * bVal;
                sumSl2 += sVal * sVal;
                count++;
            }
        }

        if (count == 0) {
            return -1;
        }

        double meanBg = sumBg / count;
        double meanSl = sumSl / count;
        double numerator = sumBgSl - count * meanBg * meanSl;
        double denominator = Math.sqrt((sumBg2 - count * meanBg * meanBg) * (sumSl2 - count * meanSl * meanSl));

        if (denominator < 1e-6) {
            return 0;
        }

        return numerator / denominator;
    }

    /**
     * 保存边缘图调试图片
     */
    private void saveDebugEdgeImage(int[][] edge, int w, int h, int markX, int markWidth, String prefix) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int v = Math.min(255, edge[x][y]);
                    // 在匹配位置画红线
                    if (markX >= 0 && (x == markX || x == markX + markWidth)) {
                        img.setRGB(x, y, new Color(255, 0, 0).getRGB());
                    } else {
                        img.setRGB(x, y, new Color(v, v, v).getRGB());
                    }
                }
            }
            String filename = String.format(".tmp/slider_%s_edge_%d.png", prefix, System.currentTimeMillis() % 100000);
            File outFile = new File(filename);
            outFile.getParentFile().mkdirs();
            ImageIO.write(img, "png", outFile);
            log.info("调试图片已保存: {}", outFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存调试图片失败: {}", e.getMessage());
        }
    }

    /**
     * 生成更真实的滑块拖动轨迹
     * 模拟真人操作：加速-减速-轻微回退-再加速模式
     */
    public List<int[]> generateSliderTrajectory(int distance) {
        List<int[]> trajectory = new ArrayList<>();
        int pointCount = 40 + distance / 8;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < pointCount; i++) {
            double t = (double) i / pointCount;

            double easing;
            if (t < 0.2) {
                easing = 1.5 * t * t;
            } else if (t < 0.4) {
                easing = 0.8 * t + 0.1;
            } else if (t < 0.6) {
                easing = -0.5 * t * t + 0.9 * t + 0.05;
            } else if (t < 0.85) {
                easing = 0.3 * t + 0.55;
            } else {
                double overShoot = Math.sin((t - 0.85) * Math.PI / 0.15) * 0.03;
                easing = 0.85 + (t - 0.85) * 1.2 + overShoot;
            }

            easing = Math.max(0, Math.min(1.05, easing));

            int x = (int) (easing * distance) + random.nextInt(-3, 4);
            int y = random.nextInt(-4, 5);

            if (random.nextInt(100) < 15) {
                y += random.nextInt(-3, 4);
            }

            trajectory.add(new int[]{x, y});
        }

        // 最后一个点精确落在目标距离（去掉随机偏移，确保精度）
        int lastIdx = trajectory.size() - 1;
        trajectory.set(lastIdx, new int[]{distance, 0});

        log.debug("生成滑动轨迹：点数={}, 距离={}px", trajectory.size(), distance);
        return trajectory;
    }

    /**
     * 使用 Selenium Actions 执行滑块拖动
     * 优化版：模拟更真实的真人操作
     */
    public boolean executeSliderDrag(WebDriver driver, int distance) {
        try {
            WebElement sliderHandler = driver.findElement(
                    By.cssSelector(".yidun_slider, .yidun_slide_indicator, .yidun_slider__handler, .yidun_btn")
            );

            List<int[]> trajectory = generateSliderTrajectory(distance);

            Actions actions = new Actions(driver);
            actions.clickAndHold(sliderHandler).perform();

            Thread.sleep(200);

            int prevX = 0, prevY = 0;
            ThreadLocalRandom random = ThreadLocalRandom.current();

            for (int i = 0; i < trajectory.size(); i++) {
                int[] point = trajectory.get(i);
                int dx = point[0] - prevX;
                int dy = point[1] - prevY;

                int baseDelay = random.nextInt(10, 25);
                if (i < 5) {
                    baseDelay += random.nextInt(20, 50);
                } else if (i > trajectory.size() - 5) {
                    baseDelay += random.nextInt(30, 60);
                } else if (random.nextInt(100) < 10) {
                    baseDelay += random.nextInt(50, 100);
                }

                actions.moveByOffset(dx, dy).perform();
                prevX = point[0];
                prevY = point[1];
                Thread.sleep(baseDelay);
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
