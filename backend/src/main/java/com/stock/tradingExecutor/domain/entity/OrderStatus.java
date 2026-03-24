package com.stock.tradingExecutor.domain.entity;

/**
 * 订单状态枚举
 */
public enum OrderStatus {
    PENDING("待执行", "PENDING"),
    SUBMITTED("已报", "SUBMITTED"),
    PARTIAL("部分成交", "PARTIAL"),
    FILLED("已成交", "FILLED"),
    CANCELLED("已撤销", "CANCELLED"),
    REJECTED("废单", "REJECTED"),
    TIMEOUT("超时", "TIMEOUT");

    private final String name;
    private final String code;

    OrderStatus(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() { return name; }
    public String getCode() { return code; }

    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING;
    }

    public boolean isFinal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == TIMEOUT;
    }

    public boolean isSuccess() {
        return this == FILLED;
    }
}
