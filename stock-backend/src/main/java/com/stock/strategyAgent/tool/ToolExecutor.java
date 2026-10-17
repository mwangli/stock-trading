// AI_GENERATE_START --
package com.stock.strategyAgent.tool;

/**
 * 工具执行函数。
 *
 * @param <T> 工具数据类型
 * @author mwangli
 * @since 2026-10-17
 */
@FunctionalInterface
public interface ToolExecutor<T> {

    /**
     * 执行工具业务逻辑。
     *
     * @param input 已校验的输入对象
     * @param context 工具上下文
     * @return 工具数据
     */
    T execute(Object input, ToolExecutionContext context);
}
// AI_GENERATE_END --
