// AI_GENERATE_START ---
package com.stock.modelService.service;

import com.stock.modelService.domain.dto.PredictionCandidateBatchDto;
import com.stock.modelService.domain.entity.ModelPredictionBatchDocument;
import com.stock.modelService.domain.entity.ModelPredictionDocument;
import com.stock.modelService.persistence.ModelPredictionBatchRepository;
import com.stock.modelService.persistence.ModelPredictionRepository;
import com.stock.strategyAnalysis.config.BacktestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
    private final BacktestProperties backtestProperties;

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
        if (!"TRADE_ELIGIBLE".equals(batch.getTradingEligibility())) {
            throw new IllegalStateException("预测批次未通过交易门禁: "
                    + (batch.getTradingEligibilityReason() == null
                    ? batch.getTradingEligibility() : batch.getTradingEligibilityReason()));
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
                .tradingEligibility(batch.getTradingEligibility())
                .tradingEligibilityReason(batch.getTradingEligibilityReason())
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
                && item.getDirectionProbability() >= 0D && item.getDirectionProbability() <= 1D
                && item.getDownsideRisk() != null && Double.isFinite(item.getDownsideRisk())
                && item.getPredictedReturnPercentile() != null
                && Double.isFinite(item.getPredictedReturnPercentile())
                && item.getDirectionProbabilityPercentile() != null
                && Double.isFinite(item.getDirectionProbabilityPercentile());
    }

    private PredictionCandidateBatchDto.PredictionCandidateItemDto toItem(ModelPredictionDocument item) {
        return PredictionCandidateBatchDto.PredictionCandidateItemDto.builder()
                .stockCode(item.getStockCode())
                .stockName(item.getStockName())
                .currentPrice(item.getBaseClosePrice())
                .predictedReturn(item.getPredictedReturn())
                .estimatedNetExpectedReturn(estimateNetExpectedReturn(item.getPredictedReturn()))
                .directionProbability(item.getDirectionProbability())
                .downsideRisk(item.getDownsideRisk())
                .predictedReturnPercentile(item.getPredictedReturnPercentile())
                .directionProbabilityPercentile(item.getDirectionProbabilityPercentile())
                .build();
    }

    /**
     * 估算扣除交易成本和安全边际后的单日净预期收益。
     *
     * 该值用于候选过滤和审计展示，不等同于真实成交收益；最低佣金、成交失败、
     * 涨跌停和部分成交需要在后续模拟交易引擎中按订单金额进一步计算。
     *
     * @param predictedReturn 模型预测收益率
     * @return 成本后净预期收益率
     */
    private Double estimateNetExpectedReturn(Double predictedReturn) {
        if (predictedReturn == null || !Double.isFinite(predictedReturn)) {
            return null;
        }
        BigDecimal slippageRate = backtestProperties.getSlippageBps()
                .multiply(BigDecimal.valueOf(2D))
                .divide(BigDecimal.valueOf(10000D), 10, java.math.RoundingMode.HALF_UP);
        BigDecimal roundTripVariableCost = backtestProperties.getCommissionRate().multiply(BigDecimal.valueOf(2D))
                .add(backtestProperties.getTransferFeeRate().multiply(BigDecimal.valueOf(2D)))
                .add(backtestProperties.getStampDutyRate())
                .add(slippageRate)
                .add(backtestProperties.getCandidateSafetyMarginRate());
        return BigDecimal.valueOf(predictedReturn).subtract(roundTripVariableCost).doubleValue();
    }
}
// AI_GENERATE_END ---
