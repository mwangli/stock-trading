// AI_GENERATE_START--
package com.stock.platform.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Agent 使用的券商账户事实摘要。
 *
 * @param availableCash 可用资金，单位元
 * @param totalAssets 总资产，单位元
 * @param positionCount 持仓数量
 * @param todayOrderCount 当日订单数量
 * @param todayFillCount 当日成交数量
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalBrokerFactsResponse(
        BigDecimal availableCash, BigDecimal totalAssets, int positionCount,
        int todayOrderCount, int todayFillCount, LocalDateTime asOf) {
}
// AI_GENERATE_END--
