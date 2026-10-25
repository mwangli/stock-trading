// AI_GENERATE_START-------
package com.stock.agent.tooling.tool;

import lombok.Builder;
import lombok.Value;

import java.time.YearMonth;
import java.util.Set;

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

    /** 本轮服务端允许执行的工具名称。 */
    Set<String> allowedToolNames;

    /** 服务端根据用户问题确定的收益分析自然月，可为空。 */
    YearMonth performanceYearMonth;
}
// AI_GENERATE_END------
