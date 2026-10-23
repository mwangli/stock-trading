// AI_GENERATE_START -
package com.stock.backend.internal.dto;

/**
 * Agent 请求构建交易决策上下文的内部参数。
 *
 * @param candidateBatchId 候选批次标识
 * @param maxCandidates 最大候选数量，范围 1 至 20
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionContextRequest(String candidateBatchId, int maxCandidates) {
}
// AI_GENERATE_END -
