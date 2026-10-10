// AI_GENERATE_START --
package com.stock.dataCollector.domain.entity;

/**
 * 股票开盘时点的买入或卖出可执行状态。
 * 状态必须来自明确数据源，不能仅根据日线是否存在推断。
 *
 * @author mwangli
 * @since 2026-09-25
 */
public enum OpenTradabilityStatus {

    /** 数据源确认开盘时允许执行对应方向交易。 */
    ALLOWED,

    /** 股票停牌。 */
    SUSPENDED,

    /** 涨停、无卖盘或排队规则导致买入不可执行。 */
    LIMIT_UP_BLOCKED,

    /** 跌停、无买盘或排队规则导致卖出不可执行。 */
    LIMIT_DOWN_BLOCKED,

    /** 数据源确认没有有效报价或流动性。 */
    NO_LIQUIDITY,

    /** 数据源无法确定是否可执行。 */
    UNKNOWN
}
// AI_GENERATE_END --
