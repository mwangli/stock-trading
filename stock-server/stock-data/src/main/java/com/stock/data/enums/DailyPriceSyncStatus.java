// AI_GENERATE_START----
package com.stock.data.enums;

/**
 * 每日日线同步状态。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum DailyPriceSyncStatus {
    /** 已保存新行情。 */
    SAVED,
    /** 行情已存在，无需重复保存。 */
    ALREADY_PRESENT,
    /** 目标日期早于股票上市日期。 */
    NOT_LISTED,
    /** 供应商未返回目标日期行情。 */
    NO_DATA
}
// AI_GENERATE_END----
