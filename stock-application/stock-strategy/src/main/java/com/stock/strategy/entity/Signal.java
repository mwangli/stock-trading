// AI_GENERATE_START------
package com.stock.strategy.entity;

/**
 * 交易信号枚举
 *
 * @author mwangli
 * @since 2026-10-23
 */
public enum Signal {
    BUY("买入", "BUY"),
    SELL("卖出", "SELL"),
    HOLD("持有", "HOLD");

    private final String name;
    private final String code;

    Signal(String name, String code) {
        this.name = name;
        this.code = code;
    }

    /**
     * 获取交易信号名称。
     *
     * @return 中文展示名称
     */
    public String getName() {
        return name;
    }

    /**
     * 获取交易信号编码。
     *
     * @return 稳定业务编码
     */
    public String getCode() {
        return code;
    }
}
// AI_GENERATE_END------
