// AI_GENERATE_START------
package com.stock.trading.dto;

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

    /** 交易金额，按已卖出部分对应的买入成本计算，单位为元。 */
    private BigDecimal transactionAmount;

    /** 买入金额，单位为元。 */
    private BigDecimal buyAmount;

    /** 卖出金额，单位为元。 */
    private BigDecimal sellAmount;

    /** 已成交交易数量，单位为股。 */
    private Integer tradeQuantity;

    /** 净收益金额，单位为元。 */
    private BigDecimal netProfit;

    /** 净收益率，小数形式。 */
    private BigDecimal returnRate;

    /** 复合日净收益率，小数形式。 */
    private BigDecimal dailyReturnRate;

    /** 买入成交时间。 */
    private LocalDateTime buyTime;

    /** 持仓天数。 */
    private BigDecimal holdingDays;

    /** 最后卖出时间。 */
    private LocalDateTime sellTime;
}
// AI_GENERATE_END------
