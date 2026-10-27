// AI_GENERATE_START- --------
package com.stock.model.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.stock.model.config.SentimentShadowConfig;
import com.stock.model.dto.SentimentModelInput;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mengzi 本地模型加载和推理运行时。
 *
 * @see <a href="http://localhost:5173/docs/topics/modelLifecycle/mengzi-shadow-deployment">
 *     Mengzi 影子部署详细设计</a>
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MengziModelRuntimeService implements SentimentModelRuntime {

    private final SentimentShadowConfig config;
    private final SentimentPredictionNormalizer predictionNormalizer;
    private volatile ZooModel<String, Classifications> loadedModel;

    /**
     * 从固定目录加载 Mengzi。
     *
     * @throws IllegalStateException 制品不完整或模型加载失败时抛出
     */
    public synchronized void loadModel() {
        if (loadedModel != null) {
            return;
        }
        try {
            Path directory = resolveModelDirectory();
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(directory)
                    .optTruncation(true)
                    .optMaxLength(config.getMaxSequenceLength())
                    .build();
            Translator<String, Classifications> translator = TextClassificationTranslator.builder(tokenizer)
                    .optIncludeTokenTypes(true)
                    .build();
            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls(directory.toUri().toString())
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();
            loadedModel = criteria.loadModel();
            log.info("Mengzi 情感模型加载成功: version={}, directory={}",
                    config.getModelVersion(), directory);
        } catch (Exception exception) {
            unloadModel();
            throw new IllegalStateException("Mengzi 情感模型加载失败", exception);
        }
    }

    /**
     * 使用已加载的 Mengzi 执行情感推理。
     *
     * @param text 规范化事件文本
     * @return 情感分析结果
     */
    @Override
    public String modelName() {
        return config.getModelName();
    }

    /**
     * 使用已完成金标训练的 Mengzi 三分类制品执行统一分块推理。
     *
     * @param input 标题优先的统一模型输入
     * @return 完成概率平均和不确定性计算的三分类结果
     */
    @Override
    public SentimentAnalysisResult predict(SentimentModelInput input) {
        if (input == null || input.getChunks() == null || input.getChunks().isEmpty()) {
            throw new IllegalArgumentException("Mengzi 情感分析文本不能为空");
        }
        if (loadedModel == null) {
            loadModel();
        }
        try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
            List<Map<String, Double>> chunkProbabilities = new ArrayList<>();
            for (String chunk : input.getChunks()) {
                Classifications classifications = predictor.predict(chunk);
                Map<String, Double> probabilities = new HashMap<>();
                List<Classifications.Classification> items = classifications.items();
                for (Classifications.Classification item : items) {
                    probabilities.put(normalizeLabel(item.getClassName()), item.getProbability());
                }
                chunkProbabilities.add(probabilities);
            }
            return predictionNormalizer.normalize(
                    chunkProbabilities, input, config.getCalibrationTemperature());
        } catch (Exception exception) {
            throw new IllegalStateException("Mengzi 情感模型推理失败", exception);
        }
    }

    /**
     * 卸载 Mengzi 并释放底层资源。
     */
    public synchronized void unloadModel() {
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
        }
    }

    /**
     * 返回 Mengzi 是否已加载。
     *
     * @return 是否已加载
     */
    public boolean isLoaded() {
        return loadedModel != null;
    }

    private Path resolveModelDirectory() throws IOException {
        String version = config.getModelVersion();
        if (version == null || version.isBlank() || "unconfigured".equalsIgnoreCase(version)) {
            throw new IOException("Mengzi 模型版本未配置");
        }
        Path root = Path.of(config.getModelPath()).toAbsolutePath().normalize();
        Path directory = root.resolve(version).normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Mengzi 模型目录越界: " + directory);
        }
        return directory;
    }

    private String normalizeLabel(String rawLabel) {
        String[] labels = config.getLabels();
        if (labels == null || labels.length == 0) {
            throw new IllegalStateException("Mengzi 输出标签配置不能为空");
        }
        for (String label : labels) {
            if (label != null && label.equalsIgnoreCase(rawLabel)) {
                return label.toLowerCase(java.util.Locale.ROOT);
            }
        }
        if (rawLabel != null && rawLabel.toUpperCase(java.util.Locale.ROOT).startsWith("LABEL_")) {
            try {
                int labelIndex = Integer.parseInt(rawLabel.substring("LABEL_".length()));
                if (labelIndex >= 0 && labelIndex < labels.length) {
                    return labels[labelIndex].toLowerCase(java.util.Locale.ROOT);
                }
            } catch (NumberFormatException ignored) {
                // 非标准标签继续按输出位置映射，并在越界时抛出明确错误。
            }
        }
        throw new IllegalStateException("Mengzi 输出标签无法映射: " + rawLabel);
    }

}
// AI_GENERATE_END- --------
