// AI_GENERATE_START---
package com.stock.agent.tool;

/**
 * 当前 Agent 工具调用上下文。
 *
 * @author mwangli
 * @since 2026-10-18
 */
public final class AgentToolContextHolder {

    private static final ThreadLocal<ToolExecutionContext> CONTEXT = new ThreadLocal<>();

    private AgentToolContextHolder() {
    }

    /**
     * 设置当前线程的工具上下文。
     *
     * @param context 工具上下文
     */
    public static void set(ToolExecutionContext context) {
        CONTEXT.set(context);
    }

    /**
     * 返回当前线程的工具上下文。
     *
     * @return 工具上下文，不存在时返回 null
     */
    public static ToolExecutionContext current() {
        return CONTEXT.get();
    }

    /**
     * 清理当前线程的工具上下文。
     */
    public static void clear() {
        CONTEXT.remove();
    }
}
// AI_GENERATE_END---
