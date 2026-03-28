package com.stock.autoLogin.enums;

/**
 * 登录页面类型枚举
 *
 * @author mwangli
 * @since 2026-03-25
 */
public enum PageType {

    /**
     * 标准登录页（账号密码登录）
     */
    LOGIN("标准登录页"),

    /**
     * 手机验证页（首次登录/新设备验证）
     */
    PHONE_VERIFY("手机验证页"),

    /**
     * 未知页面
     */
    UNKNOWN("未知页面");

    /**
     * 页面类型描述
     */
    private final String description;

    PageType(String description) {
        this.description = description;
    }

    /**
     * 获取页面类型描述
     *
     * @return 描述文字
     */
    public String getDescription() {
        return description;
    }
}
