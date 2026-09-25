// AI_GENERATE_START -
package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import com.stock.strategyAnalysis.persistence.StrategyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 策略配置服务。
 * 直接使用 MySQL 保存和读取单用户策略配置，不引入额外缓存组件。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyConfigService {

    private final StrategyConfigRepository strategyConfigRepository;

    /**
     * 获取当前启用的策略配置。
     *
     * @return 当前配置；未配置时返回默认配置
     */
    public StrategyConfig getCurrentConfig() {
        return strategyConfigRepository.findFirstByEnabledTrueOrderByUpdateTimeDesc()
                .orElseGet(() -> {
                    log.info("数据库中没有启用的策略配置，使用默认配置");
                    return StrategyConfig.defaultConfig();
                });
    }

    /**
     * 更新并持久化策略配置。
     *
     * @param config 新策略配置
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
            Optional<StrategyConfig> existing = strategyConfigRepository
                    .findFirstByEnabledTrueOrderByUpdateTimeDesc();
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
        log.info("策略配置已更新: id={}, version={}, mode={}",
                saved.getId(), saved.getVersion(), saved.getMode());
    }

    /**
     * 重置为默认策略配置。
     */
    public void resetToDefault() {
        updateConfig(StrategyConfig.defaultConfig());
        log.info("策略配置已重置为默认值");
    }

    /**
     * 获取全部策略配置版本。
     *
     * @return 按更新时间倒序的配置
     */
    public Iterable<StrategyConfig> getConfigVersions() {
        return strategyConfigRepository.findAllByOrderByUpdateTimeDesc();
    }

    /**
     * 根据版本号查询策略配置。
     *
     * @param version 配置版本
     * @return 对应配置
     */
    public Optional<StrategyConfig> getConfigByVersion(String version) {
        return strategyConfigRepository.findByVersion(version);
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
}
// AI_GENERATE_END -
