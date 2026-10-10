// AI_GENERATE_START --
package com.stock.strategyAnalysis.domain.entity;

/**
 * 策略切换类型枚举。
 * 区分人工、自动规则和定时任务触发的策略模式切换。
 *
 * @author mwangli
 * @since 2026-09-25
 */
public enum SwitchType {
    MANUAL("手动切换", "MANUAL"),
    AUTO("自动切换", "AUTO"),
    TIMER("定时切换", "TIMER");

    private final String name;
    private final String code;

    SwitchType(String name, String code) {
        this.name = name;
        this.code = code;
    }

    /**
     * 获取切换类型名称。
     *
     * @return 中文名称
     */
    public String getName() { return name; }

    /**
     * 获取切换类型编码。
     *
     * @return 类型编码
     */
    public String getCode() { return code; }
}
// AI_GENERATE_END --
