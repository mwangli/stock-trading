// AI_GENERATE_START ---
package com.stock.agent.decision.port.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Backend 返回的交易建议批量评估事实。
 *
 * @param latestMarketDate 本地最新行情日期
 * @param items 逐候选评估事实
 * @author mwangli
 * @since 2026-10-23
 */
public record TradeDecisionEvaluationFacts(
        LocalDate latestMarketDate, List<TradeDecisionEvaluationFact> items) {
}
// AI_GENERATE_END --
