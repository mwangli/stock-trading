package com.stock.strategyAnalysis.persistence;

import com.stock.strategyAnalysis.domain.entity.StrategyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略配置 JPA 仓储（MySQL）
 *
 * @author AI Assistant
 * @since 1.0
 */
@Repository
public interface StrategyConfigRepository extends JpaRepository<StrategyConfig, Long> {

    /**
     * 查询当前启用的最新一条配置（按更新时间倒序）
     */
    Optional<StrategyConfig> findFirstByEnabledTrueOrderByUpdateTimeDesc();

    /**
     * 按版本号查询
     */
    Optional<StrategyConfig> findByVersion(String version);

    /**
     * 按更新时间倒序查询所有配置（版本列表）
     */
    List<StrategyConfig> findAllByOrderByUpdateTimeDesc();

    /**
     * 按 configId 查询
     */
    Optional<StrategyConfig> findByConfigId(String configId);
}
