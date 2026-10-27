// AI_GENERATE_START ----
package com.stock.agent.model;

import java.time.LocalDate;

/**
 * Backend 返回的交易日历事实。
 *
 * @param tradeDate 查询日期
 * @param tradingDay 是否为交易日
 * @param confirmed 是否已确认
 * @param nextConfirmedTradingDay 当日或之后首个确认交易日；日历覆盖不足时为空
 * @author mwangli
 * @since 2026-10-23
 */
public record TradingCalendarFact(
        LocalDate tradeDate, boolean tradingDay, boolean confirmed,
        LocalDate nextConfirmedTradingDay) {
}
// AI_GENERATE_END ---