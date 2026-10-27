// AI_GENERATE_START -
package com.stock.platform.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 使用的股票目录查询结果。
 *
 * @param keyword 查询关键字，可为空
 * @param total 匹配总数
 * @param items 有限股票摘要列表
 * @param asOf 查询时间
 * @param availabilityReason 可用性说明
 * @author mwangli
 * @since 2026-10-25
 */
public record InternalStockDirectoryResponse(
        String keyword,
        long total,
        List<String> items,
        LocalDateTime asOf,
        String availabilityReason) {
}
// AI_GENERATE_END -
