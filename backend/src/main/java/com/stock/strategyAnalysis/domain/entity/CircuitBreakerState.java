package com.stock.strategyAnalysis.domain.entity;

/**
 * 熔断器状态枚举
 */
public enum CircuitBreakerState {
    CLOSED("关闭", "CLOSED"),
    OPEN("打开", "OPEN"),
    HALF_OPEN("半开", "HALF_OPEN");

    private final String name;
    private final String code;

    CircuitBreakerState(String name, String code) {
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
