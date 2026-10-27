// AI_GENERATE_START-------------------------
package com.stock.trading.service;

import com.stock.trading.dto.SystemRuntimeConfigResponse;
import com.stock.trading.dto.SystemRuntimeConfigUpdateRequest;
import com.stock.trading.entity.SystemRuntimeConfig;
import com.stock.trading.repository.SystemRuntimeConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;

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
                .updatedAt(config.getUpdatedAt())
                .build();
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
// AI_GENERATE_END-------------------------
