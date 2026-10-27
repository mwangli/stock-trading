// AI_GENERATE_START ----
package com.stock.agent.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Backend 返回的候选批次摘要。
 *
 * @param predictionBatchId 预测批次标识
 * @param modelVersion 模型版本
 * @param targetTradeDate 目标交易日
 * @param items 候选列表
 * @param availabilityReason 可用性说明
 * @author mwangli
 * @since 2026-10-23
 */
public record PredictionCandidateFact(
        String predictionBatchId, String modelVersion,
        LocalDate targetTradeDate, List<PredictionCandidateItemFact> items,
        String availabilityReason) {
}
// AI_GENERATE_END ---
