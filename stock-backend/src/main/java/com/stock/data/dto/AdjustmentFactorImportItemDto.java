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
 * 单日股票复权因子。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentFactorImportItemDto {

    /** 股票代码。 */
    @NotBlank
    private String stockCode;

    /** 因子对应交易日。 */
    @NotNull
    private LocalDate tradeDate;

    /** 前复权因子。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal forwardFactor;

    /** 后复权因子。 */
    @DecimalMin(value = "0", inclusive = false)
    private BigDecimal backwardFactor;
}
// AI_GENERATE_END---
