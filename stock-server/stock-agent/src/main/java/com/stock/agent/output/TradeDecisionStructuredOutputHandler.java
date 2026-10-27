// AI_GENERATE_START ---
package com.stock.agent.output;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.dto.AgentTradeDecisionDraft;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 交易建议结构化输出处理器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class TradeDecisionStructuredOutputHandler implements AgentStructuredOutputHandler {

    private static final Set<String> ALLOWED_STATUS = Set.of("VALID", "ABSTAINED");

    /** {@inheritDoc} */
    @Override
    public boolean supports(String outputType) { return "TRADE_DECISION".equals(outputType); }

    /** {@inheritDoc} */
    @Override
    public AgentStructuredOutput parseAndValidate(String outputType, String content) {
        AgentTradeDecisionDraft draft = JSON.parseObject(content, AgentTradeDecisionDraft.class);
        if (draft == null || !StringUtils.hasText(draft.getSummary())
                || !ALLOWED_STATUS.contains(draft.getStatus())
                || !StringUtils.hasText(draft.getContextId())
                || !StringUtils.hasText(draft.getContextHash())
                || !StringUtils.hasText(draft.getCandidateBatchId())
                || !StringUtils.hasText(draft.getStockCode())
                || !StringUtils.hasText(draft.getRecommendation())
                || !StringUtils.hasText(draft.getConfidenceBand())
                || draft.getReasonCodes() == null || draft.getSupportingEvidenceRefs() == null
                || draft.getContraryEvidenceRefs() == null || draft.getRiskFlags() == null
                || draft.getInvalidationConditions() == null || draft.getValidUntil() == null
                || draft.getEvidenceRefs() == null) {
            throw new IllegalArgumentException("交易决策结构化输出缺少必要字段");
        }
        return draft;
    }
}
// AI_GENERATE_END --