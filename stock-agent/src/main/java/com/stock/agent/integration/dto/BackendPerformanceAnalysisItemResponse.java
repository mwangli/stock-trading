// AI_GENERATE_START -
package com.stock.agent.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Backend 月度收益归因单项响应。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param grossProfit 毛收益金额，单位元
 * @param netProfit 净收益金额，单位元
 * @param fees 买卖费用合计，单位元
 * @param returnRate 净收益率，小数形式
 * @param sellTime 最后卖出成交时间
 * @author mwangli
 * @since 2026-10-25
 */
public record BackendPerformanceAnalysisItemResponse(
        String stockCode, String stockName, BigDecimal grossProfit, BigDecimal netProfit,
        BigDecimal fees, BigDecimal returnRate, LocalDateTime sellTime) {
}
// AI_GENERATE_END -
