// AI_GENERATE_START- -
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 跨业务消费的历史股票池版本。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HistoricalStockUniverseDto {
    /** 股票代码。 */
    private String stockCode;
    /** 股票名称。 */
    private String stockName;
    /** 所属市场。 */
    private String market;
    /** 板块代码。 */
    private String boardCode;
    /** 行业代码。 */
    private String industryCode;
    /** 上市日期。 */
    private LocalDate listedDate;
    /** 退市日期，可为空。 */
    private LocalDate delistedDate;
    /** ST 状态。 */
    private String stStatus;
    /** 版本生效日期。 */
    private LocalDate effectiveFrom;
    /** 版本失效日期，可为空。 */
    private LocalDate effectiveTo;
}
// AI_GENERATE_END- -
