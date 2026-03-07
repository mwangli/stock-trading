package com.stock.strategyAnalysis.enums;

/**
 * 策略模式枚举
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

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public int getDefaultThreshold() {
        return defaultThreshold;
    }
}