// AI_GENERATE_START------
package com.stock.strategy.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import org.springframework.data.annotation.Id;

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
@Document(collection = "strategy_runtime_state")
public class StrategyRuntimeState {

    /** 单例状态键。 */
    @Id
    private String stateKey;

    /** 策略总开关。 */
    private boolean enabled;

    /** 当前策略模式。 */
    private String currentMode;

    /** 被禁用指标列表的 JSON。 */
    private String disabledIndicatorsJson;

    /** 最近一次切换时间。 */
    private LocalDateTime lastSwitchTime;

    /** 最近一次切换原因。 */
    private String lastSwitchReason;

    /** 当前策略配置版本。 */
    private String configVersion;

    /** 是否允许规则自动切换。 */
    private boolean autoSwitchEnabled;

    /** 最近一次自动切换时间。 */
    private LocalDateTime lastAutoSwitchTime;

    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
// AI_GENERATE_END------
