// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Data;

/**
 * 人工审批或拒绝交易计划的请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class TradePlanReviewRequest {

    /** 客户端读取到的计划版本，用于防止并发审批覆盖。 */
    private Long version;

    /** 审批意见，可为空，最大长度由服务层限制。 */
    private String comment;
}
// AI_GENERATE_END---
