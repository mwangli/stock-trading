// AI_GENERATE_START ------
package com.stock.systemConfig.service;

import com.stock.systemConfig.domain.dto.SystemRuntimeConfigDto;
import com.stock.systemConfig.domain.entity.SystemRuntimeConfig;
import com.stock.systemConfig.persistence.SystemRuntimeConfigRepository;
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
    private final boolean defaultDataCollectionEnabled;
    private final boolean defaultNewsCollectionEnabled;
    private final boolean defaultStartupDataSyncEnabled;
    private final boolean defaultStartupHistorySyncEnabled;
    private final boolean defaultStartupNewsSyncEnabled;
    private final boolean defaultModelTrainingEnabled;

    /**
     * 创建系统运行控制配置服务。
     *
     * @param repository 配置仓储
     * @param scheduler Quartz 调度器
     * @param transactionManager 事务管理器
     * @param configuredNodeId 配置的节点标识
     * @param defaultSchedulerEnabled 默认调度开关
     * @param defaultDataCollectionEnabled 默认数据采集开关
     * @param defaultNewsCollectionEnabled 默认新闻采集开关
     * @param defaultStartupDataSyncEnabled 默认启动数据同步开关
     * @param defaultStartupHistorySyncEnabled 默认启动历史同步开关
     * @param defaultStartupNewsSyncEnabled 默认启动新闻同步开关
     * @param defaultModelTrainingEnabled 默认模型训练开关
     */
    public SystemRuntimeConfigService(
            SystemRuntimeConfigRepository repository,
            Scheduler scheduler,
            PlatformTransactionManager transactionManager,
            @Value("${app.runtime.node-id:}") String configuredNodeId,
            @Value("${spring.quartz.auto-startup:true}") boolean defaultSchedulerEnabled,
            @Value("${app.runtime.data-collection-enabled:true}") boolean defaultDataCollectionEnabled,
            @Value("${app.runtime.news-collection-enabled:true}") boolean defaultNewsCollectionEnabled,
            @Value("${app.startup.listener.enabled:true}") boolean defaultStartupDataSyncEnabled,
            @Value("${app.startup.history-sync.enabled:false}") boolean defaultStartupHistorySyncEnabled,
            @Value("${app.startup.news-sync.enabled:false}") boolean defaultStartupNewsSyncEnabled,
            @Value("${models.lstm.training-enabled:false}") boolean defaultModelTrainingEnabled) {
        this.repository = repository;
        this.scheduler = scheduler;
        this.defaultInitializationTransaction = new TransactionTemplate(transactionManager);
        this.defaultInitializationTransaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.nodeId = resolveNodeId(configuredNodeId);
        this.defaultSchedulerEnabled = defaultSchedulerEnabled;
        this.defaultDataCollectionEnabled = defaultDataCollectionEnabled;
        this.defaultNewsCollectionEnabled = defaultNewsCollectionEnabled;
        this.defaultStartupDataSyncEnabled = defaultStartupDataSyncEnabled;
        this.defaultStartupHistorySyncEnabled = defaultStartupHistorySyncEnabled;
        this.defaultStartupNewsSyncEnabled = defaultStartupNewsSyncEnabled;
        this.defaultModelTrainingEnabled = defaultModelTrainingEnabled;
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
        config.setDataCollectionEnabled(request.getDataCollectionEnabled());
        config.setNewsCollectionEnabled(request.getNewsCollectionEnabled());
        config.setStartupDataSyncEnabled(request.getStartupDataSyncEnabled());
        config.setStartupHistorySyncEnabled(request.getStartupHistorySyncEnabled());
        config.setStartupNewsSyncEnabled(request.getStartupNewsSyncEnabled());
        config.setModelTrainingEnabled(request.getModelTrainingEnabled());
        applySchedulerState(config.isSchedulerEnabled());
        SystemRuntimeConfig saved;
        try {
            saved = repository.saveAndFlush(config);
        } catch (RuntimeException exception) {
            applySchedulerState(previousSchedulerEnabled);
            throw exception;
        }
        log.info("[系统配置] 运行开关已更新: scheduler={}, dataCollection={}, newsCollection={}, training={}",
                saved.isSchedulerEnabled(), saved.isDataCollectionEnabled(),
                saved.isNewsCollectionEnabled(), saved.isModelTrainingEnabled());
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
     * 校验股票和行情数据采集是否开启。
     */
    public void requireDataCollectionEnabled() {
        if (!getOrCreate().isDataCollectionEnabled()) {
            throw new IllegalStateException("系统配置已关闭数据采集");
        }
    }

    /**
     * 校验新闻和公告采集是否开启。
     */
    public void requireNewsCollectionEnabled() {
        SystemRuntimeConfig config = getOrCreate();
        if (!config.isDataCollectionEnabled() || !config.isNewsCollectionEnabled()) {
            throw new IllegalStateException("系统配置已关闭新闻采集");
        }
    }

    /**
     * 判断启动数据同步是否开启。
     *
     * @return true 表示允许启动同步
     */
    public boolean isStartupDataSyncEnabled() {
        SystemRuntimeConfig config = getOrCreate();
        return config.isDataCollectionEnabled() && config.isStartupDataSyncEnabled();
    }

    /**
     * 判断启动历史行情同步是否开启。
     *
     * @return true 表示允许启动同步
     */
    public boolean isStartupHistorySyncEnabled() {
        SystemRuntimeConfig config = getOrCreate();
        return config.isDataCollectionEnabled() && config.isStartupHistorySyncEnabled();
    }

    /**
     * 判断启动新闻同步是否开启。
     *
     * @return true 表示允许启动同步
     */
    public boolean isStartupNewsSyncEnabled() {
        SystemRuntimeConfig config = getOrCreate();
        return config.isDataCollectionEnabled()
                && config.isNewsCollectionEnabled()
                && config.isStartupNewsSyncEnabled();
    }

    /**
     * 判断模型训练是否开启。
     *
     * @return true 表示允许训练
     */
    public boolean isModelTrainingEnabled() {
        return getOrCreate().isModelTrainingEnabled();
    }

    private SystemRuntimeConfig getOrCreate() {
        return repository.findById(nodeId).orElseGet(this::createDefaults);
    }

    private SystemRuntimeConfig createDefaults() {
        SystemRuntimeConfig config = new SystemRuntimeConfig();
        config.setNodeId(nodeId);
        config.setSchedulerEnabled(defaultSchedulerEnabled);
        config.setDataCollectionEnabled(defaultDataCollectionEnabled);
        config.setNewsCollectionEnabled(defaultNewsCollectionEnabled);
        config.setStartupDataSyncEnabled(defaultStartupDataSyncEnabled);
        config.setStartupHistorySyncEnabled(defaultStartupHistorySyncEnabled);
        config.setStartupNewsSyncEnabled(defaultStartupNewsSyncEnabled);
        config.setModelTrainingEnabled(defaultModelTrainingEnabled);
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
                || request.getDataCollectionEnabled() == null
                || request.getNewsCollectionEnabled() == null
                || request.getStartupDataSyncEnabled() == null
                || request.getStartupHistorySyncEnabled() == null
                || request.getStartupNewsSyncEnabled() == null
                || request.getModelTrainingEnabled() == null) {
            throw new IllegalArgumentException("运行控制配置字段不能为空");
        }
        if (!request.getDataCollectionEnabled()
                && (request.getNewsCollectionEnabled()
                || request.getStartupDataSyncEnabled()
                || request.getStartupHistorySyncEnabled()
                || request.getStartupNewsSyncEnabled())) {
            throw new IllegalArgumentException("关闭数据采集前，请先关闭其下属采集开关");
        }
        if (!request.getNewsCollectionEnabled() && request.getStartupNewsSyncEnabled()) {
            throw new IllegalArgumentException("关闭新闻采集前，请先关闭启动新闻同步");
        }
    }

    private SystemRuntimeConfigDto.SettingsResponse toResponse(SystemRuntimeConfig config) {
        return SystemRuntimeConfigDto.SettingsResponse.builder()
                .nodeId(config.getNodeId())
                .schedulerEnabled(config.isSchedulerEnabled())
                .dataCollectionEnabled(config.isDataCollectionEnabled())
                .newsCollectionEnabled(config.isNewsCollectionEnabled())
                .startupDataSyncEnabled(config.isStartupDataSyncEnabled())
                .startupHistorySyncEnabled(config.isStartupHistorySyncEnabled())
                .startupNewsSyncEnabled(config.isStartupNewsSyncEnabled())
                .modelTrainingEnabled(config.isModelTrainingEnabled())
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
// AI_GENERATE_END ------
