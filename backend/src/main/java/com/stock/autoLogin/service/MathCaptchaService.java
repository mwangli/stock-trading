package com.stock.autoLogin.service;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
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
     * 获取验证码图片并识别计算结果
     *
     * @param account 资金账号/手机号
     * @return Map 包含：success, expression, result, method, error
     */
    public Map<String, Object> getCaptchaResult(String account) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 获取验证码原始 JPEG base64
            String base64Raw = fetchCaptchaBase64(account);
            if (base64Raw == null) {
                result.put("success", false);
                result.put("error", "获取验证码图片失败");
                return result;
            }

            // 2. 加载图片并保存原图调试文件
            byte[] rawBytes = Base64.getDecoder().decode(base64Raw);
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(rawBytes));
            saveDebugImage(img, "raw");

            // 3. 整图放大 SCALE 倍（NEAREST 插值保留锐边）
            BufferedImage scaled = scaleImage(img, SCALE);
            saveDebugImage(scaled, "scaled");

            // 4. 整图送百度 general_basic OCR，直接识别完整表达式
            byte[] scaledBytes = toBytes(scaled);
            String scaledB64 = Base64.getEncoder().encodeToString(scaledBytes);

            int leftNum  = -1;
            int rightNum = -1;
            char op      = '?';

            // 4a. 尝试 general_basic：直接解析 "数字 op 数字" 表达式
            String ocrText = ocrGeneralText(scaledB64);
            log.info("general_basic OCR文本: [{}]", ocrText);
            java.util.regex.Matcher exprMatcher = java.util.regex.Pattern
                    .compile("(\\d+)\\s*([+\\-])\\s*(\\d+)")
                    .matcher(ocrText);
            if (exprMatcher.find()) {
                leftNum  = Integer.parseInt(exprMatcher.group(1));
                rightNum = Integer.parseInt(exprMatcher.group(3));
                op       = exprMatcher.group(2).charAt(0);
                log.info("general_basic 解析成功: {} {} {}", leftNum, op, rightNum);
            }

            // 4b. 若 general_basic 失败，退回 numbers OCR + 运算符检测
            if (leftNum < 0) {
                String allDigits = ocrAllDigits(scaledB64);
                log.info("numbers OCR数字串: [{}]", allDigits);
                boolean[][] dark2 = binarize(img);
                op = detectOperator(dark2, img.getWidth(), img.getHeight());

                // 根据数字串长度决定拆分方式
                String digits = allDigits.replaceAll("[^0-9]", "");
                if (digits.length() == 4) {
                    leftNum  = Integer.parseInt(digits.substring(0, 2));
                    rightNum = Integer.parseInt(digits.substring(2, 4));
                } else if (digits.length() == 3) {
                    // 判断运算符列位置：若在前 30% 则左侧为1位数，否则为2位数
                    int opCol = findOperatorColumn(dark2, img.getWidth(), img.getHeight());
                    double opRatio = (double) opCol / img.getWidth();
                    log.info("运算符列位置: {} ({:.1%})", opCol, opRatio);
                    if (opRatio < 0.30) {
                        leftNum  = Integer.parseInt(digits.substring(0, 1));
                        rightNum = Integer.parseInt(digits.substring(1, 3));
                    } else {
                        leftNum  = Integer.parseInt(digits.substring(0, 2));
                        rightNum = Integer.parseInt(digits.substring(2, 3));
                    }
                }
            }

            if (leftNum < 0 || rightNum < 0 || op == '?') {
                result.put("success", false);
                result.put("error", "OCR识别失败，无法解析表达式");
                result.put("method", "manual");
                return result;
            }

            // 5. 计算结果
            int answer = op == '+' ? leftNum + rightNum : leftNum - rightNum;
            String expression = leftNum + " " + op + " " + rightNum;

            result.put("success", true);
            result.put("expression", expression);
            result.put("result", String.valueOf(answer));
            result.put("method", "full-image-ocr");
            log.info("验证码识别成功: {} = {}", expression, answer);
            return result;

        } catch (Exception e) {
            log.error("获取验证码失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
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
            String body = "image=" + URLEncoder.encode(base64Image, StandardCharsets.UTF_8);

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
            String body = "image=" + URLEncoder.encode(base64Image, StandardCharsets.UTF_8);

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
