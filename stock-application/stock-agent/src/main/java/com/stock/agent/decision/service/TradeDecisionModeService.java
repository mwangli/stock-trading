// AI_GENERATE_START-----
package com.stock.agent.decision.service;

import com.stock.agent.dto.TradeDecisionModeConfigResponse;
import com.stock.agent.dto.TradeDecisionModeFallbackRequest;
import com.stock.agent.dto.TradeDecisionModeUpdateRequest;
import com.stock.agent.decision.entity.TradeDecisionModeConfigEntity;
import com.stock.agent.decision.repository.TradeDecisionModeConfigRepository;
import com.stock.agent.decision.vo.TradeDecisionFallbackPolicy;
import com.stock.agent.decision.vo.TradeDecisionMode;
import com.stock.agent.decision.vo.TradeDecisionModeResolution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * Agent 交易决策模式治理服务。
 * 负责持久化模式、灰度范围、生产影响开关和显式回退策略。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TradeDecisionModeService {

    private static final long CONFIG_ID = 1L;

    private final TradeDecisionModeConfigRepository repository;

    /**
     * 查询当前决策模式治理配置。
     *
     * @return 当前配置
     */
    public TradeDecisionModeConfigResponse current() {
        return toResponse(requireConfig());
    }

    /**
     * 更新决策参与模式和灰度范围。
     *
     * @param request 更新请求
     * @param operator 服务端认证的管理员操作人
     * @return 更新后的配置
     */
    public TradeDecisionModeConfigResponse update(TradeDecisionModeUpdateRequest request,
                                                   String operator) {
        if (request == null) {
            throw new IllegalArgumentException("决策模式更新请求不能为空");
        }
        TradeDecisionMode mode = parseMode(request.getDecisionMode());
        TradeDecisionFallbackPolicy fallbackPolicy = parseFallback(request.getFallbackPolicy());
        int rolloutPercentage = request.getRolloutPercentage() == null
                ? -1 : request.getRolloutPercentage();
        if (rolloutPercentage < 0 || rolloutPercentage > 100) {
            throw new IllegalArgumentException("灰度比例必须在 0 至 100 之间");
        }
        if (request.getProductionImpactEnabled() == null) {
            throw new IllegalArgumentException("生产影响开关不能为空");
        }
        if (request.getProductionImpactEnabled() && mode != TradeDecisionMode.GATED) {
            throw new IllegalArgumentException("只有 GATED 模式允许开启生产影响");
        }
        TradeDecisionModeConfigEntity entity = requireConfig();
        requireVersion(entity, request.getConfigVersion());
        entity.setDecisionMode(mode.name())
                .setFallbackPolicy(fallbackPolicy.name())
                .setStrategyVersionScope(trimToNull(request.getStrategyVersionScope()))
                .setCandidateBatchScope(trimToNull(request.getCandidateBatchScope()))
                .setRolloutPercentage(rolloutPercentage)
                .setProductionImpactEnabled(request.getProductionImpactEnabled())
                .setUpdatedBy(requireOperator(operator))
                .setUpdatedAt(LocalDateTime.now());
        TradeDecisionModeConfigEntity saved = repository.save(entity);
        log.info("[Agent治理] 更新交易决策模式: mode={}, fallback={}, rollout={}%, productionImpact={}, operator={}",
                saved.getDecisionMode(), saved.getFallbackPolicy(), saved.getRolloutPercentage(),
                saved.isProductionImpactEnabled(), saved.getUpdatedBy());
        return toResponse(saved);
    }

    /**
     * 紧急回退到 SHADOW，并关闭生产影响。
     *
     * @param request 回退请求
     * @param operator 服务端认证的管理员操作人
     * @return 回退后的配置
     */
    public TradeDecisionModeConfigResponse fallback(TradeDecisionModeFallbackRequest request,
                                                     String operator) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new IllegalArgumentException("模式回退原因不能为空");
        }
        TradeDecisionModeConfigEntity entity = requireConfig();
        requireVersion(entity, request.getConfigVersion());
        entity.setDecisionMode(TradeDecisionMode.SHADOW.name())
                .setProductionImpactEnabled(false)
                .setUpdatedBy(requireOperator(operator))
                .setUpdatedAt(LocalDateTime.now());
        TradeDecisionModeConfigEntity saved = repository.save(entity);
        log.warn("[Agent治理] 交易决策模式已回退到 SHADOW: reason={}, operator={}",
                request.getReason().trim(), saved.getUpdatedBy());
        return toResponse(saved);
    }

    /**
     * 按候选批次、策略版本和稳定灰度规则解析实际参与模式。
     *
     * @param candidateBatchId 候选批次标识
     * @param strategyVersion 策略配置版本
     * @return 当前批次的实际模式和回退策略
     */
    public TradeDecisionModeResolution resolve(String candidateBatchId, String strategyVersion) {
        TradeDecisionModeConfigEntity config = requireConfig();
        TradeDecisionMode configuredMode = parseMode(config.getDecisionMode());
        TradeDecisionFallbackPolicy fallbackPolicy = parseFallback(config.getFallbackPolicy());
        if (configuredMode == TradeDecisionMode.OFF || configuredMode == TradeDecisionMode.SHADOW) {
            return resolution(configuredMode, configuredMode, fallbackPolicy, false,
                    "当前配置不允许影响自动交易");
        }
        if (!matchesScope(config, candidateBatchId, strategyVersion)) {
            return resolution(configuredMode, TradeDecisionMode.SHADOW, fallbackPolicy, false,
                    "当前候选批次不在灰度范围内");
        }
        if (configuredMode == TradeDecisionMode.GATED && !config.isProductionImpactEnabled()) {
            return resolution(configuredMode, TradeDecisionMode.ADVISORY, fallbackPolicy, false,
                    "GATED 生产影响未开启，降级为 ADVISORY");
        }
        boolean productionImpactAllowed = configuredMode == TradeDecisionMode.GATED;
        return resolution(configuredMode, configuredMode, fallbackPolicy, productionImpactAllowed,
                productionImpactAllowed ? "当前批次允许 GATED 单向负向参与" : "当前批次仅展示 Agent 建议");
    }

    private TradeDecisionModeConfigEntity requireConfig() {
        return repository.findById(CONFIG_ID)
                .orElseThrow(() -> new IllegalStateException("Agent 交易决策治理配置尚未初始化"));
    }

    private boolean matchesScope(TradeDecisionModeConfigEntity config, String candidateBatchId,
                                 String strategyVersion) {
        if (StringUtils.hasText(config.getCandidateBatchScope())
                && !config.getCandidateBatchScope().equals(candidateBatchId)) {
            return false;
        }
        if (StringUtils.hasText(config.getStrategyVersionScope())
                && !config.getStrategyVersionScope().equals(strategyVersion)) {
            return false;
        }
        if (!StringUtils.hasText(candidateBatchId) || config.getRolloutPercentage() <= 0) {
            return false;
        }
        int bucket = Math.floorMod(candidateBatchId.hashCode(), 100);
        return bucket < config.getRolloutPercentage();
    }

    private TradeDecisionModeResolution resolution(TradeDecisionMode configuredMode,
                                                   TradeDecisionMode effectiveMode,
                                                   TradeDecisionFallbackPolicy fallbackPolicy,
                                                   boolean productionImpactAllowed,
                                                   String reason) {
        return TradeDecisionModeResolution.builder()
                .configuredMode(configuredMode)
                .effectiveMode(effectiveMode)
                .fallbackPolicy(fallbackPolicy)
                .productionImpactAllowed(productionImpactAllowed)
                .reason(reason)
                .build();
    }

    private void requireVersion(TradeDecisionModeConfigEntity entity, Long expectedVersion) {
        if (expectedVersion == null || !expectedVersion.equals(entity.getConfigVersion())) {
            throw new IllegalStateException("决策模式配置版本已变化，请刷新后重试");
        }
    }

    private TradeDecisionMode parseMode(String mode) {
        try {
            return TradeDecisionMode.valueOf(mode == null ? "" : mode.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("决策模式仅允许 OFF、SHADOW、ADVISORY 或 GATED", exception);
        }
    }

    private TradeDecisionFallbackPolicy parseFallback(String fallbackPolicy) {
        try {
            return TradeDecisionFallbackPolicy.valueOf(
                    fallbackPolicy == null ? "" : fallbackPolicy.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "回退策略仅允许 BASELINE_FALLBACK 或 MANUAL_REVIEW", exception);
        }
    }

    private String requireOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("治理配置操作人不能为空");
        }
        return operator.trim();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private TradeDecisionModeConfigResponse toResponse(TradeDecisionModeConfigEntity entity) {
        return TradeDecisionModeConfigResponse.builder()
                .decisionMode(entity.getDecisionMode())
                .fallbackPolicy(entity.getFallbackPolicy())
                .strategyVersionScope(entity.getStrategyVersionScope())
                .candidateBatchScope(entity.getCandidateBatchScope())
                .rolloutPercentage(entity.getRolloutPercentage())
                .productionImpactEnabled(entity.isProductionImpactEnabled())
                .updatedBy(entity.getUpdatedBy())
                .updatedAt(entity.getUpdatedAt())
                .configVersion(entity.getConfigVersion())
                .build();
    }
}
// AI_GENERATE_END-----
