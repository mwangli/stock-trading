// AI_GENERATE_START- -
package com.stock.trading.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 指定自然月的已实现收益和损益归因。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Value
@Builder
public class PerformanceAnalysisDto {

    /** 查询自然月，格式 yyyy-MM。 */
    String yearMonth;

    /** 结果生成时间。 */
    LocalDateTime asOf;

    /** 毛收益金额，单位元。 */
    BigDecimal grossProfit;

    /** 净收益金额，单位元。 */
    BigDecimal netProfit;

    /** 已分摊买入费和卖出费合计，单位元。 */
    BigDecimal totalFees;

    /** 净收益率，小数形式。 */
    BigDecimal returnRate;

    /** 已完成交易数量。 */
    long tradeCount;

    /** 盈利交易数量。 */
    long winningTradeCount;

    /** 亏损交易数量。 */
    long losingTradeCount;

    /** 胜率，小数形式。 */
    BigDecimal winRate;

    /** 主要盈利交易，最多五项。 */
    List<PerformanceAnalysisItemDto> topGains;

    /** 主要亏损交易，最多五项。 */
    List<PerformanceAnalysisItemDto> topLosses;

    /** 无已实现交易时的原因，可为空。 */
    String availabilityReason;
}
// AI_GENERATE_END- -
