// AI_GENERATE_START -
package com.stock.agent.integration.dto;

import java.math.BigDecimal;

/**
 * Backend 涨幅榜单项响应。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param price 最新价格，单位元
 * @param changePercent 涨跌幅，沿用行情源百分比口径
 * @author mwangli
 * @since 2026-10-25
 */
public record BackendMarketAdvancerItemResponse(
        String stockCode, String stockName, BigDecimal price, BigDecimal changePercent) {
}
// AI_GENERATE_END -
