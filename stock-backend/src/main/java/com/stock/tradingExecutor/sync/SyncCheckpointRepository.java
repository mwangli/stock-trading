// AI_GENERATE_START -
package com.stock.tradingExecutor.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 同步水位仓库。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Repository
public interface SyncCheckpointRepository extends JpaRepository<SyncCheckpointEntity, Long> {

    /** 查询数据集范围的水位。 */
    Optional<SyncCheckpointEntity> findByDatasetAndScopeKey(String dataset, String scopeKey);
}
// AI_GENERATE_END -
