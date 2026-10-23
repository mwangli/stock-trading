// AI_GENERATE_START -
package com.stock.agent.dto;

import java.time.LocalDateTime;

/**
 * Backend 调用 Agent 交易门禁的内部请求。
 *
 * @param candidateBatchId 候选批次标识
 * @param stockCode 股票代码
 * @param strategyVersion 策略版本
 * @param baselineEligible Java 基线是否允许
 * @param evaluatedAt 评估时间
 * @author mwangli
 * @since 2026-10-23
 */
public record InternalTradeDecisionGateRequest(
        String candidateBatchId,
        String stockCode,
        String strategyVersion,
        boolean baselineEligible,
        LocalDateTime evaluatedAt) {
}
// AI_GENERATE_END -
