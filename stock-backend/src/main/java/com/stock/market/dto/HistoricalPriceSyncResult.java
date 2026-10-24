// AI_GENERATE_START---
package com.stock.market.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 单只股票历史回补结果。
 *
 * @param savedCount 历史回补新增条数
 * @param apiRequestCount 供应商接口请求次数
 * @param skipped 是否因无缺口而跳过供应商请求
 * @param unresolvedDates 仍未解决的已确认交易日
 * @author mwangli
 * @since 2026-10-23
 */
public record HistoricalPriceSyncResult(int savedCount,
                                        int apiRequestCount,
                                        boolean skipped,
                                        List<LocalDate> unresolvedDates) {
}
// AI_GENERATE_END---
