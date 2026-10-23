// AI_GENERATE_START -
package com.stock.backend.internal.dto;

import java.time.LocalDate;

/**
 * 单个影子决策评估事实请求。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param targetTradeDate 目标交易日
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionEvaluationFactRequest(
        String candidateBatchId, String stockCode, LocalDate targetTradeDate) {
}
// AI_GENERATE_END -
