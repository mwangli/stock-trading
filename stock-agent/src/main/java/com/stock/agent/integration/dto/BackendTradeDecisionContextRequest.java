// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

/**
 * 请求 Backend 构建决策上下文的参数。
 *
 * @param candidateBatchId 候选批次标识
 * @param maxCandidates 最大候选数量
 * @author mwangli
 * @since 2026-10-23
 */
public record BackendTradeDecisionContextRequest(String candidateBatchId, int maxCandidates) {
}
// AI_GENERATE_END --
