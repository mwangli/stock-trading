// AI_GENERATE_START------
package com.stock.strategy.entity;

/**
 * 策略模式枚举
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum StrategyMode {
    CONSERVATIVE("保守模式", "CONSERVATIVE", 45),
    BALANCED("均衡模式", "BALANCED", 60),
    AGGRESSIVE("激进模式", "AGGRESSIVE", 70);

    private final String name;
    private final String code;
    private final int defaultThreshold;

    StrategyMode(String name, String code, int defaultThreshold) {
        this.name = name;
        this.code = code;
        this.defaultThreshold = defaultThreshold;
    }

    /**
     * 获取策略模式名称。
     *
     * @return 中文展示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取策略模式编码。
     *
     * @return 稳定业务编码
     */
    public String getCode() {
        return code;
    }

    /**
     * 获取策略模式默认评分阈值。
     *
     * @return 默认评分阈值
     */
    public int getDefaultThreshold() {
        return defaultThreshold;
    }
}
// AI_GENERATE_END------
