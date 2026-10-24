// AI_GENERATE_START---
package com.stock.platform.dto;

import java.util.List;

/**
 * 交易建议批量评估事实请求。
 *
 * @param items 待查询项目，最多 500 条
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionEvaluationFactsRequest(
        List<InternalTradeDecisionEvaluationFactRequest> items) {
}
// AI_GENERATE_END---
