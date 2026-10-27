// AI_GENERATE_START--
package com.stock.platform.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Agent 使用的候选批次摘要。
 *
 * @param candidateBatchId 候选批次标识
 * @param predictionBatchId 预测批次标识
 * @param strategyVersion 策略版本
 * @param targetTradeDate 目标交易日
 * @param items 候选股票列表
 * @param availabilityReason 可用性说明
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalPredictionCandidateResponse(
        String candidateBatchId, String predictionBatchId, String strategyVersion,
        LocalDate targetTradeDate, List<InternalPredictionCandidateItem> items,
        String availabilityReason) {
}
// AI_GENERATE_END--
