// AI_GENERATE_START---------------
package com.stock.trading.repository;

import com.stock.backend.config.CacheNames;
import com.stock.trading.entity.SystemRuntimeConfig;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
/**
 * 系统运行控制配置仓储。
 *
 * @author mwangli
 * @since 2026-10-12
 */
@Repository
public interface SystemRuntimeConfigRepository extends JpaRepository<SystemRuntimeConfig, String> {

    /**
     * 按节点标识查询运行配置并缓存可重建结果。
     *
     * @param nodeId 节点标识
     * @return 节点运行配置
     */
    @Override
    @Cacheable(cacheNames = CacheNames.RUNTIME_CONFIG, key = "#nodeId",
            unless = "#result == null || #result.isEmpty()")
    Optional<SystemRuntimeConfig> findById(String nodeId);

    /**
     * 保存运行配置并刷新对应节点缓存。
     *
     * @param entity 运行配置实体
     * @param <S> 实体类型
     * @return 已保存实体
     */
    @Override
    @CachePut(cacheNames = CacheNames.RUNTIME_CONFIG, key = "#result.nodeId")
    <S extends SystemRuntimeConfig> S save(S entity);
}
// AI_GENERATE_END---------------
