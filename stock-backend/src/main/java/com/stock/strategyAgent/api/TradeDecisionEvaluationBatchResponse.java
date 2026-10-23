// AI_GENERATE_START --
package com.stock.strategyAgent.api;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 候选批次的 Agent 影子判断评估响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionEvaluationBatchResponse {

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 评估结果总数。 */
    int total;

    /** Agent 有无参与的批次级反事实摘要。 */
    TradeDecisionEvaluationSummaryResponse summary;

    /** 候选批次评估明细。 */
    List<TradeDecisionEvaluationResponse> items;
}
// AI_GENERATE_END --
