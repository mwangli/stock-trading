// AI_GENERATE_START--------
package com.stock.agent.runtime;

/**
 * Agent 模型提供方抽象。
 *
 * @author mwangli
 * @since 2026-10-17
 */
public interface AgentModelProvider {

    /**
     * 执行 Runtime 请求并返回结构化状态。
     *
     * @param request Runtime 请求
     * @return Runtime 响应
     */
    AgentRuntimeResponse generate(AgentRuntimeRequest request);

    /**
     * 返回当前提供方名称。
     *
     * @return 提供方名称
     */
    String providerName();

    /**
     * 返回脱敏 Provider 状态。
     *
     * @return Provider 状态
     */
    default AgentModelProviderStatus status() {
        return AgentModelProviderStatus.builder().provider(providerName()).build();
    }

    /**
     * 对已配置的模型 Provider 执行实际连通性检查。
     *
     * @return Provider 健康状态
     */
    default AgentProviderHealthStatus healthCheck() {
        return AgentProviderHealthStatus.builder().build();
    }

    /**
     * 仅检查指定 Provider 的实际连通性。
     *
     * @param provider Provider 名称
     * @return Provider 是否可用
     */
    default boolean healthCheck(String provider) {
        return false;
    }
}
// AI_GENERATE_END--------
