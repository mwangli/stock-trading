package com.stock.strategyAnalysis.enums;

/**
 * 交易信号枚举
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

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }
}