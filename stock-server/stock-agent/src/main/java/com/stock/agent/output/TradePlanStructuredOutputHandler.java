// AI_GENERATE_START ---
package com.stock.agent.output;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.dto.TradePlanDraft;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 交易计划草案结构化输出处理器。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class TradePlanStructuredOutputHandler implements AgentStructuredOutputHandler {

    /** {@inheritDoc} */
    @Override
    public boolean supports(String outputType) { return "TRADE_PLAN".equals(outputType); }

    /** {@inheritDoc} */
    @Override
    public AgentStructuredOutput parseAndValidate(String outputType, String content) {
        TradePlanDraft draft = JSON.parseObject(content, TradePlanDraft.class);
        if (draft == null || !StringUtils.hasText(draft.getSummary())
                || !"PENDING_APPROVAL".equals(draft.getStatus())
                || !StringUtils.hasText(draft.getCandidateBatchId())
                || !StringUtils.hasText(draft.getPredictionBatchId())
                || !StringUtils.hasText(draft.getStockCode())
                || draft.getTargetTradeDate() == null
                || !StringUtils.hasText(draft.getStrategyVersion())
                || (!"BUY".equalsIgnoreCase(draft.getDirection()) && !"SELL".equalsIgnoreCase(draft.getDirection()))
                || draft.getExpiresAt() == null || !draft.getExpiresAt().isAfter(java.time.LocalDateTime.now())
                || draft.getEvidenceRefs() == null || draft.getEvidenceRefs().isEmpty()
                || !StringUtils.hasText(draft.getIdempotencyKey())) {
            throw new IllegalArgumentException("交易计划草案字段或状态非法");
        }
        if (draft.getPlannedAmount() == null || draft.getPlannedAmount().signum() <= 0) {
            throw new IllegalArgumentException("交易计划必须包含正数金额");
        }
        if ("SELL".equalsIgnoreCase(draft.getDirection())
                && (draft.getPlannedQuantity() == null || draft.getPlannedQuantity() <= 0
                || draft.getPlannedQuantity() % 100 != 0)) {
            throw new IllegalArgumentException("卖出交易计划必须包含有效数量");
        }
        return draft;
    }
}
// AI_GENERATE_END --