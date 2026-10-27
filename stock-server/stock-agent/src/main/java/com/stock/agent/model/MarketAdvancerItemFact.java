// AI_GENERATE_START --
package com.stock.agent.model;

import java.math.BigDecimal;

/**
 * 最近可用行情日的上涨股票事实。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param price 最新价格，单位元
 * @param changePercent 涨跌幅，沿用行情源百分比口径
 * @author mwangli
 * @since 2026-10-25
 */
public record MarketAdvancerItemFact(
        String stockCode, String stockName, BigDecimal price, BigDecimal changePercent) {
}
// AI_GENERATE_END --