// AI_GENERATE_START-------
package com.stock.base.cache;

/**
 * 进程内业务缓存名称。
 * 仅定义可丢失、可从权威数据源重建的查询缓存。
 *
 * @author mwangli
 * @since 2026-10-23
 */
public final class CacheNames {

    /** 股票基础信息缓存。 */
    public static final String STOCK_INFO = "stockInfo";

    /** 最新价格缓存。 */
    public static final String LATEST_PRICE = "latestPrice";

    /** 当前生效模型缓存。 */
    public static final String ACTIVE_MODEL = "activeModel";

    /** 运行时配置缓存。 */
    public static final String RUNTIME_CONFIG = "runtimeConfig";

    private CacheNames() {
    }
}
// AI_GENERATE_END-------
