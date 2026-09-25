// AI_GENERATE_START --
package com.stock.dataCollector.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 股票复权因子批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdjustmentFactorImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 复权因子记录列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<Item> items;

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
    public static class Item {

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
}
// AI_GENERATE_END --
