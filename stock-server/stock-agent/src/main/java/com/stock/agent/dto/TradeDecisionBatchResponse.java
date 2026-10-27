// AI_GENERATE_START------
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * 候选批次交易建议响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionBatchResponse {

    /** 候选批次标识。 */
    String candidateBatchId;

    /** 本次返回的判断数量。 */
    int total;

    /** 按创建时间排列的单候选判断。 */
    List<TradeDecisionResponse> items;
}
// AI_GENERATE_END-----