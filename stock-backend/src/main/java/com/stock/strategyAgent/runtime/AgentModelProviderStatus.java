// AI_GENERATE_START -
package com.stock.strategyAgent.runtime;

import lombok.Builder;
import lombok.Value;

/**
 * Agent 模型 Provider 的脱敏运行状态。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class AgentModelProviderStatus {

    /** 当前 Provider 选择策略。 */
    String provider;

    /** DeepSeek 是否启用。 */
    boolean deepseekEnabled;

    /** DeepSeek 是否完成必要配置。 */
    boolean deepseekConfigured;

    /** Ollama 是否启用。 */
    boolean ollamaEnabled;

    /** Ollama 是否完成必要配置。 */
    boolean ollamaConfigured;
}
// AI_GENERATE_END -
