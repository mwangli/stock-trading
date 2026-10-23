// AI_GENERATE_START --
package com.stock.agent.client.dto;

import java.util.List;

/**
 * 请求 Backend 批量查询交易建议评估事实。
 *
 * @param items 待查询项目
 * @author mwangli
 * @since 2026-10-23
 */
public record TradeDecisionEvaluationFactsRequest(List<TradeDecisionEvaluationFactRequest> items) {
}
// AI_GENERATE_END --
