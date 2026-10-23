// AI_GENERATE_START -
package com.stock.agent.client.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Backend 返回的最新日线事实。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param tradeDate 交易日期
 * @param openPrice 开盘价，单位元
 * @param highPrice 最高价，单位元
 * @param lowPrice 最低价，单位元
 * @param closePrice 收盘价，单位元
 * @param volume 成交量
 * @param amount 成交额，单位元
 * @param updatedAt 更新时间
 * @author mwangli
 * @since 2026-10-23
 */
public record LatestPriceFact(
        String stockCode, String stockName, LocalDate tradeDate,
        BigDecimal openPrice, BigDecimal highPrice, BigDecimal lowPrice,
        BigDecimal closePrice, BigDecimal volume, BigDecimal amount,
        LocalDateTime updatedAt) {
}
// AI_GENERATE_END -
