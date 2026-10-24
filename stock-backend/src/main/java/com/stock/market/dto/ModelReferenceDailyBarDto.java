// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 模型与策略消费的市场或行业参考日线。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelReferenceDailyBarDto {
    /** 参考序列类型。 */
    private String seriesType;
    /** 参考序列代码。 */
    private String seriesCode;
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
}
// AI_GENERATE_END -
