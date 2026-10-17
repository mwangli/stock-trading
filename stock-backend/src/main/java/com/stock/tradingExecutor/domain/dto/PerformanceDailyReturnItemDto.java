// AI_GENERATE_START -
package com.stock.tradingExecutor.domain.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 单笔交易的日收益明细。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Data
@Builder
public class PerformanceDailyReturnItemDto {

    /** 交易发生日期，格式为 yyyy-MM-dd。 */
    private String period;

    /** 股票代码。 */
    private String stockCode;

    /** 股票名称。 */
    private String stockName;

    /** 该笔交易的已实现净收益，单位为元。 */
    private BigDecimal netProfit;

    /** 该笔交易的收益计算成本，包含已卖出部分买入成本和分摊买入费用，单位为元。 */
    private BigDecimal transactionAmount;

    /** 该笔交易的净收益率，小数形式。 */
    private BigDecimal returnRate;
}
// AI_GENERATE_END -
