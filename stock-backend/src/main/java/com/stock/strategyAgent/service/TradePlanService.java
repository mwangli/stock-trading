// AI_GENERATE_START --
package com.stock.strategyAgent.service;

import com.stock.strategyAgent.api.TradePlanDto;
import com.stock.strategyAgent.config.AgentProperties;
import com.stock.strategyAgent.domain.AgentEnums;
import com.stock.strategyAgent.domain.entity.AgentTradePlanEntity;
import com.stock.strategyAgent.persistence.AgentTradePlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 自动交易计划应用服务。
 * 负责计划幂等落库和门禁状态转换，实际行情、账户、持仓与风险复核必须继续由 tradingExecutor 执行。
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

    /**
     * 创建或幂等返回一条自动交易计划。
     *
     * @param request 创建请求
     * @return 交易计划响应
     */
    @Transactional
    public TradePlanDto.Response create(TradePlanDto.CreateRequest request) {
        if (!agentProperties.isTradePlanEnabled()) {
            throw new IllegalStateException("TradePlan 功能当前未启用");
        }
        validator.validate(request);
        AgentTradePlanEntity existing = repository.findByIdempotencyKey(request.getIdempotencyKey()).orElse(null);
        if (existing != null) {
            return toResponse(existing);
        }
        LocalDateTime now = LocalDateTime.now();
        AgentTradePlanEntity entity = new AgentTradePlanEntity()
                .setPlanId(UUID.randomUUID().toString())
                .setCandidateBatchId(request.getCandidateBatchId().trim())
                .setStockCode(request.getStockCode().trim())
                .setDirection(request.getDirection().trim().toUpperCase())
                .setPlannedAmount(request.getPlannedAmount())
                .setPlannedQuantity(request.getPlannedQuantity())
                .setEvidenceRefs(String.join(",", request.getEvidenceRefs()))
                .setExpiresAt(request.getExpiresAt())
                .setIdempotencyKey(request.getIdempotencyKey().trim())
                .setStatus(AgentEnums.TradePlanStatus.PENDING_REVALIDATION)
                .setCreatedAt(now)
                .setUpdatedAt(now)
                .setDecisionMessage("等待 tradingExecutor 重新读取当前事实并执行 Java 风控");
        return toResponse(repository.save(entity));
    }

    /**
     * 重新执行计划级门禁检查并返回最新状态。
     * 实时行情、账户、持仓、交易日历、重复委托和 RiskController 校验由后续 tradingExecutor 执行阶段负责。
     *
     * @param planId 计划标识
     * @return 最新计划响应
     */
    @Transactional
    public TradePlanDto.Response revalidate(String planId) {
        AgentTradePlanEntity entity = find(planId);
        LocalDateTime now = LocalDateTime.now();
        if (entity.getStatus() != AgentEnums.TradePlanStatus.PENDING_REVALIDATION) {
            return toResponse(entity);
        }
        if (entity.getExpiresAt() == null || !entity.getExpiresAt().isAfter(now)) {
            entity.setStatus(AgentEnums.TradePlanStatus.EXPIRED)
                    .setDecisionMessage("交易计划已过期");
        } else {
            entity.setDecisionMessage("计划级校验通过，等待 tradingExecutor 当前事实复核");
        }
        entity.setUpdatedAt(now);
        log.info("[Agent] 交易计划重新校验: planId={}, status={}", planId, entity.getStatus());
        return toResponse(repository.save(entity));
    }

    /**
     * 查询交易计划。
     *
     * @param planId 计划标识
     * @return 交易计划响应
     */
    @Transactional(readOnly = true)
    public TradePlanDto.Response get(String planId) {
        return toResponse(find(planId));
    }

    private AgentTradePlanEntity find(String planId) {
        if (!StringUtils.hasText(planId)) {
            throw new IllegalArgumentException("交易计划标识不能为空");
        }
        return repository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("交易计划不存在: " + planId));
    }

    private TradePlanDto.Response toResponse(AgentTradePlanEntity entity) {
        List<String> evidenceRefs = StringUtils.hasText(entity.getEvidenceRefs())
                ? List.of(entity.getEvidenceRefs().split(",")) : List.of();
        return TradePlanDto.Response.builder()
                .planId(entity.getPlanId())
                .candidateBatchId(entity.getCandidateBatchId())
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
                .build();
    }
}
// AI_GENERATE_END --
