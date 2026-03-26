package com.stock.autoLogin.enums;

/**
 * 滑块验证码类型枚举
 *
 * @author mwangli
 * @since 2026-03-25
 */
public enum SliderType {

    /**
     * 网易云盾
     */
    YIDUN("yidun", "网易云盾"),

    /**
     * 极验验证
     */
    GEETEST("geetest", "极验验证"),

    /**
     * 无滑块
     */
    NONE("none", "无滑块");

    private final String code;
    private final String description;

    SliderType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
