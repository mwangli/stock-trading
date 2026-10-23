// AI_GENERATE_START--
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 股票日线价格响应项。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPriceItemDto {

    /** 股票代码。 */
    private String code;

    /** 股票名称。 */
    private String stockName;

    /** 交易日期。 */
    private LocalDate date;

    /** 开盘价，单位为元。 */
    private BigDecimal openPrice;

    /** 最高价，单位为元。 */
    private BigDecimal highPrice;

    /** 最低价，单位为元。 */
    private BigDecimal lowPrice;

    /** 收盘价，单位为元。 */
    private BigDecimal closePrice;

    /** 成交量，单位沿用数据源定义。 */
    private BigDecimal volume;

    /** 成交额，单位沿用数据源定义。 */
    private BigDecimal amount;

    /** 数据更新时间。 */
    private LocalDateTime updateTime;
}
// AI_GENERATE_END--
