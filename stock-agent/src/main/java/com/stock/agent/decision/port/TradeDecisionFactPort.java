// AI_GENERATE_START --
package com.stock.agent.decision.port;

import com.stock.agent.decision.port.model.BackendTradeDecisionContextFact;
import com.stock.agent.decision.port.model.TradeDecisionEvaluationFactRequest;
import com.stock.agent.decision.port.model.TradeDecisionEvaluationFacts;

import java.util.List;

/**
 * Agent 交易建议所需 Backend 权威事实端口。
 *
 * @author mwangli
 * @since 2026-10-24
 */
public interface TradeDecisionFactPort {

    /**
     * 构建不可变交易决策上下文。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量
     * @return 不可变决策上下文事实
     */
    List<BackendTradeDecisionContextFact> tradeDecisionContexts(String candidateBatchId, int maxCandidates);

    /**
     * 查询建议评估所需权威事实。
     *
     * @param items 待评估候选
     * @return 评估事实
     */
    TradeDecisionEvaluationFacts tradeDecisionEvaluationFacts(List<TradeDecisionEvaluationFactRequest> items);
}
// AI_GENERATE_END -
