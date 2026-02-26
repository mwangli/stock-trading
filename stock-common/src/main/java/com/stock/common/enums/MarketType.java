package com.stock.common.enums;

public enum MarketType {
    SH("上海", "sh"),
    SZ("深圳", "sz"),
    BJ("北京", "bj");

    private final String name;
    private final String code;

    MarketType(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public static MarketType fromCode(String code) {
        for (MarketType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
