// AI_GENERATE_START --
package com.stock.tradingExecutor.execution;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 百度云算术验证码实验识别器。
 * 分别识别原图和六倍放大图。50 张唯一验证码验证未达到 85% 目标，
 * 自动提交默认关闭，仅允许在显式接受识别风险后启用。
 *
 * @author mwangli
 * @since 2026-10-08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BaiduCaptchaSolver implements ArithmeticCaptchaSolver {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(\\d{1,2})([+\\-*/])(\\d{1,2})");
    private static final String BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String BAIDU_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";
    private static final int IMAGE_SCALE = 6;

    private final ZXBrokerConfig brokerConfig;
    private final Object baiduTokenLock = new Object();
    private final Object baiduRequestLock = new Object();
    private volatile String baiduAccessToken;
    private volatile long baiduAccessTokenExpireAt;
    private volatile long lastBaiduRequestAtMillis;

    /**
     * 使用百度云 OCR 双路径识别并计算算术验证码。
     * 未显式启用实验性自动提交开关时直接拒绝识别，避免低准确率结果进入登录请求。
     *
     * @param imageBytes 验证码图片原始字节
     * @return 两条识别路径一致后的算术结果
     * @throws IllegalStateException 自动提交未启用、图片无效、百度 OCR 不可用或两条路径结果不一致时抛出
     */
    @Override
    public int solve(byte[] imageBytes) {
        if (!Boolean.TRUE.equals(brokerConfig.getCaptchaAutoSubmitEnabled())) {
            log.warn("[ZXBroker] 百度 OCR 未通过 85% 准确率验收，自动提交开关保持关闭");
            throw new IllegalStateException("验证码 OCR 自动提交未通过准确率验收");
        }
        BufferedImage original = readImage(imageBytes);
        byte[] enlargedImageBytes = encodePng(scale(original, IMAGE_SCALE));

        String originalExpression = recognizeWithBaidu(imageBytes);
        String enlargedExpression = recognizeWithBaidu(enlargedImageBytes);
        if (originalExpression == null || !originalExpression.equals(enlargedExpression)) {
            log.warn("[ZXBroker] 百度 OCR 原图与放大图结果不一致，丢弃当前验证码");
            throw new IllegalStateException("验证码 OCR 双路径结果不一致");
        }

        log.warn("[ZXBroker] 实验性 OCR 自动提交已启用，双路径一致后提交验证码");
        return evaluate(originalExpression);
    }

    private BufferedImage readImage(byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            throw new IllegalStateException("验证码图片为空");
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (image == null) {
                throw new IllegalStateException("验证码图片格式无法识别");
            }
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("验证码图片读取失败", exception);
        }
    }

    private BufferedImage scale(BufferedImage source, int factor) {
        BufferedImage scaled = new BufferedImage(source.getWidth() * factor,
                source.getHeight() * factor, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, scaled.getWidth(), scaled.getHeight());
            graphics.drawImage(source, 0, 0, scaled.getWidth(), scaled.getHeight(), null);
            return scaled;
        } finally {
            graphics.dispose();
        }
    }

    private byte[] encodePng(BufferedImage image) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw new IllegalStateException("验证码放大图编码失败");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("验证码放大图编码失败", exception);
        }
    }

    private String recognizeWithBaidu(byte[] imageBytes) {
        String accessToken = getBaiduAccessToken();
        throttleBaiduRequest();
        try {
            Map<String, Object> form = new HashMap<>();
            form.put("image", Base64.getEncoder().encodeToString(imageBytes));
            form.put("detect_direction", "false");
            form.put("probability", "true");
            String body = HttpUtil.createPost(BAIDU_OCR_URL + "?access_token=" + accessToken)
                    .form(form)
                    .timeout(resolveTimeoutMillis())
                    .execute()
                    .body();
            JSONObject response = JSONObject.parseObject(body);
            Integer errorCode = response.getInteger("error_code");
            if (errorCode != null) {
                log.warn("[ZXBroker] 百度 OCR 请求失败，errorCode={}, errorMessage={}",
                        errorCode, response.getString("error_msg"));
                return null;
            }
            JSONArray words = response.getJSONArray("words_result");
            if (words == null || words.isEmpty()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < words.size(); index++) {
                text.append(words.getJSONObject(index).getString("words"));
            }
            return normalize(text.toString());
        } catch (RuntimeException exception) {
            log.warn("[ZXBroker] 百度 OCR 识别失败: {}", exception.getMessage());
            return null;
        }
    }

    private String getBaiduAccessToken() {
        if (isBlank(brokerConfig.getBaiduOcrApiKey()) || isBlank(brokerConfig.getBaiduOcrSecretKey())) {
            throw new IllegalStateException("未配置百度 OCR 凭据");
        }
        long now = Instant.now().getEpochSecond();
        if (baiduAccessToken != null && now < baiduAccessTokenExpireAt) {
            return baiduAccessToken;
        }
        synchronized (baiduTokenLock) {
            now = Instant.now().getEpochSecond();
            if (baiduAccessToken != null && now < baiduAccessTokenExpireAt) {
                return baiduAccessToken;
            }
            try {
                Map<String, Object> form = new HashMap<>();
                form.put("grant_type", "client_credentials");
                form.put("client_id", brokerConfig.getBaiduOcrApiKey());
                form.put("client_secret", brokerConfig.getBaiduOcrSecretKey());
                String body = HttpUtil.createPost(BAIDU_TOKEN_URL)
                        .form(form)
                        .timeout(resolveTimeoutMillis())
                        .execute()
                        .body();
                JSONObject response = JSONObject.parseObject(body);
                String token = response.getString("access_token");
                if (isBlank(token)) {
                    throw new IllegalStateException("百度 OCR AccessToken 获取失败");
                }
                int expiresIn = Math.max(120, response.getIntValue("expires_in", 2592000));
                baiduAccessToken = token;
                baiduAccessTokenExpireAt = now + expiresIn - 60L;
                return token;
            } catch (RuntimeException exception) {
                throw new IllegalStateException("百度 OCR AccessToken 获取失败", exception);
            }
        }
    }

    private void throttleBaiduRequest() {
        long intervalMillis = resolveRequestIntervalMillis();
        synchronized (baiduRequestLock) {
            long now = System.currentTimeMillis();
            long waitMillis = lastBaiduRequestAtMillis + intervalMillis - now;
            if (waitMillis > 0) {
                try {
                    Thread.sleep(waitMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("百度 OCR 请求等待被中断", exception);
                }
            }
            lastBaiduRequestAtMillis = System.currentTimeMillis();
        }
    }

    private int resolveTimeoutMillis() {
        Integer configured = brokerConfig.getOcrTimeoutMs();
        return configured == null ? 20000 : Math.max(1000, configured);
    }

    private long resolveRequestIntervalMillis() {
        Integer configured = brokerConfig.getOcrRequestIntervalMs();
        return configured == null ? 1050L : Math.max(0L, configured.longValue());
    }

    private String normalize(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", "")
                .replace('＋', '+')
                .replace('－', '-')
                .replace('—', '-')
                .replace('×', '*')
                .replace('x', '*')
                .replace('X', '*')
                .replace('÷', '/');
        Matcher matcher = EXPRESSION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) + matcher.group(2) + matcher.group(3) : null;
    }

    private int evaluate(String expression) {
        Matcher matcher = EXPRESSION_PATTERN.matcher(expression == null ? "" : expression);
        if (!matcher.matches()) {
            throw new IllegalStateException("OCR 返回的表达式格式非法");
        }
        int left = Integer.parseInt(matcher.group(1));
        int right = Integer.parseInt(matcher.group(3));
        return switch (matcher.group(2)) {
            case "+" -> left + right;
            case "-" -> left - right;
            case "*" -> left * right;
            case "/" -> divideExactly(left, right);
            default -> throw new IllegalStateException("OCR 返回了不支持的运算符");
        };
    }

    private int divideExactly(int left, int right) {
        if (right == 0 || left % right != 0) {
            throw new IllegalStateException("OCR 返回了非法除法表达式");
        }
        return left / right;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
// AI_GENERATE_END --