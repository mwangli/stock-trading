// AI_GENERATE_START---
package com.stock.strategy.dto;

import lombok.Builder;
import lombok.Value;

/**
 * 自适应策略决策链单环节健康状态。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class AdaptiveStrategyHealthItem {

    /** 环节标识。 */
    String key;

    /** 环节显示名称。 */
    String label;

    /** 状态，取值为 HEALTHY、WARNING、BLOCKED 或 UNAVAILABLE。 */
    String status;

    /** 状态事实说明。 */
    String detail;
}
// AI_GENERATE_END---
