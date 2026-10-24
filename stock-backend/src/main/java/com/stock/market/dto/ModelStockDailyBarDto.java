// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 模型消费的版本化股票日线。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStockDailyBarDto {
    /** 股票代码。 */
    private String stockCode;
    /** 交易日期。 */
    private LocalDate tradeDate;
    /** 开盘价，单位为元。 */
    private BigDecimal openPrice;
    /** 最高价，单位为元。 */
    private BigDecimal highPrice;
    /** 最低价，单位为元。 */
    private BigDecimal lowPrice;
    /** 收盘价，单位为元。 */
    private BigDecimal closePrice;
    /** 成交量，单位由 volumeUnit 指定。 */
    private BigDecimal volume;
    /** 成交额，单位由 amountUnit 指定。 */
    private BigDecimal amount;
    /** 换手率，单位为百分比。 */
    private BigDecimal turnoverRate;
    /** 成交量单位。 */
    private String volumeUnit;
    /** 成交额单位。 */
    private String amountUnit;
}
// AI_GENERATE_END -
