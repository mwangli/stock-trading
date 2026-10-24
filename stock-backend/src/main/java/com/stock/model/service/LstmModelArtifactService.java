// AI_GENERATE_START-------------------
package com.stock.model.service;

import ai.djl.Model;
import ai.djl.engine.Engine;
import com.stock.platform.config.CacheNames;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.model.config.LstmTrainingConfig;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelActivationDocument;
import com.stock.model.algorithm.StockLSTMModel;
import com.stock.model.algorithm.RankingLSTMModel;
import com.stock.model.algorithm.LstmArchitectureType;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelActivationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
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
    public static final String MODEL_VERSION = "v5-cross-sectional-ranking";
    private static final String V4_MODEL_VERSION = "v4-balanced-direction";
    private static final String LEGACY_MODEL_VERSION = "v3-embedding-multitask";
    private static final String LEGACY_LOSS_VERSION = "huber-bce-huber-v1";
    private static final String LOSS_VERSION = "huber-weighted-bce-huber-v2";
    private static final String RANKING_LOSS_VERSION = "pairwise-ranking-multitask-v1";
    private static final String OUTPUT_SCHEMA_VERSION = "ranking-return-probability-downside-v1";

    private final LstmTrainingConfig config;
    private final LstmModelRepository lstmModelRepository;
    private final ModelActivationRepository modelActivationRepository;
    private final ObjectMapper objectMapper;
    private final GridFsTemplate gridFsTemplate;

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
     * @param validationMetrics 验证集横截面指标
     * @param testMetrics 独立测试集横截面指标
     * @param walkForwardMetrics Walk-Forward 聚合指标
     * @param strictIsolationMetrics 60 日严格隔离敏感性指标
     * @param probabilityTemperature 概率温度校准参数
     * @param architectureType 本次候选模型架构
     * @param crossSectionBatchSize 训练使用的同日横截面块大小
     * @return 模型版本标识
     * @throws IOException 制品序列化或保存失败
     */
    public String saveModel(byte[] paramsBytes, LstmDataPreprocessor.ProcessedData processedData,
                            int epoch, double trainLoss, double valLoss,
                            LstmTrainerService.DirectionLabelStats trainLabelStats,
                            LstmTrainerService.DirectionLabelStats validationLabelStats,
                            LstmTrainerService.DirectionClassWeights directionClassWeights,
                            RankingValidationMetricsCalculator.RankingValidationMetrics validationMetrics,
                            RankingValidationMetricsCalculator.RankingValidationMetrics testMetrics,
                            RankingValidationMetricsCalculator.WalkForwardMetrics walkForwardMetrics,
                            RankingValidationMetricsCalculator.WalkForwardMetrics strictIsolationMetrics,
                            double probabilityTemperature,
                            LstmArchitectureType architectureType,
                            int crossSectionBatchSize) throws IOException {
        String metadata = buildMetadata(processedData, epoch, probabilityTemperature,
                architectureType, crossSectionBatchSize);
        try {
            ModelActivationDocument activation = modelActivationRepository.findByModelName(getModelName()).orElse(null);
            LstmModelDocument document = new LstmModelDocument();
            document.setModelName(getModelName());
            document.setModelVersion(MODEL_VERSION + "-"
                    + architectureType.name().toLowerCase(java.util.Locale.ROOT) + "-"
                    + System.currentTimeMillis());
            document.setParentModelVersionId(activation == null ? null : activation.getActiveModelVersionId());
            document.setEpoch(epoch);
            document.setCreatedAt(LocalDateTime.now());
            document.setParameterFileId(gridFsTemplate.store(
                    new ByteArrayInputStream(paramsBytes),
                    getModelName() + "-" + document.getModelVersion() + ".params",
                    "application/octet-stream",
                    new org.bson.Document("modelName", getModelName())
                            .append("modelVersion", document.getModelVersion())
                            .append("artifactType", "LSTM_MODEL_PARAMETERS"))
                    .toHexString());
            document.setNormalizationParams(metadata);
            document.setTrainingConfigJson(buildTrainingConfigJson(architectureType));
            document.setInputContractJson(objectMapper.writeValueAsString(Map.of(
                    "sequenceLength", processedData.getSequenceLength(), "inputSize", processedData.getFeatureCount(),
                    "outputSize", RankingLSTMModel.OUTPUT_SIZE, "featureVersion", processedData.getFeatureVersion(),
                    "labelVersion", RankingLstmDataPreprocessor.LABEL_VERSION,
                    "outputSchemaVersion", OUTPUT_SCHEMA_VERSION,
                    "crossSectionBatchSize", crossSectionBatchSize,
                    "architectureType", architectureType.name())));
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put("trainLoss", trainLoss);
            metrics.put("valLoss", valLoss);
            metrics.put("bestEpoch", epoch);
            metrics.put("trainedStocks", processedData.getSampleCounts().size());
            metrics.put("trainSamples", processedData.getTrainSamples().size());
            metrics.put("valSamples", processedData.getValSamples().size());
            metrics.put("trainUpSamples", trainLabelStats.positiveCount());
            metrics.put("trainDownSamples", trainLabelStats.negativeCount());
            metrics.put("valUpSamples", validationLabelStats.positiveCount());
            metrics.put("valDownSamples", validationLabelStats.negativeCount());
            metrics.put("directionPositiveWeight", directionClassWeights.positiveWeight());
            metrics.put("directionNegativeWeight", directionClassWeights.negativeWeight());
            metrics.put("validationRankIc", validationMetrics.rankIc());
            metrics.put("validationTopDecileNetReturn", validationMetrics.topDecileNetReturn());
            metrics.put("validationSelectionScore", validationMetrics.selectionScore());
            metrics.put("testRankIc", testMetrics.rankIc());
            metrics.put("testTopDecileNetReturn", testMetrics.topDecileNetReturn());
            metrics.put("testSelectionScore", testMetrics.selectionScore());
            metrics.put("outputCollapsed", testMetrics.collapsed());
            metrics.put("probabilityTemperature", probabilityTemperature);
            metrics.put("validationBrierScore", validationMetrics.brierScore());
            metrics.put("validationBrierBaseline", validationMetrics.brierBaseline());
            metrics.put("testBrierScore", testMetrics.brierScore());
            metrics.put("testBrierBaseline", testMetrics.brierBaseline());
            metrics.put("walkForwardFoldCount", walkForwardMetrics.foldCount());
            metrics.put("walkForwardMeanRankIc", walkForwardMetrics.meanRankIc());
            metrics.put("walkForwardRankIcIr", walkForwardMetrics.rankIcIr());
            metrics.put("positiveRankIcWindowRatio", walkForwardMetrics.positiveRankIcRatio());
            metrics.put("walkForwardTopDecileNetReturn", walkForwardMetrics.averageTopDecileNetReturn());
            metrics.put("walkForwardBrierScore", walkForwardMetrics.brierScore());
            metrics.put("walkForwardBrierBaseline", walkForwardMetrics.brierBaseline());
            metrics.put("walkForwardMaximumDrawdown", walkForwardMetrics.maximumDrawdown());
            metrics.put("strictIsolationFoldCount", strictIsolationMetrics.foldCount());
            metrics.put("strictIsolationMeanRankIc", strictIsolationMetrics.meanRankIc());
            metrics.put("trainStartDate", processedData.getTrainStartDate());
            metrics.put("trainEndDate", processedData.getTrainEndDate());
            metrics.put("validationStartDate", processedData.getValidationStartDate());
            metrics.put("validationEndDate", processedData.getValidationEndDate());
            metrics.put("testStartDate", processedData.getTestStartDate());
            metrics.put("testEndDate", processedData.getTestEndDate());
            document.setMetricsJson(objectMapper.writeValueAsString(metrics));
            document.setFeatureVersion(processedData.getFeatureVersion());
            document.setLabelVersion(RankingLstmDataPreprocessor.LABEL_VERSION);
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
            return saved.getId();
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
        return new ModelArtifact(loadParameterBytes(document), document.getNormalizationParams(), document.getId());
    }

    /**
     * 加载当前激活模型文档。
     *
     * @return 激活模型文档
     */
    @Cacheable(cacheNames = CacheNames.ACTIVE_MODEL, key = "#root.target.getModelName()",
            unless = "#result == null")
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
        String modelVersion = properties.getProperty("modelVersion");
        if (MODEL_VERSION.equals(modelVersion)) {
            validateRankingMetadata(properties);
            return;
        }
        if (V4_MODEL_VERSION.equals(modelVersion) || LEGACY_MODEL_VERSION.equals(modelVersion)) {
            validateLegacyMetadata(properties);
            return;
        }
        throw new IllegalStateException("共享 LSTM 元数据不兼容: modelVersion=" + modelVersion);
    }

    private void validateRankingMetadata(Properties properties) {
        requireMetadata(properties, "featureVersion", RankingLstmDataPreprocessor.FEATURE_VERSION);
        requireMetadata(properties, "labelVersion", RankingLstmDataPreprocessor.LABEL_VERSION);
        requireMetadata(properties, "splitPolicyVersion", RankingLstmDataPreprocessor.SPLIT_POLICY_VERSION);
        requireMetadata(properties, "costModelVersion", RankingLstmDataPreprocessor.COST_MODEL_VERSION);
        requireMetadata(properties, "outputSchemaVersion", OUTPUT_SCHEMA_VERSION);
        requireMetadata(properties, "sequenceLength", String.valueOf(config.getSequenceLength()));
        requireMetadata(properties, "inputSize", String.valueOf(RankingLstmDataPreprocessor.FEATURE_COUNT));
        requireMetadata(properties, "outputSize", String.valueOf(RankingLSTMModel.OUTPUT_SIZE));
        requireOneOfMetadata(properties, "architectureType",
                LstmArchitectureType.RANKING_LSTM.name(),
                LstmArchitectureType.DUAL_ATTENTION_RANKING_LSTM.name(),
                LstmArchitectureType.MASTER_TRANSFORMER.name());
        requireMetadata(properties, "lossVersion", RANKING_LOSS_VERSION);
        requireMetadata(properties, "targetReturnScale", String.valueOf(config.getTargetReturnScale()));
        requirePositiveDoubleMetadata(properties, "probabilityTemperature");
        requirePositiveIntegerMetadata(properties, "walkForwardFoldCount");
        if (LstmArchitectureType.MASTER_TRANSFORMER.name()
                .equals(properties.getProperty("architectureType"))) {
            requirePositiveIntegerMetadata(properties, "crossSectionBatchSize");
        }
    }

    private void validateLegacyMetadata(Properties properties) {
        requireMetadata(properties, "featureVersion", LstmDataPreprocessor.FEATURE_VERSION);
        requireMetadata(properties, "sequenceLength", String.valueOf(60));
        requireMetadata(properties, "inputSize", String.valueOf(LstmDataPreprocessor.FEATURE_COUNT));
        requireMetadata(properties, "outputSize", String.valueOf(StockLSTMModel.OUTPUT_SIZE));
        requireMetadata(properties, "hashAlgorithm", "java-string-hashcode-floor-mod-v1");
        requireOneOfMetadata(properties, "lossVersion", LOSS_VERSION, LEGACY_LOSS_VERSION);
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

    private String buildMetadata(
            LstmDataPreprocessor.ProcessedData data,
            int epoch,
            double probabilityTemperature,
            LstmArchitectureType architectureType,
            int crossSectionBatchSize) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("modelVersion", MODEL_VERSION);
        properties.setProperty("featureVersion", data.getFeatureVersion());
        properties.setProperty("labelVersion", RankingLstmDataPreprocessor.LABEL_VERSION);
        properties.setProperty("splitPolicyVersion", RankingLstmDataPreprocessor.SPLIT_POLICY_VERSION);
        properties.setProperty("costModelVersion", RankingLstmDataPreprocessor.COST_MODEL_VERSION);
        properties.setProperty("outputSchemaVersion", OUTPUT_SCHEMA_VERSION);
        properties.setProperty("architectureType", architectureType.name());
        properties.setProperty("randomSeed", String.valueOf(config.getRandomSeed()));
        properties.setProperty("sequenceLength", String.valueOf(data.getSequenceLength()));
        properties.setProperty("inputSize", String.valueOf(data.getFeatureCount()));
        properties.setProperty("outputSize", String.valueOf(RankingLSTMModel.OUTPUT_SIZE));
        properties.setProperty("crossSectionBatchSize", String.valueOf(crossSectionBatchSize));
        properties.setProperty("stockDictionarySize", String.valueOf(data.getStockDictionary().size()));
        properties.setProperty("stockEmbeddingSize", String.valueOf(config.getStockEmbeddingSize()));
        properties.setProperty("industryDictionarySize", String.valueOf(data.getIndustryDictionary().size()));
        properties.setProperty("industryEmbeddingSize", String.valueOf(config.getIndustryEmbeddingSize()));
        properties.setProperty("marketDictionarySize", String.valueOf(data.getMarketDictionary().size()));
        properties.setProperty("marketEmbeddingSize", String.valueOf(config.getMarketEmbeddingSize()));
        properties.setProperty("stockDictionaryJson", objectMapper.writeValueAsString(data.getStockDictionary()));
        properties.setProperty("industryDictionaryJson", objectMapper.writeValueAsString(data.getIndustryDictionary()));
        properties.setProperty("marketDictionaryJson", objectMapper.writeValueAsString(data.getMarketDictionary()));
        properties.setProperty("featureMeansJson", objectMapper.writeValueAsString(data.getFeatureMeans()));
        properties.setProperty("featureStdDeviationsJson",
                objectMapper.writeValueAsString(data.getFeatureStdDeviations()));
        properties.setProperty("lossVersion", RANKING_LOSS_VERSION);
        properties.setProperty("hiddenSize", String.valueOf(config.getHiddenSize()));
        properties.setProperty("numLayers", String.valueOf(config.getNumLayers()));
        properties.setProperty("targetReturnScale", String.valueOf(data.getTargetReturnScale()));
        properties.setProperty("costThreshold", String.valueOf(config.getCostThreshold()));
        properties.setProperty("probabilityTemperature", String.valueOf(probabilityTemperature));
        properties.setProperty("walkForwardFoldCount", String.valueOf(
                data.getWalkForwardFolds() == null ? 0 : data.getWalkForwardFolds().size()));
        properties.setProperty("strictIsolationFoldCount", String.valueOf(
                data.getStrictIsolationFolds() == null ? 0 : data.getStrictIsolationFolds().size()));
        properties.setProperty("trainStartDate", String.valueOf(data.getTrainStartDate()));
        properties.setProperty("trainEndDate", String.valueOf(data.getTrainEndDate()));
        properties.setProperty("validationStartDate", String.valueOf(data.getValidationStartDate()));
        properties.setProperty("validationEndDate", String.valueOf(data.getValidationEndDate()));
        properties.setProperty("testStartDate", String.valueOf(data.getTestStartDate()));
        properties.setProperty("testEndDate", String.valueOf(data.getTestEndDate()));
        properties.setProperty("epoch", String.valueOf(epoch));
        properties.setProperty("createdAt", LocalDateTime.now().toString());
        properties.setProperty("modelName", getModelName());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            properties.store(output, "stock-trading shared LSTM metadata");
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String buildTrainingConfigJson(LstmArchitectureType architectureType) throws IOException {
        return objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("architectureType", architectureType.name()),
                Map.entry("randomSeed", config.getRandomSeed()),
                Map.entry("inputSize", config.getInputSize()), Map.entry("hiddenSize", config.getHiddenSize()),
                Map.entry("numLayers", config.getNumLayers()), Map.entry("dropout", config.getDropout()),
                Map.entry("sequenceLength", config.getSequenceLength()),
                Map.entry("stockEmbeddingSize", config.getStockEmbeddingSize()),
                Map.entry("industryEmbeddingSize", config.getIndustryEmbeddingSize()),
                Map.entry("marketEmbeddingSize", config.getMarketEmbeddingSize()),
                Map.entry("costThreshold", config.getCostThreshold()),
                Map.entry("purgeTradingDays", config.getPurgeTradingDays()),
                Map.entry("embargoTradingDays", config.getEmbargoTradingDays()),
                Map.entry("learningRate", config.getLearningRate()), Map.entry("batchSize", config.getBatchSize()),
                Map.entry("epochs", config.getEpochs())));
    }

    private void validateParameterIntegrity(LstmModelDocument document) {
        byte[] params = loadParameterBytes(document);
        if (params.length == 0) {
            throw new IllegalStateException("共享 LSTM 模型参数为空");
        }
        if (!sha256(params).equalsIgnoreCase(document.getParameterSha256())) {
            throw new IllegalStateException("共享 LSTM 模型参数摘要不匹配");
        }
    }

    private byte[] loadParameterBytes(LstmModelDocument document) {
        if (document.getParameterFileId() == null || document.getParameterFileId().isBlank()) {
            throw new IllegalStateException("共享 LSTM 模型缺少 GridFS 参数文件标识");
        }
        try {
            GridFSFile file = gridFsTemplate.findOne(Query.query(Criteria.where("_id")
                    .is(new org.bson.types.ObjectId(document.getParameterFileId()))));
            if (file == null) {
                throw new IllegalStateException("共享 LSTM GridFS 参数文件不存在: " + document.getParameterFileId());
            }
            GridFsResource resource = gridFsTemplate.getResource(file);
            try (var input = resource.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("读取共享 LSTM GridFS 参数失败", exception);
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

    private void requirePositiveDoubleMetadata(Properties properties, String key) {
        try {
            if (Double.parseDouble(properties.getProperty(key)) <= 0D) {
                throw new NumberFormatException("value must be positive");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("共享 LSTM 元数据无效: " + key, exception);
        }
    }

    private void requirePositiveIntegerMetadata(Properties properties, String key) {
        try {
            if (Integer.parseInt(properties.getProperty(key)) <= 0) {
                throw new NumberFormatException("value must be positive");
            }
        } catch (RuntimeException exception) {
            throw new IllegalStateException("共享 LSTM 元数据无效: " + key, exception);
        }
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
// AI_GENERATE_END-------------------
