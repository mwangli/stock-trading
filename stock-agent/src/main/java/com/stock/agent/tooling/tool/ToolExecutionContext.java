// AI_GENERATE_START---------
package com.stock.agent.tooling.tool;

import lombok.Builder;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 本轮各工具已尝试调用次数。 */
    @Builder.Default
    Map<String, Integer> toolCallCounts = new ConcurrentHashMap<>();

    /**
     * 原子占用一次工具调用额度。
     *
     * @param toolName 工具名称
     * @param maxCalls 本轮最大调用次数
     * @return 未超过调用上限时返回 true
     */
    public boolean tryAcquireToolCall(String toolName, int maxCalls) {
        if (toolName == null || toolName.isBlank() || maxCalls < 1) {
            return false;
        }
        return toolCallCounts.merge(toolName, 1, Integer::sum) <= maxCalls;
    }

}
// AI_GENERATE_END--------
