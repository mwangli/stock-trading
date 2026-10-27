// AI_GENERATE_START----------------
package com.stock.agent.service;

import com.stock.agent.enums.AgentTaskType;

import com.stock.agent.dto.TradePlanDraftRequest;
import com.stock.agent.dto.TradePlanCreateRequest;
import com.stock.agent.dto.TradePlanResponse;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.entity.AgentSessionEntity;
import com.stock.agent.model.EvidenceBundle;
import com.stock.agent.model.AgentRuntimeRequest;
import com.stock.agent.model.AgentRuntimeResponse;
import com.stock.agent.dto.TradePlanDraft;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * TradePlanDraft 生成服务。
 * 只负责生成和校验结构化草案，实际执行继续由 stock-trading 负责。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradePlanAgentService {

    private final AgentSessionService sessionService;
    private final AgentProperties agentProperties;
    private final AgentRuntimeCoordinator runtimeService;
    private final EvidenceAssembler evidenceAssembler;
    private final TradePlanService tradePlanService;

    /**
     * 生成并保存待重新复核的交易计划草案。
     *
     * @param request 生成请求
     * @param userScope 服务端认证用户范围
     * @return 待人工审批的交易计划响应
     */
    public TradePlanResponse generate(TradePlanDraftRequest request, String userScope) {
        if (request == null || !StringUtils.hasText(request.getCandidateBatchId())) {
            throw new IllegalArgumentException("候选批次不能为空");
        }
        if (!agentProperties.isTradePlanEnabled()) {
            throw new IllegalStateException("TradePlan 功能当前未启用");
        }
        AgentSessionEntity session = sessionService.ensure(
                request.getSessionId(), userScope);
        String question = StringUtils.hasText(request.getQuestion())
                ? request.getQuestion().trim() : "基于当前候选批次生成交易计划草案";
        String taskId = UUID.randomUUID().toString();
        long startedAt = System.nanoTime();
        log.info("[Agent][Progress] taskId={} sessionId={} type=TRADE_PLAN progress=1/4 stage=TASK_STARTED candidateBatchId={} questionLength={}",
                taskId, session.getSessionId(), request.getCandidateBatchId(), question.length());
        EvidenceBundle evidence = evidenceAssembler.assemble(AgentTaskType.TRADE_PLAN,
                UUID.randomUUID().toString(), session.getSessionId(), taskId,
                session.getUserScope(), request.getCandidateBatchId(), null);
        validateEvidence(evidence, request);
        log.info("[Agent][Progress] taskId={} sessionId={} type=TRADE_PLAN progress=2/4 stage=EVIDENCE_READY evidenceCount={} evidenceRefCount={}",
                taskId, session.getSessionId(), evidence.getItems().size(), evidence.getEvidenceRefs().size());
        AgentRuntimeRequest runtimeRequest = AgentRuntimeRequest.builder()
                .taskType("TRADE_PLAN")
                .sessionId(session.getSessionId())
                .taskId(taskId)
                .userScope(session.getUserScope())
                .systemPrompt("你是交易计划草案 Agent。只能根据证据生成 PENDING_APPROVAL 草案，不得生成券商订单号、凭据或直接执行指令。只返回 TradePlanDraft JSON。")
                .userPrompt(question + "\n候选批次=" + request.getCandidateBatchId()
                        + "\n股票代码=" + (request.getStockCode() == null ? "" : request.getStockCode())
                        + "\n证据：" + evidence.getSummary())
                .evidenceSummary(evidence.getSummary())
                .evidenceRefs(evidence.getEvidenceRefs())
                .allowedTools(Set.of())
                .outputType("TRADE_PLAN")
                .build();
        log.info("[Agent][Progress] taskId={} sessionId={} type=TRADE_PLAN progress=3/4 stage=MODEL_CALL message=开始调用模型",
                taskId, session.getSessionId());
        AgentRuntimeResponse response = runtimeService.execute(runtimeRequest);
        if (response.getStructuredOutput() == null || !(response.getStructuredOutput() instanceof TradePlanDraft draft)) {
            throw new IllegalStateException(response.getErrorCode() == null
                    ? "Agent 未返回有效交易计划草案" : response.getErrorCode());
        }
        validateDraft(draft, evidence, request);
        TradePlanCreateRequest createRequest = new TradePlanCreateRequest();
        createRequest.setCandidateBatchId(draft.getCandidateBatchId());
        createRequest.setPredictionBatchId(draft.getPredictionBatchId());
        createRequest.setStockCode(draft.getStockCode());
        createRequest.setDirection(draft.getDirection());
        createRequest.setPlannedAmount(draft.getPlannedAmount());
        createRequest.setPlannedQuantity(draft.getPlannedQuantity());
        createRequest.setEvidenceRefs(draft.getEvidenceRefs());
        createRequest.setExpiresAt(draft.getExpiresAt());
        createRequest.setIdempotencyKey(draft.getIdempotencyKey());
        createRequest.setTargetTradeDate(draft.getTargetTradeDate() == null ? LocalDate.now() : draft.getTargetTradeDate());
        createRequest.setStrategyVersion(draft.getStrategyVersion());
        createRequest.setPriceConstraint(draft.getPriceConstraint());
        createRequest.setReason(draft.getReason());
        createRequest.setRiskWarnings(draft.getRiskWarnings());
        createRequest.setUncertainty(draft.getUncertainty());
        TradePlanResponse result = tradePlanService.create(createRequest, session.getUserScope());
        log.info("[Agent][Progress] taskId={} sessionId={} type=TRADE_PLAN progress=4/4 stage=TASK_FINISHED status=COMPLETED elapsedMs={}",
                taskId, session.getSessionId(), elapsedMillis(startedAt));
        return result;
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private void validateEvidence(EvidenceBundle evidence, TradePlanDraftRequest request) {
        if (evidence.getItems().stream().anyMatch(item -> Set.of("UNKNOWN", "FAILED", "EXPIRED").contains(item.getStatus()))) {
            throw new IllegalStateException("交易计划所需事实证据不可用，暂不生成草案");
        }
        if (!StringUtils.hasText(evidence.getPredictionBatchId())
                || !StringUtils.hasText(evidence.getStrategyVersion())
                || evidence.getTargetTradeDate() == null) {
            throw new IllegalStateException("候选批次缺少预测批次、策略版本或目标交易日");
        }
        if (StringUtils.hasText(request.getStockCode())
                && evidence.getItems().stream().noneMatch(item -> item.getSummary() != null
                && item.getSummary().contains(request.getStockCode().trim()))) {
            throw new IllegalArgumentException("股票代码不属于当前候选批次");
        }
    }

    private void validateDraft(TradePlanDraft draft, EvidenceBundle evidence,
                               TradePlanDraftRequest request) {
        if (!request.getCandidateBatchId().trim().equals(draft.getCandidateBatchId())
                || !evidence.getPredictionBatchId().equals(draft.getPredictionBatchId())
                || !evidence.getStrategyVersion().equals(draft.getStrategyVersion())
                || !evidence.getTargetTradeDate().equals(draft.getTargetTradeDate())) {
            throw new IllegalArgumentException("模型返回的交易计划与候选批次事实不一致");
        }
        if (StringUtils.hasText(request.getStockCode())
                && !request.getStockCode().trim().equals(draft.getStockCode())) {
            throw new IllegalArgumentException("模型返回的股票代码与请求上下文不一致");
        }
    }
}
// AI_GENERATE_END---------------