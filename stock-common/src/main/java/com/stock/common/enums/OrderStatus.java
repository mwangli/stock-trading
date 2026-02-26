package com.stock.common.enums;

public enum OrderStatus {
    PENDING("待成交", "PENDING"),
    PARTIAL("部分成交", "PARTIAL"),
    FILLED("全部成交", "FILLED"),
    CANCELLED("已撤销", "CANCELLED"),
    REJECTED("已拒绝", "REJECTED");

    private final String name;
    private final String code;

    OrderStatus(String name, String code) {
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
