// AI_GENERATE_START --
package com.stock.dataCollector.domain.dto;

import com.stock.dataCollector.domain.entity.ReferenceSeriesType;
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
 * 市场或行业参考日线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelReferenceDailyBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 不可覆盖的数据版本。 */
    @NotBlank
    private String sourceVersion;

    /** 本批参考序列类型。 */
    @NotNull
    private ReferenceSeriesType seriesType;

    /** 参考日线列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<Item> items;

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
    public static class Item {

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
}
// AI_GENERATE_END --
