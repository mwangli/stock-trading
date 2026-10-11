// AI_GENERATE_START --
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单笔交易收益排行项。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class PerformanceRankingItemDto {

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 交易状态。 */
    private String status;

    /** 净收益金额，单位为元。 */
    private BigDecimal netProfit;

    /** 净收益率，小数形式。 */
    private BigDecimal returnRate;

    /** 复合日净收益率，小数形式。 */
    private BigDecimal dailyReturnRate;

    /** 持仓天数。 */
    private BigDecimal holdingDays;

    /** 最后卖出时间。 */
    private LocalDateTime sellTime;
}
// AI_GENERATE_END --
