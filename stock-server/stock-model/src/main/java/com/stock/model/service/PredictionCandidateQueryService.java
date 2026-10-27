// AI_GENERATE_START-------------
package com.stock.model.service;

import com.stock.model.dto.PredictionCandidateItemDto;

import com.stock.model.dto.PredictionCandidateBatchDto;
import com.stock.model.entity.ModelPredictionBatchDocument;
import com.stock.model.entity.ModelPredictionDocument;
import com.stock.model.repository.ModelPredictionBatchRepository;
import com.stock.model.repository.ModelPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 生产策略候选预测批次查询服务。
 * 只返回已完成且明细数量与批次统计一致的每日预测结果。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Service
@RequiredArgsConstructor
public class PredictionCandidateQueryService {

    private static final List<String> USABLE_STATUSES = List.of("COMPLETED", "PARTIAL", "EVALUATED");

    private final ModelPredictionBatchRepository batchRepository;
    private final ModelPredictionRepository predictionRepository;
    private final TemporalModelEnsembleService temporalModelEnsembleService;

    /**
     * 查询指定目标交易日最新可用预测批次。
     *
     * @param targetTradeDate 目标交易日
     * @return 可用于策略排序的预测批次
     * @throws IllegalStateException 批次不存在或明细不完整时抛出
     */
    public PredictionCandidateBatchDto getLatestUsableBatch(LocalDate targetTradeDate) {
        return findLatestUsableBatch(targetTradeDate)
                .orElseThrow(() -> new IllegalStateException(
                        "目标交易日没有可用预测批次: " + targetTradeDate));
    }

    /**
     * 查询指定目标交易日最新可用预测批次，缺失时返回空结果。
     * 适用于只读展示场景；真实交易执行仍应调用严格查询方法。
     *
     * @param targetTradeDate 目标交易日
     * @return 可用于策略排序的预测批次
     * @throws IllegalStateException 批次存在但明细不完整时抛出
     */
    public Optional<PredictionCandidateBatchDto> findLatestUsableBatch(LocalDate targetTradeDate) {
        ModelPredictionBatchDocument batch = batchRepository
                .findFirstByTargetTradeDateAndStatusInOrderByCreatedAtDesc(targetTradeDate, USABLE_STATUSES);
        if (batch == null) {
            return Optional.empty();
        }
        List<ModelPredictionDocument> predictions = predictionRepository.findByBatchId(batch.getId());
        if (predictions.isEmpty() || predictions.size() != batch.getSuccessCount()) {
            throw new IllegalStateException("预测批次明细数量与成功数量不一致: batchId=" + batch.getId());
        }
        List<PredictionCandidateItemDto> items = predictions.stream()
                .filter(item -> hasValidPrediction(item, batch))
                .map(this::toItem)
                .toList();
        if (items.size() != predictions.size()) {
            throw new IllegalStateException("预测批次包含无效或版本不一致的预测明细: batchId=" + batch.getId());
        }
        PredictionCandidateBatchDto baseline = PredictionCandidateBatchDto.builder()
                .batchId(batch.getId())
                .modelId(batch.getModelId())
                .modelVersion(batch.getModelVersion())
                .baseTradeDate(batch.getBaseTradeDate())
                .targetTradeDate(batch.getTargetTradeDate())
                .informationCutoffAt(batch.getInformationCutoffAt())
                .tradingEligibility(batch.getTradingEligibility())
                .tradingEligibilityReason(batch.getTradingEligibilityReason())
                .items(items)
                .build();
        return Optional.of(temporalModelEnsembleService.apply(baseline));
    }

    /**
     * 校验预测批次是否允许进入真实交易写入链路。
     *
     * @param predictionBatchId 预测批次标识
     * @throws IllegalStateException 批次不存在或未通过真实交易门禁时抛出
     */
    public void requireTradingEligible(String predictionBatchId) {
        ModelPredictionBatchDocument batch = batchRepository.findById(predictionBatchId)
                .orElseThrow(() -> new IllegalStateException("预测批次不存在: " + predictionBatchId));
        if (!"TRADE_ELIGIBLE".equals(batch.getTradingEligibility())) {
            throw new IllegalStateException("预测批次未通过交易门禁: "
                    + (batch.getTradingEligibilityReason() == null
                    ? batch.getTradingEligibility() : batch.getTradingEligibilityReason()));
        }
    }

    private boolean hasValidPrediction(
            ModelPredictionDocument item, ModelPredictionBatchDocument batch) {
        return item.getStockCode() != null && !item.getStockCode().isBlank()
                && batch.getModelId() != null && batch.getModelId().equals(item.getModelId())
                && batch.getModelVersion() != null && batch.getModelVersion().equals(item.getModelVersion())
                && batch.getBaseTradeDate() != null && batch.getBaseTradeDate().equals(item.getBaseTradeDate())
                && batch.getTargetTradeDate() != null && batch.getTargetTradeDate().equals(item.getTargetTradeDate())
                && item.getModelId() != null && !item.getModelId().isBlank();
    }

    private PredictionCandidateItemDto toItem(ModelPredictionDocument item) {
        return PredictionCandidateItemDto.builder()
                .stockCode(item.getStockCode())
                .stockName(item.getStockName())
                .currentPrice(item.getBaseClosePrice())
                .predictedReturn(item.getPredictedReturn())
                .directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk())
                .predictedReturnPercentile(item.getPredictedReturnPercentile())
                .directionProbabilityPercentile(item.getDirectionProbabilityPercentile())
                .rankScore(item.getRankScore())
                .crossSectionPercentile(item.getCrossSectionPercentile())
                .expectedExcessReturn(item.getExpectedExcessReturn())
                .calibratedUpProbability(item.getCalibratedUpProbability())
                .downsideQuantile(item.getDownsideQuantile())
                .build();
    }

}
// AI_GENERATE_END-------------
