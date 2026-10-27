// AI_GENERATE_START---
package com.stock.platform.dto;

/**
 * Agent 使用的候选股票摘要。
 *
 * @param stockCode 股票代码
 * @param stockName 股票名称
 * @param rank 候选排名
 * @param effectiveModelScore 有效模型得分
 * @param estimatedNetReturn 预估净收益
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalPredictionCandidateItem(
        String stockCode, String stockName, int rank, double effectiveModelScore, Double estimatedNetReturn) {
}
// AI_GENERATE_END---
