// AI_GENERATE_START -
package com.stock.trading.dto;

import java.time.LocalDateTime;

/**
 * Backend 发往 Agent 的交易门禁请求。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param strategyVersion 策略版本
 * @param baselineEligible Java 基线是否允许
 * @param evaluatedAt 评估时间
 * @author mwangli
 * @since 2026-10-23
 */
public record AgentTradeDecisionGateRequest(
        String candidateBatchId,
        String stockCode,
        String strategyVersion,
        boolean baselineEligible,
        LocalDateTime evaluatedAt) {
}
// AI_GENERATE_END -
