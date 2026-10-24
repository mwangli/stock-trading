// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Backend 交易建议批量评估事实 HTTP 响应。
 *
 * @param latestMarketDate 本地最新行情日期
 * @param items 逐候选评估事实
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendTradeDecisionEvaluationFactsResponse(
        LocalDate latestMarketDate, List<BackendTradeDecisionEvaluationFactResponse> items) {
}
// AI_GENERATE_END --
