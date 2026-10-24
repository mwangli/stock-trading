// AI_GENERATE_START---
package com.stock.market.dto;

import com.stock.market.enums.DailyPriceSyncStatus;

/**
 * 单只股票每日增量同步结果。
 *
 * @param savedCount 新增条数
 * @param apiRequestCount 供应商接口请求次数
 * @param status 同步状态
 * @author mwangli
 * @since 2026-10-23
 */
public record DailyPriceSyncResult(int savedCount,
                                   int apiRequestCount,
                                   DailyPriceSyncStatus status) {
}
// AI_GENERATE_END---
