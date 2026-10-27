// AI_GENERATE_START---------
package com.stock.strategy.dto;

import com.stock.strategy.entity.StrategyMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 策略运行状态 DTO。
 * 用于向接口调用方返回策略开关、运行模式和指标启停信息。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyStateDto {

    /**
     * 策略总开关是否启用
     */
    private boolean enabled;

    /**
     * 当前策略运行模式
     */
    private StrategyMode currentMode;

    /**
     * 当前被禁用的指标标识列表
     */
    private List<String> disabledIndicators;

    /**
     * 最近一次状态切换时间
     */
    private LocalDateTime lastSwitchTime;

    /**
     * 最近一次状态切换原因
     */
    private String lastSwitchReason;

    /**
     * 当前策略配置版本
     */
    private String configVersion;

    /** 是否允许规则自动切换策略模式。 */
    private boolean autoSwitchEnabled;

    /** 最近一次自动切换时间。 */
    private LocalDateTime lastAutoSwitchTime;
}
// AI_GENERATE_END---------
