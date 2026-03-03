package com.stock.strategyAnalysis.enums;

/**
 * 熔断器状态枚举
 */
public enum CircuitBreakerState {
    CLOSED("关闭", "CLOSED"),       // 正常状态
    OPEN("打开", "OPEN"),           // 熔断状态
    HALF_OPEN("半开", "HALF_OPEN"); // 恢复尝试状态

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