package com.stock.strategyAnalysis.repository;

import com.stock.strategyAnalysis.entity.StrategyConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略配置Repository
 * 使用MongoDB存储配置版本
 */
@Repository
public interface ConfigRepository extends MongoRepository<StrategyConfig, String> {
    
    /**
     * 查询最新配置
     */
    Optional<StrategyConfig> findFirstByEnabledTrueOrderByUpdateTimeDesc();
    
    /**
     * 根据版本号查询配置
     */
    Optional<StrategyConfig> findByVersion(String version);
    
    /**
     * 查询所有历史配置版本
     */
    List<StrategyConfig> findAllByOrderByUpdateTimeDesc();
    
    /**
     * 根据配置ID查询
     */
    Optional<StrategyConfig> findByConfigId(String configId);
}