// AI_GENERATE_START ------
package com.stock.model.service;

import com.stock.model.config.TemporalEnsembleConfig;
import com.stock.model.dto.LstmPredictionResultDto;
import com.stock.model.entity.LstmModelDocument;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.entity.TemporalModelShadowPredictionDocument;
import com.stock.model.algorithm.LstmArchitectureType;
import com.stock.model.repository.LstmModelRepository;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import com.stock.model.repository.TemporalModelShadowPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * MASTER 候选模型影子预测、结果对齐和后验评估服务。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MasterShadowPredictionService {

    private static final List<String> USABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");

    private final TemporalEnsembleConfig config;
    private final LstmInferenceService inferenceService;
    private final LstmModelRepository modelRepository;
    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final TemporalModelShadowPredictionRepository shadowRepository;
    private final TemporalModelEnsembleService ensembleService;

    /**
     * 对最近生产预测批次执行一次 MASTER 影子预测并刷新动态权重。
     *
     * @throws IllegalStateException 模型契约不一致或推理覆盖低于最低要求时抛出
     */
    public synchronized void runShadowCycle() {
        if (!config.isMasterShadowEnabled()) {
            log.info("MASTER 影子预测配置关闭，本次任务跳过");
            return;
        }
        String masterModelId = requireMasterModelId();
        if (masterModelId == null) {
            log.info("当前没有 MASTER 候选模型，本次影子任务跳过");
            return;
        }
        evaluatePendingShadowPredictions(masterModelId);
        ModelPredictionBatchDocument baseline = batchRepository
                .findFirstByStatusInOrderByCreatedAtDesc(USABLE_STATUSES);
        if (baseline == null) {
            log.info("当前没有可用于 MASTER 影子预测的生产基线批次，本次任务跳过");
            return;
        }
        if (!shadowRepository.existsByBaselineBatchIdAndModelId(baseline.getId(), masterModelId)) {
            createShadowPredictions(baseline, masterModelId);
        }
        evaluatePendingShadowPredictions(masterModelId);
        ensembleService.refreshState();
    }

    private void createShadowPredictions(ModelPredictionBatchDocument baseline, String masterModelId) {
        LstmModelDocument masterModel = modelRepository.findById(masterModelId)
                .orElseThrow(() -> new IllegalStateException("MASTER 候选模型不存在: " + masterModelId));
        LstmModelDocument baselineModel = modelRepository.findById(baseline.getModelId())
                .orElseThrow(() -> new IllegalStateException("生产基线模型不存在: " + baseline.getModelId()));
        requireCompatibleModels(masterModel, baselineModel);
        List<ModelPredictionDocument> baselineItems = predictionRepository.findByBatchId(baseline.getId());
        List<String> stockCodes = baselineItems.stream()
                .map(ModelPredictionDocument::getStockCode)
                .filter(code -> code != null && !code.isBlank())
                .sorted()
                .toList();
        if (stockCodes.isEmpty() || stockCodes.size() != baseline.getSuccessCount()) {
            throw new IllegalStateException("生产基线批次明细不完整: batchId=" + baseline.getId());
        }
        Map<String, LstmPredictionResultDto> results = new LinkedHashMap<>();
        inferenceService.predictNextBatchChunks(
                stockCodes, masterModelId, baseline.getBaseTradeDate(),
                stockCodes.size(),
                (ignored, chunk) -> results.putAll(chunk));
        double coverageRate = stockCodes.isEmpty() ? 0D : (double) results.size() / stockCodes.size();
        if (coverageRate < 0.95D) {
            throw new IllegalStateException("MASTER 影子预测覆盖率低于 95%: expected="
                    + stockCodes.size() + ", actual=" + results.size());
        }
        results.forEach(this::validatePredictionResult);
        Map<String, Double> percentiles = percentiles(results);
        LocalDateTime now = LocalDateTime.now();
        List<TemporalModelShadowPredictionDocument> documents = new ArrayList<>();
        for (String stockCode : results.keySet()) {
            LstmPredictionResultDto result = results.get(stockCode);
            TemporalModelShadowPredictionDocument document = new TemporalModelShadowPredictionDocument();
            document.setModelId(masterModelId);
            document.setModelVersion(masterModel.getModelVersion());
            document.setBaselineBatchId(baseline.getId());
            document.setStockCode(stockCode);
            document.setBaseTradeDate(result.getBaseTradeDate());
            document.setTargetTradeDate(baseline.getTargetTradeDate());
            document.setRankScore(result.getRankScore());
            document.setRankPercentile(percentiles.get(stockCode));
            document.setExpectedExcessReturn(result.getExpectedExcessReturn());
            document.setUpProbability(result.getCalibratedUpProbability());
            document.setDownsideQuantile(result.getDownsideQuantile());
            document.setEvaluationStatus("PENDING");
            document.setPredictedAt(now);
            documents.add(document);
        }
        shadowRepository.saveAll(documents);
        log.info("MASTER 影子预测完成: baselineBatchId={}, modelId={}, count={}",
                baseline.getId(), masterModelId, documents.size());
    }

    private void evaluatePendingShadowPredictions(String masterModelId) {
        List<TemporalModelShadowPredictionDocument> shadows = shadowRepository
                .findByModelIdAndEvaluationStatusOrderByPredictedAtAsc(
                        masterModelId, "PENDING", PageRequest.of(0, 20000));
        if (shadows.isEmpty()) {
            return;
        }
        List<String> batchIds = shadows.stream()
                .map(TemporalModelShadowPredictionDocument::getBaselineBatchId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<String, ModelPredictionDocument> baselineItems = predictionRepository.findByBatchIdIn(batchIds)
                .stream().collect(java.util.stream.Collectors.toMap(
                        item -> item.getBatchId() + "|" + item.getStockCode(), item -> item,
                        (left, right) -> left));
        LocalDateTime now = LocalDateTime.now();
        boolean changed = false;
        for (TemporalModelShadowPredictionDocument shadow : shadows) {
            ModelPredictionDocument actual = baselineItems.get(
                    shadow.getBaselineBatchId() + "|" + shadow.getStockCode());
            if (actual == null || actual.getActualReturn() == null
                    || !Double.isFinite(actual.getActualReturn())) {
                continue;
            }
            shadow.setActualReturn(actual.getActualReturn());
            shadow.setEvaluationStatus("EVALUATED");
            shadow.setEvaluatedAt(now);
            changed = true;
        }
        if (changed) {
            shadowRepository.saveAll(shadows);
        }
    }

    private void requireCompatibleModels(LstmModelDocument master, LstmModelDocument baseline) {
        Properties properties = loadProperties(master.getNormalizationParams());
        Properties baselineProperties = loadProperties(baseline.getNormalizationParams());
        if (!LstmArchitectureType.MASTER_TRANSFORMER.name()
                .equals(properties.getProperty("architectureType"))) {
            throw new IllegalStateException("指定候选模型不是 MASTER_TRANSFORMER");
        }
        if (master.getFeatureVersion() == null
                || !master.getFeatureVersion().equals(baseline.getFeatureVersion())) {
            throw new IllegalStateException("MASTER 与 Ranking LSTM 特征契约版本不一致");
        }
        requireSameProperty(properties, baselineProperties, "labelVersion", "标签版本");
        requireSameProperty(properties, baselineProperties, "splitPolicyVersion", "时间切分版本");
        requireSameProperty(properties, baselineProperties, "costModelVersion", "成本模型版本");
        requireSameProperty(properties, baselineProperties, "outputSchemaVersion", "输出契约版本");
        requireSameProperty(properties, baselineProperties, "targetReturnScale", "收益缩放参数");
    }

    private void requireSameProperty(
            Properties master, Properties baseline, String key, String displayName) {
        String masterValue = master.getProperty(key);
        String baselineValue = baseline.getProperty(key);
        if (masterValue == null || !masterValue.equals(baselineValue)) {
            throw new IllegalStateException("MASTER 与 Ranking LSTM " + displayName + "不一致");
        }
    }

    private Properties loadProperties(String text) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
            return properties;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("MASTER 模型元数据无法解析", exception);
        }
    }

    private Map<String, Double> percentiles(Map<String, LstmPredictionResultDto> results) {
        List<Map.Entry<String, LstmPredictionResultDto>> ordered = results.entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> entry.getValue().getRankScore()))
                .toList();
        Map<String, Double> values = new LinkedHashMap<>();
        int denominator = Math.max(1, ordered.size() - 1);
        int position = 0;
        while (position < ordered.size()) {
            int end = position + 1;
            double score = ordered.get(position).getValue().getRankScore();
            while (end < ordered.size()
                    && Double.compare(ordered.get(end).getValue().getRankScore(), score) == 0) {
                end++;
            }
            double percentile = ((position + end - 1D) / 2D) / denominator;
            for (int index = position; index < end; index++) {
                values.put(ordered.get(index).getKey(), percentile);
            }
            position = end;
        }
        return values;
    }

    private void validatePredictionResult(String stockCode, LstmPredictionResultDto result) {
        if (result == null || !finite(result.getRankScore())
                || !finite(result.getExpectedExcessReturn())
                || !probability(result.getCalibratedUpProbability())
                || !probability(result.getDownsideQuantile())) {
            throw new IllegalStateException("MASTER 影子预测输出契约无效: stockCode=" + stockCode);
        }
    }

    private boolean finite(Double value) {
        return value != null && Double.isFinite(value);
    }

    private boolean probability(Double value) {
        return finite(value) && value >= 0D && value <= 1D;
    }

    private String requireMasterModelId() {
        String modelId = ensembleService.currentState().getMasterModelId();
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        return modelId.trim();
    }
}
// AI_GENERATE_END ------
