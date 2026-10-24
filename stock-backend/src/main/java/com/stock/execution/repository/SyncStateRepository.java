// AI_GENERATE_START-------
package com.stock.execution.repository;

import com.stock.execution.entity.SyncStateEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 同步单表状态仓库。
 *
 * @author mwangli
 * @since 2026-10-19
 */
@Repository
public interface SyncStateRepository extends JpaRepository<SyncStateEntity, Long> {

    /**
     * 查询数据集和范围的同步状态。
     *
     * @param dataset 数据集名称
     * @param scopeKey 业务范围
     * @return 同步状态
     */
    Optional<SyncStateEntity> findByDatasetAndScopeKey(String dataset, String scopeKey);
}
// AI_GENERATE_END-------
