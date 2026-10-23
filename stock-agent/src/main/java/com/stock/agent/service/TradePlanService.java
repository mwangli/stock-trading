// AI_GENERATE_START------------
package com.stock.agent.service;

import com.stock.agent.dto.TradePlanCreateRequest;
import com.stock.agent.dto.TradePlanResponse;
import com.stock.agent.dto.TradePlanReviewRequest;
import com.stock.agent.config.AgentProperties;
import com.stock.agent.domain.AgentEnums;
import com.stock.agent.entity.AgentTradePlanEntity;
import com.stock.agent.repository.AgentTradePlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import com.alibaba.fastjson2.JSON;
import java.util.List;
import java.util.UUID;

/**
 * 自动交易计划应用服务。
 * 负责计划幂等落库和门禁状态转换，实际行情、账户、持仓与风险复核必须继续由 stock-trading 执行。
 *
 * @author mwangli
 * @since 2026-10-17
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradePlanService {

    private final AgentTradePlanRepository repository;
    private final TradePlanValidator validator;
    private final AgentProperties agentProperties;
    private final TradePlanStateMachine stateMachine;

    /**
     * 创建或幂等返回一条自动交易计划。
     *
     * @param request 创建请求
     * @param userScope 服务端认证用户范围
     * @return 交易计划响应
     */
    public TradePlanResponse create(TradePlanCreateRequest request, String userScope) {
        if (!agentProperties.isTradePlanEnabled()) {
            throw new IllegalStateException("TradePlan 功能当前未启用");
        }
        validator.validate(request);
        String ownedScope = requireUserScope(userScope);
        AgentTradePlanEntity existing = repository
                .findByIdempotencyKeyAndUserScope(request.getIdempotencyKey(), ownedScope).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        AgentTradePlanEntity entity = new AgentTradePlanEntity()
                .setPlanId(UUID.randomUUID().toString())
                .setUserScope(ownedScope)
                .setCandidateBatchId(request.getCandidateBatchId().trim())
                .setPredictionBatchId(request.getPredictionBatchId())
                .setStockCode(request.getStockCode().trim())
                .setDirection(request.getDirection().trim().toUpperCase())
                .setPlannedAmount(request.getPlannedAmount())
                .setPlannedQuantity(request.getPlannedQuantity())
                .setEvidenceRefs(String.join(",", request.getEvidenceRefs()))
                .setExpiresAt(request.getExpiresAt())
                .setIdempotencyKey(request.getIdempotencyKey().trim())
                .setStatus(AgentEnums.TradePlanStatus.PENDING_APPROVAL)
                .setVersion(0L)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setDecisionMessage("等待所属用户人工审批，尚未进入执行队列");
        entity.setTargetTradeDate(request.getTargetTradeDate())
                .setStrategyVersion(request.getStrategyVersion())
                .setPriceConstraint(request.getPriceConstraint())
                .setReason(request.getReason())
                .setRiskWarnings(request.getRiskWarnings() == null ? null : JSON.toJSONString(request.getRiskWarnings()))
                .setUncertainty(request.getUncertainty());
        return toResponse(repository.save(entity));
    }


    /**
     * 查询交易计划。
     *
     * @param planId 计划标识
     * @param userScope 服务端认证用户范围
     * @return 交易计划响应
     */
    public TradePlanResponse get(String planId, String userScope) {
        return toResponse(find(planId, userScope));
    }

    /**
     * 所属用户人工批准交易计划。
     *
     * @param planId 计划标识
     * @param request 审批请求
     * @param userScope 服务端认证用户范围
     * @return 批准后的计划
     */
    public TradePlanResponse approve(String planId, TradePlanReviewRequest request, String userScope) {
        AgentTradePlanEntity entity = find(planId, userScope);
        requireReviewVersion(entity, request);
        if (entity.getStatus() != AgentEnums.TradePlanStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("只有待审批交易计划可以批准");
        }
        LocalDateTime now = LocalDateTime.now();
        if (entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(now)) {
            stateMachine.requireTransition(entity.getStatus(), AgentEnums.TradePlanStatus.EXPIRED);
            entity.setStatus(AgentEnums.TradePlanStatus.EXPIRED)
                    .setDecisionMessage("人工审批时交易计划已过期")
                    .setUpdatedAt(now);
            return toResponse(repository.save(entity));
        }
        stateMachine.requireTransition(entity.getStatus(), AgentEnums.TradePlanStatus.APPROVED);
        entity.setStatus(AgentEnums.TradePlanStatus.APPROVED)
                .setApprovedBy(requireUserScope(userScope))
                .setApprovedAt(now)
                .setReviewComment(limitComment(request.getComment()))
                .setDecisionMessage("人工审批通过，等待计划级复核")
                .setUpdatedAt(now);
        log.info("[Agent] 交易计划人工审批通过: planId={}, userScope={}", planId, entity.getUserScope());
        return toResponse(repository.save(entity));
    }

    /**
     * 所属用户人工拒绝交易计划。
     *
     * @param planId 计划标识
     * @param request 拒绝请求
     * @param userScope 服务端认证用户范围
     * @return 拒绝后的计划
     */
    public TradePlanResponse reject(String planId, TradePlanReviewRequest request, String userScope) {
        AgentTradePlanEntity entity = find(planId, userScope);
        requireReviewVersion(entity, request);
        stateMachine.requireTransition(entity.getStatus(), AgentEnums.TradePlanStatus.REJECTED);
        LocalDateTime now = LocalDateTime.now();
        entity.setStatus(AgentEnums.TradePlanStatus.REJECTED)
                .setRejectedBy(requireUserScope(userScope))
                .setRejectedAt(now)
                .setReviewComment(limitComment(request.getComment()))
                .setDecisionMessage("交易计划已被人工拒绝")
                .setUpdatedAt(now);
        log.info("[Agent] 交易计划已人工拒绝: planId={}, userScope={}", planId, entity.getUserScope());
        return toResponse(repository.save(entity));
    }

    private AgentTradePlanEntity find(String planId, String userScope) {
        if (!StringUtils.hasText(planId)) {
            throw new IllegalArgumentException("交易计划标识不能为空");
        }
        return repository.findByPlanIdAndUserScope(planId.trim(), requireUserScope(userScope))
                .orElseThrow(() -> new IllegalArgumentException("交易计划不存在"));
    }

    private String requireUserScope(String userScope) {
        if (!StringUtils.hasText(userScope)) {
            throw new IllegalArgumentException("交易计划用户范围不能为空");
        }
        return userScope.trim();
    }

    private void requireReviewVersion(AgentTradePlanEntity entity, TradePlanReviewRequest request) {
        if (request == null || request.getVersion() == null
                || !request.getVersion().equals(entity.getVersion())) {
            throw new IllegalStateException("交易计划版本已变化，请刷新后重新审批");
        }
    }

    private String limitComment(String comment) {
        if (!StringUtils.hasText(comment)) {
            return null;
        }
        String trimmed = comment.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }

    private TradePlanResponse toResponse(AgentTradePlanEntity entity) {
        List<String> evidenceRefs = StringUtils.hasText(entity.getEvidenceRefs())
                ? List.of(entity.getEvidenceRefs().split(",")) : List.of();
        return TradePlanResponse.builder()
                .planId(entity.getPlanId())
                .candidateBatchId(entity.getCandidateBatchId())
                .predictionBatchId(entity.getPredictionBatchId())
                .stockCode(entity.getStockCode())
                .direction(entity.getDirection())
                .plannedAmount(entity.getPlannedAmount())
                .plannedQuantity(entity.getPlannedQuantity())
                .evidenceRefs(evidenceRefs)
                .expiresAt(entity.getExpiresAt())
                .idempotencyKey(entity.getIdempotencyKey())
                .status(entity.getStatus().name())
                .decisionMessage(entity.getDecisionMessage())
                .updatedAt(entity.getUpdatedAt())
                .version(entity.getVersion())
                .targetTradeDate(entity.getTargetTradeDate())
                .strategyVersion(entity.getStrategyVersion())
                .priceConstraint(entity.getPriceConstraint())
                .reason(entity.getReason())
                .riskWarnings(StringUtils.hasText(entity.getRiskWarnings())
                        ? JSON.parseArray(entity.getRiskWarnings(), String.class) : List.of())
                .uncertainty(entity.getUncertainty())
                .approvedBy(entity.getApprovedBy())
                .approvedAt(entity.getApprovedAt())
                .rejectedBy(entity.getRejectedBy())
                .rejectedAt(entity.getRejectedAt())
                .reviewComment(entity.getReviewComment())
                .build();
    }
}
// AI_GENERATE_END------------
