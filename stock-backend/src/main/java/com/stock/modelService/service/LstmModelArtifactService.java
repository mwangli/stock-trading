// AI_GENERATE_START -
package com.stock.modelService.service;

import ai.djl.Model;
import ai.djl.engine.Engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.modelService.config.LstmTrainingConfig;
import com.stock.modelService.domain.entity.LstmModelDocument;
import com.stock.modelService.domain.entity.ModelActivationDocument;
import com.stock.modelService.model.StockLSTMModel;
import com.stock.modelService.persistence.LstmModelRepository;
import com.stock.modelService.persistence.ModelActivationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Properties;

/**
 * LSTM 模型制品持久化与兼容性校验服务。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LstmModelArtifactService {

    /** 当前共享模型固定名称。 */
    public static final String DEFAULT_SHARED_MODEL_NAME = "global-shared-lstm";
    /** 当前网络结构与标签口径版本。 */
    public static final String MODEL_VERSION = "v4-balanced-direction";
    private static final String LEGACY_MODEL_VERSION = "v3-embedding-multitask";
    private static final String LEGACY_LOSS_VERSION = "huber-bce-huber-v1";
    private static final String LOSS_VERSION = "huber-weighted-bce-huber-v2";

    private final LstmTrainingConfig config;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 保存不可变候选模型版本。
     *
     * @param paramsBytes DJL 参数字节
     * @param processedData 训练数据摘要
     * @param epoch 最佳轮次
     * @param trainLoss 训练损失
     * @param valLoss 验证损失
     * @param trainLabelStats 训练集方向标签统计
     * @param validationLabelStats 验证集方向标签统计
     * @param directionClassWeights 方向任务类别权重
     * @return MongoDB 模型标识
     * @throws IOException 制品序列化或保存失败
     */
    public String saveModel(byte[] paramsBytes, LstmDataPreprocessor.ProcessedData processedData,
                            int epoch, double trainLoss, double valLoss,
                            LstmTrainerService.DirectionLabelStats trainLabelStats,
                            LstmTrainerService.DirectionLabelStats validationLabelStats,
                            LstmTrainerService.DirectionClassWeights directionClassWeights) throws IOException {
        String metadata = buildMetadata(processedData, epoch);
        try {
            ModelActivationDocument activation = modelActivationRepository.findByModelName(getModelName()).orElse(null);
            LstmModelDocument document = new LstmModelDocument();
            document.setModelName(getModelName());
            document.setModelVersion(MODEL_VERSION + "-" + System.currentTimeMillis());
            document.setParentModelVersionId(activation == null ? null : activation.getActiveModelVersionId());
            document.setEpoch(epoch);
            document.setCreatedAt(LocalDateTime.now());
            document.setParams(paramsBytes);
            document.setNormalizationParams(metadata);
            document.setTrainingConfigJson(buildTrainingConfigJson());
            document.setInputContractJson(objectMapper.writeValueAsString(Map.of(
                    "sequenceLength", processedData.getSequenceLength(), "inputSize", processedData.getFeatureCount(),
                    "outputSize", StockLSTMModel.OUTPUT_SIZE, "featureVersion", processedData.getFeatureVersion(),
                    "labelVersion", "return-direction-downside-v3")));
            document.setMetricsJson(objectMapper.writeValueAsString(Map.of(
                    "trainLoss", trainLoss, "valLoss", valLoss, "bestEpoch", epoch,
                    "trainedStocks", processedData.getSampleCounts().size(),
                    "trainSamples", processedData.getTrainSamples().size(),
                    "valSamples", processedData.getValSamples().size(),
                    "trainUpSamples", trainLabelStats.positiveCount(),
                    "trainDownSamples", trainLabelStats.negativeCount(),
                    "valUpSamples", validationLabelStats.positiveCount(),
                    "valDownSamples", validationLabelStats.negativeCount(),
                    "directionPositiveWeight", directionClassWeights.positiveWeight(),
                    "directionNegativeWeight", directionClassWeights.negativeWeight())));
            document.setFeatureVersion(processedData.getFeatureVersion());
            document.setLabelVersion("return-direction-downside-v3");
            document.setEngineName("PyTorch");
            document.setEngineVersion(Engine.getInstance().getVersion());
            document.setDjlVersion(Model.class.getPackage().getImplementationVersion());
            document.setParameterSha256(sha256(paramsBytes));
            document.setParameterSize(paramsBytes.length);
            document.setStatus("CANDIDATE");
            document.setTrainLoss(trainLoss);
            document.setValLoss(valLoss);
            LstmModelDocument saved = lstmModelRepository.save(document);
            log.info("共享 LSTM 候选模型保存完成: id={}, version={}, parent={}",
                    saved.getId(), saved.getModelVersion(), saved.getParentModelVersionId());
            return "mongo:" + saved.getId();
        } catch (Exception exception) {
            throw new IOException("保存共享 LSTM 模型失败", exception);
        }
    }

    /**
     * 加载指定版本或当前激活版本，并校验参数摘要。
     *
     * @param modelVersionId 模型文档 ID，为空时读取激活版本
     * @return 已校验模型制品
     */
    public ModelArtifact loadArtifact(String modelVersionId) {
        LstmModelDocument document = modelVersionId == null || modelVersionId.isBlank()
                ? loadActiveDocument()
                : lstmModelRepository.findById(modelVersionId)
                .orElseThrow(() -> new IllegalStateException("指定 LSTM 模型版本不存在"));
        validateParameterIntegrity(document);
        return new ModelArtifact(document.getParams(), document.getNormalizationParams(), document.getId());
    }

    /**
     * 加载当前激活模型文档。
     *
     * @return 激活模型文档
     */
    public LstmModelDocument loadActiveDocument() {
        ModelActivationDocument activation = modelActivationRepository.findByModelName(getModelName())
                .orElseThrow(() -> new IllegalStateException("共享 LSTM 尚未激活模型版本"));
        return lstmModelRepository.findById(activation.getActiveModelVersionId())
                .orElseThrow(() -> new IllegalStateException("共享 LSTM 激活版本不存在"));
    }

    /**
     * 校验指定模型版本的参数完整性和当前代码兼容性。
     *
     * @param versionId 模型版本文档 ID
     */
    public void validateModelVersion(String versionId) {
        LstmModelDocument document = lstmModelRepository.findById(versionId)
                .orElseThrow(() -> new IllegalStateException("模型版本不存在"));
        validateParameterIntegrity(document);
        validateMetadata(document.getNormalizationParams());
    }

    /**
     * 校验模型元数据与当前特征、结构和标签契约一致。
     *
     * @param metadata 模型元数据
     */
    public void validateMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            throw new IllegalStateException("共享 LSTM 模型缺少元数据");
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(metadata));
        } catch (IOException exception) {
            throw new IllegalStateException("共享 LSTM 元数据无法解析", exception);
        }
        requireOneOfMetadata(properties, "modelVersion", MODEL_VERSION, LEGACY_MODEL_VERSION);
        requireMetadata(properties, "featureVersion", LstmDataPreprocessor.FEATURE_VERSION);
        requireMetadata(properties, "sequenceLength", String.valueOf(config.getSequenceLength()));
        requireMetadata(properties, "inputSize", String.valueOf(config.getInputSize()));
        requireMetadata(properties, "outputSize", String.valueOf(StockLSTMModel.OUTPUT_SIZE));
        requireMetadata(properties, "stockEmbeddingBuckets", String.valueOf(config.getStockEmbeddingBuckets()));
        requireMetadata(properties, "stockEmbeddingSize", String.valueOf(config.getStockEmbeddingSize()));
        requireMetadata(properties, "industryEmbeddingBuckets", String.valueOf(config.getIndustryEmbeddingBuckets()));
        requireMetadata(properties, "industryEmbeddingSize", String.valueOf(config.getIndustryEmbeddingSize()));
        requireMetadata(properties, "groupHeadCount", String.valueOf(config.getGroupHeadCount()));
        requireMetadata(properties, "residualHeadEnabled", String.valueOf(config.isResidualHeadEnabled()));
        requireMetadata(properties, "groupHeadScale", String.valueOf(config.getGroupHeadScale()));
        requireMetadata(properties, "residualHeadScale", String.valueOf(config.getResidualHeadScale()));
        requireMetadata(properties, "hashAlgorithm", "java-string-hashcode-floor-mod-v1");
        requireOneOfMetadata(properties, "lossVersion", LOSS_VERSION, LEGACY_LOSS_VERSION);
        requireMetadata(properties, "targetReturnScale", String.valueOf(config.getTargetReturnScale()));
    }

    /**
     * 获取当前共享模型逻辑名称。
     *
     * @return 模型逻辑名称
     */
    public String getModelName() {
        return config.getModelName() == null || config.getModelName().isBlank()
                ? DEFAULT_SHARED_MODEL_NAME : config.getModelName().trim();
    }

    private String buildMetadata(LstmDataPreprocessor.ProcessedData data, int epoch) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("modelVersion", MODEL_VERSION);
        properties.setProperty("featureVersion", data.getFeatureVersion());
        properties.setProperty("labelVersion", "return-direction-downside-v3");
        properties.setProperty("sequenceLength", String.valueOf(data.getSequenceLength()));
        properties.setProperty("inputSize", String.valueOf(data.getFeatureCount()));
        properties.setProperty("outputSize", String.valueOf(StockLSTMModel.OUTPUT_SIZE));
        properties.setProperty("stockEmbeddingBuckets", String.valueOf(config.getStockEmbeddingBuckets()));
        properties.setProperty("stockEmbeddingSize", String.valueOf(config.getStockEmbeddingSize()));
        properties.setProperty("industryEmbeddingBuckets", String.valueOf(config.getIndustryEmbeddingBuckets()));
        properties.setProperty("industryEmbeddingSize", String.valueOf(config.getIndustryEmbeddingSize()));
        properties.setProperty("groupHeadCount", String.valueOf(config.getGroupHeadCount()));
        properties.setProperty("residualHeadEnabled", String.valueOf(config.isResidualHeadEnabled()));
        properties.setProperty("groupHeadScale", String.valueOf(config.getGroupHeadScale()));
        properties.setProperty("residualHeadScale", String.valueOf(config.getResidualHeadScale()));
        properties.setProperty("hashAlgorithm", "java-string-hashcode-floor-mod-v1");
        properties.setProperty("lossVersion", LOSS_VERSION);
        properties.setProperty("hiddenSize", String.valueOf(config.getHiddenSize()));
        properties.setProperty("numLayers", String.valueOf(config.getNumLayers()));
        properties.setProperty("targetReturnScale", String.valueOf(data.getTargetReturnScale()));
        properties.setProperty("epoch", String.valueOf(epoch));
        properties.setProperty("createdAt", LocalDateTime.now().toString());
        properties.setProperty("modelName", getModelName());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "stock-trading shared LSTM metadata");
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String buildTrainingConfigJson() throws IOException {
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("inputSize", config.getInputSize()), Map.entry("hiddenSize", config.getHiddenSize()),
                Map.entry("numLayers", config.getNumLayers()), Map.entry("dropout", config.getDropout()),
                Map.entry("sequenceLength", config.getSequenceLength()),
                Map.entry("stockEmbeddingBuckets", config.getStockEmbeddingBuckets()),
                Map.entry("stockEmbeddingSize", config.getStockEmbeddingSize()),
                Map.entry("industryEmbeddingBuckets", config.getIndustryEmbeddingBuckets()),
                Map.entry("industryEmbeddingSize", config.getIndustryEmbeddingSize()),
                Map.entry("groupHeadCount", config.getGroupHeadCount()),
                Map.entry("residualHeadEnabled", config.isResidualHeadEnabled()),
                Map.entry("groupHeadScale", config.getGroupHeadScale()),
                Map.entry("residualHeadScale", config.getResidualHeadScale()),
                Map.entry("learningRate", config.getLearningRate()), Map.entry("batchSize", config.getBatchSize()),
                Map.entry("epochs", config.getEpochs())));
    }

    private void validateParameterIntegrity(LstmModelDocument document) {
        if (document.getParams() == null || document.getParams().length == 0) {
            throw new IllegalStateException("共享 LSTM 模型参数为空");
        }
        if (!sha256(document.getParams()).equalsIgnoreCase(document.getParameterSha256())) {
            throw new IllegalStateException("共享 LSTM 模型参数摘要不匹配");
        }
    }

    private String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private void requireMetadata(Properties properties, String key, String expected) {
        String actual = properties.getProperty(key);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("共享 LSTM 元数据不兼容: " + key
                    + ", expected=" + expected + ", actual=" + actual);
        }
    }

    private void requireOneOfMetadata(Properties properties, String key, String... expectedValues) {
        String actual = properties.getProperty(key);
        for (String expected : expectedValues) {
            if (expected.equals(actual)) {
                return;
            }
        }
        throw new IllegalStateException("共享 LSTM 元数据不兼容: " + key
                + ", expectedOneOf=" + String.join(",", expectedValues) + ", actual=" + actual);
    }

    /**
     * 已完成摘要校验的模型制品。
     *
     * @param params 参数字节
     * @param metadata 元数据
     * @param modelId 模型文档 ID
     */
    public record ModelArtifact(byte[] params, String metadata, String modelId) { }
}
// AI_GENERATE_END -
