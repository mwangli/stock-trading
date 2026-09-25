// AI_GENERATE_START ---
package com.stock.tradingExecutor.execution;

/**
 * 真实交易运行模式。
 *
 * @author mwangli
 * @since 2026-09-25
 */
public enum TradingMode {

    /** 真实交易，每笔新委托需要人工确认。 */
    LIVE_MANUAL,

    /** 在授权和风控范围内执行无人值守真实交易。 */
    LIVE_AUTO;

    /**
     * 判断是否允许无人值守执行新委托。
     *
     * @return LIVE_AUTO 时返回 true
     */
    public boolean isAutomatic() {
        return this == LIVE_AUTO;
    }
}
// AI_GENERATE_END ---
