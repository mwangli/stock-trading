// AI_GENERATE_START ---
package com.stock.agent.output;

/**
 * 业务能力结构化输出解析与校验扩展点。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface AgentStructuredOutputHandler {

    /** @param outputType 输出类型 @return true 表示当前处理器负责该类型 */
    boolean supports(String outputType);

    /**
     * 解析并校验业务结构化输出。
     *
     * @param outputType 输出类型
     * @param content 已归一化的 JSON 内容
     * @return 合法结构化结果
     */
    AgentStructuredOutput parseAndValidate(String outputType, String content);
}
// AI_GENERATE_END --