package com.stock.tradingExecutor.execution;

import com.stock.autoLogin.enums.SliderType;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 验证码服务 — 截图驱动方案
 * <p>
 * 核心思路：所有检测和识别均基于全页面截图，不依赖 DOM 选择器（因 Yidun SDK 弹窗
 * 在不同环境下 DOM 结构不一致，且可能在 Shadow DOM / 独立 overlay 中渲染）。
 * <p>
 * 流程：截图 → 检测弹窗 → 定位面板 → 裁剪拼图 → Sobel 边缘检测 → S 曲线拖动
 *
 * @author mwangli
 * @since 2026-03-25
 */
@Slf4j
@Service
public class CaptchaService {

    // ==================== 截图与滑块检测 ====================

    /**
     * 截取全页面截图并保存到 .tmp 目录
     *
     * @param driver WebDriver 实例
     * @param tag    截图标签（用于文件名）
     * @return 截图的 BufferedImage，失败返回 null
     */
    public BufferedImage takeScreenshot(WebDriver driver, String tag) {
        try {
            driver.switchTo().defaultContent();
            byte[] bytes = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            saveDebugImage(img, tag);
            log.info("截图成功 [{}]: {}x{}", tag, img.getWidth(), img.getHeight());
            return img;
        } catch (Exception e) {
            log.error("截图失败 [{}]: {}", tag, e.getMessage());
            return null;
        }
    }

    /**
     * 通过截图检测滑块弹窗是否存在
     * 检测逻辑：在截图中查找白色/浅色矩形弹窗面板（网易云盾特征）
     *
     * @param driver WebDriver 实例
     * @return 如果检测到滑块弹窗返回 YIDUN，否则返回 NONE
     */
    public SliderType detectSliderByScreenshot(WebDriver driver) {
        BufferedImage screenshot = takeScreenshot(driver, "detect_slider");
        if (screenshot == null) {
            return SliderType.NONE;
        }
        Rectangle panel = locateSliderPanel(screenshot);
        if (panel != null) {
            log.info("截图检测到滑块弹窗：x={}, y={}, w={}, h={}", panel.x, panel.y, panel.width, panel.height);
            return SliderType.YIDUN;
        }
        log.info("截图未检测到滑块弹窗");
        return SliderType.NONE;
    }

    // ==================== 截图式距离计算 ====================

    /**
     * 基于截图计算滑块拖动距离
     * 完整流程：截图 → 定位面板 → 裁剪拼图 → Sobel 边缘检测 → 坐标映射
     *
     * @param driver WebDriver 实例
     * @return 包含距离和面板位置的结果，失败返回 null
     */
    public SliderResult calculateSliderDistance(WebDriver driver) {
        // 1. 截图
        BufferedImage fullPage = takeScreenshot(driver, "solve_slider");
        if (fullPage == null) {
            return null;
        }

        // 2. 定位滑块面板
        Rectangle panelRect = locateSliderPanel(fullPage);
        if (panelRect == null) {
            log.error("截图中未定位到滑块面板");
            return null;
        }
        log.info("面板定位：x={}, y={}, w={}, h={}", panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        BufferedImage panelImage = fullPage.getSubimage(panelRect.x, panelRect.y, panelRect.width, panelRect.height);
        saveDebugImage(panelImage, "slider_panel");

        // 3. 裁剪拼图区域（去除标题栏和底部滑轨）
        int titleH = (int) (panelRect.height * 0.12);
        int barH = (int) (panelRect.height * 0.20);
        int puzzleH = panelRect.height - titleH - barH;
        if (puzzleH <= 20) {
            log.error("面板高度异常: {}", panelRect.height);
            return null;
        }
        BufferedImage puzzle = fullPage.getSubimage(panelRect.x, panelRect.y + titleH, panelRect.width, puzzleH);
        saveDebugImage(puzzle, "slider_puzzle");

        // 4. Sobel 边缘检测缺口
        int gapX = detectGapByEdge(puzzle);
        if (gapX < 0) {
            log.error("缺口检测失败");
            return null;
        }
        log.info("缺口位置：x={}px（截图像素）", gapX);

        // 5. 坐标映射
        double dpr = getDpr(driver);
        int initOffset = (int) (panelRect.width * 0.06 / dpr);
        int distance = (int) (gapX / dpr) - initOffset;
        distance = Math.max(10, Math.min(distance, 300));
        log.info("拖动距离：{}px（DPR={}, 初始偏移={}）", distance, dpr, initOffset);

        return new SliderResult(distance, panelRect);
    }

    // ==================== 面板定位 ====================

    /**
     * 从截图中定位滑块弹窗面板
     * 策略 A：扫描白色矩形面板（25%~50% 页宽）
     * 策略 B：从灰色滑轨条反推面板位置
     *
     * @param fullPage 全页面截图
     * @return 面板区域，未找到返回 null
     */
    public Rectangle locateSliderPanel(BufferedImage fullPage) {
        int w = fullPage.getWidth();
        int h = fullPage.getHeight();
        int minPW = (int) (w * 0.20);
        int maxPW = (int) (w * 0.50);
        int minPH = (int) (h * 0.15);
        int maxPH = (int) (h * 0.50);

        // 策略 A：扫描白色矩形
        for (int y = (int) (h * 0.05); y < h * 0.7; y++) {
            int runStart = -1;
            int runLen = 0;
            for (int x = (int) (w * 0.1); x < w * 0.9; x++) {
                Color c = new Color(fullPage.getRGB(x, y));
                if (c.getRed() > 230 && c.getGreen() > 230 && c.getBlue() > 230) {
                    if (runStart < 0) runStart = x;
                    runLen++;
                } else {
                    if (runLen >= minPW && runLen <= maxPW) {
                        Rectangle panel = verifyPanel(fullPage, runStart, y, runLen, minPH, maxPH);
                        if (panel != null) return panel;
                    }
                    runStart = -1;
                    runLen = 0;
                }
            }
            if (runLen >= minPW && runLen <= maxPW) {
                Rectangle panel = verifyPanel(fullPage, runStart, y, runLen, minPH, maxPH);
                if (panel != null) return panel;
            }
        }

        // 策略 B：灰色滑轨反推
        return locateBySliderBar(fullPage);
    }

    /**
     * 验证候选面板：内部有拼图图片（色彩丰富）
     */
    private Rectangle verifyPanel(BufferedImage img, int x, int y, int width, int minH, int maxH) {
        int imgH = img.getHeight();
        int panelH = 0;
        for (int dy = 1; dy < maxH && (y + dy) < imgH; dy++) {
            Color left = new Color(img.getRGB(x + 2, y + dy));
            Color right = new Color(img.getRGB(x + width - 3, y + dy));
            if (left.getRed() <= 200 && right.getRed() <= 200) {
                panelH = dy;
                break;
            }
            panelH = dy;
        }
        if (panelH < minH) return null;

        // 检查面板中部色彩丰富度
        int midY = y + panelH / 3;
        int midX = x + width / 2;
        int colorVar = 0;
        int prev = -1;
        for (int dx = -20; dx <= 20; dx++) {
            int px = midX + dx;
            if (px < 0 || px >= img.getWidth()) continue;
            Color c = new Color(img.getRGB(px, midY));
            int gray = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            if (prev >= 0) colorVar += Math.abs(gray - prev);
            prev = gray;
        }
        if (colorVar < 100) return null;

        return new Rectangle(x, y, width, panelH);
    }

    /**
     * 策略 B：通过灰色滑轨定位
     */
    private Rectangle locateBySliderBar(BufferedImage fullPage) {
        int w = fullPage.getWidth();
        int h = fullPage.getHeight();
        for (int y = (int) (h * 0.2); y < h * 0.85; y++) {
            int grayRun = 0;
            int grayStart = -1;
            for (int x = (int) (w * 0.1); x < w * 0.9; x++) {
                Color c = new Color(fullPage.getRGB(x, y));
                boolean isGray = c.getRed() > 200 && c.getRed() < 245
                        && Math.abs(c.getRed() - c.getGreen()) < 10
                        && Math.abs(c.getRed() - c.getBlue()) < 10;
                if (isGray) {
                    if (grayStart < 0) grayStart = x;
                    grayRun++;
                } else {
                    if (grayRun > w * 0.2) {
                        int panelW = grayRun;
                        int panelH = (int) (panelW * 0.65);
                        int panelY = y - panelH;
                        if (panelY > 0) {
                            log.info("滑轨定位面板：barY={}, barW={}", y, grayRun);
                            return new Rectangle(grayStart, panelY, panelW, panelH + 40);
                        }
                    }
                    grayRun = 0;
                    grayStart = -1;
                }
            }
        }
        return null;
    }

    // ==================== 边缘检测 ====================

    /**
     * Sobel 双方向边缘检测定位拼图缺口
     *
     * @param puzzle 裁剪后的拼图图片
     * @return 缺口 X 坐标（截图像素），失败返回 -1
     */
    public int detectGapByEdge(BufferedImage puzzle) {
        int w = puzzle.getWidth();
        int h = puzzle.getHeight();

        // 1. 灰度 + 双次高斯模糊
        int[][] gray = toGrayMatrix(puzzle);
        int[][] blurred = gaussianBlur(gray, w, h);
        blurred = gaussianBlur(blurred, w, h);

        // 2. Sobel 双方向梯度幅值
        int[][] edge = new int[w][h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int gx = -blurred[x - 1][y - 1] + blurred[x + 1][y - 1]
                        - 2 * blurred[x - 1][y] + 2 * blurred[x + 1][y]
                        - blurred[x - 1][y + 1] + blurred[x + 1][y + 1];
                int gy = -blurred[x - 1][y - 1] - 2 * blurred[x][y - 1] - blurred[x + 1][y - 1]
                        + blurred[x - 1][y + 1] + 2 * blurred[x][y + 1] + blurred[x + 1][y + 1];
                edge[x][y] = (int) Math.sqrt(gx * gx + gy * gy);
            }
        }

        // 3. 列投影（跳过左 20%，搜索到 88%）
        int startX = (int) (w * 0.20);
        int endX = (int) (w * 0.88);
        int yStart = (int) (h * 0.15);
        int yEnd = (int) (h * 0.85);
        int[] colSum = new int[w];
        for (int x = startX; x < endX; x++) {
            for (int y = yStart; y < yEnd; y++) {
                colSum[x] += edge[x][y];
            }
        }

        // 4. 滑动窗口平滑
        int[] smooth = new int[w];
        for (int x = startX; x < endX; x++) {
            int sum = 0;
            int cnt = 0;
            for (int dx = -2; dx <= 2; dx++) {
                int nx = x + dx;
                if (nx >= startX && nx < endX) {
                    sum += colSum[nx];
                    cnt++;
                }
            }
            smooth[x] = cnt > 0 ? sum / cnt : 0;
        }

        // 5. 计算基线（排除 top 10%）
        int rangeLen = endX - startX;
        int[] sorted = new int[rangeLen];
        for (int i = 0; i < rangeLen; i++) sorted[i] = smooth[startX + i];
        java.util.Arrays.sort(sorted);
        long baseSum = 0;
        int cutoff = (int) (rangeLen * 0.9);
        for (int i = 0; i < cutoff; i++) baseSum += sorted[i];
        double baseline = cutoff > 0 ? (double) baseSum / cutoff : 0;

        // 6. 找显著峰值区间
        double threshold = baseline * 1.8;
        int bestStart = -1;
        int bestScore = 0;
        int regionStart = -1;
        int regionScore = 0;
        for (int x = startX; x < endX; x++) {
            if (smooth[x] > threshold) {
                if (regionStart < 0) regionStart = x;
                regionScore += smooth[x];
            } else {
                if (regionStart > 0 && regionScore > bestScore) {
                    bestStart = regionStart;
                    bestScore = regionScore;
                }
                regionStart = -1;
                regionScore = 0;
            }
        }
        if (regionStart > 0 && regionScore > bestScore) bestStart = regionStart;

        if (bestStart > 0) {
            log.info("缺口检测成功（区间法）: x={}, 基线={}, 阈值={}", bestStart, (int) baseline, (int) threshold);
            return bestStart;
        }

        // 7. 回退：全局最大峰值
        int maxVal = 0;
        int gapX = -1;
        for (int x = startX; x < endX; x++) {
            if (smooth[x] > maxVal) {
                maxVal = smooth[x];
                gapX = x;
            }
        }
        if (gapX > 0 && maxVal > baseline * 1.3) {
            log.info("缺口检测成功（峰值法）: x={}", gapX);
            return gapX;
        }

        log.warn("缺口检测失败: maxVal={}, baseline={}", maxVal, (int) baseline);
        return -1;
    }

    // ==================== 轨迹与拖动 ====================

    /**
     * 生成 S 曲线缓动滑动轨迹
     *
     * @param distance 拖动总距离
     * @return 轨迹点数组
     */
    public List<int[]> generateSliderTrajectory(int distance) {
        List<int[]> trajectory = new ArrayList<>();
        int count = 30 + distance / 10;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            double t = (double) i / count;
            double ease = t < 0.3 ? 2 * t * t
                    : t < 0.7 ? -2 * t * t + 2 * t - 0.2
                    : 1 - Math.pow(1 - t, 2);
            int x = (int) (ease * distance) + rng.nextInt(-2, 3);
            int y = (int) (Math.sin(i * 0.3) * 3);
            trajectory.add(new int[]{x, y});
        }
        return trajectory;
    }

    /**
     * 通过 DOM 元素定位执行滑块拖动
     *
     * @param driver   WebDriver 实例
     * @param distance 拖动距离
     * @return 是否成功
     */
    public boolean executeSliderDrag(WebDriver driver, int distance) {
        try {
            // 先切到滑块所在 frame
            switchToSliderFrame(driver);

            WebElement handler = driver.findElement(By.cssSelector(
                    ".yidun_slider__handler, .yidun_btn, [class*='yidun'][class*='handler']"));

            List<int[]> trajectory = generateSliderTrajectory(distance);
            Actions actions = new Actions(driver);
            actions.clickAndHold(handler).perform();
            Thread.sleep(100);

            int prevX = 0, prevY = 0;
            for (int[] pt : trajectory) {
                actions.moveByOffset(pt[0] - prevX, pt[1] - prevY).perform();
                prevX = pt[0];
                prevY = pt[1];
                Thread.sleep(ThreadLocalRandom.current().nextInt(15, 26));
            }
            actions.release().perform();
            log.info("滑块拖动完成: {}px", distance);
            return true;
        } catch (Exception e) {
            log.error("滑块拖动失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 等待滑块验证结果
     *
     * @param driver     WebDriver 实例
     * @param maxRetries 最大等待次数
     * @return 是否通过
     */
    public boolean waitForVerificationResult(WebDriver driver, int maxRetries) {
        for (int i = 1; i <= maxRetries; i++) {
            try {
                Thread.sleep(2000);
                // 重新截图检查弹窗是否消失
                BufferedImage check = takeScreenshot(driver, "verify_" + i);
                if (check != null && locateSliderPanel(check) == null) {
                    log.info("滑块弹窗已消失，视为验证通过");
                    return true;
                }
                // 检查 URL 跳转
                String url = driver.getCurrentUrl();
                if (!url.contains("login") && !url.contains("activePhone")) {
                    log.info("URL 跳转检测到验证通过: {}", url);
                    return true;
                }
            } catch (Exception e) {
                log.error("验证结果检测异常", e);
            }
        }
        log.warn("滑块验证未通过（{}次检测）", maxRetries);
        return false;
    }

    // ==================== Frame 切换（辅助） ====================

    /**
     * 切换到滑块所在的 frame（尝试顶层 + 所有 iframe）
     *
     * @param driver WebDriver 实例
     * @return 是否成功
     */
    public boolean switchToSliderFrame(WebDriver driver) {
        // 先检查当前 frame
        if (hasYidunDomElement(driver)) return true;
        // 顶层
        driver.switchTo().defaultContent();
        if (hasYidunDomElement(driver)) return true;
        // 遍历 iframe
        List<WebElement> iframes = driver.findElements(By.tagName("iframe"));
        for (int i = 0; i < iframes.size(); i++) {
            try {
                driver.switchTo().defaultContent();
                driver.switchTo().frame(i);
                if (hasYidunDomElement(driver)) {
                    log.info("滑块在 iframe[{}] 中", i);
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        // 找不到也回到顶层
        driver.switchTo().defaultContent();
        return false;
    }

    /**
     * DOM 辅助检测（仅用于 frame 切换定位，不作为滑块存在的判据）
     */
    private boolean hasYidunDomElement(WebDriver driver) {
        List<WebElement> elements = driver.findElements(By.cssSelector("[class*='yidun']"));
        for (WebElement el : elements) {
            try {
                if (!"input".equalsIgnoreCase(el.getTagName()) && el.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    // ==================== 工具方法 ====================

    private double getDpr(WebDriver driver) {
        try {
            Object dpr = ((JavascriptExecutor) driver).executeScript("return window.devicePixelRatio || 1");
            return dpr instanceof Number ? ((Number) dpr).doubleValue() : 1.0;
        } catch (Exception e) {
            return 1.0;
        }
    }

    private int[][] toGrayMatrix(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        int[][] m = new int[w][h];
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                Color c = new Color(image.getRGB(x, y));
                m[x][y] = (c.getRed() + c.getGreen() + c.getBlue()) / 3;
            }
        return m;
    }

    private int[][] gaussianBlur(int[][] gray, int w, int h) {
        int[][] b = new int[w][h];
        int[][] k = {{1, 2, 1}, {2, 4, 2}, {1, 2, 1}};
        for (int y = 1; y < h - 1; y++)
            for (int x = 1; x < w - 1; x++) {
                int sum = 0;
                for (int ky = -1; ky <= 1; ky++)
                    for (int kx = -1; kx <= 1; kx++)
                        sum += gray[x + kx][y + ky] * k[ky + 1][kx + 1];
                b[x][y] = sum / 16;
            }
        return b;
    }

    private void saveDebugImage(BufferedImage image, String prefix) {
        try {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            Path dest = Paths.get(".tmp", prefix + "_" + ts + ".png");
            Files.createDirectories(dest.getParent());
            ImageIO.write(image, "png", dest.toFile());
            log.debug("调试图片: {}", dest);
        } catch (Exception e) {
            log.debug("保存调试图片失败: {}", e.getMessage());
        }
    }

    // ==================== 兼容方法（供 ZXRequestUtils 调用） ====================

    /**
     * 计算滑块距离（兼容旧接口，基于图片模板匹配）
     */
    public int calculateDistance(byte[] bgImage, byte[] sliderImage) {
        try {
            BufferedImage bg = ImageIO.read(new ByteArrayInputStream(bgImage));
            BufferedImage slider = ImageIO.read(new ByteArrayInputStream(sliderImage));
            int[][] bgGray = toGrayMatrix(bg);
            int[][] slGray = toGrayMatrix(slider);
            int bestX = 0;
            int minDiff = Integer.MAX_VALUE;
            for (int x = 0; x < bgGray.length - slGray.length; x++) {
                int diff = 0;
                for (int sy = 0; sy < slGray[0].length; sy++)
                    for (int sx = 0; sx < slGray.length; sx++)
                        diff += Math.abs(bgGray[x + sx][sy] - slGray[sx][sy]);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestX = x;
                }
            }
            return bestX;
        } catch (IOException e) {
            log.error("模板匹配失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 生成滑块拖动轨迹（兼容旧接口）
     */
    public List<Integer> generateSlideTrack(int distance) {
        List<Integer> track = new ArrayList<>();
        if (distance <= 0) return track;
        int cur = 0;
        double acc = 0.3;
        int step = 0;
        while (cur < distance) {
            if (step < 10) acc = 0.3 + step * 0.05;
            else if (step > distance / 5) acc = 2.0 - (step - distance / 5.0) / (distance / 10.0);
            int move = (int) (Math.random() * 3 + 1 + acc);
            cur += Math.min(move, distance - cur);
            track.add(cur);
            step++;
            if (step > 1000) break;
        }
        while (track.size() < 30 && track.size() < distance) track.add(distance);
        return track;
    }

    // ==================== 数据类 ====================

    /**
     * 滑块识别结果
     */
    public static class SliderResult {
        private final int distance;
        private final Rectangle panelRect;

        public SliderResult(int distance, Rectangle panelRect) {
            this.distance = distance;
            this.panelRect = panelRect;
        }

        public int getDistance() {
            return distance;
        }

        public Rectangle getPanelRect() {
            return panelRect;
        }
    }
}
