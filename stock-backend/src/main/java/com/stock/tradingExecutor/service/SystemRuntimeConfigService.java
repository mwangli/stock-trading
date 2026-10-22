// AI_GENERATE_START---- -------
package com.stock.tradingExecutor.service;

import com.stock.tradingExecutor.domain.dto.SystemRuntimeConfigDto;
import com.stock.tradingExecutor.domain.entity.SystemRuntimeConfig;
import com.stock.tradingExecutor.persistence.SystemRuntimeConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final Scheduler scheduler;
    private final TransactionTemplate defaultInitializationTransaction;
    private final String nodeId;
    private final boolean defaultSchedulerEnabled;
    private final boolean defaultStartupDataSyncEnabled;

    /**
     * 创建系统运行控制配置服务。
     *
     * @param repository 配置仓储
     * @param scheduler Quartz 调度器
     * @param transactionManager 事务管理器
     * @param configuredNodeId 配置的节点标识
     * @param defaultSchedulerEnabled 默认调度开关
     * @param defaultStartupDataSyncEnabled 默认启动数据同步开关
     */
    public SystemRuntimeConfigService(
            SystemRuntimeConfigRepository repository,
            Scheduler scheduler,
            PlatformTransactionManager transactionManager,
            @Value("${app.runtime.node-id:}") String configuredNodeId,
            @Value("${spring.quartz.auto-startup:true}") boolean defaultSchedulerEnabled,
            @Value("${app.startup.listener.enabled:true}") boolean defaultStartupDataSyncEnabled) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.defaultInitializationTransaction = new TransactionTemplate(transactionManager);
        this.defaultInitializationTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.nodeId = resolveNodeId(configuredNodeId);
        this.defaultSchedulerEnabled = defaultSchedulerEnabled;
        this.defaultStartupDataSyncEnabled = defaultStartupDataSyncEnabled;
    }

    /**
     * 查询当前生效的运行控制配置。
     *
     * @return 当前配置
     */
    public SystemRuntimeConfigDto.SettingsResponse getSettings() {
        return toResponse(getOrCreate());
    }

    /**
     * 保存运行控制配置并立即同步当前节点的 Quartz 状态。
     *
     * @param request 保存请求
     * @return 保存后的配置
     */
    public synchronized SystemRuntimeConfigDto.SettingsResponse updateSettings(
            SystemRuntimeConfigDto.UpdateRequest request) {
        validateRequest(request);
        SystemRuntimeConfig config = getOrCreate();
        boolean previousSchedulerEnabled = config.isSchedulerEnabled();
        config.setSchedulerEnabled(request.getSchedulerEnabled());
        config.setDataCollectionEnabled(true);
        config.setNewsCollectionEnabled(true);
        config.setStartupDataSyncEnabled(request.getStartupDataSyncEnabled());
        config.setModelTrainingEnabled(true);
        applySchedulerState(config.isSchedulerEnabled());
        SystemRuntimeConfig saved;
        try {
            saved = repository.saveAndFlush(config);
        } catch (RuntimeException exception) {
            applySchedulerState(previousSchedulerEnabled);
            throw exception;
        }
        log.info("[系统配置] 运行设置已更新: scheduler={}, startupData={}",
                saved.isSchedulerEnabled(), saved.isStartupDataSyncEnabled());
        return toResponse(saved);
    }

    /**
     * 按数据库配置同步当前节点的 Quartz 运行状态。
     */
    public void synchronizeSchedulerState() {
        applySchedulerState(getOrCreate().isSchedulerEnabled());
    }

    /**
     * 判断 Quartz 固定任务是否允许执行。
     *
     * @return true 表示允许执行
     */
    public boolean isSchedulerEnabled() {
        return getOrCreate().isSchedulerEnabled();
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

    private SystemRuntimeConfig createDefaults() {
        SystemRuntimeConfig config = new SystemRuntimeConfig();
        config.setNodeId(nodeId);
        config.setSchedulerEnabled(defaultSchedulerEnabled);
        config.setDataCollectionEnabled(true);
        config.setNewsCollectionEnabled(true);
        config.setStartupDataSyncEnabled(defaultStartupDataSyncEnabled);
        config.setModelTrainingEnabled(true);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        log.info("[系统配置] 节点 {} 尚无数据库配置，使用环境默认值初始化运行开关", nodeId);
        SystemRuntimeConfig initialized = defaultInitializationTransaction.execute(status -> {
            repository.insertIfAbsent(config);
            return repository.findById(nodeId)
                    .orElseThrow(() -> new IllegalStateException("初始化节点运行配置失败: " + nodeId));
        });
        if (initialized == null) {
            throw new IllegalStateException("初始化节点运行配置事务未返回结果: " + nodeId);
        }
        return initialized;
    }

    private void applySchedulerState(boolean enabled) {
        try {
            if (enabled) {
                scheduler.start();
                log.info("[系统配置] 当前节点 Quartz 调度器已启动");
            } else {
                scheduler.standby();
                log.info("[系统配置] 当前节点 Quartz 调度器已进入待机状态");
            }
        } catch (SchedulerException exception) {
            throw new IllegalStateException("切换 Quartz 调度器状态失败", exception);
        }
    }

    private void validateRequest(SystemRuntimeConfigDto.UpdateRequest request) {
        if (request == null
                || request.getSchedulerEnabled() == null
                || request.getStartupDataSyncEnabled() == null) {
            throw new IllegalArgumentException("运行控制配置字段不能为空");
        }
    }

    private SystemRuntimeConfigDto.SettingsResponse toResponse(SystemRuntimeConfig config) {
        return SystemRuntimeConfigDto.SettingsResponse.builder()
                .nodeId(config.getNodeId())
                .schedulerEnabled(config.isSchedulerEnabled())
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
// AI_GENERATE_END---- --------
