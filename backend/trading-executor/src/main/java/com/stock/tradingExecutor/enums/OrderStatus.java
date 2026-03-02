package com.stock.tradingExecutor.enums;

/**
 * 订单状态枚举
 */
public enum OrderStatus {
    /**
     * 待执行
     */
    PENDING("待执行", "PENDING"),
    
    /**
     * 已报
     */
    SUBMITTED("已报", "SUBMITTED"),
    
    /**
     * 部分成交
     */
    PARTIAL("部分成交", "PARTIAL"),
    
    /**
     * 已成交
     */
    FILLED("已成交", "FILLED"),
    
    /**
     * 已撤销
     */
    CANCELLED("已撤销", "CANCELLED"),
    
    /**
     * 废单
     */
    REJECTED("废单", "REJECTED"),
    
    /**
     * 超时
     */
    TIMEOUT("超时", "TIMEOUT");
    
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
    
    /**
     * 根据code获取枚举
     */
    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return PENDING;
    }
    
    /**
     * 是否为终态
     */
    public boolean isFinal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == TIMEOUT;
    }
    
    /**
     * 是否成功
     */
    public boolean isSuccess() {
        return this == FILLED;
    }
}