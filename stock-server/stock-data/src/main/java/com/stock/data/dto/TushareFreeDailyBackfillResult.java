// AI_GENERATE_START- -
package com.stock.data.dto;

import java.time.LocalDate;

/**
 * Tushare 免费日线回填结果。
 *
 * @param startDate 请求开始日期
 * @param endDate 请求结束日期
 * @param requestedDateCount 实际发起请求的工作日数量
 * @param returnedRowCount 数据源返回并命中股票池的记录数量
 * @param upsertedRowCount 提交幂等写入的记录数量
 * @param coveredStockCount 返回数据覆盖的股票数量
 * @author mwangli
 * @since 2026-10-26
 */
public record TushareFreeDailyBackfillResult(
        LocalDate startDate,
        LocalDate endDate,
        int requestedDateCount,
        long returnedRowCount,
        long upsertedRowCount,
        int coveredStockCount) {
}
// AI_GENERATE_END- -