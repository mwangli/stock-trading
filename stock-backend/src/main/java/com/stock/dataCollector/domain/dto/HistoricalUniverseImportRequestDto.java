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
 * 历史股票池批量导入请求。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalUniverseImportRequestDto {

    /** 数据来源。 */
    @NotBlank
    private String source;

    /** 数据源版本或批次。 */
    @NotBlank
    private String sourceVersion;

    /** 历史股票版本列表。 */
    @Valid
    @NotEmpty
    @Size(max = 10000)
    private List<Item> items;

    /**
     * 单只股票的历史有效期版本。
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

        /** 股票名称。 */
        private String stockName;

        /** 交易市场。 */
        @NotBlank
        private String market;

        /** 板块代码。 */
        private String boardCode;

        /** 行业代码。 */
        private String industryCode;

        /** 上市日期。 */
        private LocalDate listedDate;

        /** 退市日期。 */
        private LocalDate delistedDate;

        /** ST 状态。 */
        @NotBlank
        private String stStatus;

        /** 版本生效日期。 */
        @NotNull
        private LocalDate effectiveFrom;

        /** 版本失效日期。 */
        private LocalDate effectiveTo;
    }
}
// AI_GENERATE_END --
