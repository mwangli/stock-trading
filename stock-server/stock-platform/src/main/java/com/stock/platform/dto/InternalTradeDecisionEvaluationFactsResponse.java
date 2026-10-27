// AI_GENERATE_START---
package com.stock.platform.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易建议批量评估事实响应。
 *
 * @param latestMarketDate 本地最新行情日期
 * @param items 逐候选评估事实
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionEvaluationFactsResponse(
        LocalDate latestMarketDate, List<InternalTradeDecisionEvaluationFactResponse> items) {
}
// AI_GENERATE_END---
