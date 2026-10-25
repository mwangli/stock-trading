// AI_GENERATE_START-----
package com.stock.model.service;

import com.stock.model.dto.PredictionAbilityResponse;
import com.stock.model.dto.PredictionBatchPageResponse;
import com.stock.model.dto.PredictionBatchResponse;
import com.stock.model.dto.PredictionResultPageResponse;
import com.stock.model.dto.PredictionResultResponse;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * 模型预测只读查询服务。
 *
 * 负责预测批次、预测明细和模型能力的分页查询及响应对象转换。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class ModelPredictionQueryService {

    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;

    /**
     * 分页查询预测批次。
     *
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测批次分页响应
     */
    public PredictionBatchPageResponse listBatches(int current, int pageSize) {
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Sort predictionWindowSort = Sort.by(
                Sort.Order.desc("targetTradeDate"),
                Sort.Order.desc("baseTradeDate"),
                Sort.Order.desc("id"));
        Page<ModelPredictionBatchDocument> page = batchRepository.findAll(PageRequest.of(
                safeCurrent - 1, safePageSize, predictionWindowSort));
        return PredictionBatchPageResponse.builder()
                .items(page.getContent().stream().map(this::toBatchItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 分页查询预测值与实际值对比。
     *
     * @param batchId 预测批次 ID；为空时查询最新批次
     * @param evaluationStatus 评估状态筛选
     * @param current 当前页码，从 1 开始
     * @param pageSize 每页大小
     * @return 预测明细分页响应
     */
    public PredictionResultPageResponse listPredictions(
            String batchId, String evaluationStatus, int current, int pageSize) {
        ModelPredictionBatchDocument batch = resolveBatch(batchId);
        int safeCurrent = Math.max(1, current);
        int safePageSize = Math.max(1, Math.min(200, pageSize));
        PageRequest pageable = PageRequest.of(safeCurrent - 1, safePageSize,
                Sort.by(Sort.Direction.DESC, "predictedReturn"));
        Page<ModelPredictionDocument> page = evaluationStatus == null || evaluationStatus.isBlank()
                ? predictionRepository.findByBatchId(batch.getId(), pageable)
                : predictionRepository.findByBatchIdAndEvaluationStatus(
                        batch.getId(), evaluationStatus.trim().toUpperCase(), pageable);
        return PredictionResultPageResponse.builder()
                .items(page.getContent().stream().map(this::toPredictionItem).toList())
                .total(page.getTotalElements())
                .current(safeCurrent)
                .pageSize(safePageSize)
                .build();
    }

    /**
     * 查询指定或最新预测批次的模型能力概要。
     *
     * @param batchId 预测批次 ID；为空时查询最新批次
     * @return 模型能力概要
     */
    public PredictionAbilityResponse getAbility(String batchId) {
        return toAbility(resolveBatch(batchId));
    }

    /**
     * 查询指定批次和股票的预测明细。
     *
     * @param batchId 预测批次标识
     * @param stockCode 股票代码
     * @return 预测明细，不存在时返回 null
     */
    public PredictionResultResponse getPrediction(String batchId, String stockCode) {
        return predictionRepository.findByBatchIdAndStockCode(batchId, stockCode)
                .map(this::toPredictionItem).orElse(null);
    }

    ModelPredictionBatchDocument resolveBatch(String batchId) {
        if (batchId != null && !batchId.isBlank()) {
            return batchRepository.findById(batchId.trim())
                    .orElseThrow(() -> new IllegalArgumentException("预测批次不存在"));
        }
        ModelPredictionBatchDocument latest = batchRepository.findTopByOrderByCreatedAtDesc();
        if (latest == null) {
            throw new IllegalStateException("暂无预测批次");
        }
        return latest;
    }

    private PredictionBatchResponse toBatchItem(ModelPredictionBatchDocument item) {
        return PredictionBatchResponse.builder()
                .id(item.getId()).modelName(item.getModelName()).modelId(item.getModelId())
                .modelVersion(item.getModelVersion()).baseTradeDate(item.getBaseTradeDate())
                .targetTradeDate(item.getTargetTradeDate()).informationCutoffAt(item.getInformationCutoffAt())
                .calendarFallback(item.getCalendarFallback()).status(item.getStatus())
                .totalStocks(item.getTotalStocks()).processedStocks(item.getProcessedStocks())
                .successCount(item.getSuccessCount()).failedCount(item.getFailedCount())
                .evaluatedCount(item.getEvaluatedCount()).noMarketDataCount(item.getNoMarketDataCount())
                .evaluationQueueStatus(item.getEvaluationQueueStatus())
                .evaluationWaitReason(item.getEvaluationWaitReason())
                .evaluationQueuedAt(item.getEvaluationQueuedAt())
                .evaluationStartedAt(item.getEvaluationStartedAt())
                .evaluationFinishedAt(item.getEvaluationFinishedAt())
                .evaluationErrorMessage(item.getEvaluationErrorMessage())
                .coverageRate(item.getCoverageRate()).evaluationRate(item.getEvaluationRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).tradingEligibility(item.getTradingEligibility())
                .tradingEligibilityReason(item.getTradingEligibilityReason())
                .progressMessage(item.getProgressMessage())
                .triggeredBy(item.getTriggeredBy()).startedAt(item.getStartedAt())
                .finishedAt(item.getFinishedAt()).errorMessage(item.getErrorMessage())
                .createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt()).build();
    }

    private PredictionResultResponse toPredictionItem(ModelPredictionDocument item) {
        return PredictionResultResponse.builder()
                .id(item.getId()).batchId(item.getBatchId()).stockCode(item.getStockCode())
                .stockName(item.getStockName()).industryCode(item.getIndustryCode())
                .modelId(item.getModelId()).modelVersion(item.getModelVersion())
                .baseTradeDate(item.getBaseTradeDate()).targetTradeDate(item.getTargetTradeDate())
                .calendarFallback(item.getCalendarFallback())
                .baseClosePrice(item.getBaseClosePrice()).predictedClosePrice(item.getPredictedClosePrice())
                .predictedReturn(item.getPredictedReturn()).directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk())
                .predictedReturnPercentile(item.getPredictedReturnPercentile())
                .directionProbabilityPercentile(item.getDirectionProbabilityPercentile())
                .rankScore(item.getRankScore()).crossSectionPercentile(item.getCrossSectionPercentile())
                .expectedExcessReturn(item.getExpectedExcessReturn())
                .calibratedUpProbability(item.getCalibratedUpProbability())
                .downsideQuantile(item.getDownsideQuantile())
                .architectureType(item.getArchitectureType())
                .featureSchemaVersion(item.getFeatureSchemaVersion())
                .actualClosePrice(item.getActualClosePrice())
                .actualReturn(item.getActualReturn()).returnError(item.getReturnError())
                .absoluteError(item.getAbsoluteError()).directionCorrect(item.getDirectionCorrect())
                .evaluationStatus(item.getEvaluationStatus()).predictedAt(item.getPredictedAt())
                .evaluatedAt(item.getEvaluatedAt()).build();
    }

    private PredictionAbilityResponse toAbility(ModelPredictionBatchDocument item) {
        return PredictionAbilityResponse.builder()
                .batchId(item.getId()).modelId(item.getModelId()).modelVersion(item.getModelVersion())
                .baseTradeDate(item.getBaseTradeDate()).targetTradeDate(item.getTargetTradeDate())
                .predictedCount(item.getSuccessCount()).evaluatedCount(item.getEvaluatedCount())
                .coverageRate(item.getCoverageRate()).evaluationRate(item.getEvaluationRate())
                .directionAccuracy(item.getDirectionAccuracy()).actualUpRate(item.getActualUpRate())
                .constantBaselineAccuracy(item.getConstantBaselineAccuracy())
                .directionAuc(item.getDirectionAuc()).brierScore(item.getBrierScore())
                .brierBaseline(item.getBrierBaseline())
                .expectedCalibrationError(item.getExpectedCalibrationError())
                .calibrationSampleCount(item.getCalibrationSampleCount())
                .directionProbabilityStd(item.getDirectionProbabilityStd())
                .predictedReturnStd(item.getPredictedReturnStd())
                .rollingWindowBatchCount(item.getRollingWindowBatchCount())
                .rollingWindowSampleCount(item.getRollingWindowSampleCount())
                .rollingAverageSpearmanRankIc(item.getRollingAverageSpearmanRankIc())
                .rollingAverageTopDecileReturn(item.getRollingAverageTopDecileReturn())
                .returnMae(item.getReturnMae())
                .returnRmse(item.getReturnRmse()).pearsonIc(item.getPearsonIc())
                .spearmanRankIc(item.getSpearmanRankIc())
                .topDecileAverageReturn(item.getTopDecileAverageReturn())
                .topDecileDirectionAccuracy(item.getTopDecileDirectionAccuracy())
                .highConfidenceAccuracy(item.getHighConfidenceAccuracy())
                .downsideRiskHitRate(item.getDownsideRiskHitRate())
                .abilityLevel(item.getAbilityLevel()).iterationRecommended(item.isIterationRecommended())
                .abilityMessage(item.getAbilityMessage()).tradingEligibility(item.getTradingEligibility())
                .tradingEligibilityReason(item.getTradingEligibilityReason()).build();
    }
}
// AI_GENERATE_END-----
