// AI_GENERATE_START----
package com.stock.market.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

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
public class HistoricalUniverseImportItemDto {

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
// AI_GENERATE_END----
