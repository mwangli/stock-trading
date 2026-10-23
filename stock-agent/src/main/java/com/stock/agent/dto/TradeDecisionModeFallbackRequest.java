// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Data;

/**
 * 将 Agent 交易决策立即回退到 SHADOW 的管理请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class TradeDecisionModeFallbackRequest {

    /** 回退原因，写入治理审计摘要，不能为空。 */
    private String reason;

    /** 客户端读取到的配置版本，用于防止并发覆盖。 */
    private Long configVersion;
}
// AI_GENERATE_END---
