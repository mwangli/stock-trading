// AI_GENERATE_START---
package com.stock.agent.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * Agent 交易决策参与模式配置响应。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Value
@Builder
public class TradeDecisionModeConfigResponse {

    /** 当前配置模式。 */
    String decisionMode;

    /** Agent 判断不可用时的回退策略。 */
    String fallbackPolicy;

    /** 策略版本灰度范围，为空表示不限制。 */
    String strategyVersionScope;

    /** 候选批次灰度范围，为空表示不限制。 */
    String candidateBatchScope;

    /** 稳定灰度比例，范围 0 至 100。 */
    Integer rolloutPercentage;

    /** 是否允许 GATED 模式影响自动买入候选。 */
    boolean productionImpactEnabled;

    /** 最近修改人。 */
    String updatedBy;

    /** 最近修改时间。 */
    LocalDateTime updatedAt;

    /** 当前配置乐观锁版本。 */
    Long configVersion;
}
// AI_GENERATE_END---
