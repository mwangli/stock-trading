// AI_GENERATE_START ---
package com.stock.agent.integration.dto;

/**
 * Backend 候选股票 HTTP 响应项。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param rank 候选排名
 * @param totalScore 综合评分
 * @param reason 入选原因
 * @author mwangli
 * @since 2026-10-24
 */
public record BackendPredictionCandidateItemResponse(
        String stockCode, String stockName, int rank, double totalScore, String reason) {
}
// AI_GENERATE_END --
