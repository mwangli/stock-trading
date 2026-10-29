// AI_GENERATE_START -
package com.stock.data.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 单只股票单交易日的统一日线导入项。
 *
 * @author mwangli
 * @since 2026-10-29
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDailyBarImportItemDto {

    /** 股票代码。 */
    @NotBlank
    private String stockCode;

    /** 交易日期。 */
    @NotNull
    private LocalDate tradeDate;

    /** 开盘价，单位为元。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal openPrice;

    /** 最高价，单位为元。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal highPrice;

    /** 最低价，单位为元。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal lowPrice;

    /** 收盘价，单位为元。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal closePrice;

    /** 数据源给出的除权昨收，首个可用交易日允许为空。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal preClosePrice;

    /** 成交量，单位由导入请求统一声明。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal volume;

    /** 成交额，单位由导入请求统一声明。 */
    @NotNull
    @DecimalMin("0")
    private BigDecimal amount;
}
// AI_GENERATE_END -
