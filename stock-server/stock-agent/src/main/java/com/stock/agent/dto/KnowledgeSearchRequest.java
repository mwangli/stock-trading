// AI_GENERATE_START --
package com.stock.agent.dto;

/**
 * 业务知识库检索工具请求。
 *
 * 请求只包含检索文本，用户范围始终由服务端工具上下文提供。
 *
 * @param query 检索文本，不能为空，最大长度受 Agent 问题契约限制
 * @author mwangli
 * @since 2026-10-25
 */
public record KnowledgeSearchRequest(String query) {
}
// AI_GENERATE_END --