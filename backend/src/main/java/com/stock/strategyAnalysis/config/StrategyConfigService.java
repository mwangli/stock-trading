package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.persistence.StrategyConfigRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 策略配置服务
 * 管理策略配置的获取、更新和持久化（MySQL）
 */
@Slf4j
@Service
public class StrategyConfigService {

    private final StrategyConfigRepository strategyConfigRepository;

    public StrategyConfigService(StrategyConfigRepository strategyConfigRepository) {
        this.strategyConfigRepository = strategyConfigRepository;
    }

    /**
     * 获取当前配置
     * 从 MySQL 获取，无则返回默认配置
     */
    public StrategyConfig getCurrentConfig() {
        Optional<StrategyConfig> dbConfig = strategyConfigRepository.findFirstByEnabledTrueOrderByUpdateTimeDesc();
        if (dbConfig.isPresent()) {
            return dbConfig.get();
        }

        log.info("使用默认策略配置");
        return StrategyConfig.defaultConfig();
    }

    /**
     * 更新配置并写入 MySQL
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateConfig(StrategyConfig config) {
        LocalDateTime now = LocalDateTime.now();
        config.setUpdateTime(now);
        if (config.getConfigId() == null || config.getConfigId().isBlank()) {
            config.setConfigId(UUID.randomUUID().toString());
        }
        if (config.getVersion() == null || config.getVersion().isBlank()) {
            config.setVersion("1");
        }

        StrategyConfig toSave;
        if (config.getId() != null) {
            toSave = strategyConfigRepository.findById(config.getId()).orElse(config);
            if (toSave != config) {
                copyTo(config, toSave);
            }
        } else {
            Optional<StrategyConfig> existing = strategyConfigRepository.findFirstByEnabledTrueOrderByUpdateTimeDesc();
            if (existing.isPresent()) {
                toSave = existing.get();
                copyTo(config, toSave);
            } else {
                toSave = config;
                if (toSave.getCreateTime() == null) {
                    toSave.setCreateTime(now);
                }
            }
        }
        StrategyConfig saved = strategyConfigRepository.save(toSave);
        log.info("策略配置已更新(MySQL): id={}, version={}, mode={}", saved.getId(), saved.getVersion(), saved.getMode());
    }

    private void copyTo(StrategyConfig from, StrategyConfig to) {
        to.setConfigId(from.getConfigId());
        to.setVersion(from.getVersion());
        to.setMode(from.getMode());
        to.setLstmWeight(from.getLstmWeight());
        to.setSentimentWeight(from.getSentimentWeight());
        to.setTopN(from.getTopN());
        to.setMinScore(from.getMinScore());
        to.setTrailingStopWeight(from.getTrailingStopWeight());
        to.setTrailingStopTolerance(from.getTrailingStopTolerance());
        to.setRsiWeight(from.getRsiWeight());
        to.setRsiOverboughtThreshold(from.getRsiOverboughtThreshold());
        to.setVolumeWeight(from.getVolumeWeight());
        to.setVolumeShrinkThreshold(from.getVolumeShrinkThreshold());
        to.setBollingerWeight(from.getBollingerWeight());
        to.setBollingerBreakoutThreshold(from.getBollingerBreakoutThreshold());
        to.setHighReturnThreshold(from.getHighReturnThreshold());
        to.setNormalReturnThreshold(from.getNormalReturnThreshold());
        to.setLowReturnThreshold(from.getLowReturnThreshold());
        to.setLossReturnThreshold(from.getLossReturnThreshold());
        to.setIndicatorEnabled(from.getIndicatorEnabled());
        to.setConsecutiveFailureThreshold(from.getConsecutiveFailureThreshold());
        to.setDailyFailureThreshold(from.getDailyFailureThreshold());
        to.setCircuitBreakerRecoveryMinutes(from.getCircuitBreakerRecoveryMinutes());
        to.setEnabled(from.isEnabled());
        to.setUpdateTime(from.getUpdateTime());
    }

    /**
     * 重置为默认配置（写入 MySQL）
     */
    public void resetToDefault() {
        updateConfig(StrategyConfig.defaultConfig());
        log.info("策略配置已重置为默认值");
    }

    /**
     * 获取配置版本列表
     */
    public Iterable<StrategyConfig> getConfigVersions() {
        return strategyConfigRepository.findAllByOrderByUpdateTimeDesc();
    }

    /**
     * 根据版本号获取配置
     */
    public Optional<StrategyConfig> getConfigByVersion(String version) {
        return strategyConfigRepository.findByVersion(version);
    }
}