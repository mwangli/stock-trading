// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Backend 候选批次 HTTP 响应。
 *
 * @param candidateBatchId 候选批次标识
 * @param predictionBatchId 预测批次标识
 * @param strategyVersion 策略版本
 * @param targetTradeDate 目标交易日
 * @param items 候选股票列表
 * @param availabilityReason 可用性说明
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendPredictionCandidateResponse(
        String candidateBatchId, String predictionBatchId, String strategyVersion,
        LocalDate targetTradeDate, List<BackendPredictionCandidateItemResponse> items,
        String availabilityReason) {
}
// AI_GENERATE_END --
