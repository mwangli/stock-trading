// AI_GENERATE_START --
package com.stock.modelService.service;

import com.stock.modelService.domain.dto.PredictionCandidateBatchDto;
import com.stock.modelService.domain.entity.ModelPredictionBatchDocument;
import com.stock.modelService.domain.entity.ModelPredictionDocument;
import com.stock.modelService.persistence.ModelPredictionBatchRepository;
import com.stock.modelService.persistence.ModelPredictionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

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

    /**
     * 查询指定目标交易日最新可用预测批次。
     *
     * @param targetTradeDate 目标交易日
     * @return 可用于策略排序的预测批次
     * @throws IllegalStateException 批次不存在或明细不完整时抛出
     */
    public PredictionCandidateBatchDto getLatestUsableBatch(LocalDate targetTradeDate) {
        ModelPredictionBatchDocument batch = batchRepository
                .findFirstByTargetTradeDateAndStatusInOrderByCreatedAtDesc(targetTradeDate, USABLE_STATUSES);
        if (batch == null) {
            throw new IllegalStateException("目标交易日没有可用预测批次: " + targetTradeDate);
        }
        List<ModelPredictionDocument> predictions = predictionRepository.findByBatchId(batch.getId());
        if (predictions.isEmpty() || predictions.size() != batch.getSuccessCount()) {
            throw new IllegalStateException("预测批次明细数量与成功数量不一致: batchId=" + batch.getId());
        }
        List<PredictionCandidateBatchDto.PredictionCandidateItemDto> items = predictions.stream()
                .filter(item -> hasValidPrediction(item, batch))
                .map(this::toItem)
                .toList();
        if (items.size() != predictions.size()) {
            throw new IllegalStateException("预测批次包含无效或版本不一致的预测明细: batchId=" + batch.getId());
        }
        return PredictionCandidateBatchDto.builder()
                .batchId(batch.getId())
                .modelId(batch.getModelId())
                .modelVersion(batch.getModelVersion())
                .baseTradeDate(batch.getBaseTradeDate())
                .targetTradeDate(batch.getTargetTradeDate())
                .informationCutoffAt(batch.getInformationCutoffAt())
                .items(items)
                .build();
    }

    private boolean hasValidPrediction(
            ModelPredictionDocument item, ModelPredictionBatchDocument batch) {
        return item.getStockCode() != null && !item.getStockCode().isBlank()
                && batch.getModelId() != null && batch.getModelId().equals(item.getModelId())
                && batch.getModelVersion() != null && batch.getModelVersion().equals(item.getModelVersion())
                && batch.getBaseTradeDate() != null && batch.getBaseTradeDate().equals(item.getBaseTradeDate())
                && batch.getTargetTradeDate() != null && batch.getTargetTradeDate().equals(item.getTargetTradeDate())
                && item.getPredictedReturn() != null && Double.isFinite(item.getPredictedReturn())
                && item.getDirectionProbability() != null && Double.isFinite(item.getDirectionProbability())
                && item.getDownsideRisk() != null && Double.isFinite(item.getDownsideRisk());
    }

    private PredictionCandidateBatchDto.PredictionCandidateItemDto toItem(ModelPredictionDocument item) {
        return PredictionCandidateBatchDto.PredictionCandidateItemDto.builder()
                .stockCode(item.getStockCode())
                .stockName(item.getStockName())
                .predictedReturn(item.getPredictedReturn())
                .directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk())
                .build();
    }
}
// AI_GENERATE_END --
