// AI_GENERATE_START--------------------
package com.stock.strategy.config;

import com.stock.strategy.entity.StrategyConfig;
import com.stock.strategy.dto.StrategyConfigDto;
import com.stock.strategy.entity.StrategyMode;
import com.stock.strategy.repository.StrategyConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
@Component
@RequiredArgsConstructor
public class StrategyConfigManager {

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
     * 获取当前策略配置 DTO。
     *
     * @return 当前策略配置
     */
    public StrategyConfigDto getCurrentConfigDto() {
        return toDto(getCurrentConfig());
    }

    /**
     * 更新并持久化策略配置。
     *
     * @param config 新策略配置
     */
    public void updateConfig(StrategyConfig config) {
        LocalDateTime now = LocalDateTime.now();
        StrategyConfig toSave = copyForNewVersion(config);
        toSave.setConfigId(config.getConfigId() == null || config.getConfigId().isBlank()
                ? UUID.randomUUID().toString() : config.getConfigId());
        toSave.setVersion(config.getVersion() == null || config.getVersion().isBlank()
                ? "1" : config.getVersion());
        toSave.setCreateTime(now);
        toSave.setUpdateTime(now);
        StrategyConfig saved = strategyConfigRepository.save(toSave);
        log.info("策略配置已更新: id={}, version={}, mode={}",
                saved.getId(), saved.getVersion(), saved.getMode());
    }

    /**
     * 校验并保存页面提交的策略配置。
     *
     * @param dto 页面提交的策略配置
     * @return 持久化后的策略配置
     */
    public StrategyConfigDto updateConfig(StrategyConfigDto dto) {
        validate(dto);
        StrategyConfig config = copyForNewVersion(dto.getId() == null
                ? getCurrentConfig()
                : strategyConfigRepository.findById(dto.getId()).orElseGet(StrategyConfig::defaultConfig));
        String previousVersion = config.getVersion();
        applyDto(dto, config);
        config.setVersion(nextVersion(previousVersion));
        updateConfig(config);
        return toDto(strategyConfigRepository.findFirstByEnabledTrueOrderByUpdateTimeDesc().orElse(config));
    }

    /**
     * 重置为默认策略配置。
     */
    public void resetToDefault() {
        StrategyConfig current = getCurrentConfig();
        StrategyConfig defaults = StrategyConfig.defaultConfig();
        defaults.setConfigId(current.getConfigId());
        defaults.setVersion(nextVersion(current.getVersion()));
        updateConfig(defaults);
        log.info("策略配置已重置为默认值");
    }

    /**
     * 重置并返回默认策略配置。
     *
     * @return 默认策略配置
     */
    public StrategyConfigDto resetToDefaultDto() {
        resetToDefault();
        return getCurrentConfigDto();
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

    /**
     * 按版本查询策略配置 DTO。
     *
     * @param version 策略配置版本
     * @return 策略配置 DTO，不存在时返回空
     */
    public Optional<StrategyConfigDto> getConfigDtoByVersion(String version) {
        return strategyConfigRepository.findByVersion(version).map(this::toDto);
    }

    /**
     * 复制当前配置用于创建不可变的新版本。
     *
     * @return 不含数据库主键的新配置对象
     */
    public StrategyConfig copyCurrentForNewVersion() {
        return copyForNewVersion(getCurrentConfig());
    }

    private void validate(StrategyConfigDto dto) {
        if (dto == null || dto.getMode() == null) {
            throw new IllegalArgumentException("策略模式不能为空");
        }
        requireFinite("LSTM 权重", dto.getLstmWeight());
        requireFinite("情感权重", dto.getSentimentWeight());
        requireFinite("最低综合得分", dto.getMinScore());
        requireFinite("最小上市年限", dto.getMinimumListingYears());
        requireFinite("移动止损权重", dto.getTrailingStopWeight());
        requireFinite("移动止损容忍度", dto.getTrailingStopTolerance());
        requireFinite("RSI 权重", dto.getRsiWeight());
        requireFinite("RSI 超买阈值", dto.getRsiOverboughtThreshold());
        requireFinite("成交量权重", dto.getVolumeWeight());
        requireFinite("成交量萎缩阈值", dto.getVolumeShrinkThreshold());
        requireFinite("布林带权重", dto.getBollingerWeight());
        requireFinite("布林带突破阈值", dto.getBollingerBreakoutThreshold());
        if (dto.getLstmWeight() < 0 || dto.getLstmWeight() > 1
                || dto.getSentimentWeight() < 0 || dto.getSentimentWeight() > 1
                || Math.abs(dto.getLstmWeight() + dto.getSentimentWeight() - 1D) > 0.0001D) {
            throw new IllegalArgumentException("LSTM 与情感权重必须在 0 至 1 之间且合计为 1");
        }
        if (dto.getTopN() < 1 || dto.getTopN() > 100) {
            throw new IllegalArgumentException("选股数量必须在 1 至 100 之间");
        }
        if (dto.getMinScore() < 0 || dto.getMinScore() > 1) {
            throw new IllegalArgumentException("最低综合得分必须在 0 至 1 之间");
        }
        if (dto.getMinimumListingYears() < 0 || dto.getMinimumListingYears() > 100) {
            throw new IllegalArgumentException("最小上市年限必须在 0 至 100 年之间");
        }
        if (dto.getHighReturnThreshold() < dto.getNormalReturnThreshold()
                || dto.getNormalReturnThreshold() < dto.getLowReturnThreshold()
                || dto.getLowReturnThreshold() < dto.getLossReturnThreshold()) {
            throw new IllegalArgumentException("收益退出阈值必须按高收益、正常、低收益、亏损顺序递减");
        }
        validateRange("移动止损权重", dto.getTrailingStopWeight());
        validateRange("RSI 权重", dto.getRsiWeight());
        validateRange("成交量权重", dto.getVolumeWeight());
        validateRange("布林带权重", dto.getBollingerWeight());
        validateRange("移动止损容忍度", dto.getTrailingStopTolerance());
        validateRange("成交量萎缩阈值", dto.getVolumeShrinkThreshold());
        if (dto.getRsiOverboughtThreshold() < 50 || dto.getRsiOverboughtThreshold() > 100) {
            throw new IllegalArgumentException("RSI 超买阈值必须在 50 至 100 之间");
        }
        if (dto.getBollingerBreakoutThreshold() < 0 || dto.getBollingerBreakoutThreshold() > 2) {
            throw new IllegalArgumentException("布林带突破阈值必须在 0 至 2 之间");
        }
    }

    private void requireFinite(String name, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + "必须是有限数值");
        }
    }

    private void validateRange(String name, double value) {
        if (value < 0 || value > 1) {
            throw new IllegalArgumentException(name + "必须在 0 至 1 之间");
        }
    }

    private String nextVersion(String version) {
        if (version == null || version.isBlank()) {
            return "1";
        }
        try {
            return String.valueOf(Long.parseLong(version) + 1);
        } catch (NumberFormatException exception) {
            return version + "-updated";
        }
    }

    private StrategyConfigDto toDto(StrategyConfig config) {
        java.util.Map<String, Boolean> indicators = config.getIndicatorEnabled();
        return StrategyConfigDto.builder()
                .id(config.getId())
                .configId(config.getConfigId())
                .version(config.getVersion())
                .mode(config.getMode())
                .lstmWeight(config.getLstmWeight())
                .sentimentWeight(config.getSentimentWeight())
                .topN(config.getTopN())
                .minScore(config.getMinScore())
                .minimumListingYears(config.getMinimumListingYears())
                .trailingStopWeight(config.getTrailingStopWeight())
                .trailingStopTolerance(config.getTrailingStopTolerance())
                .rsiWeight(config.getRsiWeight())
                .rsiOverboughtThreshold(config.getRsiOverboughtThreshold())
                .volumeWeight(config.getVolumeWeight())
                .volumeShrinkThreshold(config.getVolumeShrinkThreshold())
                .bollingerWeight(config.getBollingerWeight())
                .bollingerBreakoutThreshold(config.getBollingerBreakoutThreshold())
                .highReturnThreshold(config.getHighReturnThreshold())
                .normalReturnThreshold(config.getNormalReturnThreshold())
                .lowReturnThreshold(config.getLowReturnThreshold())
                .lossReturnThreshold(config.getLossReturnThreshold())
                .trailingStopEnabled(isEnabled(indicators, "trailingStop"))
                .rsiEnabled(isEnabled(indicators, "rsi"))
                .volumeEnabled(isEnabled(indicators, "volume"))
                .bollingerEnabled(isEnabled(indicators, "bollinger"))
                .enabled(config.isEnabled())
                .updateTime(config.getUpdateTime() == null ? null : config.getUpdateTime().toString())
                .build();
    }

    private boolean isEnabled(java.util.Map<String, Boolean> indicators, String key) {
        return indicators == null || indicators.getOrDefault(key, true);
    }

    private void applyDto(StrategyConfigDto dto, StrategyConfig config) {
        config.setConfigId(dto.getConfigId());
        config.setVersion(dto.getVersion());
        config.setMode(dto.getMode() == null ? StrategyMode.BALANCED : dto.getMode());
        config.setLstmWeight(dto.getLstmWeight());
        config.setSentimentWeight(dto.getSentimentWeight());
        config.setTopN(dto.getTopN());
        config.setMinScore(dto.getMinScore());
        config.setMinimumListingYears(dto.getMinimumListingYears());
        config.setTrailingStopWeight(dto.getTrailingStopWeight());
        config.setTrailingStopTolerance(dto.getTrailingStopTolerance());
        config.setRsiWeight(dto.getRsiWeight());
        config.setRsiOverboughtThreshold(dto.getRsiOverboughtThreshold());
        config.setVolumeWeight(dto.getVolumeWeight());
        config.setVolumeShrinkThreshold(dto.getVolumeShrinkThreshold());
        config.setBollingerWeight(dto.getBollingerWeight());
        config.setBollingerBreakoutThreshold(dto.getBollingerBreakoutThreshold());
        config.setHighReturnThreshold(dto.getHighReturnThreshold());
        config.setNormalReturnThreshold(dto.getNormalReturnThreshold());
        config.setLowReturnThreshold(dto.getLowReturnThreshold());
        config.setLossReturnThreshold(dto.getLossReturnThreshold());
        java.util.Map<String, Boolean> indicators = new java.util.HashMap<>();
        indicators.put("trailingStop", dto.isTrailingStopEnabled());
        indicators.put("rsi", dto.isRsiEnabled());
        indicators.put("volume", dto.isVolumeEnabled());
        indicators.put("bollinger", dto.isBollingerEnabled());
        config.setIndicatorEnabled(indicators);
        config.setEnabled(dto.isEnabled());
    }

    private void copyTo(StrategyConfig from, StrategyConfig to) {
        to.setConfigId(from.getConfigId());
        to.setVersion(from.getVersion());
        to.setMode(from.getMode());
        to.setLstmWeight(from.getLstmWeight());
        to.setSentimentWeight(from.getSentimentWeight());
        to.setTopN(from.getTopN());
        to.setMinScore(from.getMinScore());
        to.setMinimumListingYears(from.getMinimumListingYears());
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
        to.setEnabled(from.isEnabled());
        to.setUpdateTime(from.getUpdateTime());
    }

    private StrategyConfig copyForNewVersion(StrategyConfig source) {
        StrategyConfig target = new StrategyConfig();
        copyTo(source, target);
        target.setId(null);
        target.setCreateTime(null);
        target.setUpdateTime(null);
        return target;
    }
}
// AI_GENERATE_END--------------------
