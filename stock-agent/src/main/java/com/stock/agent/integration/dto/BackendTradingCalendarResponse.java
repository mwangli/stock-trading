// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.time.LocalDate;

/**
 * Backend 交易日历 HTTP 响应。
 *
 * @param tradeDate 查询日期
 * @param tradingDay 是否为交易日
 * @param confirmed 是否已经权威确认
 * @param nextConfirmedTradingDay 当日或之后首个确认交易日
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendTradingCalendarResponse(
        LocalDate tradeDate, boolean tradingDay, boolean confirmed,
        LocalDate nextConfirmedTradingDay) {
}
// AI_GENERATE_END --
