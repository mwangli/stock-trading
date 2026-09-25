// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.PositionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 持仓快照头仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, Long> {
}
// AI_GENERATE_END --
