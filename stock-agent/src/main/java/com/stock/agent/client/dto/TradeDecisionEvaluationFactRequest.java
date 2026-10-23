// AI_GENERATE_START --
package com.stock.agent.client.dto;

import java.time.LocalDate;

/**
 * 请求 Backend 查询单个交易建议评估事实。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param targetTradeDate 目标交易日
 * @author mwangli
 * @since 2026-10-23
 */
public record TradeDecisionEvaluationFactRequest(
        String candidateBatchId, String stockCode, LocalDate targetTradeDate) {
}
// AI_GENERATE_END --
