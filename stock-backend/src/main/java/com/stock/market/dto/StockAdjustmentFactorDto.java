// AI_GENERATE_START -
package com.stock.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 跨业务消费的股票复权因子。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockAdjustmentFactorDto {
    /** 股票代码。 */
    private String stockCode;
    /** 交易日期。 */
    private LocalDate tradeDate;
    /** 前复权因子，必须为正数。 */
    private BigDecimal forwardFactor;
    /** 后复权因子，必须为正数。 */
    private BigDecimal backwardFactor;
}
// AI_GENERATE_END -
