// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.PositionSnapshotItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 持仓快照明细仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface PositionSnapshotItemRepository extends JpaRepository<PositionSnapshotItemEntity, Long> {
}
// AI_GENERATE_END --
