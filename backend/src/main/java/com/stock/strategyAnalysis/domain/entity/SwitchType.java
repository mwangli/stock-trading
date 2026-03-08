package com.stock.strategyAnalysis.domain.entity;

/**
 * 策略切换类型枚举
 */
public enum SwitchType {
    MANUAL("手动切换", "MANUAL"),
    AUTO("自动切换", "AUTO"),
    CIRCUIT_BREAKER("熔断触发", "CIRCUIT_BREAKER"),
    TIMER("定时切换", "TIMER");

    private final String name;
    private final String code;

    SwitchType(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public String getCode() { return code; }
}
