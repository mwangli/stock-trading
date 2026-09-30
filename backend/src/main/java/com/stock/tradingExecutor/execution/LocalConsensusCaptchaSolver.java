// AI_GENERATE_START ------
package com.stock.tradingExecutor.execution;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.benjaminwan.ocrlibrary.OcrResult;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Java 进程内的多模型算术验证码识别器。
 * 使用 RapidOCR、Tesseract 和百度 OCR 三个独立引擎交叉投票，并针对固定算术版式修复干扰线造成的字符粘连。
 *
 * @author mwangli
 * @since 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalConsensusCaptchaSolver implements ArithmeticCaptchaSolver {

    private static final Pattern EXPRESSION_PATTERN = Pattern.compile("(\\d{1,2})([+\\-*/])(\\d{1,2})");
    private static final String BAIDU_TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String BAIDU_OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";
    private static final int IMAGE_SCALE = 8;

    private final ZXBrokerConfig brokerConfig;
    private final TesseractCaptchaRecognizer tesseractRecognizer;
    private final Object engineLock = new Object();
    private final Object baiduTokenLock = new Object();
    private volatile InferenceEngine rapidEngine;
    private volatile String baiduAccessToken;
    private volatile long baiduAccessTokenExpireAt;

    /**
     * 在 Java 进程内识别并计算算术验证码。
     * 只有至少两条识别路径得到同一完整表达式时才返回结果。
     *
     * @param imageBytes 验证码图片原始字节
     * @return 交叉验证通过后的算术结果
     * @throws IllegalStateException 图片无效、OCR 不可用或识别结果未达一致票数时抛出
     */
    @Override
    public int solve(byte[] imageBytes) {
        BufferedImage original = readImage(imageBytes);
        BufferedImage enlarged = scale(original, IMAGE_SCALE);
        BufferedImage expressionArea = cropExpressionArea(original);
        BufferedImage enlargedExpression = scale(expressionArea, IMAGE_SCALE);
        BufferedImage thresholdExpression = threshold(enlargedExpression);

        List<String> rapidRecognitions = new ArrayList<>();
        addRecognition(rapidRecognitions, recognizeRapid(enlargedExpression));
        addRecognition(rapidRecognitions, recognizeRapid(original));
        addRecognition(rapidRecognitions, recognizeRapid(enlarged));
        addRecognition(rapidRecognitions, recognizeRapid(thresholdExpression));
        List<String> tesseractRecognitions = new ArrayList<>();
        addRecognition(tesseractRecognitions, normalize(tesseractRecognizer.recognize(enlargedExpression)));
        addRecognition(tesseractRecognitions, normalize(tesseractRecognizer.recognize(thresholdExpression)));
        addRecognition(tesseractRecognitions, normalize(tesseractRecognizer.recognize(enlarged)));
        VoteResult tesseractVote = selectBestVote(tesseractRecognitions);
        String tesseractCandidate = tesseractVote.agreement() >= 2 ? tesseractVote.expression() : null;

        String baiduCandidate = recognizeWithBaidu(original);
        VoteResult result = selectEngineConsensus(rapidRecognitions, tesseractCandidate, baiduCandidate);
        int minAgreement = Math.max(2, Math.min(brokerConfig.getOcrMinAgreement(), 3));
        if (result.expression() == null || result.agreement() < minAgreement) {
            log.warn("[ZXBroker] OCR 独立引擎未达成一致，RapidOCR={}, Tesseract={}, Baidu={}",
                    rapidRecognitions, tesseractCandidate, baiduCandidate);
            throw new IllegalStateException("验证码 OCR 独立引擎未达到一致票数");
        }

        int calculated = evaluate(result.expression());
        log.info("[ZXBroker] Java OCR 独立引擎交叉验证通过，agreement={}", result.agreement());
        return calculated;
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

    private InferenceEngine getRapidEngine() {
        if (rapidEngine == null) {
            synchronized (engineLock) {
                if (rapidEngine == null) {
                    rapidEngine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
                }
            }
        }
        return rapidEngine;
    }

    private String recognizeRapid(BufferedImage image) {
        Path tempImage = null;
        try {
            InferenceEngine engine = getRapidEngine();
            tempImage = Files.createTempFile("zx-captcha-", ".png");
            if (!ImageIO.write(image, "png", tempImage.toFile())) {
                throw new IllegalStateException("验证码临时图片写入失败");
            }
            OcrResult ocrResult;
            synchronized (engineLock) {
                ocrResult = engine.runOcr(tempImage.toString());
            }
            return normalize(ocrResult == null ? null : ocrResult.getStrRes());
        } catch (Exception exception) {
            log.warn("[ZXBroker] RapidOCR 识别路径失败: {}", exception.getMessage());
            return null;
        } finally {
            deleteQuietly(tempImage);
        }
    }

    private String recognizeWithBaidu(BufferedImage image) {
        String accessToken = getBaiduAccessToken();
        if (accessToken == null) {
            return null;
        }
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            Map<String, Object> form = new HashMap<>();
            form.put("image", Base64.getEncoder().encodeToString(output.toByteArray()));
            form.put("detect_direction", "false");
            form.put("probability", "true");
            String body = HttpUtil.createPost(BAIDU_OCR_URL + "?access_token=" + accessToken)
                    .form(form)
                    .timeout(brokerConfig.getOcrTimeoutMs())
                    .execute()
                    .body();
            JSONObject response = JSONObject.parseObject(body);
            JSONArray words = response.getJSONArray("words_result");
            if (words == null || words.isEmpty()) {
                return null;
            }
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < words.size(); index++) {
                text.append(words.getJSONObject(index).getString("words"));
            }
            return normalize(text.toString());
        } catch (Exception exception) {
            log.warn("[ZXBroker] 百度 OCR 补票失败: {}", exception.getMessage());
            return null;
        }
    }

    private String getBaiduAccessToken() {
        if (brokerConfig.getBaiduOcrApiKey().isBlank() || brokerConfig.getBaiduOcrSecretKey().isBlank()) {
            return null;
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
                        .timeout(brokerConfig.getOcrTimeoutMs())
                        .execute()
                        .body();
                JSONObject response = JSONObject.parseObject(body);
                String token = response.getString("access_token");
                if (token == null || token.isBlank()) {
                    return null;
                }
                int expiresIn = Math.max(120, response.getIntValue("expires_in", 2592000));
                baiduAccessToken = token;
                baiduAccessTokenExpireAt = now + expiresIn - 60L;
                return token;
            } catch (Exception exception) {
                log.warn("[ZXBroker] 百度 OCR AccessToken 获取失败: {}", exception.getMessage());
                return null;
            }
        }
    }

    private BufferedImage cropExpressionArea(BufferedImage source) {
        int width = Math.max(1, Math.min(source.getWidth(), (int) Math.round(source.getWidth() * 0.83)));
        return source.getSubimage(0, 0, width, source.getHeight());
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

    private BufferedImage threshold(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                Color color = new Color(source.getRGB(x, y));
                int gray = (color.getRed() * 30 + color.getGreen() * 59 + color.getBlue() * 11) / 100;
                result.setRGB(x, y, gray < 180 ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return result;
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
        String structuredExpression = rebuildFixedLayoutExpression(value);
        if (structuredExpression != null) {
            return structuredExpression;
        }
        Matcher matcher = EXPRESSION_PATTERN.matcher(value);
        return matcher.find() ? matcher.group(1) + matcher.group(2) + matcher.group(3) : null;
    }

    private String rebuildFixedLayoutExpression(String value) {
        List<Integer> digitPositions = new ArrayList<>();
        StringBuilder digits = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                digitPositions.add(index);
                digits.append(value.charAt(index));
            }
        }
        if (digits.length() < 2 || digits.length() > 4) {
            return null;
        }
        int splitIndex = digits.length() >= 3 ? 2 : 1;
        if (splitIndex >= digitPositions.size()) {
            return null;
        }
        int gapStart = digitPositions.get(splitIndex - 1) + 1;
        int gapEnd = digitPositions.get(splitIndex);
        Character operator = findLastOperator(value.substring(gapStart, gapEnd));
        if (operator == null) {
            return null;
        }
        return digits.substring(0, splitIndex) + operator + digits.substring(splitIndex);
    }

    private Character findLastOperator(String value) {
        Character selected = null;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '+' || character == '-' || character == '*' || character == '/') {
                selected = character;
            }
        }
        return selected;
    }

    private void addRecognition(List<String> recognitions, String expression) {
        if (expression != null) {
            recognitions.add(expression);
        }
    }

    private VoteResult selectBestVote(List<String> recognitions) {
        Map<String, Integer> votes = new LinkedHashMap<>();
        for (String expression : recognitions) {
            votes.merge(expression, 1, Integer::sum);
        }
        String selectedExpression = null;
        int selectedAgreement = 0;
        for (Map.Entry<String, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > selectedAgreement) {
                selectedExpression = entry.getKey();
                selectedAgreement = entry.getValue();
            }
        }
        return new VoteResult(selectedExpression, selectedAgreement);
    }

    private VoteResult selectEngineConsensus(List<String> rapidRecognitions,
                                             String tesseractCandidate,
                                             String baiduCandidate) {
        Map<String, Integer> engineVotes = new LinkedHashMap<>();
        for (String rapidRecognition : rapidRecognitions.stream().distinct().toList()) {
            engineVotes.put(rapidRecognition, 1);
        }
        if (tesseractCandidate != null) {
            engineVotes.merge(tesseractCandidate, 1, Integer::sum);
        }
        if (baiduCandidate != null) {
            engineVotes.merge(baiduCandidate, 1, Integer::sum);
        }

        String selectedExpression = null;
        int selectedAgreement = 0;
        boolean ambiguous = false;
        for (Map.Entry<String, Integer> entry : engineVotes.entrySet()) {
            if (entry.getValue() > selectedAgreement) {
                selectedExpression = entry.getKey();
                selectedAgreement = entry.getValue();
                ambiguous = false;
            } else if (entry.getValue() == selectedAgreement) {
                ambiguous = true;
            }
        }
        return new VoteResult(ambiguous ? null : selectedExpression, selectedAgreement);
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

    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.debug("[ZXBroker] 验证码临时文件删除失败: {}", path);
        }
    }

    private record VoteResult(String expression, int agreement) {
    }
}
// AI_GENERATE_END ------