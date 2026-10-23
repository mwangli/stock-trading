// AI_GENERATE_START---
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
 * 单条市场或行业参考日线。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelReferenceDailyBarImportItemDto {

    /** 指数或行业序列代码。 */
    @NotBlank
    private String seriesCode;

    /** 交易日期。 */
    @NotNull
    private LocalDate tradeDate;

    /** 开盘价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal openPrice;

    /** 最高价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal highPrice;

    /** 最低价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal lowPrice;

    /** 收盘价。 */
    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal closePrice;
}
// AI_GENERATE_END---
