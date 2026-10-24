// AI_GENERATE_START------
package com.stock.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 日或月收益聚合项。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class PerformancePeriodItemDto {

    /** 聚合周期，日格式为 yyyy-MM-dd，月格式为 yyyy-MM。 */
    private String period;

    /** 周期内已实现净收益，单位为元。 */
    private BigDecimal netProfit;

    /** 截至当前周期累计已实现净收益，单位为元。 */
    private BigDecimal cumulativeProfit;

    /** 周期收益率，小数形式。 */
    private BigDecimal returnRate;

    /** 周期内交易记录数量。 */
    private long tradeCount;

    /** 周期内盈利交易数量。 */
    private long winningTradeCount;
}
// AI_GENERATE_END------
