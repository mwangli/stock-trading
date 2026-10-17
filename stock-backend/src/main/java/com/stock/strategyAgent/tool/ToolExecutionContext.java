// AI_GENERATE_START --
package com.stock.strategyAgent.tool;

import lombok.Builder;
import lombok.Value;

/**
 * 工具调用上下文。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class ToolExecutionContext {

    /** 追踪标识。 */
    String traceId;

    /** 会话标识。 */
    String sessionId;

    /** 任务标识。 */
    String taskId;

    /** 用户范围。 */
    String userScope;
}
// AI_GENERATE_END --
