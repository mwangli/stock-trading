// AI_GENERATE_START------
package com.stock.agent.dto;

import lombok.Data;

/**
 * 生成候选批次 Agent 交易建议的请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class TradeDecisionGenerateRequest {

    /** 候选批次标识，不能为空。 */
    private String candidateBatchId;

    /** 最大候选数量，范围 1 至 20；为空时默认 5。 */
    private Integer maxCandidates;
}
// AI_GENERATE_END-----