// AI_GENERATE_START --
package com.stock.dataCollector.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * 交易日历批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeCalendarImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 交易日历记录列表。 */
    @Valid
    @NotEmpty
    @Size(max = 5000)
    private List<Item> items;

    /**
     * 单日交易日历记录。
     *
     * @author mwangli
     * @since 2026-09-25
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {

        /** 市场代码。 */
        @NotBlank
        private String market;

        /** 自然日期。 */
        @NotNull
        private LocalDate tradeDate;

        /** 是否为交易日。 */
        private boolean tradingDay;

        /** 前一交易日。 */
        private LocalDate previousTradingDay;

        /** 后一交易日。 */
        private LocalDate nextTradingDay;
    }
}
// AI_GENERATE_END --
