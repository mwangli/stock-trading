// AI_GENERATE_START-------
package com.stock.strategy.repository;

import com.stock.strategy.entity.StrategyConfig;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 策略配置 MongoDB 仓储。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Repository
public interface StrategyConfigRepository extends MongoRepository<StrategyConfig, Long> {

    /**
     * 查询当前启用的最新一条配置。
     *
     * @return 最新启用配置，不存在时为空
     */
    Optional<StrategyConfig> findFirstByEnabledTrueOrderByUpdateTimeDesc();

    /**
     * 按版本号查询配置。
     *
     * @param version 配置版本号
     * @return 匹配配置，不存在时为空
     */
    Optional<StrategyConfig> findByVersion(String version);

    /**
     * 按更新时间倒序查询所有配置版本。
     *
     * @return 配置版本列表
     */
    List<StrategyConfig> findAllByOrderByUpdateTimeDesc();

    /**
     * 按配置业务标识查询。
     *
     * @param configId 配置业务标识
     * @return 匹配配置，不存在时为空
     */
    Optional<StrategyConfig> findByConfigId(String configId);
}
// AI_GENERATE_END-------
