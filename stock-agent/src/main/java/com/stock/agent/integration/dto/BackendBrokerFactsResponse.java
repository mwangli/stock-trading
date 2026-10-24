// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Backend 券商账户事实 HTTP 响应。
 *
 * @param availableCash 可用资金，单位元
 * @param totalAssets 总资产，单位元
 * @param positionCount 持仓数量
 * @param todayOrderCount 当日订单数量
 * @param todayFillCount 当日成交数量
 * @param asOf 事实查询时间
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendBrokerFactsResponse(
        BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
        int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
}
// AI_GENERATE_END --
