// AI_GENERATE_START----
package com.stock.agent.runtime;

import lombok.Builder;
import lombok.Value;

/**
 * Agent Runtime 受控调用响应。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Value
@Builder
public class AgentRuntimeResponse {

    /** Runtime 状态。 */
    AgentRuntimeStatus status;

    /** 模型提供方。 */
    String provider;

    /** 模型名称。 */
    String model;

    /** 受限长度的模型或降级文本。 */
    String content;

    /** 结构化证据摘要。 */
    String evidenceSummary;

    /** 归一化错误码。 */
    String errorCode;

    /** 结构化业务结果。 */
    AgentStructuredOutput structuredOutput;
}
// AI_GENERATE_END----
