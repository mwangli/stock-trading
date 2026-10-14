// AI_GENERATE_START -
package com.stock.strategyAnalysis.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 策略运行状态持久化实体。
 * 保存跨进程重启和多实例共享的策略开关、模式及切换时间。
 *
 * @author mwangli
 * @since 2026-10-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "strategy_runtime_state")
public class StrategyRuntimeState {

    /** 单例状态键。 */
    @Id
    @Column(name = "state_key", length = 64)
    private String stateKey;

    /** 策略总开关。 */
    @Column(nullable = false)
    private boolean enabled;

    /** 当前策略模式。 */
    @Column(name = "current_mode", nullable = false, length = 32)
    private String currentMode;

    /** 被禁用指标列表的 JSON。 */
    @Column(name = "disabled_indicators_json", nullable = false, columnDefinition = "TEXT")
    private String disabledIndicatorsJson;

    /** 最近一次切换时间。 */
    @Column(name = "last_switch_time")
    private LocalDateTime lastSwitchTime;

    /** 最近一次切换原因。 */
    @Column(name = "last_switch_reason", length = 500)
    private String lastSwitchReason;

    /** 当前策略配置版本。 */
    @Column(name = "config_version", nullable = false, length = 64)
    private String configVersion;

    /** 是否允许规则自动切换。 */
    @Column(name = "auto_switch_enabled", nullable = false)
    private boolean autoSwitchEnabled;

    /** 最近一次自动切换时间。 */
    @Column(name = "last_auto_switch_time")
    private LocalDateTime lastAutoSwitchTime;

    /** 最近更新时间。 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END -
