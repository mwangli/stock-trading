// AI_GENERATE_START-------
package com.stock.decision.entity;

/**
 * 卖出触发类型枚举
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum SellTriggerType {
    TRAILING_STOP("移动止损", "TRAILING_STOP"),
    RSI("RSI超买", "RSI"),
    VOLUME("成交量背离", "VOLUME"),
    BOLLINGER("布林带突破", "BOLLINGER"),
    SCORE("综合得分", "SCORE"),
    FORCE("强制卖出", "FORCE");

    private final String name;
    private final String code;

    SellTriggerType(String name, String code) {
        this.name = name;
        this.code = code;
    }

    /**
     * 获取卖出触发类型名称。
     *
     * @return 中文展示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取卖出触发类型编码。
     *
     * @return 稳定业务编码
     */
    public String getCode() {
        return code;
    }
}
// AI_GENERATE_END-------
