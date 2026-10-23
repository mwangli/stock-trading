// AI_GENERATE_START---
package com.stock.data.dto;

import lombok.Data;

/**
 * 股票列表同步结果。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class StockListSyncResult {

    /** 供应商返回的股票总数。 */
    private int totalCount;

    /** 新增股票数量。 */
    private int savedCount;

    /** 更新股票数量。 */
    private int updatedCount;

    /** 已确认退市并完成清理的股票数量。 */
    private int cleanedCount;

    /** 数据库存在但供应商未返回且未确认退市的保留数量。 */
    private int ignoredMissingCount;

    /** 同步失败数量。 */
    private int failedCount;

    /** 同步耗时，单位毫秒。 */
    private long costTimeMs;

}
// AI_GENERATE_END---
