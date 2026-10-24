// AI_GENERATE_START --
package com.stock.agent.tooling.model;

import java.util.List;

/**
 * Backend 返回的策略状态事实。
 *
 * @param enabled 策略总开关是否启用
 * @param currentMode 当前策略模式
 * @param configVersion 策略版本
 * @param autoSwitchEnabled 是否允许自动切换
 * @param disabledIndicators 已禁用指标
 * @author mwangli
 * @since 2026-10-23
 */
public record StrategyStateFact(
        boolean enabled, String currentMode, String configVersion,
        boolean autoSwitchEnabled, List<String> disabledIndicators) {
}
// AI_GENERATE_END -
