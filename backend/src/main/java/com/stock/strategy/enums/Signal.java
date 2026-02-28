package com.stock.strategy.enums;

public enum Signal {
    BUY("买入", "BUY"),
    SELL("卖出", "SELL"),
    HOLD("持有", "HOLD"),
    WATCH("观望", "WATCH");

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
