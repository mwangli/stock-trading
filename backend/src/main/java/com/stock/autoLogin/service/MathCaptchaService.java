package com.stock.autoLogin.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 数学验证码服务
 * 获取中信证券验证码图片（格式：两位数 +/- 两位数 =），
 * 整图放大后交由百度数字OCR识别，提取4个数字后计算答案
 *
 * @author mwangli
 * @since 2026-03-28
 */
@Slf4j
@Service
public class MathCaptchaService {

    /** 中信证券验证码图片 API */
    private static final String CAPTCHA_API_URL = "https://weixin.citicsinfo.com/reqxml?";

    /** 百度 OAuth token 接口 */
    private static final String BAIDU_TOKEN_URL =
            "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=%s&client_secret=%s";

    /** 百度通用文字识别接口（用于识别完整表达式） */
    private static final String BAIDU_OCR_GENERAL_URL =
            "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=%s";

    /** 百度数字识别 OCR 接口（备用，纯数字场景） */
    private static final String BAIDU_OCR_NUMBERS_URL =
            "https://aip.baidubce.com/rest/2.0/ocr/v1/numbers?access_token=%s";

    /** 整图放大倍数（送OCR前放大，提升识别率） */
    private static final int SCALE = 4;

    /** 二值化灰度阈值 */
    private static final int BINARY_THRESHOLD = 150;

    /** 运算符区域（图片宽度 35%~55% 之间）竖向笔画比例超过此值判定为加号 */
    private static final double PLUS_RATIO = 0.12;

    private final ImagePreprocessor imagePreprocessor;

    public MathCaptchaService(ImagePreprocessor imagePreprocessor) {
        this.imagePreprocessor = imagePreprocessor;
    }

    @Value("${spring.auto-login.account:13278828091}")
    private String defaultAccount;

    @Value("${spring.auto-login.baidu-ocr.api-key:hgfY9DodOtbSuF7v6n89YGki}")
    private String baiduApiKey;

    @Value("${spring.auto-login.baidu-ocr.secret-key:DGsjMnwV4QxoQDjjrRfPxZxvaUmRmBsF}")
    private String baiduSecretKey;

    private final AtomicReference<String> cachedToken = new AtomicReference<>();
    private final AtomicLong tokenExpireAt = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // 公开入口
    // -------------------------------------------------------------------------

    /**
     * 获取验证码原始图片
     *
     * @param account 资金账号/手机号
     * @return 验证码原始图片，失败返回 null
     */
    public BufferedImage fetchCaptchaImage(String account) {
        try {
            String base64Raw = fetchCaptchaBase64(account);
            if (base64Raw == null) {
                return null;
            }
            byte[] rawBytes = Base64.getDecoder().decode(base64Raw);
            return ImageIO.read(new ByteArrayInputStream(rawBytes));
        } catch (Exception e) {
            log.error("获取验证码图片失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 获取验证码图片并识别计算结果
     *
     * @param account 资金账号/手机号
     * @return Map 包含：success, expression, result, method, error
     */
    @Data
    public static class CaptchaQuality {
        private double score;
        private boolean acceptable;
        private boolean lowQuality;
        private java.util.List<String> reasons;

        public static CaptchaQuality good() {
            CaptchaQuality q = new CaptchaQuality();
            q.score = 1.0;
            q.acceptable = true;
            q.lowQuality = false;
            q.reasons = new ArrayList<>();
            return q;
        }

        public static CaptchaQuality low(double score, String reason) {
            CaptchaQuality q = new CaptchaQuality();
            q.score = score;
            q.acceptable = score >= 0.5;
            q.lowQuality = true;
            q.reasons = new ArrayList<>();
            q.reasons.add(reason);
            return q;
        }

        public static CaptchaQuality fail(String reason) {
            CaptchaQuality q = new CaptchaQuality();
            q.score = 0;
            q.acceptable = false;
            q.lowQuality = true;
            q.reasons = new ArrayList<>();
            q.reasons.add(reason);
            return q;
        }
    }

    /**
     * 验证码质量检测
     * 评估图片质量，决定是否需要重试
     * 验证码图片通常较小，调整尺寸标准为实际合理范围
     */
    private CaptchaQuality assessCaptchaQuality(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();

        // 检查图片尺寸 - 验证码图片通常较小，放宽标准
        if (w < 50 || h < 20) {
            return CaptchaQuality.fail("图片尺寸过小: " + w + "x" + h);
        }

        // 计算灰度统计
        int[] grayValues = new int[256];
        int pixelCount = 0;
        long sumGray = 0;
        long sumVariance = 0;

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                grayValues[gray]++;
                sumGray += gray;
                pixelCount++;
            }
        }

        int meanGray = (int) (sumGray / pixelCount);

        // 计算方差
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                int gray = (int) (0.299 * r + 0.587 * g + 0.114 * b);
                sumVariance += (gray - meanGray) * (gray - meanGray);
            }
        }
        double variance = sumVariance / pixelCount;
        double stdDev = Math.sqrt(variance);

        // 计算暗像素比例（验证码内容通常是深色的）
        int darkPixels = 0;
        int threshold = 150; // 亮度小于150视为暗像素
        for (int gray : grayValues) {
            // 忽略极端值计算
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int gray = (rgb >> 16) & 0xFF;
                if (gray < threshold) darkPixels++;
            }
        }
        double darkRatio = (double) darkPixels / pixelCount;

        // 质量评分
        double score = 1.0;
        java.util.List<String> reasons = new java.util.ArrayList<>();

        // 1. 检查对比度（标准差）- 验证码图片通常对比度较低，放宽标准
        if (stdDev < 15) {
            score -= 0.3;
            reasons.add("对比度过低 (stdDev=" + String.format("%.1f", stdDev) + ")");
        }

        // 2. 检查暗像素比例（内容区域占比）
        if (darkRatio < 0.03) {
            score -= 0.4;
            reasons.add("内容区域过少 (darkRatio=" + String.format("%.2f", darkRatio) + ")");
        } else if (darkRatio > 0.6) {
            score -= 0.2;
            reasons.add("背景过暗 (darkRatio=" + String.format("%.2f", darkRatio) + ")");
        }

        // 3. 检查是否有足够的灰度分布
        int graySpan = 0;
        int firstNonZero = -1, lastNonZero = -1;
        for (int i = 0; i < 256; i++) {
            if (grayValues[i] > 0 && firstNonZero < 0) firstNonZero = i;
            if (grayValues[255 - i] > 0 && lastNonZero < 0) lastNonZero = 255 - i;
        }
        graySpan = lastNonZero - firstNonZero;
        if (graySpan < 30) {
            score -= 0.2;
            reasons.add("灰度范围过窄 (span=" + graySpan + ")");
        }

        // 4. 检查图片尺寸是否合理 - 验证码天生较小，降低要求
        if (w < 60 || h < 25) {
            score -= 0.1;
            reasons.add("图片尺寸偏小");
        }

        // 判定
        if (score >= 0.7) {
            return CaptchaQuality.good();
        } else if (score >= 0.4) {
            CaptchaQuality q = CaptchaQuality.low(score, reasons.isEmpty() ? "轻微质量问题" : String.join(", ", reasons));
            return q;
        } else {
            return CaptchaQuality.fail(reasons.isEmpty() ? "质量问题严重" : String.join(", ", reasons));
        }
    }

    public Map<String, Object> getCaptchaResult(String account) {
        Map<String, Object> result = new HashMap<>();
        int maxRetries = 3;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("验证码识别第 {} 次尝试", attempt);

                String base64Raw = fetchCaptchaBase64(account);
                if (base64Raw == null) {
                    result.put("success", false);
                    result.put("error", "获取验证码图片失败");
                    return result;
                }

                byte[] rawBytes = Base64.getDecoder().decode(base64Raw);
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));

                CaptchaQuality quality = assessCaptchaQuality(img);
                if (!quality.isAcceptable()) {
                    log.warn("验证码质量不合格: {} (分数: {}), 原因: {}",
                            quality.isLowQuality() ? "低质量" : "不合格",
                            quality.getScore(), quality.getReasons());
                    if (attempt < maxRetries) {
                        log.info("重新获取验证码...");
                        continue;
                    }
                    result.put("success", false);
                    result.put("error", "验证码质量不合格: " + quality.getReasons());
                    result.put("quality", quality);
                    return result;
                }

                saveDebugImage(img, "raw");
                ImagePreprocessor.ProcessedResult preprocessed = imagePreprocessor.preprocess(img, 1);
                saveDebugImage(preprocessed.processed(), "preprocessed");

                int leftNum = -1, rightNum = -1;
                char op = '?';
                String method = "unknown";

                BufferedImage scaled = scaleImage(img, SCALE);
                byte[] scaledBytes = toBytes(scaled);
                String scaledB64 = Base64.getEncoder().encodeToString(scaledBytes);
                saveDebugImage(scaled, "scaled");

                String ocrText = ocrGeneralText(scaledB64);
                log.info("方案A - 放大{}倍 OCR: [{}]", SCALE, ocrText);

                if (ocrText != null && ocrText.matches(".*\\d+.*")) {
                    String digitsOnly = ocrText.replaceAll("[^0-9]", "");
                    java.util.regex.Matcher exprMatcher = java.util.regex.Pattern
                            .compile("(\\d+)\\s*([+\\-×*])\\s*(\\d+)").matcher(ocrText);
                    if (exprMatcher.find() && digitsOnly.length() >= 3) {
                        leftNum = Integer.parseInt(exprMatcher.group(1));
                        rightNum = Integer.parseInt(exprMatcher.group(3));
                        op = exprMatcher.group(2).charAt(0);
                        if (op == '×' || op == '*') op = '+';
                        method = "scaled-image-ocr";
                        log.info("方案A成功: {} {} {}", leftNum, op, rightNum);
                    }
                }

                if (leftNum < 0) {
                    String allDigits = ocrAllDigits(scaledB64);
                    log.info("numbers OCR数字串: [{}]", allDigits);
                    String digits = allDigits.replaceAll("[^0-9]", "");
                    log.info("清理后数字串: [{}] (长度={})", digits, digits.length());

                    if (digits.length() >= 4) {
                        String num4 = digits.substring(digits.length() - 4);
                        if (num4.charAt(0) != '0' && num4.charAt(2) != '0') {
                            leftNum = Integer.parseInt(num4.substring(0, 2));
                            rightNum = Integer.parseInt(num4.substring(2, 4));
                            op = detectOperator(preprocessed.darkMatrix(), img.getWidth(), img.getHeight());
                            if (op == '?') op = '+';
                            method = "numbers-ocr-4digits";
                            log.info("方案B成功: {} {} {}", leftNum, op, rightNum);
                        }
                    } else if (digits.length() == 3) {
                        boolean[][] dark = preprocessed.darkMatrix();
                        op = detectOperator(dark, img.getWidth(), img.getHeight());
                        int opCol = findOperatorColumn(dark, img.getWidth(), img.getHeight());
                        double opRatio = (double) opCol / img.getWidth();
                        log.info("运算符列位置: {} ({:.1%}), 运算符: {}", opCol, opRatio, op);

                        if (opRatio < 0.35) {
                            leftNum = Integer.parseInt(digits.substring(0, 1));
                            rightNum = Integer.parseInt(digits.substring(1, 3));
                        } else {
                            leftNum = Integer.parseInt(digits.substring(0, 2));
                            rightNum = Integer.parseInt(digits.substring(2, 3));
                        }
                        if (op == '?') op = '+';
                        method = "numbers-ocr-3digits";
                        log.info("方案B(3位)成功: {} {} {}", leftNum, op, rightNum);
                    } else if (digits.length() == 2) {
                        log.warn("numbers OCR只返回2位数字，无法分割: {}", digits);
                    }
                }

                if (leftNum < 0 || rightNum < 0 || op == '?') {
                    if (attempt < maxRetries) {
                        log.info("方案失败，重试...");
                        continue;
                    }
                    result.put("success", false);
                    result.put("error", "OCR识别失败，无法解析表达式");
                    result.put("method", "manual");
                    return result;
                }

                int answer = op == '+' ? leftNum + rightNum : leftNum - rightNum;
                String expression = leftNum + " " + op + " " + rightNum;

                result.put("success", true);
                result.put("expression", expression);
                result.put("result", String.valueOf(answer));
                result.put("method", method);
                log.info("验证码识别成功: {} = {}", expression, answer);
                return result;

            } catch (Exception e) {
                log.error("验证码识别异常: {}", e.getMessage());
                if (attempt < maxRetries) {
                    log.info("异常发生，重试...");
                    continue;
                }
                result.put("success", false);
                result.put("error", e.getMessage());
                return result;
            }
        }

        result.put("success", false);
        result.put("error", "达到最大重试次数");
        return result;
    }

    // -------------------------------------------------------------------------
    // 图像处理
    // -------------------------------------------------------------------------

    /**
     * 用 NEAREST_NEIGHBOR 插值放大图片，保留锐边
     *
     * @param img   原始图片
     * @param scale 放大倍数
     * @return 放大后的图片
     */
    private BufferedImage scaleImage(BufferedImage img, int scale) {
        int newW = img.getWidth()  * scale;
        int newH = img.getHeight() * scale;
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g2.setColor(java.awt.Color.WHITE);
        g2.fillRect(0, 0, newW, newH);
        g2.drawImage(img, 0, 0, newW, newH, null);
        g2.dispose();
        return out;
    }

    /**
     * 灰度二值化：返回 dark[x][y] = true 表示该像素为暗（内容）
     *
     * @param img 原始图片
     * @return 二值数组
     */
    private boolean[][] binarize(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        boolean[][] dark = new boolean[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8)  & 0xFF;
                int b = rgb & 0xFF;
                dark[x][y] = (r + g + b) / 3 < BINARY_THRESHOLD;
            }
        }
        return dark;
    }

    /**
     * 通过分析图片宽度 35%~55% 区域的竖向笔画密度判断运算符
     * 加号（+）在上下两段均有暗像素；减号（-）只在中段有
     *
     * @param dark 二值数组（基于原始图片尺寸）
     * @param w    图片宽度
     * @param h    图片高度
     * @return '+' 或 '-'
     */
    private char detectOperator(boolean[][] dark, int w, int h) {
        int opLeft  = (int)(w * 0.35);
        int opRight = (int)(w * 0.55);
        int topEnd       = h / 3;
        int bottomStart  = h * 2 / 3;
        int darkTopBottom = 0;
        int total = 0;
        for (int x = opLeft; x < opRight && x < w; x++) {
            for (int y = 0; y < topEnd; y++)       if (dark[x][y]) darkTopBottom++;
            for (int y = bottomStart; y < h; y++)  if (dark[x][y]) darkTopBottom++;
            total += h;
        }
        double ratio = total > 0 ? (double) darkTopBottom / total : 0;
        log.info("运算符竖向密度: {:.4f}（阈值 {}）", ratio, PLUS_RATIO);
        char op = ratio >= PLUS_RATIO ? '+' : '-';
        log.info("运算符判定: {}", op);
        return op;
    }

    /**
     * 基于形状的运算符检测
     * 分析运算符区域的连通组件和几何特征
     *
     * @param dark 二值数组
     * @param w 图片宽度
     * @param h 图片高度
     * @return '+' 或 '-' 或 '?'
     */
    private char detectOperatorByShape(boolean[][] dark, int w, int h) {
        // 找到运算符区域（宽度的35%~55%）
        int opLeft = (int)(w * 0.35);
        int opRight = (int)(w * 0.55);

        // 统计水平和垂直方向的暗像素
        int horizontalCount = 0;
        int verticalCount = 0;
        int centerX = (opLeft + opRight) / 2;
        int centerY = h / 2;

        for (int x = opLeft; x < opRight; x++) {
            // 水平线（中段）
            if (dark[x][centerY]) horizontalCount++;
        }

        for (int y = 0; y < h; y++) {
            // 垂直线（中段）
            if (dark[centerX][y]) verticalCount++;
        }

        // 加号：水平和垂直方向都有较多暗像素
        // 减号：只有水平方向有暗像素
        double hRatio = (double) horizontalCount / (opRight - opLeft);
        double vRatio = (double) verticalCount / h;

        log.info("运算符形状分析 - 水平比例: {:.2f}, 垂直比例: {:.2f}", hRatio, vRatio);

        if (hRatio > 0.3 && vRatio > 0.3) {
            return '+';
        } else if (hRatio > 0.3) {
            return '-';
        }
        return '?';
    }

    /**
     * 基于二值化和连通区域分析提取数字
     * 通过垂直投影分割数字区域，然后逐个识别
     *
     * @param img 原始图片
     * @return 提取的数字数组，失败返回null
     */
    private int[] extractNumbersByBinarization(BufferedImage img) {
        try {
            int w = img.getWidth();
            int h = img.getHeight();
            boolean[][] dark = binarize(img);

            // 计算垂直投影
            int[] projection = new int[w];
            for (int x = 0; x < w; x++) {
                for (int y = 0; y < h; y++) {
                    if (dark[x][y]) {
                        projection[x]++;
                    }
                }
            }

            // 找到分割点（波谷位置）
            int threshold = h / 10;
            java.util.List<Integer> splitPoints = new java.util.ArrayList<>();
            boolean inContent = false;
            int contentStart = 0;

            for (int x = 0; x < w; x++) {
                boolean hasContent = projection[x] > threshold;
                if (!inContent && hasContent) {
                    inContent = true;
                    contentStart = x;
                } else if (inContent && !hasContent) {
                    inContent = false;
                    splitPoints.add((contentStart + x) / 2);
                }
            }

            // 根据分割点提取数字
            if (splitPoints.size() < 3) {
                log.info("分割点数量不足: {}", splitPoints.size());
                return null;
            }

            // 通常格式为：数字1 | 运算符 | 数字2 | 等号
            // 我们需要找到运算符的位置来分割两个数字
            java.util.List<Integer> numRegions = new java.util.ArrayList<>();
            int prev = 0;
            for (int split : splitPoints) {
                if (split - prev > 10) { // 忽略太小的区域
                    numRegions.add(prev);
                    numRegions.add(split);
                }
                prev = split;
            }

            log.info("提取到 {} 个区域", numRegions.size() / 2);

            // 简单策略：如果有4个区域，取中间两个作为数字
            if (numRegions.size() >= 4) {
                int[] numbers = new int[2];
                // 区域索引 1 和 2 是两个数字（跳过第一个区域和最后一个等号）
                int num1Start = numRegions.get(1);
                int num1End = numRegions.get(2);
                int num2Start = numRegions.get(3);
                int num2End = numRegions.size() > 4 ? numRegions.get(4) : w;

                // 简单数字识别：统计暗像素数量作为特征
                numbers[0] = estimateDigitByPixels(dark, num1Start, num1End, h);
                numbers[1] = estimateDigitByPixels(dark, num2Start, num2End, h);

                return numbers;
            }

            return null;
        } catch (Exception e) {
            log.error("二值化数字提取异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据暗像素数量估算数字
     * 这是一个非常简化的实现，实际应该用模板匹配
     */
    private int estimateDigitByPixels(boolean[][] dark, int startX, int endX, int h) {
        int count = 0;
        int pixelCount = 0;
        for (int x = startX; x < endX && x < dark.length; x++) {
            for (int y = 0; y < h; y++) {
                if (dark[x][y]) {
                    count++;
                }
                pixelCount++;
            }
        }

        if (pixelCount == 0) {
            return 0;
        }

        // 根据像素比例估算
        int avgCount = count * 100 / pixelCount;

        if (avgCount < 5) return 1;
        if (avgCount < 10) return 7;
        if (avgCount < 15) return 4;
        if (avgCount < 20) return 0;
        return 8;
    }

    // -------------------------------------------------------------------------
    // OCR
    // -------------------------------------------------------------------------

    /**
     * 调用百度 general_basic OCR，返回识别到的全部文本（拼接所有 words）
     *
     * @param base64Image PNG base64
     * @return 识别文本，如 "8+82=" 或 "19+15="
     */
    private String ocrGeneralText(String base64Image) {
        try {
            String token = getAccessToken();
            if (token == null) return "";

            String url  = String.format(BAIDU_OCR_GENERAL_URL, token);
            String body = "image=" + java.net.URLEncoder.encode(base64Image, StandardCharsets.UTF_8);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("general_basic HTTP错误: {}", code);
                return "";
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("general_basic响应: {}", response);

            JSONObject json = JSONObject.parseObject(response);
            JSONArray wordsResult = json.getJSONArray("words_result");
            if (wordsResult == null || wordsResult.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wordsResult.size(); i++) {
                String word = wordsResult.getJSONObject(i).getString("words");
                if (word != null) sb.append(word);
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("general_basic OCR调用失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 在二值化图片中找到运算符区域的起始列（用于3位数字时判断左侧位数）
     * 扫描 15%~60% 宽度范围，找第一个从高密度到低密度的转折点（左数结束处）
     *
     * @param dark 二值数组
     * @param w    图片宽度
     * @param h    图片高度
     * @return 运算符大致起始列，找不到则返回 w/3
     */
    private int findOperatorColumn(boolean[][] dark, int w, int h) {
        int scanStart = w / 8;         // 从 12.5% 开始扫（跳过边缘空白）
        int scanEnd   = (int)(w * 0.6); // 最多扫到 60%
        boolean wasHigh = false;
        for (int x = scanStart; x < scanEnd; x++) {
            int count = 0;
            for (int y = 0; y < h; y++) if (dark[x][y]) count++;
            double density = (double) count / h;
            if (!wasHigh && density >= 0.15) {
                wasHigh = true;
            } else if (wasHigh && density < 0.10) {
                // 找到第一个字符块结束后的间隙，即运算符起始列附近
                return x;
            }
        }
        return w / 3;
    }

    /**
     * 调用百度 numbers OCR，从 words_result 中提取所有数字字符并拼接
     *
     * @param base64Image PNG base64
     * @return 纯数字字符串（如 "1915" 代表 19 op 15）
     */
    private String ocrAllDigits(String base64Image) {
        try {
            String token = getAccessToken();
            if (token == null) return "";

            String url  = String.format(BAIDU_OCR_NUMBERS_URL, token);
            String body = "image=" + java.net.URLEncoder.encode(base64Image, StandardCharsets.UTF_8);

            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code != 200) {
                log.error("百度OCR HTTP错误: {}", code);
                return "";
            }

            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("百度OCR响应: {}", response);

            JSONObject json = JSONObject.parseObject(response);
            JSONArray wordsResult = json.getJSONArray("words_result");
            if (wordsResult == null || wordsResult.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < wordsResult.size(); i++) {
                String word = wordsResult.getJSONObject(i).getString("words");
                if (word != null) {
                    sb.append(word.replaceAll("[^0-9]", ""));
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("百度OCR调用失败: {}", e.getMessage());
            return "";
        }
    }

    // -------------------------------------------------------------------------
    // 百度 Access Token
    // -------------------------------------------------------------------------

    /**
     * 获取百度 access_token（带30天缓存，提前10分钟刷新）
     *
     * @return access_token，失败返回 null
     */
    private String getAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedToken.get() != null && now < tokenExpireAt.get()) return cachedToken.get();
        try {
            String tokenUrl = String.format(BAIDU_TOKEN_URL, baiduApiKey, baiduSecretKey);
            HttpURLConnection conn = (HttpURLConnection) new URL(tokenUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(response);
            String token = json.getString("access_token");
            long expiresIn = json.getLongValue("expires_in", 2592000L);
            cachedToken.set(token);
            tokenExpireAt.set(now + (expiresIn - 600) * 1000L);
            log.info("百度 access_token 获取成功");
            return token;
        } catch (Exception e) {
            log.error("获取百度 access_token 失败: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 中信证券验证码 API
    // -------------------------------------------------------------------------

    /**
     * 调用中信证券 API 获取验证码图片 base64（JPEG，不含 data:image 前缀）
     *
     * @param account 资金账号
     * @return base64，失败返回 null
     */
    private String fetchCaptchaBase64(String account) {
        try {
            String reqno = String.valueOf(System.currentTimeMillis());
            String params = String.format(
                    "action=41092&reqno=%s&MobileCode=%s&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=",
                    reqno, account);
            String url = CAPTCHA_API_URL + params;
            log.info("请求验证码API: {}", url);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            if (conn.getResponseCode() != 200) return null;
            String response = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JSONObject json = JSONObject.parseObject(response);
            String data = json.getString("MESSAGE");
            if (data == null || !data.contains("base64,")) {
                log.error("验证码API响应格式异常: {}", data);
                return null;
            }
            return data.substring(data.indexOf("base64,") + 7);
        } catch (Exception e) {
            log.error("调用验证码API失败: {}", e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------

    /**
     * BufferedImage 转 PNG 字节数组
     *
     * @param img 图片
     * @return PNG 字节数组
     * @throws IOException IO异常
     */
    private byte[] toBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * 保存调试图片到 .tmp/captcha/
     *
     * @param img 图片
     * @param tag 标签（raw/scaled）
     */
    private void saveDebugImage(BufferedImage img, String tag) {
        try {
            Path dir = Paths.get(".tmp", "captcha");
            Files.createDirectories(dir);
            String name = "captcha_" + tag + "_" + System.currentTimeMillis() + ".png";
            File out = dir.resolve(name).toFile();
            ImageIO.write(img, "PNG", out);
            log.debug("调试图片: {}", out.getAbsolutePath());
        } catch (Exception e) {
            log.warn("保存调试图片失败: {}", e.getMessage());
        }
    }

    /**
     * 计算数学表达式（供外部调用）
     *
     * @param expression 如 "39+85" 或 "95-20"
     * @return 计算结果字符串
     */
    public String calculateExpression(String expression) {
        if (expression == null || expression.isBlank()) return null;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)")
                    .matcher(expression);
            if (m.find()) {
                int a = Integer.parseInt(m.group(1));
                char op = m.group(2).charAt(0);
                int b = Integer.parseInt(m.group(3));
                return String.valueOf(switch (op) {
                    case '+' -> a + b;
                    case '-' -> a - b;
                    case '*' -> a * b;
                    case '/' -> a / b;
                    default  -> throw new IllegalArgumentException("未知运算符: " + op);
                });
            }
            if (expression.trim().matches("-?\\d+")) return expression.trim();
            return null;
        } catch (Exception e) {
            log.error("计算表达式失败: {}", e.getMessage());
            return null;
        }
    }
}
