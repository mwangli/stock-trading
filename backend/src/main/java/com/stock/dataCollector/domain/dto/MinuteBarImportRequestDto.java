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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 股票 5 分钟 K 线批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MinuteBarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 成交量单位，例如 SHARE、LOT。 */
    @NotBlank
    private String volumeUnit;

    /** 成交额单位，例如 CNY。 */
    @NotBlank
    private String amountUnit;

    /** 5 分钟 K 线记录列表。 */
    @Valid
    @NotEmpty
    @Size(max = 20000)
    private List<Item> items;

    /**
     * 单根 5 分钟 K 线。
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

        /** K 线起始时间或结束时间，由数据源版本统一定义。 */
        @NotNull
        private LocalDateTime barTime;

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
    }
}
// AI_GENERATE_END --
