// AI_GENERATE_START -
package com.stock.strategyAgent.decision;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Agent 交易判断上下文一致性和范围校验器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class AgentTradeDecisionValidator {

    /**
     * 校验模型判断是否严格绑定当前不可变上下文。
     *
     * @param context 服务端固化的决策上下文
     * @param draft 模型结构化判断
     * @throws IllegalArgumentException 当身份、证据、枚举、范围或有效期不合法时抛出
     */
    public void validate(TradeDecisionContext context, AgentTradeDecisionDraft draft) {
        if (context == null || draft == null) {
            throw new IllegalArgumentException("交易决策上下文和模型判断不能为空");
        }
        if (!context.getContextId().equals(draft.getContextId())
                || !context.getContextHash().equals(draft.getContextHash())
                || !context.getCandidateBatchId().equals(draft.getCandidateBatchId())
                || !context.getStockCode().equals(draft.getStockCode())) {
            throw new IllegalArgumentException("模型判断与不可变上下文身份不一致");
        }

        TradeDecisionRecommendation recommendation = parseRecommendation(draft.getRecommendation());
        parseConfidenceBand(draft.getConfidenceBand());
        validateStatus(recommendation, draft.getStatus());
        validatePriority(draft.getSuggestedPriority());
        validateAllocation(draft.getAllocationMin(), draft.getAllocationMax());
        validateValidity(context, draft.getValidUntil());
        validateReasonCodes(draft.getReasonCodes());
        validateEvidence(context, draft);
    }

    private TradeDecisionRecommendation parseRecommendation(String recommendation) {
        try {
            return TradeDecisionRecommendation.valueOf(recommendation);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Agent recommendation 非法", exception);
        }
    }

    private void parseConfidenceBand(String confidenceBand) {
        try {
            TradeDecisionConfidenceBand.valueOf(confidenceBand);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Agent confidenceBand 非法", exception);
        }
    }

    private void validateStatus(TradeDecisionRecommendation recommendation, String status) {
        if (recommendation == TradeDecisionRecommendation.ABSTAIN
                && !TradeDecisionStatus.ABSTAINED.name().equals(status)) {
            throw new IllegalArgumentException("ABSTAIN 建议必须使用 ABSTAINED 状态");
        }
        if (recommendation != TradeDecisionRecommendation.ABSTAIN
                && !TradeDecisionStatus.VALID.name().equals(status)) {
            throw new IllegalArgumentException("非 ABSTAIN 建议必须使用 VALID 状态");
        }
    }

    private void validatePriority(Integer priority) {
        if (priority == null || priority < 0 || priority > 100) {
            throw new IllegalArgumentException("建议优先级必须在 0 至 100 之间");
        }
    }

    private void validateAllocation(Double allocationMin, Double allocationMax) {
        if (allocationMin == null || allocationMax == null
                || !Double.isFinite(allocationMin) || !Double.isFinite(allocationMax)
                || allocationMin < 0D || allocationMax > 1D || allocationMin > allocationMax) {
            throw new IllegalArgumentException("建议仓位区间必须满足 0 <= min <= max <= 1");
        }
    }

    private void validateValidity(TradeDecisionContext context, LocalDateTime validUntil) {
        LocalDateTime now = LocalDateTime.now();
        if (validUntil == null || !validUntil.isAfter(now)
                || validUntil.isAfter(context.getExpiresAt())) {
            throw new IllegalArgumentException("Agent 判断有效期必须晚于当前时间且不得超过上下文有效期");
        }
    }

    private void validateReasonCodes(List<String> reasonCodes) {
        if (reasonCodes == null || reasonCodes.isEmpty()
                || reasonCodes.stream().anyMatch(code -> !StringUtils.hasText(code) || code.length() > 64)) {
            throw new IllegalArgumentException("Agent 判断必须包含长度受限的原因码");
        }
    }

    private void validateEvidence(TradeDecisionContext context, AgentTradeDecisionDraft draft) {
        Set<String> allowed = Set.copyOf(context.getEvidenceRefs());
        Set<String> referenced = new HashSet<>();
        referenced.addAll(requireEvidenceList(draft.getEvidenceRefs(), "evidenceRefs"));
        referenced.addAll(requireEvidenceList(draft.getSupportingEvidenceRefs(), "supportingEvidenceRefs"));
        referenced.addAll(requireEvidenceList(draft.getContraryEvidenceRefs(), "contraryEvidenceRefs"));
        if (!allowed.containsAll(referenced)) {
            throw new IllegalArgumentException("Agent 判断包含上下文之外的证据引用");
        }
        if (draft.getEvidenceRefs().isEmpty()
                || !new HashSet<>(draft.getEvidenceRefs()).containsAll(draft.getSupportingEvidenceRefs())
                || !new HashSet<>(draft.getEvidenceRefs()).containsAll(draft.getContraryEvidenceRefs())) {
            throw new IllegalArgumentException("evidenceRefs 必须覆盖支持证据和反证引用");
        }
    }

    private List<String> requireEvidenceList(List<String> refs, String fieldName) {
        if (refs == null || refs.stream().anyMatch(ref -> !StringUtils.hasText(ref))) {
            throw new IllegalArgumentException(fieldName + " 包含空证据编号");
        }
        return refs;
    }
}
// AI_GENERATE_END -
