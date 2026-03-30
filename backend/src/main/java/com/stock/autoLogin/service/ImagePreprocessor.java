package com.stock.autoLogin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * 验证码图片预处理服务
 * 包含去噪、二值化、干扰线去除等功能
 *
 * @author mwangli
 * @since 2026-03-30
 */
@Slf4j
@Component
public class ImagePreprocessor {

    /**
     * 预处理结果封装
     */
    public record ProcessedResult(
            BufferedImage original,
            boolean[][] darkMatrix,
            BufferedImage processed,
            String debugInfo
    ) {}

    /**
     * 预处理验证码图片
     * 流程：灰度化 -> 干扰线去除 -> 二值化 -> 放大
     *
     * @param img 原始图片
     * @param scale 放大倍数（建议1-2）
     * @return 预处理结果
     */
    public ProcessedResult preprocess(BufferedImage img, int scale) {
        StringBuilder debug = new StringBuilder();

        // 1. 灰度化
        int[][] gray = toGrayMatrix(img);
        debug.append("灰度化完成, 尺寸=").append(img.getWidth()).append("x").append(img.getHeight()).append("; ");

        // 2. 干扰线去除
        int[][] denoised = removeHorizontalNoiseLine(gray, img.getWidth(), img.getHeight());
        debug.append("干扰线去除完成; ");

        // 3. 二值化
        boolean[][] dark = binarize(denoised, img.getWidth(), img.getHeight());
        debug.append("二值化完成; ");

        // 4. 放大
        BufferedImage scaled = scale > 1 ? scaleImage(dark, img.getWidth(), img.getHeight(), scale) : img;
        debug.append("放大").append(scale).append("倍完成");

        return new ProcessedResult(img, dark, scaled, debug.toString());
    }

    /**
     * 将图片转换为灰度矩阵
     */
    public int[][] toGrayMatrix(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[][] gray = new int[w][h];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                // 使用 luminance 公式
                gray[x][y] = (int) (0.299 * r + 0.587 * g + 0.114 * b);
            }
        }
        return gray;
    }

    /**
     * 去除横向贯穿干扰线
     *
     * 干扰线特征：
     * - 方向：横向（从左到右贯穿）
     * - 长度：占图片宽度的 60%~90%（3/4 = 75%）
     * - 灰度：与数字相比较暗
     *
     * @param gray 灰度矩阵
     * @param w 图片宽度
     * @param h 图片高度
     * @return 去噪后的灰度矩阵
     */
    public int[][] removeHorizontalNoiseLine(int[][] gray, int w, int h) {
        int[][] result = new int[w][h];

        // 计算每行和每列的灰度均值，用于判断是否为干扰线
        int[] rowSum = new int[h];
        int[] colSum = new int[w];

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                rowSum[y] += gray[x][y];
                colSum[x] += gray[x][y];
            }
        }

        // 计算全图均值
        long totalSum = 0;
        for (int y = 0; y < h; y++) {
            totalSum += rowSum[y];
        }
        int avgGray = (int) (totalSum / (w * h));

        for (int y = 0; y < h; y++) {
            int rowAvg = rowSum[y] / w;

            // 判断这一行是否为干扰线：均值接近背景（较亮）且行长度足够长
            boolean isNoiseLine = false;

            // 干扰线判断条件：
            // 1. 行均值接近全图均值（说明大部分是背景）
            // 2. 行的方差小（说明是均匀的直线）
            int variance = calculateRowVariance(gray, w, y);
            if (rowAvg > avgGray && variance < 500) {
                // 检查是否有连续暗像素段占宽度的 60%~90%
                int consecutiveDark = 0;
                int maxConsecutive = 0;
                for (int x = 0; x < w; x++) {
                    if (gray[x][y] < avgGray - 30) {
                        consecutiveDark++;
                        maxConsecutive = Math.max(maxConsecutive, consecutiveDark);
                    } else {
                        consecutiveDark = 0;
                    }
                }
                double ratio = (double) maxConsecutive / w;
                if (ratio >= 0.60 && ratio <= 0.90) {
                    isNoiseLine = true;
                }
            }

            // 如果是干扰线，用上下行的均值替代
            if (isNoiseLine) {
                int upRow = Math.max(0, y - 1);
                int downRow = Math.min(h - 1, y + 1);
                for (int x = 0; x < w; x++) {
                    result[x][y] = (gray[x][upRow] + gray[x][downRow]) / 2;
                }
            } else {
                for (int x = 0; x < w; x++) {
                    result[x][y] = gray[x][y];
                }
            }
        }

        return result;
    }

    /**
     * 计算某行的方差
     */
    private int calculateRowVariance(int[][] gray, int w, int y) {
        int sum = 0;
        for (int x = 0; x < w; x++) {
            sum += gray[x][y];
        }
        int mean = sum / w;
        int variance = 0;
        for (int x = 0; x < w; x++) {
            int diff = gray[x][y] - mean;
            variance += diff * diff;
        }
        return variance / w;
    }

    /**
     * 二值化灰度矩阵
     *
     * @param gray 灰度矩阵
     * @param w 宽度
     * @param h 高度
     * @return 二值矩阵（true=暗像素/内容，false=亮像素/背景）
     */
    public boolean[][] binarize(int[][] gray, int w, int h) {
        // 计算全局阈值（Otsu 方法简化版）
        int[] histogram = new int[256];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                histogram[gray[x][y]]++;
            }
        }

        int total = w * h;
        int sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += i * histogram[i];
        }

        int threshold = 128; // 默认阈值
        int maxVariance = 0;

        for (int t = 0; t < 256; t++) {
            int w0 = 0, w1 = 0;
            int sum0 = 0, sum1 = 0;

            for (int i = 0; i <= t; i++) {
                w0 += histogram[i];
                sum0 += i * histogram[i];
            }
            for (int i = t + 1; i < 256; i++) {
                w1 += histogram[i];
                sum1 += i * histogram[i];
            }

            if (w0 == 0 || w1 == 0) continue;

            double mu0 = (double) sum0 / w0;
            double mu1 = (double) sum1 / w1;
            double variance = w0 * w1 * (mu0 - mu1) * (mu0 - mu1);

            if (variance > maxVariance) {
                maxVariance = (int) variance;
                threshold = t;
            }
        }

        log.debug("二值化阈值: {}", threshold);

        boolean[][] dark = new boolean[w][h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                dark[x][y] = gray[x][y] < threshold;
            }
        }
        return dark;
    }

    /**
     * 放大二值化图片
     */
    public BufferedImage scaleImage(boolean[][] dark, int w, int h, int scale) {
        int newW = w * scale;
        int newH = h * scale;
        BufferedImage result = new BufferedImage(newW, newH, BufferedImage.TYPE_BYTE_BINARY);

        for (int y = 0; y < newH; y++) {
            for (int x = 0; x < newW; x++) {
                int origX = x / scale;
                int origY = y / scale;
                result.setRGB(x, y, dark[origX][origY] ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return result;
    }

    /**
     * 从 Base64 字符串创建 BufferedImage
     */
    public BufferedImage fromBase64(String base64) throws IOException {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    /**
     * 将 BufferedImage 转换为 Base64
     */
    public String toBase64(BufferedImage img, String format) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    /**
     * 定位表达式区域（数字和运算符的边界）
     * 使用垂直投影找到每个字符的起始/结束位置
     *
     * @param dark 二值化矩阵
     * @param w 宽度
     * @param h 高度
     * @return 分割点列表
     */
    public int[] findSplitPoints(boolean[][] dark, int w, int h) {
        // 计算垂直投影
        int[] projection = new int[w];
        for (int x = 0; x < w; x++) {
            int count = 0;
            for (int y = 0; y < h; y++) {
                if (dark[x][y]) {
                    count++;
                }
            }
            projection[x] = count;
        }

        // 找分割点（波谷位置）
        int threshold = h / 8; // 暗像素 < 12.5% 视为间隔
        java.util.List<Integer> points = new java.util.ArrayList<>();

        boolean inContent = false;
        int contentStart = 0;

        for (int x = 0; x < w; x++) {
            boolean hasContent = projection[x] > threshold;
            if (!inContent && hasContent) {
                inContent = true;
                contentStart = x;
            } else if (inContent && !hasContent) {
                inContent = false;
                points.add((contentStart + x) / 2); // 中点作为分割点
            }
        }

        int[] result = new int[points.size()];
        for (int i = 0; i < points.size(); i++) {
            result[i] = points.get(i);
        }
        return result;
    }
}
