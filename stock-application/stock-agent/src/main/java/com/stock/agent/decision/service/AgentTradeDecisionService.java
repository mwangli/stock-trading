// AI_GENERATE_START--------
package com.stock.agent.decision.service;

import com.alibaba.fastjson2.JSON;
import com.stock.agent.dto.TradeDecisionBatchResponse;
import com.stock.agent.dto.TradeDecisionResponse;
import com.stock.agent.decision.dto.AgentTradeDecisionDraft;
import com.stock.agent.decision.entity.AgentTradeDecisionEntity;
import com.stock.agent.decision.repository.AgentTradeDecisionRepository;
import com.stock.agent.decision.validator.AgentTradeDecisionValidator;
import com.stock.agent.decision.vo.TradeDecisionConfidenceBand;
import com.stock.agent.decision.vo.TradeDecisionContext;
import com.stock.agent.decision.vo.TradeDecisionFallbackPolicy;
import com.stock.agent.decision.vo.TradeDecisionFusionResult;
import com.stock.agent.decision.vo.TradeDecisionMode;
import com.stock.agent.decision.vo.TradeDecisionModeResolution;
import com.stock.agent.decision.vo.TradeDecisionRecommendation;
import com.stock.agent.decision.vo.TradeDecisionStatus;
import com.stock.agent.runtime.AgentRuntimeRequest;
import com.stock.agent.runtime.AgentRuntimeResponse;
import com.stock.agent.runtime.AgentRuntimeCoordinator;
import com.stock.agent.service.AgentConversationLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Agent 单候选影子交易判断生成服务。
 * 只消费 Java 固化上下文并保存结构化判断，不接入真实交易执行链。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTradeDecisionService {

    private static final String PROMPT_VERSION = "trade-decision-v1";
    private static final String SYSTEM_USER_SCOPE = "system:trade-decision";
    private static final String SYSTEM_PROMPT = """
            你是候选股票影子交易判断 Agent。只能使用用户消息中的不可变 TradeDecisionContext JSON，禁止调用工具、补充外部事实、修改基线门禁或生成真实委托参数。
            只返回一个 JSON 对象。recommendation 仅允许 BUY、HOLD、SKIP、ABSTAIN；status 仅允许 VALID、ABSTAINED；confidenceBand 仅允许 LOW、MEDIUM、HIGH。
            ABSTAIN 必须对应 ABSTAINED，其他建议必须对应 VALID。suggestedPriority 范围 0-100，allocationMin/allocationMax 满足 0<=min<=max<=1。
            contextId、contextHash、candidateBatchId、stockCode 必须原样返回。所有证据引用必须来自 context.evidenceRefs，evidenceRefs 必须覆盖 supportingEvidenceRefs 和 contraryEvidenceRefs。
            validUntil 不得超过 context.expiresAt。证据不足、数据质量异常或无法形成可靠判断时必须 ABSTAIN。
            JSON 必须包含以下字段且不得使用 Markdown 代码块：summary、status、contextId、contextHash、candidateBatchId、stockCode、recommendation、reasonCodes、thesis、supportingEvidenceRefs、contraryEvidenceRefs、riskFlags、invalidationConditions、suggestedPriority、allocationMin、allocationMax、confidenceBand、validUntil、evidenceRefs、uncertainty。
            """;

    private final TradeDecisionContextService contextService;
    private final AgentRuntimeCoordinator runtimeService;
    private final AgentTradeDecisionValidator validator;
    private final TradeDecisionFusionService fusionService;
    private final TradeDecisionModeService modeService;
    private final AgentTradeDecisionRepository decisionRepository;
    private final AgentConversationLockService conversationLockService;

    /**
     * 为候选批次生成 SHADOW 模式判断并持久化。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量
     * @return 候选批次影子判断
     */
    public TradeDecisionBatchResponse generateShadow(String candidateBatchId, int maxCandidates) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        String normalizedBatchId = candidateBatchId.trim();
        return conversationLockService.execute("trade-decision-batch:" + normalizedBatchId,
                () -> generateLocked(normalizedBatchId, maxCandidates,
                        TradeDecisionMode.SHADOW, TradeDecisionFallbackPolicy.BASELINE_FALLBACK));
    }

    /**
     * 按当前治理配置为候选批次生成判断。
     *
     * @param candidateBatchId 候选批次标识
     * @param maxCandidates 最大候选数量
     * @return 当前有效模式下生成的候选判断
     */
    public TradeDecisionBatchResponse generateConfigured(String candidateBatchId, int maxCandidates) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        String normalizedBatchId = candidateBatchId.trim();
        return conversationLockService.execute("trade-decision-batch:" + normalizedBatchId,
                () -> generateConfiguredLocked(normalizedBatchId, maxCandidates));
    }

    private TradeDecisionBatchResponse generateConfiguredLocked(String candidateBatchId, int maxCandidates) {
        List<TradeDecisionContext> contexts = contextService.buildBatch(candidateBatchId, maxCandidates);
        TradeDecisionModeResolution resolution = modeService.resolve(
                candidateBatchId, contexts.get(0).getStrategyVersion());
        if (resolution.getEffectiveMode() == TradeDecisionMode.OFF) {
            return TradeDecisionBatchResponse.builder()
                    .candidateBatchId(candidateBatchId)
                    .total(0)
                    .items(List.of())
                    .build();
        }
        return generateFromContexts(candidateBatchId, contexts, resolution.getEffectiveMode(),
                resolution.getFallbackPolicy());
    }

    private TradeDecisionBatchResponse generateLocked(String candidateBatchId, int maxCandidates,
                                                       TradeDecisionMode mode,
                                                       TradeDecisionFallbackPolicy fallbackPolicy) {
        return generateFromContexts(candidateBatchId,
                contextService.buildBatch(candidateBatchId, maxCandidates), mode, fallbackPolicy);
    }

    private TradeDecisionBatchResponse generateFromContexts(String candidateBatchId,
                                                             List<TradeDecisionContext> contexts,
                                                             TradeDecisionMode mode,
                                                             TradeDecisionFallbackPolicy fallbackPolicy) {
        List<TradeDecisionResponse> items = contexts.stream()
                .map(context -> generateOne(context, mode, fallbackPolicy))
                .toList();
        return TradeDecisionBatchResponse.builder()
                .candidateBatchId(candidateBatchId.trim())
                .total(items.size())
                .items(items)
                .build();
    }

    /**
     * 查询候选批次已保存的影子判断。
     *
     * @param candidateBatchId 候选批次标识
     * @return 已保存的候选批次判断
     */
    public TradeDecisionBatchResponse listBatch(String candidateBatchId) {
        if (!StringUtils.hasText(candidateBatchId)) {
            throw new IllegalArgumentException("候选批次标识不能为空");
        }
        List<TradeDecisionResponse> items = decisionRepository
                .findByCandidateBatchIdOrderByCreatedAtAsc(candidateBatchId.trim()).stream()
                .map(this::toResponse)
                .toList();
        return TradeDecisionBatchResponse.builder()
                .candidateBatchId(candidateBatchId.trim())
                .total(items.size())
                .items(items)
                .build();
    }

    private TradeDecisionResponse generateOne(TradeDecisionContext context, TradeDecisionMode mode,
                                              TradeDecisionFallbackPolicy fallbackPolicy) {
        return decisionRepository.findByContextHashAndPromptVersionAndDecisionMode(
                        context.getContextHash(), PROMPT_VERSION, mode.name())
                .map(this::toResponse)
                .orElseGet(() -> executeAndSave(context, mode, fallbackPolicy));
    }

    private TradeDecisionResponse executeAndSave(TradeDecisionContext context, TradeDecisionMode mode,
                                                 TradeDecisionFallbackPolicy fallbackPolicy) {
        AgentRuntimeResponse runtimeResponse = null;
        AgentTradeDecisionDraft draft;
        try {
            String taskId = UUID.randomUUID().toString();
            AgentRuntimeRequest request = AgentRuntimeRequest.builder()
                    .taskType("TRADE_DECISION")
                    .sessionId("trade-decision:" + context.getContextId())
                    .taskId(taskId)
                    .userScope(SYSTEM_USER_SCOPE)
                    .systemPrompt(SYSTEM_PROMPT)
                    .userPrompt("请对以下不可变上下文生成影子交易判断，只返回约定 JSON：\n"
                            + JSON.toJSONString(context))
                    .evidenceSummary(JSON.toJSONString(context))
                    .evidenceRefs(context.getEvidenceRefs())
                    .allowedTools(Set.of())
                    .outputType("TRADE_DECISION")
                    .build();
            runtimeResponse = runtimeService.execute(request);
            if (!(runtimeResponse.getStructuredOutput() instanceof AgentTradeDecisionDraft decisionDraft)) {
                String errorCode = StringUtils.hasText(runtimeResponse.getErrorCode())
                        ? runtimeResponse.getErrorCode() : "AGENT_DECISION_UNAVAILABLE";
                draft = fallback(context, errorCode, "Agent 未返回可校验的结构化交易判断");
            } else {
                validator.validate(context, decisionDraft);
                draft = decisionDraft;
            }
        } catch (RuntimeException exception) {
            log.warn("生成影子交易判断失败并回退为 ABSTAIN: contextId={}, causeType={}",
                    context.getContextId(), exception.getClass().getSimpleName());
            String reasonCode = resolveFailureReasonCode(exception);
            draft = fallback(context, reasonCode, "模型调用、解析或上下文校验失败");
        }

        TradeDecisionRecommendation recommendation = TradeDecisionRecommendation.valueOf(draft.getRecommendation());
        LocalDateTime createdAt = LocalDateTime.now();
        TradeDecisionFusionResult fusion = fusionService.fuse(
                context.isBaselineEligible(), recommendation,
                TradeDecisionStatus.valueOf(draft.getStatus()), draft.getValidUntil(),
                mode, fallbackPolicy, createdAt);
        AgentTradeDecisionEntity entity = new AgentTradeDecisionEntity()
                .setDecisionId(UUID.randomUUID().toString())
                .setContextId(context.getContextId())
                .setContextHash(context.getContextHash())
                .setCandidateBatchId(context.getCandidateBatchId())
                .setStockCode(context.getStockCode())
                .setStrategyVersion(context.getStrategyVersion())
                .setTargetTradeDate(context.getTargetTradeDate())
                .setRecommendation(recommendation.name())
                .setReasonCodes(JSON.toJSONString(draft.getReasonCodes()))
                .setSummary(limit(draft.getSummary(), 500))
                .setThesis(draft.getThesis())
                .setSupportingEvidenceRefs(JSON.toJSONString(draft.getSupportingEvidenceRefs()))
                .setContraryEvidenceRefs(JSON.toJSONString(draft.getContraryEvidenceRefs()))
                .setRiskFlags(JSON.toJSONString(draft.getRiskFlags()))
                .setInvalidationConditions(JSON.toJSONString(draft.getInvalidationConditions()))
                .setSuggestedPriority(draft.getSuggestedPriority())
                .setAllocationMin(draft.getAllocationMin())
                .setAllocationMax(draft.getAllocationMax())
                .setConfidenceBand(draft.getConfidenceBand())
                .setDecisionMode(mode.name())
                .setStatus(draft.getStatus())
                .setFusionOutcome(fusion.getOutcome().name())
                .setImpacted(fusion.isImpacted())
                .setFusionReason(limit(fusion.getReason(), 500))
                .setProvider(runtimeResponse == null ? null : runtimeResponse.getProvider())
                .setModel(runtimeResponse == null ? null : runtimeResponse.getModel())
                .setPromptVersion(PROMPT_VERSION)
                .setUncertainty(draft.getUncertainty())
                .setValidUntil(draft.getValidUntil())
                .setCreatedAt(createdAt);
        decisionRepository.save(entity);
        log.info("Agent 交易判断已保存: decisionId={}, contextId={}, stockCode={}, mode={}, recommendation={}, status={}, impacted={}",
                entity.getDecisionId(), context.getContextId(), context.getStockCode(),
                entity.getDecisionMode(), entity.getRecommendation(), entity.getStatus(), entity.isImpacted());
        return toResponse(entity);
    }

    private AgentTradeDecisionDraft fallback(TradeDecisionContext context, String reasonCode, String summary) {
        AgentTradeDecisionDraft draft = new AgentTradeDecisionDraft();
        draft.setSummary(summary);
        draft.setStatus(TradeDecisionStatus.ABSTAINED.name());
        draft.setContextId(context.getContextId());
        draft.setContextHash(context.getContextHash());
        draft.setCandidateBatchId(context.getCandidateBatchId());
        draft.setStockCode(context.getStockCode());
        draft.setRecommendation(TradeDecisionRecommendation.ABSTAIN.name());
        draft.setReasonCodes(List.of(reasonCode));
        draft.setThesis("Agent 未形成有效判断，Java 融合继续保持确定性基线。");
        draft.setSupportingEvidenceRefs(List.of());
        draft.setContraryEvidenceRefs(List.of());
        draft.setRiskFlags(List.of("AGENT_UNAVAILABLE"));
        draft.setInvalidationConditions(List.of("重新生成新的不可变决策上下文后方可重试"));
        draft.setSuggestedPriority(0);
        draft.setAllocationMin(0D);
        draft.setAllocationMax(0D);
        draft.setConfidenceBand(TradeDecisionConfidenceBand.LOW.name());
        draft.setValidUntil(context.getExpiresAt());
        draft.setEvidenceRefs(List.of());
        draft.setUncertainty("Provider、结构化解析或上下文校验未完成，当前结果为显式弃权。");
        return draft;
    }

    private TradeDecisionResponse toResponse(AgentTradeDecisionEntity entity) {
        return TradeDecisionResponse.builder()
                .decisionId(entity.getDecisionId())
                .contextId(entity.getContextId())
                .contextHash(entity.getContextHash())
                .candidateBatchId(entity.getCandidateBatchId())
                .stockCode(entity.getStockCode())
                .recommendation(entity.getRecommendation())
                .status(entity.getStatus())
                .summary(entity.getSummary())
                .reasonCodes(parseStringList(entity.getReasonCodes()))
                .thesis(entity.getThesis())
                .supportingEvidenceRefs(parseStringList(entity.getSupportingEvidenceRefs()))
                .contraryEvidenceRefs(parseStringList(entity.getContraryEvidenceRefs()))
                .riskFlags(parseStringList(entity.getRiskFlags()))
                .invalidationConditions(parseStringList(entity.getInvalidationConditions()))
                .suggestedPriority(entity.getSuggestedPriority())
                .allocationMin(entity.getAllocationMin())
                .allocationMax(entity.getAllocationMax())
                .confidenceBand(entity.getConfidenceBand())
                .decisionMode(entity.getDecisionMode())
                .fusionOutcome(entity.getFusionOutcome())
                .impacted(entity.isImpacted())
                .fusionReason(entity.getFusionReason())
                .provider(entity.getProvider())
                .model(entity.getModel())
                .promptVersion(entity.getPromptVersion())
                .uncertainty(entity.getUncertainty())
                .validUntil(entity.getValidUntil())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        return JSON.parseArray(json, String.class);
    }

    private String limit(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private String resolveFailureReasonCode(RuntimeException exception) {
        String type = exception.getClass().getSimpleName().toLowerCase();
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return type.contains("timeout") || message.contains("timeout") || message.contains("超时")
                ? "AGENT_PROVIDER_TIMEOUT" : "AGENT_DECISION_INVALID";
    }
}
// AI_GENERATE_END--------
