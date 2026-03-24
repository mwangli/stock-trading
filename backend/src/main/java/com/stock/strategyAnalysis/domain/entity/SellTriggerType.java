package com.stock.strategyAnalysis.domain.entity;

/**
 * 卖出触发类型枚举
 */
public enum SellTriggerType {
    TRAILING_STOP("移动止损", "TRAILING_STOP"),
    RSI("RSI超买", "RSI"),
    VOLUME("成交量背离", "VOLUME"),
    BOLLINGER("布林带突破", "BOLLINGER"),
    SCORE("综合得分", "SCORE"),
    FORCE("强制卖出", "FORCE");

    private final String name;
    private final String code;

    SellTriggerType(String name, String code) {
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
