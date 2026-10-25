// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

/**
 * 最近可用行情日的上涨股票条目。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Value
@Builder
public class MarketAdvancerItemDto {

    /** 股票代码，不可为空。 */
    String stockCode;

    /** 股票名称，不可为空。 */
    String stockName;

    /** 最新价格，单位元。 */
    BigDecimal price;

    /** 涨跌幅，沿用行情源百分比口径。 */
    BigDecimal changePercent;
}
// AI_GENERATE_END -
