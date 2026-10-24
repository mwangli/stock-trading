// AI_GENERATE_START-----
package com.stock.agent.decision.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 交易建议 Provider 稳定性统计响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionProviderStatisticsResponse {

    /** 统计窗口起始时间，包含。 */
    LocalDateTime from;

    /** 统计窗口结束时间，包含。 */
    LocalDateTime to;

    /** 统计窗口内交易建议总数。 */
    long totalCount;

    /** 按 Provider 和模型分组的稳定性指标。 */
    List<TradeDecisionProviderMetricResponse> providers;
}
// AI_GENERATE_END----
