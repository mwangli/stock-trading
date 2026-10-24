// AI_GENERATE_START----
package com.stock.agent.runtime.api;

import lombok.Builder;
import lombok.Value;

/**
 * Agent Provider 实际调用健康状态。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Value
@Builder
public class AgentProviderHealthStatus {

    /** DeepSeek 是否可用。 */
    boolean deepseekAvailable;

    /** Ollama 是否可用。 */
    boolean ollamaAvailable;
}
// AI_GENERATE_END---
