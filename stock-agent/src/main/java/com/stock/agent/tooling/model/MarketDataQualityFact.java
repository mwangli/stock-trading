// AI_GENERATE_START --
package com.stock.agent.tooling.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场数据质量只读事实。
 *
 * @param totalRecords 股票记录总数
 * @param marketDistribution 市场分布摘要
 * @param completeness 字段完整率摘要
 * @param missingFields 缺失字段摘要
 * @param valid 关键代码字段是否有效
 * @param dataQuality 总体数据质量
 * @param asOf 查询时间
 * @author mwangli
 * @since 2026-10-25
 */
public record MarketDataQualityFact(
        long totalRecords, List<String> marketDistribution, List<String> completeness,
        List<String> missingFields, boolean valid, String dataQuality, LocalDateTime asOf) {
}
// AI_GENERATE_END --
