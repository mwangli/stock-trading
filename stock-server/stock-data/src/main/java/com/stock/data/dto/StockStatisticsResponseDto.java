// AI_GENERATE_START-------
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 股票数据统计业务数据。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockStatisticsResponseDto {

    /** 股票总记录数。 */
    private long totalCount;

    /** 按市场分组的记录数。 */
    private Map<String, Long> marketDistribution;

    /** 各关键字段的非空记录数。 */
    private Map<String, Long> fieldCompleteness;

    /** 各关键字段的完整率。 */
    private Map<String, String> completenessRate;
}
// AI_GENERATE_END-------
