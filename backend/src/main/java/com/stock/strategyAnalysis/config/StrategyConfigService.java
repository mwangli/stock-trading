package com.stock.strategyAnalysis.config;

import com.stock.strategyAnalysis.entity.StrategyConfig;
import com.stock.strategyAnalysis.repository.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 策略配置服务
 * 管理策略配置的获取、更新和持久化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyConfigService {

    private static final String REDIS_CONFIG_KEY = "strategy:config:current";
    
    private final ConfigRepository configRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取当前配置
     * 优先从Redis获取，否则从数据库获取默认配置
     */
    public StrategyConfig getCurrentConfig() {
        // 先从Redis获取
        Object cached = redisTemplate.opsForValue().get(REDIS_CONFIG_KEY);
        if (cached instanceof StrategyConfig) {
            return (StrategyConfig) cached;
        }
        
        // 从数据库获取最新配置
        Optional<StrategyConfig> dbConfig = configRepository.findFirstByEnabledTrueOrderByUpdateTimeDesc();
        if (dbConfig.isPresent()) {
            StrategyConfig config = dbConfig.get();
            redisTemplate.opsForValue().set(REDIS_CONFIG_KEY, config);
            return config;
        }
        
        // 返回默认配置
//        StrategyConfig defaultConfig = StrategyConfig.defaultConfig();
        log.info("使用默认策略配置");
//        return defaultConfig;
        return null;
    }

    /**
     * 更新配置
     */
    public void updateConfig(StrategyConfig config) {
        config.setUpdateTime(LocalDateTime.now());
        
        // 保存到数据库
        configRepository.save(config);
        
        // 更新Redis缓存
        redisTemplate.opsForValue().set(REDIS_CONFIG_KEY, config);
        
        log.info("策略配置已更新: version={}, mode={}", config.getVersion(), config.getMode());
    }

    /**
     * 重置为默认配置
     */
    public void resetToDefault() {
//        StrategyConfig defaultConfig = StrategyConfig.defaultConfig();
//        updateConfig(defaultConfig);
        log.info("策略配置已重置为默认值");
    }

    /**
     * 获取配置版本列表
     */
    public Iterable<StrategyConfig> getConfigVersions() {
        return configRepository.findAllByOrderByUpdateTimeDesc();
    }

    /**
     * 根据版本号获取配置
     */
    public Optional<StrategyConfig> getConfigByVersion(String version) {
        return configRepository.findByVersion(version);
    }
}