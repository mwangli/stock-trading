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
 * 模型特征专用股票日线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelStockDailyBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 不可覆盖的数据版本。 */
    @NotBlank
    private String sourceVersion;

    /** 成交量单位，例如 SHARE。 */
    @NotBlank
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    @NotBlank
    private String amountUnit;

    /** 股票日线列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<Item> items;

    /**
     * 单只股票单日模型特征行情。
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

        /** 成交量。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal volume;

        /** 成交额。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal amount;

        /** 当日换手率，必须使用数据源明确的比例口径。 */
        @NotNull
        @DecimalMin("0")
        private BigDecimal turnoverRate;
    }
}
// AI_GENERATE_END --
