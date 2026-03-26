package com.stock.autoLogin.enums;

/**
 * 登录页面类型枚举
 *
 * @author mwangli
 * @since 2026-03-25
 */
public enum PageType {

    /**
     * 标准登录页
     */
    LOGIN("/account/login.html", "标准登录页"),

    /**
     * 手机验证页
     */
    ACTIVE_PHONE("/activePhone.html", "手机验证页"),

    /**
     * 未知页面
     */
    UNKNOWN("", "未知页面");

    private final String pathKeyword;
    private final String description;

    PageType(String pathKeyword, String description) {
        this.pathKeyword = pathKeyword;
        this.description = description;
    }

    /**
     * 根据 URL 判断页面类型
     */
    public static PageType fromUrl(String url) {
        if (url == null) {
            return UNKNOWN;
        }
        for (PageType type : values()) {
            if (url.contains(type.pathKeyword)) {
                return type;
            }
        }
        return UNKNOWN;
    }

    public String getDescription() {
        return description;
    }
}
