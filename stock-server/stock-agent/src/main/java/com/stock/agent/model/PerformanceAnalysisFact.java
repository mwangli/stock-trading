// AI_GENERATE_START --
package com.stock.agent.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 指定自然月的已实现收益分析事实。
 *
 * @param yearMonth 查询自然月，格式 yyyy-MM
 * @param asOf 结果生成时间
 * @param grossProfit 毛收益金额，单位元
 * @param netProfit 净收益金额，单位元
 * @param totalFees 买卖费用合计，单位元
 * @param returnRate 净收益率，小数形式
 * @param tradeCount 已完成交易数量
 * @param winningTradeCount 盈利交易数量
 * @param losingTradeCount 亏损交易数量
 * @param winRate 胜率，小数形式
 * @param topGains 主要盈利交易
 * @param topLosses 主要亏损交易
 * @param availabilityReason 无交易数据时的原因
 * @author mwangli
 * @since 2026-10-25
 */
public record PerformanceAnalysisFact(
        String yearMonth, LocalDateTime asOf, BigDecimal grossProfit, BigDecimal netProfit,
        BigDecimal totalFees, BigDecimal returnRate, long tradeCount, long winningTradeCount,
        long losingTradeCount, BigDecimal winRate, List<PerformanceAnalysisItemFact> topGains,
        List<PerformanceAnalysisItemFact> topLosses, String availabilityReason) {
}
// AI_GENERATE_END --