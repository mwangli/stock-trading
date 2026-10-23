// AI_GENERATE_START --
package com.stock.backend.config;

/**
 * Redis 业务缓存名称。
 * 仅定义可丢失、可从权威数据源重建的查询缓存。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public final class CacheNames {

    public static final String STOCK_INFO = "stockInfo";
    public static final String LATEST_PRICE = "latestPrice";
    public static final String TRADING_CALENDAR = "tradingCalendar";
    public static final String ACTIVE_MODEL = "activeModel";
    public static final String RUNTIME_CONFIG = "runtimeConfig";

    private CacheNames() {
    }
}
// AI_GENERATE_END --
