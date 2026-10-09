// AI_GENERATE_START --
package com.stock.modelService.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import com.stock.modelService.config.OnnxInferenceConfig;
import com.stock.modelService.config.SentimentModelConfig;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 使用 Python 产出的 ONNX 制品执行情感在线推理。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentInferenceService {

    private final OnnxInferenceConfig onnxConfig;
    private final SentimentModelConfig modelConfig;
    private final OnnxSessionRegistry sessionRegistry;
    private final SentimentDataPreprocessor dataPreprocessor;
    private HuggingFaceTokenizer tokenizer;
    private LocalDateTime lastLoadedTime;

    @PostConstruct
    public void init() {
        log.info("情感 ONNX 制品目录: {}",
                Path.of(onnxConfig.getSentimentArtifactDir()).toAbsolutePath().normalize());
    }

    public boolean loadModel() {
        try {
            Path artifactDir = Path.of(onnxConfig.getSentimentArtifactDir());
            sessionRegistry.get(artifactDir);
            tokenizer(artifactDir.resolve("tokenizer"));
            lastLoadedTime = LocalDateTime.now();
            return true;
        } catch (Exception exception) {
            log.error("加载情感 ONNX 制品失败", exception);
            return false;
        }
    }

    public SentimentAnalysisResult analyzeSentiment(String text) {
        try {
            return analyzeSentimentRequired(text);
        } catch (RuntimeException exception) {
            log.warn("情感 ONNX 推理不可用，非交易接口使用规则结果: {}", exception.getMessage());
            return analyzeWithRules(text);
        }
    }

    public SentimentAnalysisResult analyzeSentimentRequired(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("情感分析文本不能为空");
        }
        try {
            Path artifactDir = Path.of(onnxConfig.getSentimentArtifactDir());
            OnnxSessionRegistry.SessionHandle handle = sessionRegistry.get(artifactDir);
            int maxLength = compatibilityInt(handle, "maxSequenceLength");
            Encoding encoding = tokenizer(artifactDir.resolve("tokenizer")).encode(text);
            long[] inputIds = truncateOrPad(encoding.getIds(), maxLength);
            long[] attentionMask = truncateOrPad(encoding.getAttentionMask(), maxLength);
            try (OnnxTensor idsTensor = OnnxTensor.createTensor(
                    handle.environment(),
                    LongBuffer.wrap(inputIds), new long[]{1, maxLength});
                 OnnxTensor maskTensor = OnnxTensor.createTensor(
                         handle.environment(),
                         LongBuffer.wrap(attentionMask), new long[]{1, maxLength});
                 var output = handle.session().run(Map.of(
                         "input_ids", idsTensor, "attention_mask", maskTensor))) {
                float[] probabilities = softmax(((float[][]) output.get(0).getValue())[0]);
                int labelIndex = maxIndex(probabilities);
                String[] labels = modelConfig.getLabels();
                if (probabilities.length != labels.length) {
                    throw new IllegalStateException("情感 ONNX 标签数量不匹配");
                }
                Map<String, Double> probabilityMap = new HashMap<>();
                for (int index = 0; index < labels.length; index++) {
                    probabilityMap.put(labels[index], (double) probabilities[index]);
                }
                String label = labels[labelIndex];
                lastLoadedTime = LocalDateTime.now();
                return SentimentAnalysisResult.builder()
                        .label(label)
                        .score(calculateSentimentScore(probabilityMap))
                        .normalizedScore(calculateNormalizedScore(label, probabilities[labelIndex]))
                        .confidence(probabilities[labelIndex])
                        .probabilities(probabilityMap)
                        .text(text)
                        .build();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("情感 ONNX 模型推理失败", exception);
        }
    }

    public boolean isModelLoaded() {
        return lastLoadedTime != null;
    }

    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
    }

    public Map<String, Object> analyzeSentimentWithDetails(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("情感分析文本不能为空");
        }
        SentimentAnalysisResult result = analyzeSentiment(text);
        Map<String, Object> response = new HashMap<>();
        response.put("label", result.getLabel());
        response.put("score", result.getScore());
        response.put("normalizedScore", result.getNormalizedScore());
        response.put("confidence", result.getConfidence());
        response.put("probabilities", result.getProbabilities());
        response.put("text", result.getText());
        response.put("modelLoaded", isModelLoaded());
        return response;
    }

    private synchronized HuggingFaceTokenizer tokenizer(Path tokenizerDir) throws IOException {
        if (tokenizer == null) {
            tokenizer = HuggingFaceTokenizer.newInstance(tokenizerDir);
        }
        return tokenizer;
    }

    private int compatibilityInt(OnnxSessionRegistry.SessionHandle handle, String key) {
        Object value = handle.artifact().metadata().getCompatibility().get(key);
        if (!(value instanceof Number number) || number.intValue() <= 0) {
            throw new IllegalStateException("情感 ONNX 元数据缺少: " + key);
        }
        return number.intValue();
    }

    private long[] truncateOrPad(long[] source, int maxLength) {
        long[] result = new long[maxLength];
        System.arraycopy(source, 0, result, 0, Math.min(source.length, maxLength));
        return result;
    }

    private float[] softmax(float[] logits) {
        float maximum = Float.NEGATIVE_INFINITY;
        for (float value : logits) {
            maximum = Math.max(maximum, value);
        }
        double total = 0D;
        float[] probabilities = new float[logits.length];
        for (int index = 0; index < logits.length; index++) {
            probabilities[index] = (float) Math.exp(logits[index] - maximum);
            total += probabilities[index];
        }
        for (int index = 0; index < probabilities.length; index++) {
            probabilities[index] /= (float) total;
        }
        return probabilities;
    }

    private int maxIndex(float[] values) {
        int best = 0;
        for (int index = 1; index < values.length; index++) {
            if (values[index] > values[best]) {
                best = index;
            }
        }
        return best;
    }

    private SentimentAnalysisResult analyzeWithRules(String text) {
        int labelIndex = dataPreprocessor.autoLabelSentiment(text == null ? "" : text);
        String label = modelConfig.getLabels()[labelIndex];
        Map<String, Double> probabilities = new HashMap<>();
        probabilities.put("neutral", labelIndex == 0 ? 0.7D : 0.15D);
        probabilities.put("positive", labelIndex == 1 ? 0.7D : 0.15D);
        probabilities.put("negative", labelIndex == 2 ? 0.7D : 0.15D);
        return SentimentAnalysisResult.builder()
                .label(label)
                .score(calculateSentimentScore(probabilities))
                .normalizedScore(calculateNormalizedScore(label, 0.7D))
                .confidence(0.7D)
                .probabilities(probabilities)
                .text(text)
                .build();
    }

    private double calculateNormalizedScore(String label, double probability) {
        if ("negative".equalsIgnoreCase(label)) {
            return 40D - probability * 40D;
        }
        if ("neutral".equalsIgnoreCase(label)) {
            return 40D + probability * 20D;
        }
        if ("positive".equalsIgnoreCase(label)) {
            return 60D + probability * 40D;
        }
        return 50D;
    }

    private double calculateSentimentScore(Map<String, Double> probabilities) {
        return Math.max(-1D, Math.min(1D,
                probabilities.getOrDefault("positive", 0D)
                        - probabilities.getOrDefault("negative", 0D)));
    }

    @PreDestroy
    public void close() {
        if (tokenizer != null) {
            tokenizer.close();
            tokenizer = null;
        }
        lastLoadedTime = null;
    }
}
// AI_GENERATE_END --
