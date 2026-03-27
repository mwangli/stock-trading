package com.stock.autoLogin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 数学验证码服务
 * 通过API接口获取验证码图片Base64数据，进行图像处理和模式识别
 * 注意：由于阿里云仓库无可用OCR库，采用图像分析+模板匹配方案
 *
 * @author mwangli
 * @since 2026-03-27
 */
@Slf4j
@Service
public class MathCaptchaService {

    private static final String CAPTCHA_API_URL = "https://weixin.citicsinfo.com/reqxml?";

    @Value("${spring.auto-login.account:13278828091}")
    private String defaultAccount;

    /**
     * 获取验证码图片并尝试OCR识别
     *
     * @param account 资金账号/手机号
     * @return 包含识别结果的Map：expression(原始表达式), result(计算结果), success(是否成功), imagePath(图片路径供人工识别)
     */
    public Map<String, Object> getCaptchaResult(String account) {
        Map<String, Object> result = new HashMap<>();

        try {
            String base64Data = fetchCaptchaBase64(account);
            if (base64Data == null) {
                result.put("success", false);
                result.put("error", "获取验证码图片失败");
                return result;
            }

            byte[] imageBytes = decodeBase64Image(base64Data);
            if (imageBytes == null) {
                result.put("success", false);
                result.put("error", "图片解码失败");
                return result;
            }

            File imageFile = saveDebugImage(imageBytes);
            result.put("imagePath", imageFile.getAbsolutePath());
            result.put("imageUrl", "file:" + imageFile.getAbsolutePath());

            String expression = ocrImage(imageBytes);
            if (expression != null) {
                String calcResult = calculateExpression(expression);
                if (calcResult != null) {
                    result.put("success", true);
                    result.put("expression", expression);
                    result.put("result", calcResult);
                    result.put("method", "ocr");
                    log.info("数学验证码OCR识别成功: {} = {}", expression, calcResult);
                    return result;
                }
            }

            result.put("success", false);
            result.put("error", "OCR识别失败，请人工识别后调用 input-captcha 接口");
            result.put("method", "manual");
            return result;

        } catch (Exception e) {
            log.error("获取验证码失败: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * 调用API获取验证码图片Base64数据
     */
    private String fetchCaptchaBase64(String account) {
        try {
            String reqno = String.valueOf(System.currentTimeMillis());
            String params = String.format(
                    "action=41092&reqno=%s&MobileCode=%s&newindex=1&cfrom=H5&tfrom=PC&CHANNEL=",
                    reqno, account
            );
            String url = CAPTCHA_API_URL + params;

            log.info("请求验证码API: {}", url);

            java.net.HttpURLConnection conn = (java.net.HttpURLConnection)
                    new java.net.URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                log.error("API返回错误码: {}", responseCode);
                return null;
            }

            String response = new String(conn.getInputStream().readAllBytes(), "UTF-8");
            log.debug("API响应: {}", response);

            return parseBase64FromResponse(response);

        } catch (Exception e) {
            log.error("调用验证码API失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从API响应中解析Base64图片数据
     */
    private String parseBase64FromResponse(String jsonResponse) {
        try {
            com.alibaba.fastjson2.JSONObject json =
                    com.alibaba.fastjson2.JSONObject.parseObject(jsonResponse);

            String errorNo = json.getString("ERRORNO");
            if (!"0".equals(errorNo)) {
                log.error("API返回错误: ERRORNO={}, MESSAGE={}", errorNo, json.getString("MESSAGE"));
                return null;
            }

            String data = json.getString("MESSAGE");
            if (data == null || !data.contains("base64,")) {
                log.error("响应数据格式错误: {}", data);
                return null;
            }

            String base64 = data.substring(data.indexOf("base64,") + 7);
            return base64;

        } catch (Exception e) {
            log.error("解析JSON响应失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 解码Base64图片
     */
    private byte[] decodeBase64Image(String base64) {
        try {
            return Base64.getDecoder().decode(base64);
        } catch (Exception e) {
            log.error("Base64解码失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存验证码图片供人工识别
     */
    private File saveDebugImage(byte[] imageBytes) {
        try {
            Path debugDir = Paths.get(".tmp", "captcha");
            Files.createDirectories(debugDir);

            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);

            String timestamp = String.valueOf(System.currentTimeMillis());
            String filename = "math_captcha_" + timestamp + "_" + UUID.randomUUID().toString().substring(0, 8) + ".png";
            File outputFile = debugDir.resolve(filename).toFile();
            ImageIO.write(image, "PNG", outputFile);
            log.info("验证码图片已保存: {}", outputFile.getAbsolutePath());
            return outputFile;
        } catch (Exception e) {
            log.error("保存调试图片失败: {}", e.getMessage());
            throw new RuntimeException("保存图片失败", e);
        }
    }

    /**
     * OCR识别图片中的数学表达式
     * 使用图像分析算法检测数字和运算符区域
     */
    private String ocrImage(byte[] imageBytes) {
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(bais);

            int width = image.getWidth();
            int height = image.getHeight();
            log.info("验证码图片尺寸: {}x{}", width, height);

            StringBuilder expression = new StringBuilder();
            int currentX = 0;
            int segmentWidth = width / 6;

            while (currentX < width) {
                int actualWidth = Math.min(segmentWidth, width - currentX);
                BufferedImage segment = image.getSubimage(currentX, 0, actualWidth, height);

                Character detected = detectDigit(segment);
                if (detected != null) {
                    expression.append(detected);
                }

                currentX += actualWidth;
            }

            String result = expression.toString();
            log.info("图像分析识别结果: [{}]", result);

            result = cleanExpression(result);

            if (isValidMathExpression(result)) {
                return result;
            }

            return null;

        } catch (Exception e) {
            log.error("图像分析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 检测单个数字/运算符区域
     */
    private Character detectDigit(BufferedImage segment) {
        int width = segment.getWidth();
        int height = segment.getHeight();

        int nonWhitePixels = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = segment.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;

                if (!(r > 200 && g > 200 && b > 200)) {
                    nonWhitePixels++;
                }
            }
        }

        double density = (double) nonWhitePixels / (width * height);

        if (density < 0.05) {
            return ' ';
        }

        int avgGray = calculateAverageGray(segment);
        if (avgGray < 100) {
            return detectDigitByPattern(segment);
        }

        return null;
    }

    /**
     * 计算平均灰度
     */
    private int calculateAverageGray(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        long totalGray = 0;
        int count = 0;

        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = image.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                int gray = (r + g + b) / 3;
                totalGray += gray;
                count++;
            }
        }

        return count > 0 ? (int) (totalGray / count) : 0;
    }

    /**
     * 通过模式匹配检测数字
     */
    private Character detectDigitByPattern(BufferedImage segment) {
        int width = segment.getWidth();
        int height = segment.getHeight();

        int[][] binary = new int[width][height];
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int pixel = segment.getRGB(x, y);
                int r = (pixel >> 16) & 0xFF;
                int g = (pixel >> 8) & 0xFF;
                int b = pixel & 0xFF;
                binary[x][y] = (r + g + b) / 3 < 128 ? 1 : 0;
            }
        }

        int leftMargin = countLeftMargin(binary);
        int rightMargin = countRightMargin(binary);
        int centerMass = calculateCenterMass(binary);

        double ratio = (double) leftMargin / width;
        double centerRatio = (double) centerMass / width;

        if (ratio < 0.15 && centerRatio > 0.3 && centerRatio < 0.7) {
            if (isHorizontalLine(binary, height / 2)) {
                return '7';
            }
            return '1';
        }

        if (ratio > 0.2 && ratio < 0.4) {
            if (hasVerticalLine(binary, width / 3)) {
                return '4';
            }
        }

        if (hasTwoHorizontalLines(binary, height)) {
            if (hasVerticalLine(binary, width / 2)) {
                return '8';
            }
        }

        if (isPlusOrMinus(binary, width, height)) {
            return '+';
        }

        if (isMultiply(binary, width, height)) {
            return '*';
        }

        if (isDivide(binary, width, height)) {
            return '/';
        }

        return null;
    }

    private int countLeftMargin(int[][] binary) {
        int width = binary.length;
        int height = binary[0].length;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (binary[x][y] == 1) return x;
            }
        }
        return width - 1;
    }

    private int countRightMargin(int[][] binary) {
        int width = binary.length;
        int height = binary[0].length;
        for (int x = width - 1; x >= 0; x--) {
            for (int y = 0; y < height; y++) {
                if (binary[x][y] == 1) return width - 1 - x;
            }
        }
        return width - 1;
    }

    private int calculateCenterMass(int[][] binary) {
        int width = binary.length;
        int height = binary[0].length;
        long totalX = 0;
        int count = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                if (binary[x][y] == 1) {
                    totalX += x;
                    count++;
                }
            }
        }
        return count > 0 ? (int) (totalX / count) : width / 2;
    }

    private boolean isHorizontalLine(int[][] binary, int y) {
        int width = binary.length;
        if (y < 0 || y >= binary[0].length) return false;
        int count = 0;
        for (int x = 0; x < width; x++) {
            if (binary[x][y] == 1) count++;
        }
        return count > width * 0.6;
    }

    private boolean hasVerticalLine(int[][] binary, int x) {
        int height = binary[0].length;
        if (x < 0 || x >= binary.length) return false;
        int count = 0;
        for (int y = 0; y < height; y++) {
            if (binary[x][y] == 1) count++;
        }
        return count > height * 0.5;
    }

    private boolean hasTwoHorizontalLines(int[][] binary, int height) {
        int quarter = height / 4;
        int threeQuarters = height * 3 / 4;
        return isHorizontalLine(binary, quarter) && isHorizontalLine(binary, threeQuarters);
    }

    private boolean isPlusOrMinus(int[][] binary, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        int armLength = Math.min(width, height) / 3;

        int horizontalCount = 0;
        int verticalCount = 0;

        for (int i = -armLength; i <= armLength; i++) {
            if (centerX + i >= 0 && centerX + i < width) {
                if (binary[centerX + i][centerY] == 1) horizontalCount++;
            }
            if (centerY + i >= 0 && centerY + i < height) {
                if (binary[centerX][centerY + i] == 1) verticalCount++;
            }
        }

        if (verticalCount > armLength * 0.6 && horizontalCount > armLength * 0.6) {
            return true;
        }
        if (horizontalCount > armLength * 0.8) {
            return true;
        }
        return false;
    }

    private boolean isMultiply(int[][] binary, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        int armLength = Math.min(width, height) / 3;

        int diagonal1 = 0;
        int diagonal2 = 0;

        for (int i = -armLength; i <= armLength; i++) {
            int x1 = centerX + i;
            int y1 = centerY + i;
            int x2 = centerX + i;
            int y2 = centerY - i;

            if (x1 >= 0 && x1 < width && y1 >= 0 && y1 < height) {
                if (binary[x1][y1] == 1) diagonal1++;
            }
            if (x2 >= 0 && x2 < width && y2 >= 0 && y2 < height) {
                if (binary[x2][y2] == 1) diagonal2++;
            }
        }

        return diagonal1 > armLength * 0.5 && diagonal2 > armLength * 0.5;
    }

    private boolean isDivide(int[][] binary, int width, int height) {
        int centerX = width / 2;
        int topY = height / 3;
        int bottomY = height * 2 / 3;

        boolean hasTopDot = binary[centerX][topY] == 1 || (topY > 0 && binary[centerX][topY - 1] == 1);
        boolean hasBottomDot = binary[centerX][bottomY] == 1 || (bottomY < height - 1 && binary[centerX][bottomY + 1] == 1);

        return hasTopDot || hasBottomDot;
    }

    /**
     * 清理OCR识别结果中的干扰字符
     */
    private String cleanExpression(String raw) {
        String cleaned = raw.replaceAll("[^0-9+\\-×÷*/=()。．]", "");
        cleaned = cleaned.replace('。', '.').replace('．', '.');
        cleaned = cleaned.replace('×', '*').replace('÷', '/');
        cleaned = cleaned.replace('x', '*').replace('X', '*');
        return cleaned;
    }

    /**
     * 判断是否为有效数学表达式
     */
    private boolean isValidMathExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        return expr.matches(".*\\d+.*[+\\-*/].*");
    }

    /**
     * 计算数学表达式
     */
    public String calculateExpression(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        try {
            String expr = expression.replace("×", "*").replace("÷", "/").replace("X", "*").replace("x", "*");

            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(-?\\d+)\\s*([+\\-*/])\\s*(-?\\d+)");
            java.util.regex.Matcher matcher = pattern.matcher(expr);

            if (matcher.find()) {
                int a = Integer.parseInt(matcher.group(1));
                char op = matcher.group(2).charAt(0);
                int b = Integer.parseInt(matcher.group(3));

                int result = switch (op) {
                    case '+' -> a + b;
                    case '-' -> a - b;
                    case '*' -> a * b;
                    case '/' -> a / b;
                    default -> throw new IllegalArgumentException("未知运算符: " + op);
                };

                return String.valueOf(result);
            }

            if (expr.matches("-?\\d+")) {
                return expr;
            }

            return null;

        } catch (Exception e) {
            log.error("计算表达式失败: {} - {}", expression, e.getMessage());
            return null;
        }
    }
}
