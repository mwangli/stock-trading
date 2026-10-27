// AI_GENERATE_START ---
package com.stock.agent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Backend 返回的收益概览事实。
 *
 * @param capturedAt 账户快照时间
 * @param totalAssets 总资产，单位元
 * @param availableCash 可用资金，单位元
 * @param marketValue 持仓市值，单位元
 * @param cumulativeProfit 累计已实现收益，单位元
 * @param cumulativeReturnRate 累计已实现收益率
 * @param dailyProfit 当日已实现收益，单位元
 * @param dailyReturnRate 当日已实现收益率
 * @param monthlyProfit 当月已实现收益，单位元
 * @param monthlyReturnRate 当月已实现收益率
 * @param closedTradeCount 已完成交易数量
 * @param winRate 胜率
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record PerformanceOverviewFact(
        LocalDateTime capturedAt, BigDecimal totalAssets, BigDecimal availableCash,
        BigDecimal marketValue, BigDecimal cumulativeProfit, BigDecimal cumulativeReturnRate,
        BigDecimal dailyProfit, BigDecimal dailyReturnRate, BigDecimal monthlyProfit,
        BigDecimal monthlyReturnRate, long closedTradeCount, BigDecimal winRate,
        LocalDateTime asOf) {
}
// AI_GENERATE_END --