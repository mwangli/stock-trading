// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Data;

/**
 * 更新 Agent 交易决策参与模式的管理请求。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Data
public class TradeDecisionModeUpdateRequest {

    /** 目标模式，仅允许 OFF、SHADOW、ADVISORY 或 GATED。 */
    private String decisionMode;

    /** 显式回退策略，仅允许 BASELINE_FALLBACK 或 MANUAL_REVIEW。 */
    private String fallbackPolicy;

    /** 策略版本灰度范围，为空表示不限制。 */
    private String strategyVersionScope;

    /** 候选批次灰度范围，为空表示不限制。 */
    private String candidateBatchScope;

    /** 稳定灰度比例，范围 0 至 100。 */
    private Integer rolloutPercentage;

    /** 是否允许 GATED 模式影响自动买入候选。 */
    private Boolean productionImpactEnabled;

    /** 客户端读取到的配置版本，用于防止并发覆盖。 */
    private Long configVersion;
}
// AI_GENERATE_END---
