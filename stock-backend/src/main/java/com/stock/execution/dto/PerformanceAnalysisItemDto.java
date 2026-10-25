// AI_GENERATE_START -
package com.stock.execution.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 月度收益归因中的单笔已实现交易。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Value
@Builder
public class PerformanceAnalysisItemDto {

    /** 股票代码。 */
    String stockCode;

    /** 股票名称。 */
    String stockName;

    /** 毛收益金额，单位元。 */
    BigDecimal grossProfit;

    /** 净收益金额，单位元。 */
    BigDecimal netProfit;

    /** 买卖费用合计，单位元。 */
    BigDecimal fees;

    /** 净收益率，小数形式。 */
    BigDecimal returnRate;

    /** 最后卖出成交时间。 */
    LocalDateTime sellTime;
}
// AI_GENERATE_END -
