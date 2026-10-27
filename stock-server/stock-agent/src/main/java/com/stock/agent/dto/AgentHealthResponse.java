// AI_GENERATE_START-----
package com.stock.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent Provider 健康检查结果。
 *
 * @author mwangli
 * @since 2026-10-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentHealthResponse {

    /** Agent 核心服务状态。 */
    private String agentCoreStatus;

    /** 模型 Provider 汇总状态。 */
    private String providerStatus;

    /** Embedding 运行状态。 */
    private String embeddingStatus;

    /** Backend 权威事实源状态。 */
    private String backendFactsStatus;

    /** DeepSeek 是否可以完成实际模型调用。 */
    private boolean deepseekAvailable;

    /** Ollama 是否可以完成实际模型调用。 */
    private boolean ollamaAvailable;

    /** Backend 权威事实源是否启用。 */
    private boolean backendFactsEnabled;

    /** Backend 权威事实源是否可用。 */
    private boolean backendFactsAvailable;
}
// AI_GENERATE_END----