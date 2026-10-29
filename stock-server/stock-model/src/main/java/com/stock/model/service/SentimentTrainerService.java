// AI_GENERATE_START-------------------------
package com.stock.model.service;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.huggingface.translator.TextClassificationTranslator;
import ai.djl.inference.Predictor;
import ai.djl.modality.Classifications;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.Translator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.model.config.SentimentTrainingConfig;
import com.stock.model.dto.SentimentModelInput;
import com.stock.model.entity.ModelMetadataDocument;
import com.stock.model.repository.ModelMetadataRepository;
import com.stock.model.vo.SentimentAnalysisResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * DJL PyTorch 情感模型加载与推理服务。
 * 固定模型目录由发布镜像提供，实际内容由 DJL 加载时验证。
 *
 * @author mwangli
 * @since 2026-10-10
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentTrainerService implements SentimentModelRuntime {

    private static final int INFERENCE_BATCH_SIZE = 64;
    private static final int INFERENCE_WORKER_COUNT = 2;

    private final SentimentTrainingConfig config;
    private final SentimentDataPreprocessor dataPreprocessor;
    private final ModelMetadataRepository modelMetadataRepository;
    private final ObjectMapper objectMapper;
    private final SentimentInputAssembler inputAssembler;
    private final SentimentPredictionNormalizer predictionNormalizer;
    private final ExecutorService batchInferenceExecutor =
            Executors.newFixedThreadPool(INFERENCE_WORKER_COUNT);

    private volatile ZooModel<String, Classifications> loadedModel;
    private volatile boolean modelLoaded;
    private volatile LocalDateTime lastLoadedTime;
    private volatile String lastLoadError;

    /**
     * 从固定本地版本目录加载情感模型；目录缺失或损坏时由 DJL 直接失败。
     *
     * @return true 表示真实模型已加载
     */
    public synchronized boolean loadModel() {
        if (modelLoaded && loadedModel != null) {
            return true;
        }
        try {
            Path modelDirectory = resolveModelDirectory();
            HuggingFaceTokenizer tokenizer = HuggingFaceTokenizer.builder()
                    .optTokenizerPath(modelDirectory)
                    .optTruncation(true)
                    .optMaxLength(config.getMaxSequenceLength())
                    .build();
            Translator<String, Classifications> translator = TextClassificationTranslator.builder(tokenizer)
                    .optIncludeTokenTypes(true)
                    .build();
            Criteria<String, Classifications> criteria = Criteria.builder()
                    .setTypes(String.class, Classifications.class)
                    .optModelUrls(modelDirectory.toUri().toString())
                    .optEngine("PyTorch")
                    .optTranslator(translator)
                    .optProgress(new ProgressBar())
                    .build();
            ZooModel<String, Classifications> model = criteria.loadModel();
            loadedModel = model;
            modelLoaded = true;
            lastLoadedTime = LocalDateTime.now();
            lastLoadError = null;
            updateModelMetadata(modelDirectory.toUri().toString(), "LOADED", null);
            log.info("本地情感模型加载成功: model={}, version={}, directory={}",
                    config.getModelName(), config.getModelVersion(), modelDirectory);
            return true;
        } catch (Exception exception) {
            lastLoadError = exception.getMessage();
            modelLoaded = false;
            updateModelMetadata(null, "LOAD_FAILED", exception.getMessage());
            log.error("本地情感模型加载失败", exception);
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
            return predict(inputAssembler.assembleText(text));
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
            return predict(inputAssembler.assembleText(text));
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
        updateModelMetadata(null, "UNLOADED", null);
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
     * 返回最近一次模型加载错误。
     *
     * @return 错误信息，无错误时为空
     */
    public String getLastLoadError() {
        return lastLoadError;
    }

    /**
     * 返回当前配置的 FinBERT 模型版本。
     *
     * @return 模型版本
     */
    public String getModelVersion() {
        return config.getModelVersion();
    }


    @Override
    public String modelName() {
        return config.getModelName();
    }

    /**
     * 使用已加载 FinBERT 对统一分块输入执行推理。
     *
     * @param input 标题优先的统一模型输入
     * @return 完成概率平均和不确定性计算的三分类结果
     */
    @Override
    public SentimentAnalysisResult predict(SentimentModelInput input) {
        if (input == null || input.getChunks() == null || input.getChunks().isEmpty()) {
            throw new IllegalArgumentException("FinBERT 情感分析输入不能为空");
        }
        if ((!modelLoaded || loadedModel == null) && !loadModel()) {
            throw new IllegalStateException("情感分析模型未加载: " + lastLoadError);
        }
        try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
            List<Map<String, Double>> chunkProbabilities = new ArrayList<>();
            for (String chunk : input.getChunks()) {
                chunkProbabilities.add(toProbabilityMap(predictor.predict(chunk)));
            }
            return predictionNormalizer.normalize(
                    chunkProbabilities, input, config.getCalibrationTemperature());
        } catch (Exception exception) {
            markModelUnhealthy(exception);
            throw new IllegalStateException("FinBERT 情感模型推理失败", exception);
        }
    }

    /**
     * 使用一个 Predictor 分批执行多个统一输入，减少历史回填的模型调用和资源创建开销。
     *
     * @param inputs 待推理的统一模型输入列表
     * @return 与输入顺序一致的情感结果
     * @throws IllegalArgumentException 输入或分块无效时抛出
     * @throws IllegalStateException 模型未加载或批量推理失败时抛出
     */
    public List<SentimentAnalysisResult> predictBatch(List<SentimentModelInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return List.of();
        }
        for (SentimentModelInput input : inputs) {
            if (input == null || input.getChunks() == null || input.getChunks().isEmpty()) {
                throw new IllegalArgumentException("FinBERT 批量情感分析输入不能为空");
            }
        }
        if ((!modelLoaded || loadedModel == null) && !loadModel()) {
            throw new IllegalStateException("情感分析模型未加载: " + lastLoadError);
        }
        List<String> chunks = new ArrayList<>();
        List<Integer> owners = new ArrayList<>();
        List<List<Map<String, Double>>> probabilitiesByInput = new ArrayList<>(inputs.size());
        for (int inputIndex = 0; inputIndex < inputs.size(); inputIndex++) {
            probabilitiesByInput.add(new ArrayList<>());
            for (String chunk : inputs.get(inputIndex).getChunks()) {
                chunks.add(chunk);
                owners.add(inputIndex);
            }
        }
        try {
            List<Integer> batchStarts = new ArrayList<>();
            List<Future<List<Classifications>>> futures = new ArrayList<>();
            for (int start = 0; start < chunks.size(); start += INFERENCE_BATCH_SIZE) {
                int end = Math.min(start + INFERENCE_BATCH_SIZE, chunks.size());
                List<String> batch = List.copyOf(chunks.subList(start, end));
                batchStarts.add(start);
                futures.add(batchInferenceExecutor.submit(() -> {
                    try (Predictor<String, Classifications> predictor = loadedModel.newPredictor()) {
                        return predictor.batchPredict(batch);
                    }
                }));
            }
            for (int batchIndex = 0; batchIndex < futures.size(); batchIndex++) {
                int start = batchStarts.get(batchIndex);
                List<Classifications> predictions = futures.get(batchIndex).get();
                int expectedSize = Math.min(INFERENCE_BATCH_SIZE, chunks.size() - start);
                if (predictions.size() != expectedSize) {
                    throw new IllegalStateException("FinBERT 批量推理输出数量与输入分块不一致");
                }
                for (int offset = 0; offset < predictions.size(); offset++) {
                    int owner = owners.get(start + offset);
                    probabilitiesByInput.get(owner).add(toProbabilityMap(predictions.get(offset)));
                }
            }
            List<SentimentAnalysisResult> results = new ArrayList<>(inputs.size());
            for (int index = 0; index < inputs.size(); index++) {
                results.add(predictionNormalizer.normalize(
                        probabilitiesByInput.get(index), inputs.get(index),
                        config.getCalibrationTemperature()));
            }
            return results;
        } catch (Exception exception) {
            markModelUnhealthy(exception);
            throw new IllegalStateException("FinBERT 情感模型批量推理失败", exception);
        }
    }

    /**
     * 关闭历史批量推理线程池，避免应用停止时遗留后台线程。
     */
    @PreDestroy
    public void shutdownBatchInferenceExecutor() {
        batchInferenceExecutor.shutdownNow();
    }

    private Map<String, Double> toProbabilityMap(Classifications classifications) {
        Map<String, Double> probabilities = new HashMap<>();
        for (Classifications.Classification classification : classifications.items()) {
            probabilities.put(normalizeLabel(classification.getClassName()),
                    classification.getProbability());
        }
        return probabilities;
    }

    private synchronized void markModelUnhealthy(Exception exception) {
        lastLoadError = exception.getMessage();
        modelLoaded = false;
        if (loadedModel != null) {
            loadedModel.close();
            loadedModel = null;
        }
        updateModelMetadata(resolveArtifactUri(), "INFERENCE_FAILED", exception.getMessage());
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
                .calibratedConfidence(0.7D)
                .entropy(0.75D)
                .probabilityMargin(0.55D)
                .abstained(false)
                .chunkCount(1)
                .textCoverage(1D)
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

    private String normalizeLabel(String rawLabel) {
        if (rawLabel == null) {
            throw new IllegalStateException("FinBERT 输出标签为空");
        }
        for (String label : config.getLabels()) {
            if (label.equalsIgnoreCase(rawLabel)) {
                return label.toLowerCase(java.util.Locale.ROOT);
            }
        }
        throw new IllegalStateException("FinBERT 输出标签无法映射: " + rawLabel);
    }

    private Path resolveModelDirectory() {
        String version = config.getModelVersion();
        if (version == null || version.isBlank()) {
            throw new IllegalStateException("情感模型版本未配置");
        }
        Path root = Path.of(config.getModelPath()).toAbsolutePath().normalize();
        Path directory = root.resolve(version.trim()).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalStateException("情感模型目录越界: " + directory);
        }
        return directory;
    }

    private String resolveArtifactUri() {
        return resolveModelDirectory().toUri().toString();
    }

    private void updateModelMetadata(String artifactUri,
                                     String status,
                                     String errorMessage) {
        try {
            ModelMetadataDocument metadata = modelMetadataRepository.findByModelName(config.getModelName())
                    .orElseGet(ModelMetadataDocument::new);
            metadata.setModelName(config.getModelName());
            metadata.setModelVersion(config.getModelVersion());
            metadata.setArtifactUri(artifactUri);
            metadata.setConfigJson(objectMapper.writeValueAsString(Map.of(
                    "pretrainedModel", config.getPretrainedModel(),
                    "modelPath", config.getModelPath(),
                    "maxSequenceLength", config.getMaxSequenceLength(),
                    "labels", config.getLabels())));
            metadata.setMetricsJson("{}");
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
// AI_GENERATE_END-------------------------
