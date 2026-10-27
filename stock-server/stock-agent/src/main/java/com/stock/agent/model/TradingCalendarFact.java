// AI_GENERATE_START -----
package com.stock.agent.model;

import java.time.LocalDate;

/**
 * Backend 返回的工作日规则结果。
 *
 * @param tradeDate 查询日期
 * @param tradingDay 是否为周一至周五
 * @param confirmed 日期非空时固定为 true，保留用于兼容现有 Agent 契约
 * @param nextConfirmedTradingDay 当日或之后首个周一至周五
 * @author mwangli
 * @since 2026-10-23
 */
public record TradingCalendarFact(
        LocalDate tradeDate, boolean tradingDay, boolean confirmed,
        LocalDate nextConfirmedTradingDay) {
}
// AI_GENERATE_END ----
