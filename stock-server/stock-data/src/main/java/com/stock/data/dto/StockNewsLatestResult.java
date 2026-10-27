// AI_GENERATE_START -
package com.stock.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 股票最新有效新闻查询结果。
 *
 * @author mwangli
 * @since 2026-10-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockNewsLatestResult {

    /** 按发布时间倒序排列的最新有效新闻，最多 3 条，不可为空。 */
    private List<StockNewsSummaryResponse> items;
}
// AI_GENERATE_END -