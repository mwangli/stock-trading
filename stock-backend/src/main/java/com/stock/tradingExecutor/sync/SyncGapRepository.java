// AI_GENERATE_START -
package com.stock.tradingExecutor.sync;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 同步缺口仓库。
 *
 * @author mwangli
 * @since 2026-10-16
 */
@Repository
public interface SyncGapRepository extends JpaRepository<SyncGapEntity, Long> {

    /** 查询数据集范围的未解决缺口。 */
    List<SyncGapEntity> findByDatasetAndScopeKeyAndStatusOrderByWindowStartAsc(
            String dataset, String scopeKey, String status);
}
// AI_GENERATE_END -
