// AI_GENERATE_START -
package com.stock.modelService.service;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OnnxTensor;
import com.stock.modelService.config.OnnxInferenceConfig;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 对严格情感推理结果执行 ONNX 影子比较，不改变 DJL 主结果。
 *
 * @author mwangli
 * @since 2026-10-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentOnnxShadowService {

    private static final List<String> LABELS = List.of("neutral", "positive", "negative");

    private final OnnxInferenceConfig config;
    private final OnnxSessionRegistry sessionRegistry;
    private HuggingFaceTokenizer tokenizer;

    /**
     * 使用同一文本执行 ONNX 推理并比较标签和概率。
     *
     * @param text 原始文本
     * @param djlResult DJL 主结果
     */
    public void compare(String text, SentimentAnalysisResult djlResult) {
        if (!config.isEnabled() || !config.isShadowEnabled()) {
            return;
        }
        try {
            Path artifactDir = Path.of(config.getSentimentArtifactDir());
            OnnxSessionRegistry.SessionHandle handle = sessionRegistry.get(artifactDir);
            int maxLength = compatibilityInt(handle, "maxSequenceLength");
            HuggingFaceTokenizer activeTokenizer = tokenizer(artifactDir.resolve("tokenizer"));
            Encoding encoding = activeTokenizer.encode(text);
            long[] inputIds = truncateOrPad(encoding.getIds(), maxLength);
            long[] attentionMask = truncateOrPad(encoding.getAttentionMask(), maxLength);
            try (OnnxTensor idsTensor = OnnxTensor.createTensor(
                    ai.onnxruntime.OrtEnvironment.getEnvironment(),
                    LongBuffer.wrap(inputIds), new long[]{1, maxLength});
                 OnnxTensor maskTensor = OnnxTensor.createTensor(
                         ai.onnxruntime.OrtEnvironment.getEnvironment(),
                         LongBuffer.wrap(attentionMask), new long[]{1, maxLength});
                 var result = handle.session().run(Map.of(
                         "input_ids", idsTensor, "attention_mask", maskTensor))) {
                float[] probabilities = softmax(((float[][]) result.get(0).getValue())[0]);
                int labelIndex = maxIndex(probabilities);
                String onnxLabel = LABELS.get(labelIndex);
                double djlProbability = djlResult.getProbabilities()
                        .getOrDefault(onnxLabel, 0D);
                double probabilityDifference = Math.abs(probabilities[labelIndex] - djlProbability);
                if (!onnxLabel.equalsIgnoreCase(djlResult.getLabel())
                        || probabilityDifference > config.getOutputTolerance()) {
                    log.warn("情感 ONNX 影子输出存在差异: djlLabel={}, onnxLabel={}, probabilityDifference={}",
                            djlResult.getLabel(), onnxLabel, probabilityDifference);
                } else {
                    log.info("情感 ONNX 影子输出通过: label={}, probabilityDifference={}",
                            onnxLabel, probabilityDifference);
                }
            }
        } catch (Exception exception) {
            log.error("情感 ONNX 影子推理失败，继续使用 DJL 主结果", exception);
        }
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

    /** 释放 Tokenizer 原生资源。 */
    @PreDestroy
    public void close() {
        if (tokenizer != null) {
            tokenizer.close();
            tokenizer = null;
        }
    }
}
// AI_GENERATE_END -
