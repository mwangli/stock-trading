// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDate;

/**
 * Backend 交易建议评估 HTTP 请求项。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param targetTradeDate 目标交易日
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendTradeDecisionEvaluationFactRequest(
        String candidateBatchId, String stockCode, LocalDate targetTradeDate) {
}
// AI_GENERATE_END --
