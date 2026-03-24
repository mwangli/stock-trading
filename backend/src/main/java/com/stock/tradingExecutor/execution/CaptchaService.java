package com.stock.tradingExecutor.execution;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 滑块验证码破解服务
 * 负责图像识别和滑动轨迹生成
 *
 * @author mwangli
 * @since 2026-03-21
 */
@Slf4j
@Component
public class CaptchaService {

    private static final int GRAY_THRESHOLD = 30;

    /**
     * 计算滑块需要移动的距离
     *
     * @param backgroundImage 背景图字节数组
     * @param sliderImage     滑块图字节数组
     * @return 滑块需要移动的距离(像素)
     */
    public int calculateDistance(byte[] backgroundImage, byte[] sliderImage) {
        try {
            BufferedImage bgImg = ImageIO.read(new ByteArrayInputStream(backgroundImage));
            BufferedImage sliderImg = ImageIO.read(new ByteArrayInputStream(sliderImage));

            if (bgImg == null || sliderImg == null) {
                log.error("[CaptchaService] 图片解析失败");
                return 0;
            }

            BufferedImage bgGray = toGray(bgImg);
            BufferedImage sliderGray = toGray(sliderImg);

            return templateMatch(bgGray, sliderGray);
        } catch (IOException e) {
            log.error("[CaptchaService] 图像处理失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 按设计文档缓动曲线生成滑动轨迹（累积 X、微小 Y 抖动），用于 Selenium Actions 的 moveByOffset 差分。
     *
     * @param distance 目标水平距离（像素）
     * @return 轨迹点 [x, y]，x 从 0 增至约 distance
     */
    public List<int[]> generateEaseSlideTrack(int distance) {
        return generateEaseSlideTrack(distance, new Random());
    }

    /**
     * 与 {@link #generateEaseSlideTrack(int)} 相同，可注入固定 {@link Random} 便于测试。
     *
     * @param distance 目标水平距离（像素）
     * @param random   随机源
     * @return 轨迹点列表
     */
    public List<int[]> generateEaseSlideTrack(int distance, Random random) {
        List<int[]> track = new ArrayList<>();
        int safeDist = Math.max(0, distance);
        int trackSize = Math.max(2, 30 + safeDist / 10);
        for (int i = 0; i < trackSize; i++) {
            double progress = (double) i / (trackSize - 1);
            double easeProgress = calculateEaseProgress(progress);
            int x = (int) Math.round(safeDist * easeProgress + (random.nextDouble() - 0.5) * 4);
            int y = (int) Math.round(Math.sin(i * 0.3) * 3);
            track.add(new int[]{x, y});
        }
        return track;
    }

    /**
     * 设计文档中的分段缓动函数。
     *
     * @param progress 0~1
     * @return 缓动后的进度 0~1
     */
    public double calculateEaseProgress(double progress) {
        if (progress < 0.3) {
            return 2 * progress * progress;
        } else if (progress < 0.7) {
            return -1 + (4 - 2 * progress) * progress;
        } else {
            return 1 - Math.pow(-2 * progress + 2, 2) / 2;
        }
    }

    /**
     * 生成模拟滑动轨迹
     * 模拟人类滑动行为：先快后慢，带有随机抖动
     *
     * @param distance 目标距离
     * @return 滑动轨迹列表(每时刻的位置)
     */
    public List<Integer> generateSlideTrack(int distance) {
        List<Integer> track = new ArrayList<>();
        int current = 0;
        int acceleration = 1;
        int maxAcceleration = 5;
        Random random = new Random();

        while (current < distance) {
            int step = (int) (current * 0.1 + acceleration + random.nextInt(3));
            if (acceleration < maxAcceleration) {
                acceleration++;
            }
            current += step;
            if (current > distance) {
                current = distance;
            }
            track.add(current);
        }

        for (int i = 0; i < track.size() / 3; i++) {
            int idx = random.nextInt(track.size());
            int jitter = random.nextInt(5) - 2;
            track.set(idx, Math.max(0, Math.min(distance, track.get(idx) + jitter)));
        }

        return track;
    }

    /**
     * 转换为灰度图
     * 减少计算复杂度，提高匹配效率
     */
    private BufferedImage toGray(BufferedImage image) {
        BufferedImage grayImage = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics g = grayImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return grayImage;
    }

    /**
     * 模板匹配
     * 在背景图中寻找滑块的最佳匹配位置
     */
    private int templateMatch(BufferedImage background, BufferedImage template) {
        int bgWidth = background.getWidth();
        int bgHeight = background.getHeight();
        int tplWidth = template.getWidth();
        int tplHeight = template.getHeight();

        if (tplWidth > bgWidth || tplHeight > bgHeight) {
            log.warn("[CaptchaService] 模板尺寸大于背景图");
            return 0;
        }

        int minDiff = Integer.MAX_VALUE;
        int bestX = 0;

        for (int x = 0; x <= bgWidth - tplWidth; x++) {
            for (int y = 0; y <= bgHeight - tplHeight; y++) {
                int diff = calculateDiff(background, template, x, y);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestX = x;
                }
            }
        }

        return bestX;
    }

    /**
     * 计算图像差异
     * 用于评估模板匹配的准确度
     */
    private int calculateDiff(BufferedImage background, BufferedImage template, int x, int y) {
        int diff = 0;
        for (int i = 0; i < template.getWidth(); i++) {
            for (int j = 0; j < template.getHeight(); j++) {
                int bgPixel = background.getRGB(x + i, y + j);
                int tplPixel = template.getRGB(i, j);
                diff += Math.abs((bgPixel & 0xFF) - (tplPixel & 0xFF));
            }
        }
        return diff;
    }

    /**
     * 获取图片的原始宽度（像素），用于网易云盾距离修正。
     *
     * @param imageBytes 图片字节数组
     * @return 图片宽度；解析失败返回 0
     */
    public int getImageWidth(byte[] imageBytes) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            return img != null ? img.getWidth() : 0;
        } catch (IOException e) {
            log.error("[CaptchaService] 获取图片宽度失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 边缘检测计算缺口位置
     * 通过边缘检测算法寻找背景图中的缺口位置
     *
     * @param backgroundImage 背景图字节数组
     * @return 缺口X坐标
     */
    public int calculateDistanceByEdge(byte[] backgroundImage) {
        try {
            BufferedImage bgImg = ImageIO.read(new ByteArrayInputStream(backgroundImage));
            if (bgImg == null) {
                return 0;
            }

            BufferedImage grayImage = toGray(bgImg);
            int width = grayImage.getWidth();
            int height = grayImage.getHeight();

            int[] edgeMap = new int[width];
            for (int x = 0; x < width; x++) {
                int edgeCount = 0;
                for (int y = 0; y < height; y++) {
                    int pixel = grayImage.getRGB(x, y) & 0xFF;
                    if (y > 0) {
                        int prevPixel = grayImage.getRGB(x, y - 1) & 0xFF;
                        if (Math.abs(pixel - prevPixel) > GRAY_THRESHOLD) {
                            edgeCount++;
                        }
                    }
                }
                edgeMap[x] = edgeCount;
            }

            int maxEdge = 0;
            int edgeX = 0;
            for (int x = 0; x < width; x++) {
                if (edgeMap[x] > maxEdge) {
                    maxEdge = edgeMap[x];
                    edgeX = x;
                }
            }

            return edgeX;
        } catch (IOException e) {
            log.error("[CaptchaService] 边缘检测失败: {}", e.getMessage());
            return 0;
        }
    }
}