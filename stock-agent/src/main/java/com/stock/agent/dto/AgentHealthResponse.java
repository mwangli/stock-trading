// AI_GENERATE_START---
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

    /** DeepSeek 是否可以完成实际模型调用。 */
    private boolean deepseekAvailable;

    /** Ollama 是否可以完成实际模型调用。 */
    private boolean ollamaAvailable;
}
// AI_GENERATE_END---
