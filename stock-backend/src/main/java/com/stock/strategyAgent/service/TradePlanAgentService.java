// AI_GENERATE_START --
package com.stock.strategyAgent.service;

import com.stock.strategyAgent.api.AgentDtos;
import com.stock.strategyAgent.api.TradePlanDto;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentSessionEntity;
import com.stock.strategyAgent.evidence.EvidenceAssembler;
import com.stock.strategyAgent.evidence.EvidenceBundle;
import com.stock.strategyAgent.runtime.AgentRuntimeRequest;
import com.stock.strategyAgent.runtime.AgentRuntimeResponse;
import com.stock.strategyAgent.runtime.AgentRuntimeService;
import com.stock.strategyAgent.runtime.TradePlanDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * TradePlanDraft 生成服务。
 * 只负责生成和校验结构化草案，实际执行继续由 tradingExecutor 负责。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Service
@RequiredArgsConstructor
public class TradePlanAgentService {

    private final AgentApplicationService applicationService;
    private final AgentRuntimeService runtimeService;
    private final EvidenceAssembler evidenceAssembler;
    private final TradePlanService tradePlanService;

    /**
     * 生成并保存待重新复核的交易计划草案。
     *
     * @param request 生成请求
     * @return 交易计划响应
     */
    @Transactional
    public TradePlanDto.Response generate(AgentDtos.TradePlanDraftRequest request) {
        if (request == null || !StringUtils.hasText(request.getCandidateBatchId())) {
            throw new IllegalArgumentException("候选批次不能为空");
        }
        AgentSessionEntity session = applicationService.ensureSessionForTradePlan(
                request.getSessionId(), request.getUserScope());
        String question = StringUtils.hasText(request.getQuestion())
                ? request.getQuestion().trim() : "基于当前候选批次生成交易计划草案";
        EvidenceBundle evidence = evidenceAssembler.assemble(AgentEnums.TaskType.TRADE_PLAN,
                UUID.randomUUID().toString(), session.getSessionId(), UUID.randomUUID().toString(),
                session.getUserScope(), request.getCandidateBatchId(), null);
        validateEvidence(evidence, request);
        AgentRuntimeRequest runtimeRequest = AgentRuntimeRequest.builder()
                .taskType("TRADE_PLAN")
                .sessionId(session.getSessionId())
                .taskId(UUID.randomUUID().toString())
                .userScope(session.getUserScope())
                .systemPrompt("你是交易计划草案 Agent。只能根据证据生成 PENDING_REVALIDATION 草案，不得生成券商订单号、凭据或直接执行指令。只返回 TradePlanDraft JSON。")
                .userPrompt(question + "\n候选批次=" + request.getCandidateBatchId()
                        + "\n股票代码=" + (request.getStockCode() == null ? "" : request.getStockCode())
                        + "\n证据：" + evidence.getSummary())
                .evidenceSummary(evidence.getSummary())
                .evidenceRefs(evidence.getEvidenceRefs())
                .outputType("TRADE_PLAN")
                .build();
        AgentRuntimeResponse response = runtimeService.execute(runtimeRequest);
        if (response.getStructuredOutput() == null || !(response.getStructuredOutput() instanceof TradePlanDraft draft)) {
            throw new IllegalStateException(response.getErrorCode() == null
                    ? "Agent 未返回有效交易计划草案" : response.getErrorCode());
        }
        validateDraft(draft, evidence, request);
        TradePlanDto.CreateRequest createRequest = new TradePlanDto.CreateRequest();
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
        return tradePlanService.create(createRequest);
    }

    private void validateEvidence(EvidenceBundle evidence, AgentDtos.TradePlanDraftRequest request) {
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
                               AgentDtos.TradePlanDraftRequest request) {
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
// AI_GENERATE_END --
