package com.stock.tradingExecutor.execution;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证码服务纯逻辑测试（无浏览器）：缓动轨迹、模板匹配距离。
 *
 * @author mwangli
 * @since 2026-03-22
 */
class CaptchaServiceTest {

    private final CaptchaService captchaService = new CaptchaService();

    @Test
    @DisplayName("缓动曲线：起点接近 0、终点接近 distance、点数足够")
    void generateEaseSlideTrack_shape() {
        int distance = 180;
        Random seeded = new Random(42L);
        List<int[]> track = captchaService.generateEaseSlideTrack(distance, seeded);

        assertNotNull(track);
        assertTrue(track.size() >= 20, "轨迹点数量应足够");
        assertTrue(Math.abs(track.get(0)[0]) <= 4, "起点 X 应接近 0（含抖动）");
        int lastX = track.get(track.size() - 1)[0];
        assertTrue(Math.abs(lastX - distance) < 25, "终点 X 应接近目标距离");
    }

    @Test
    @DisplayName("calculateEaseProgress：边界与单调性抽样")
    void calculateEaseProgress_bounds() {
        assertEquals(0.0, captchaService.calculateEaseProgress(0.0), 1e-9);
        double mid = captchaService.calculateEaseProgress(0.5);
        assertTrue(mid > 0 && mid < 1);
        assertEquals(1.0, captchaService.calculateEaseProgress(1.0), 1e-6);
    }

    @Test
    @DisplayName("模板匹配：在纯色背景上定位深色竖条")
    void calculateDistance_templateMatch() throws Exception {
        int w = 200;
        int h = 60;
        BufferedImage bg = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = bg.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        int gapX = 87;
        g.setColor(Color.BLACK);
        g.fillRect(gapX, 10, 20, 40);
        g.dispose();

        BufferedImage piece = bg.getSubimage(gapX, 10, 20, 40);
        byte[] bgBytes = toPngBytes(bg);
        byte[] pieceBytes = toPngBytes(piece);

        int x = captchaService.calculateDistance(bgBytes, pieceBytes);
        assertTrue(x > gapX - 15 && x < gapX + 15, "匹配位置应接近缺口 X=" + gapX + "，实际=" + x);
    }

    private static byte[] toPngBytes(BufferedImage img) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}
