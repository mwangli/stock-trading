// AI_GENERATE_START --
package com.stock.agent.tooling.model;

/**
 * Backend 返回的候选股票摘要。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param rank 候选排名
 * @param totalScore 综合评分
 * @param reason 入选原因
 * @author mwangli
 * @since 2026-10-23
 */
public record PredictionCandidateItemFact(
        String stockCode, String stockName, int rank, double totalScore, String reason) {
}
// AI_GENERATE_END -
