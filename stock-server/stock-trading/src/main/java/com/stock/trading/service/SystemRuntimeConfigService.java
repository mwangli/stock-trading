// AI_GENERATE_START------------------------
package com.stock.trading.service;

import com.stock.trading.dto.SystemRuntimeConfigResponse;
import com.stock.trading.dto.SystemRuntimeConfigUpdateRequest;
import com.stock.trading.entity.SystemRuntimeConfig;
import com.stock.trading.repository.SystemRuntimeConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;

/**
 * 系统运行控制配置服务。
 * 数据库配置优先生效，首次初始化时使用环境变量和 application.yml 作为默认值。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Slf4j
@Service
public class SystemRuntimeConfigService {

    private static final String EMERGENCY_STOP_RESULT = "BLOCKED_FUTURE_AUTOMATIC_TRADES";

    private final SystemRuntimeConfigRepository repository;
    private final String nodeId;
    private final boolean defaultStartupDataSyncEnabled;

    /**
     * 创建系统运行控制配置服务。
     *
     * @param repository 配置仓储
     * @param configuredNodeId 配置的节点标识
     * @param defaultStartupDataSyncEnabled 默认启动数据同步开关
     */
    public SystemRuntimeConfigService(
            SystemRuntimeConfigRepository repository,
            @Value("${app.runtime.node-id:}") String configuredNodeId,
            @Value("${app.runtime.startup-data-sync-enabled:true}") boolean defaultStartupDataSyncEnabled) {
        this.repository = repository;
        this.nodeId = resolveNodeId(configuredNodeId);
        this.defaultStartupDataSyncEnabled = defaultStartupDataSyncEnabled;
    }

    /**
     * 查询当前生效的运行控制配置。
     *
     * @return 当前配置
     */
    public SystemRuntimeConfigResponse getSettings() {
        return toResponse(getOrCreate());
    }

    /**
     * 保存运行控制配置。
     *
     * @param request 保存请求
     * @return 保存后的配置
     */
    public synchronized SystemRuntimeConfigResponse updateSettings(
            SystemRuntimeConfigUpdateRequest request) {
        validateRequest(request);
        SystemRuntimeConfig config = getOrCreate();
        config.setStartupDataSyncEnabled(request.getStartupDataSyncEnabled());
        SystemRuntimeConfig saved = repository.save(config);
        log.info("[系统配置] 运行设置已更新: startupData={}", saved.isStartupDataSyncEnabled());
        return toResponse(saved);
    }

    /**
     * 判断启动数据同步是否开启。
     *
     * @return true 表示允许启动同步
     */
    public boolean isStartupDataSyncEnabled() {
        return getOrCreate().isStartupDataSyncEnabled();
    }

    /**
     * 判断自动交易是否已被紧急停止。
     *
     * @return true 表示后续自动交易必须拒绝执行
     */
    public boolean isAutomaticTradingPaused() {
        return getOrCreate().isAutomaticTradingPaused();
    }

    /**
     * 紧急停止后续自动交易。
     *
     * 该操作只阻止后续自动买入和自动退出，不撤销已提交券商的订单。
     *
     * @param reason 紧急停止原因
     * @param operator 操作者
     * @param strategyVersion 紧急停止时生效的策略版本
     * @return 保存后的运行控制配置
     */
    public synchronized SystemRuntimeConfigResponse emergencyStopAutomaticTrading(
            String reason, String operator, String strategyVersion) {
        if (!StringUtils.hasText(reason)) {
            throw new IllegalArgumentException("紧急停止原因不能为空");
        }
        String normalizedReason = reason.trim();
        if (normalizedReason.length() > 500) {
            throw new IllegalArgumentException("紧急停止原因不能超过 500 个字符");
        }
        SystemRuntimeConfig config = getOrCreate();
        config.setAutomaticTradingPaused(true);
        config.setEmergencyStopReason(normalizedReason);
        config.setEmergencyStoppedAt(LocalDateTime.now());
        config.setEmergencyStopOperator(normalizeAuditValue(operator, "UNKNOWN"));
        config.setEmergencyStopStrategyVersion(normalizeAuditValue(strategyVersion, "UNAVAILABLE"));
        config.setEmergencyStopResult(EMERGENCY_STOP_RESULT);
        SystemRuntimeConfig saved = repository.save(config);
        log.warn("[系统配置] 自动交易已紧急停止: nodeId={}, operator={}, strategyVersion={}, result={}, reason={}",
                saved.getNodeId(), saved.getEmergencyStopOperator(), saved.getEmergencyStopStrategyVersion(),
                saved.getEmergencyStopResult(), normalizedReason);
        return toResponse(saved);
    }

    private SystemRuntimeConfig getOrCreate() {
        return repository.findById(nodeId).orElseGet(this::createDefaults);
    }

    private synchronized SystemRuntimeConfig createDefaults() {
        SystemRuntimeConfig existing = repository.findById(nodeId).orElse(null);
        if (existing != null) {
            return existing;
        }
        SystemRuntimeConfig config = new SystemRuntimeConfig();
        config.setNodeId(nodeId);
        config.setStartupDataSyncEnabled(defaultStartupDataSyncEnabled);
        config.setAutomaticTradingPaused(false);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        log.info("[系统配置] 节点 {} 尚无数据库配置，使用环境默认值初始化运行开关", nodeId);
        return repository.save(config);
    }

    private void validateRequest(SystemRuntimeConfigUpdateRequest request) {
        if (request == null || request.getStartupDataSyncEnabled() == null) {
            throw new IllegalArgumentException("运行控制配置字段不能为空");
        }
    }

    private SystemRuntimeConfigResponse toResponse(SystemRuntimeConfig config) {
        return SystemRuntimeConfigResponse.builder()
                .nodeId(config.getNodeId())
                .startupDataSyncEnabled(config.isStartupDataSyncEnabled())
                .automaticTradingPaused(config.isAutomaticTradingPaused())
                .emergencyStopReason(config.getEmergencyStopReason())
                .emergencyStoppedAt(config.getEmergencyStoppedAt())
                .emergencyStopOperator(config.getEmergencyStopOperator())
                .emergencyStopStrategyVersion(config.getEmergencyStopStrategyVersion())
                .emergencyStopResult(config.getEmergencyStopResult())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private String normalizeAuditValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String resolveNodeId(String configuredNodeId) {
        String candidate = configuredNodeId == null ? "" : configuredNodeId.trim();
        if (candidate.isEmpty()) {
            try {
                candidate = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException exception) {
                throw new IllegalStateException("无法识别当前运行节点，请配置 APP_NODE_ID", exception);
            }
        }
        if (candidate.length() > 128) {
            throw new IllegalArgumentException("APP_NODE_ID 长度不能超过 128 个字符");
        }
        return candidate;
    }
}
// AI_GENERATE_END------------------------
