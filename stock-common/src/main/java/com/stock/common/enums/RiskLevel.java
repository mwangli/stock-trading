package com.stock.common.enums;

public enum RiskLevel {
    LOW("低风险", 1),
    MEDIUM("中等风险", 2),
    HIGH("高风险", 3),
    EXTREME("极高风险", 4);

    private final String name;
    private final int level;

    RiskLevel(String name, int level) {
        this.name = name;
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public int getLevel() {
        return level;
    }
}
