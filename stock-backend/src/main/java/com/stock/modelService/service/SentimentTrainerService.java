// AI_GENERATE_START ---------
package com.stock.modelService.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.modelService.config.SentimentTrainingConfig;
import com.stock.modelService.domain.entity.ModelMetadataDocument;
import com.stock.modelService.domain.vo.SentimentAnalysisResult;
import com.stock.modelService.domain.vo.SentimentArtifactInspection;
import com.stock.modelService.persistence.ModelMetadataRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * DJL PyTorch 情感模型加载与推理服务。
 * 本地模型加载前必须通过版本目录、manifest、文件大小和 SHA-256 完整校验。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentTrainerService {

    private final SentimentTrainingConfig config;
    private final SentimentDataPreprocessor dataPreprocessor;
    private final ModelMetadataRepository modelMetadataRepository;
    private final SentimentArtifactService artifactService;
    private final ObjectMapper objectMapper;

    private volatile ZooModel<String, Classifications> loadedModel;
    private volatile boolean modelLoaded;
    private volatile LocalDateTime lastLoadedTime;
    private volatile String lastLoadError;
    private volatile SentimentArtifactInspection loadedArtifact;

    /**
     * 输出情感模型配置和轻量制品检查结果。
     * 应用启动不主动加载大模型，避免模型故障阻断其他业务模块启动。
     */
    @PostConstruct
    public void init() {
        SentimentArtifactInspection inspection = artifactService.inspectForHealth();
        log.info("情感模型配置: modelName={}, version={}, source={}, artifactPath={}, artifactAvailable={}, error={}",
                config.getModelName(), config.getModelVersion(), config.getModelSource(),
                inspection.getModelDirectory(), inspection.isAvailable(), inspection.getErrorMessage());
        if (useRemoteModel()) {
            log.warn("情感模型配置为远程加载；生产环境应改为经过校验的本地只读制品");
        }
    }

    /**
     * 加载配置指定的情感模型。
     * 本地制品在创建 Tokenizer 和 DJL 模型前完成流式摘要校验。
     *
     * @return true 表示真实模型已加载
     */
    public synchronized boolean loadModel() {
        if (modelLoaded && loadedModel != null) {
            return true;
        }
        SentimentArtifactInspection inspection = null;
        try {
            String modelUrl;
            HuggingFaceTokenizer tokenizer;
            if (useLocalModel()) {
                inspection = artifactService.inspectForLoad();
                modelUrl = inspection.getModelDirectory().toUri().toString();
                tokenizer = HuggingFaceTokenizer.builder()
                        .optTokenizerPath(inspection.getModelDirectory())
                        .optTruncation(true)
                        .optMaxLength(config.getMaxSequenceLength())
                        .build();
                log.info("情感模型制品校验通过，开始加载: version={}, sizeBytes={}, manifestSha256={}, path={}, maxSequenceLength={}, includeTokenTypes=true",
                        inspection.getModelVersion(), inspection.getTotalSizeBytes(),
                        inspection.getManifestSha256(), inspection.getModelDirectory(), config.getMaxSequenceLength());
            } else if (useRemoteModel()) {
                tokenizer = HuggingFaceTokenizer.builder()
                        .optTokenizerName(config.getPretrainedModel())
                        .optTruncation(true)
                        .optMaxLength(config.getMaxSequenceLength())
                        .build();
                modelUrl = "djl://ai.djl.huggingface.pytorch/" + config.getPretrainedModel();
                log.warn("开始从远程仓库加载情感模型: {}", config.getPretrainedModel());
            } else {
                throw new IOException("情感模型来源配置非法: " + config.getModelSource());
            }

            Translator<String, Classifications> translator =
                    TextClassificationTranslator.builder(tokenizer)
                            .optIncludeTokenTypes(true)
                            .build();
            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls(modelUrl)
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();

            ZooModel<String, Classifications> model = criteria.loadModel();
            loadedModel = model;
            loadedArtifact = inspection;
            modelLoaded = true;
            lastLoadedTime = LocalDateTime.now();
            lastLoadError = null;
            updateModelMetadata(modelUrl, inspection, "LOADED", null);
            log.info("情感分析模型加载成功: version={}, modelUrl={}",
                    inspection == null ? config.getModelVersion() : inspection.getModelVersion(), modelUrl);
            return true;
        } catch (Exception exception) {
            lastLoadError = exception.getMessage();
            modelLoaded = false;
            loadedArtifact = inspection;
            updateModelMetadata(resolveArtifactUri(inspection), inspection, "LOAD_FAILED", exception.getMessage());
            log.error("情感分析模型加载失败，将在非交易接口中使用规则降级", exception);
            return false;
        }
    }

    /**
     * 执行情感分析。
     * 非交易场景在模型不可用时允许返回明确标记的规则结果。
     *
     * @param text 新闻或公告文本
     * @return 情感分析结果
     */
    public SentimentAnalysisResult analyzeSentiment(String text) {
        if (!modelLoaded || loadedModel == null) {
            log.warn("情感模型未加载，使用规则分析");
            return analyzeWithRules(text);
        }
        try {
            return predict(text);
        } catch (Exception exception) {
            log.error("情感分析推理失败，使用规则分析", exception);
            markModelUnhealthy(exception);
            return analyzeWithRules(text);
        }
    }

    /**
     * 使用真实情感模型执行交易候选所需的严格推理。
     * 模型无法加载或单次推理失败时直接抛出异常，禁止用规则结果替代。
     *
     * @param text 新闻或公告文本
     * @return 情感标签、连续分数和置信度
     */
    public SentimentAnalysisResult analyzeSentimentRequired(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("情感分析文本不能为空");
        }
        if ((!modelLoaded || loadedModel == null) && !loadModel()) {
            throw new IllegalStateException("情感分析模型未加载: " + lastLoadError);
        }
        try {
            return predict(text);
        } catch (Exception exception) {
            markModelUnhealthy(exception);
            throw new IllegalStateException("情感分析模型推理失败", exception);
        }
    }

    /**
     * 查询情感模型是否已成功加载。
     *
     * @return true 表示当前可执行真实模型推理
     */
    public boolean isModelLoaded() {
        return modelLoaded;
    }

    /**
     * 卸载当前情感模型并释放底层资源。
     */
    public synchronized void unloadModel() {
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
        }
        modelLoaded = false;
        lastLoadedTime = null;
        loadedArtifact = null;
        updateModelMetadata(null, null, "UNLOADED", null);
        log.info("情感分析模型已卸载");
    }

    /**
     * 返回上次模型加载成功的时间。
     *
     * @return 加载时间，未加载时为空
     */
    public LocalDateTime getLastLoadedTime() {
        return lastLoadedTime;
    }

    /**
     * 返回当前已加载制品或配置版本的轻量检查结果。
     *
     * @return 制品检查结果
     */
    public SentimentArtifactInspection getArtifactInspection() {
        SentimentArtifactInspection current = loadedArtifact;
        return current != null ? current : artifactService.inspectForHealth();
    }

    /**
     * 返回最近一次模型加载错误。
     *
     * @return 错误信息，无错误时为空
     */
    public String getLastLoadError() {
        return lastLoadError;
    }

    /**
     * 情感分析并返回兼容旧接口的详细字段。
     *
     * @param text 待分析文本
     * @return 标签、分数、概率和真实模型加载标识
     */
    public Map<String, Object> analyzeSentimentWithDetails(String text) {
        if (text != null && !text.isBlank() && !modelLoaded) {
            loadModel();
        }
        SentimentAnalysisResult result = text == null || text.isBlank()
                ? analyzeWithRules(text == null ? "" : text)
                : analyzeSentiment(text);
        Map<String, Object> response = new HashMap<>();
        response.put("label", result.getLabel());
        response.put("score", result.getScore());
        response.put("normalizedScore", result.getNormalizedScore());
        response.put("confidence", result.getConfidence());
        response.put("probabilities", result.getProbabilities() == null ? Map.of() : result.getProbabilities());
        response.put("text", result.getText() == null ? "" : result.getText());
        response.put("modelLoaded", modelLoaded);
        return response;
    }

    private SentimentAnalysisResult predict(String text) throws Exception {
        try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
            Classifications classifications = predictor.predict(text);
            Map<String, Double> probabilities = new HashMap<>();
            String bestLabel = null;
            double bestProbability = 0D;
            for (Classifications.Classification classification : classifications.items()) {
                probabilities.put(classification.getClassName(), classification.getProbability());
                if (classification.getProbability() > bestProbability) {
                    bestProbability = classification.getProbability();
                    bestLabel = classification.getClassName();
                }
            }
            return SentimentAnalysisResult.builder()
                    .label(bestLabel)
                    .score(calculateSentimentScore(probabilities))
                    .normalizedScore(calculateNormalizedScore(bestLabel, bestProbability))
                    .confidence(bestProbability)
                    .probabilities(probabilities)
                    .text(text)
                    .build();
        }
    }

    private synchronized void markModelUnhealthy(Exception exception) {
        lastLoadError = exception.getMessage();
        modelLoaded = false;
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
        }
        updateModelMetadata(resolveArtifactUri(loadedArtifact), loadedArtifact,
                "INFERENCE_FAILED", exception.getMessage());
    }

    private SentimentAnalysisResult analyzeWithRules(String text) {
        String safeText = text == null ? "" : text;
        int label = dataPreprocessor.autoLabelSentiment(safeText);
        String labelText = config.getLabels()[label];
        Map<String, Double> probabilities = new HashMap<>();
        probabilities.put("neutral", label == 0 ? 0.7D : 0.15D);
        probabilities.put("positive", label == 1 ? 0.7D : 0.15D);
        probabilities.put("negative", label == 2 ? 0.7D : 0.15D);
        return SentimentAnalysisResult.builder()
                .label(labelText)
                .score(label == 2 ? -0.7D : label == 1 ? 0.7D : 0D)
                .normalizedScore(calculateNormalizedScore(labelText, 0.7D))
                .confidence(0.7D)
                .probabilities(probabilities)
                .text(safeText)
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
        if (probabilities == null || probabilities.isEmpty()) {
            return 0D;
        }
        double positive = probabilities.entrySet().stream()
                .filter(entry -> "positive".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0D);
        double negative = probabilities.entrySet().stream()
                .filter(entry -> "negative".equalsIgnoreCase(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(0D);
        return Math.max(-1D, Math.min(1D, positive - negative));
    }

    private boolean useLocalModel() {
        return "local".equalsIgnoreCase(config.getModelSource());
    }

    private boolean useRemoteModel() {
        return "huggingface".equalsIgnoreCase(config.getModelSource()) && config.isDownloadPretrained();
    }

    private String resolveArtifactUri(SentimentArtifactInspection inspection) {
        if (inspection != null && inspection.getModelDirectory() != null) {
            return inspection.getModelDirectory().toUri().toString();
        }
        return useRemoteModel()
                ? "djl://ai.djl.huggingface.pytorch/" + config.getPretrainedModel()
                : config.getModelPath();
    }

    private void updateModelMetadata(String artifactUri,
                                     SentimentArtifactInspection inspection,
                                     String status,
                                     String errorMessage) {
        try {
            ModelMetadataDocument metadata = modelMetadataRepository.findByModelName(config.getModelName())
                    .orElseGet(ModelMetadataDocument::new);
            metadata.setModelName(config.getModelName());
            metadata.setModelVersion(inspection == null ? config.getModelVersion() : inspection.getModelVersion());
            metadata.setArtifactUri(artifactUri);
            metadata.setConfigJson(objectMapper.writeValueAsString(Map.of(
                    "modelSource", config.getModelSource(),
                    "pretrainedModel", config.getPretrainedModel(),
                    "maxSequenceLength", config.getMaxSequenceLength(),
                    "labels", config.getLabels())));
            metadata.setMetricsJson(inspection == null || inspection.getMetrics() == null
                    ? "{}" : objectMapper.writeValueAsString(inspection.getMetrics()));
            metadata.setStatus(status);
            metadata.setEngineName("PyTorch");
            metadata.setLastError(errorMessage);
            metadata.setUpdatedAt(LocalDateTime.now());
            modelMetadataRepository.save(metadata);
        } catch (RuntimeException | IOException exception) {
            log.warn("记录情感模型元数据失败: {}", exception.getMessage());
        }
    }
}
// AI_GENERATE_END ---------
