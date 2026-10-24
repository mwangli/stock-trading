// AI_GENERATE_START-------
package com.stock.execution.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易收益报表总览。
 * 已实现收益类指标基于 FIFO 交易记录计算，账户资产类指标来自最新账户资金快照。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Data
@Builder
public class PerformanceOverviewDto {

    /** 最新账户快照时间。 */
    private LocalDateTime capturedAt;

    /** 当前账户总资产，单位为元。 */
    private BigDecimal totalAssets;

    /** 当前账户可用资金，单位为元。 */
    private BigDecimal availableCash;

    /** 当前持仓市值，单位为元。 */
    private BigDecimal marketValue;

    /** 当前冻结资金，单位为元。 */
    private BigDecimal frozenCash;

    /** 全部已实现净收益，单位为元。 */
    private BigDecimal cumulativeProfit;

    /** 全部已实现收益率，小数形式。 */
    private BigDecimal cumulativeReturnRate;

    /** 当前交易日已实现净收益，单位为元。 */
    private BigDecimal dailyProfit;

    /** 当前交易日已实现收益率，小数形式。 */
    private BigDecimal dailyReturnRate;

    /** 当前自然月已实现净收益，单位为元。 */
    private BigDecimal monthlyProfit;

    /** 当前自然月已实现收益率，小数形式。 */
    private BigDecimal monthlyReturnRate;

    /** 交易记录总数，包含未完成交易。 */
    private long totalTradeCount;

    /** 已完成交易记录数。 */
    private long closedTradeCount;

    /** 盈利交易记录数。 */
    private long winningTradeCount;

    /** 亏损交易记录数。 */
    private long losingTradeCount;

    /** 已完成交易胜率，小数形式。 */
    private BigDecimal winRate;

    /** 已完成交易平均收益率，小数形式。 */
    private BigDecimal averageReturnRate;

    /** 单笔最高收益率，小数形式。 */
    private BigDecimal bestReturnRate;

    /** 单笔最低收益率，小数形式。 */
    private BigDecimal worstReturnRate;

    /** 按收益率倒序排列的交易排行。 */
    private List<PerformanceRankingItemDto> ranking;

    /** 按交易日返回的已实现收益明细，每条对应一笔交易。 */
    private List<PerformanceDailyReturnItemDto> dailyReturns;

    /** 按自然月聚合的已实现收益。 */
    private List<PerformancePeriodItemDto> monthlyReturns;
}
// AI_GENERATE_END-------
