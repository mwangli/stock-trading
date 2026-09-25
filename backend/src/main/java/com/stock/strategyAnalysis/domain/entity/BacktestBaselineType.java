// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.entity;

/**
 * 日线 T+1 基准候选选择方式。
 * 所有类型均使用 T 日开盘买入、下一可退出交易日开盘卖出的统一成交口径。
 *
 * @author mwangli
 * @since 2026-09-25
 */
public enum BacktestBaselineType {
    /** 按股票代码排序选择，用于验证固定成交链路。 */
    FIXED_CODE_ORDER,
    /** 使用固定随机种子选择候选，用于随机对照。 */
    RANDOM,
    /** 根据 T 日之前的历史收益选择强势股票。 */
    MOMENTUM,
    /** 根据 T 日之前的历史收益选择弱势股票。 */
    REVERSAL
}
// AI_GENERATE_END --
