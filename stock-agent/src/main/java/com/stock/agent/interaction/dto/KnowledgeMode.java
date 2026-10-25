// AI_GENERATE_START -
package com.stock.agent.interaction.dto;

/**
 * 单轮 Agent 请求的知识库使用模式。
 *
 * @author mwangli
 * @since 2026-10-25
 */
public enum KnowledgeMode {
    /** 禁止本轮访问业务 RAG。 */
    NONE,
    /** 默认模式，由模型通过受控工具决定是否检索。 */
    AUTO,
    /** 服务端在模型调用前强制检索。 */
    REQUIRED
}
// AI_GENERATE_END -
