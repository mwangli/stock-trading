// AI_GENERATE_START--
package com.stock.decision.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 自适应策略演化时间线事件。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Value
@Builder
public class AdaptiveStrategyEvolutionEvent {

    /** 事件类型。 */
    String type;

    /** 事件标题。 */
    String title;

    /** 事件状态。 */
    String status;

    /** 事件事实说明。 */
    String detail;

    /** 事件发生或记录时间，可为空。 */
    LocalDateTime occurredAt;
}
// AI_GENERATE_END--
