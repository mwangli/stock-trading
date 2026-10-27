// AI_GENERATE_START -
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 股票数据质量聚合快照。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockDataQualitySnapshotDto {

    /** 同一批股票数据计算得到的规模、市场分布和字段完整率。 */
    private StockStatisticsResponseDto statistics;

    /** 同一批股票数据计算得到的关键字段缺失校验结果。 */
    private StockDataValidationResponseDto validation;
}
// AI_GENERATE_END -