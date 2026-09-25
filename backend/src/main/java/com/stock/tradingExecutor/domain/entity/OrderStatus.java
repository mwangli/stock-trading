// AI_GENERATE_START ---
package com.stock.tradingExecutor.domain.entity;

/**
 * 订单状态枚举。
 *
 * @author mwangli
 * @since 2026-09-25
 */
public enum OrderStatus {
    UNKNOWN("未知", "UNKNOWN"),
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

    /**
     * 获取中文状态名称。
     *
     * @return 中文状态名称
     */
    public String getName() { return name; }

    /**
     * 获取标准状态编码。
     *
     * @return 标准状态编码
     */
    public String getCode() { return code; }

    /**
     * 根据标准编码解析订单状态。
     *
     * @param code 标准状态编码
     * @return 匹配状态，无法识别时返回 UNKNOWN
     */
    public static OrderStatus fromCode(String code) {
        for (OrderStatus status : values()) {
            if (status.getCode().equalsIgnoreCase(code)) {
                return status;
            }
        }
        return UNKNOWN;
    }

    /**
     * 判断是否为券商事实终态。
     *
     * @return 终态时返回 true
     */
    public boolean isFinal() {
        return this == FILLED || this == CANCELLED || this == REJECTED || this == TIMEOUT;
    }

    /**
     * 判断是否为成功成交状态。
     *
     * @return 全部成交时返回 true
     */
    public boolean isSuccess() {
        return this == FILLED;
    }
}
// AI_GENERATE_END ---
