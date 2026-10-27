// AI_GENERATE_START---
package com.stock.strategy.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 自适应策略页面的真实交易表现摘要。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class AdaptiveStrategyPerformanceResponse {

    /** 最新账户事实采集时间，可为空。 */
    LocalDateTime capturedAt;

    /** 已实现累计收益率，小数形式。 */
    BigDecimal cumulativeReturnRate;

    /** 已完成交易胜率，小数形式。 */
    BigDecimal winRate;

    /** 已完成交易平均收益率，小数形式。 */
    BigDecimal averageReturnRate;

    /** 单笔最差已实现收益率，小数形式。 */
    BigDecimal worstReturnRate;

    /** 已完成交易数量。 */
    long closedTradeCount;

    /** 是否已经形成最大回撤指标。 */
    boolean drawdownAvailable;

    /** 是否已经形成换手率指标。 */
    boolean turnoverAvailable;
}
// AI_GENERATE_END---
