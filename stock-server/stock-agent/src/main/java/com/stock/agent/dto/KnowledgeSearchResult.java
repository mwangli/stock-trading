// AI_GENERATE_START --
package com.stock.agent.dto;

import java.util.List;

/**
 * 业务知识库检索工具响应。
 *
 * @param available 是否存在可供模型使用的匹配资料
 * @param items 最多五个匹配片段，不可为空
 * @param message 无匹配或不可用时的受控提示，可为空
 * @author mwangli
 * @since 2026-10-25
 */
public record KnowledgeSearchResult(
        boolean available,
        List<KnowledgeSearchItem> items,
        String message) {
}
// AI_GENERATE_END --