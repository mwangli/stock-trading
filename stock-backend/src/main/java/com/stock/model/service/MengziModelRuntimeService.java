// AI_GENERATE_START -----
package com.stock.model.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.model.config.SentimentShadowConfig;
import com.stock.model.dto.SentimentModelInput;
import com.stock.model.vo.SentimentAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mengzi 本地模型校验、加载和推理运行时。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MengziModelRuntimeService implements SentimentModelRuntime {

    private final SentimentShadowConfig config;
    private final ObjectMapper objectMapper;
    private final SentimentPredictionNormalizer predictionNormalizer;
    private volatile ZooModel<String, Classifications> loadedModel;
    private volatile Path validatedEncoderDirectory;

    /**
     * 校验固定制品并加载 Mengzi。
     *
     * @throws IllegalStateException 制品不完整或模型加载失败时抛出
     */
    public synchronized void loadModel() {
        if (loadedModel != null) {
            return;
        }
        try {
            Path directory = validateArtifact();
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
     * 校验官方 Mengzi 金融编码器制品。
     * 该方法只证明编码器文件完整，不代表三分类头已训练或具备情感预测能力。
     *
     * @return 已通过校验的编码器目录
     */
    public Path validateEncoderArtifact() {
        Path current = validatedEncoderDirectory;
        if (current != null) {
            return current;
        }
        try {
            Path validated = validateManifestArtifact(
                    config.getEncoderPath(), config.getEncoderVersion(),
                    "encoder-artifact-v1", "BASE_ENCODER", false);
            validatedEncoderDirectory = validated;
            return validated;
        } catch (IOException exception) {
            throw new IllegalStateException("Mengzi 官方编码器制品校验失败", exception);
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

    private Path validateArtifact() throws IOException {
        String version = config.getModelVersion();
        if (version == null || version.isBlank() || "unconfigured".equalsIgnoreCase(version)) {
            throw new IOException("Mengzi 模型版本未配置");
        }
        return validateManifestArtifact(config.getModelPath(), version,
                "sentiment-artifact-v1", "FINE_TUNED_CLASSIFIER", true);
    }

    private Path validateManifestArtifact(
            String configuredRoot,
            String version,
            String schemaVersion,
            String artifactRole,
            boolean requireServingProperties) throws IOException {
        if (version == null || version.isBlank() || "unconfigured".equalsIgnoreCase(version)) {
            throw new IOException("Mengzi 制品版本未配置");
        }
        Path root = Path.of(configuredRoot).toAbsolutePath().normalize();
        Path directory = root.resolve(version).normalize();
        if (!directory.startsWith(root) || !Files.isDirectory(directory)) {
            throw new IOException("Mengzi 制品目录不存在或越界: " + directory);
        }
        Path manifestPath = directory.resolve("manifest.json");
        JsonNode manifest;
        try (var reader = Files.newBufferedReader(manifestPath)) {
            manifest = objectMapper.readTree(reader);
        }
        if (!schemaVersion.equals(manifest.path("schemaVersion").asText())
                || !version.equals(manifest.path("modelVersion").asText())
                || !"PyTorch".equals(manifest.path("engineName").asText())
                || !artifactRole.equals(manifest.path("artifactRole").asText())) {
            throw new IOException("Mengzi manifest 契约不匹配");
        }
        if (requireServingProperties
                && (!config.getModelName().equals(manifest.path("modelName").asText())
                || !"Langboat/mengzi-bert-base-fin".equals(manifest.path("baseEncoder").asText())
                || manifest.path("goldLabelSampleCount").asLong(0L) <= 0L
                || !manifest.path("encoderFrozen").asBoolean(false))) {
            throw new IOException("Mengzi 分类器缺少金标训练或冻结编码器证明");
        }
        boolean hasWeights = false;
        for (JsonNode fileNode : manifest.path("files")) {
            String relative = fileNode.path("path").asText("").replace('\\', '/');
            Path file = directory.resolve(relative).normalize();
            if (relative.isBlank() || !file.startsWith(directory) || !Files.isRegularFile(file)
                    || Files.size(file) != fileNode.path("size").asLong(-1L)
                    || !sha256(file).equalsIgnoreCase(fileNode.path("sha256").asText())) {
                throw new IOException("Mengzi 模型文件校验失败: " + relative);
            }
            hasWeights = hasWeights || relative.endsWith(".pt")
                    || relative.endsWith(".bin") || relative.endsWith(".safetensors");
        }
        if (!hasWeights || !Files.isRegularFile(directory.resolve("config.json"))
                || !Files.isRegularFile(directory.resolve("tokenizer.json"))
                || requireServingProperties && !Files.isRegularFile(directory.resolve("serving.properties"))) {
            throw new IOException("Mengzi 模型缺少必要制品");
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

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[1024 * 1024];
                int length;
                while ((length = input.read(buffer)) >= 0) {
                    if (length > 0) {
                        digest.update(buffer, 0, length);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
// AI_GENERATE_END -----
