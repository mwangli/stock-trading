// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Data;

/**
 * 候选批次影子交易判断生成请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class ShadowTradeDecisionRequest {

    /** 候选批次标识，不能为空。 */
    private String candidateBatchId;

    /** 最大候选数量，允许为空，默认 5，范围 1 至 20。 */
    private Integer maxCandidates;
}
// AI_GENERATE_END---
