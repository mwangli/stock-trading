// AI_GENERATE_START ---
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.PositionSnapshotItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 持仓快照明细仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface PositionSnapshotItemRepository extends JpaRepository<PositionSnapshotItemEntity, Long> {

    /**
     * 查询指定持仓快照的全部明细。
     *
     * @param snapshotId 持仓快照编号
     * @return 按股票代码排序的持仓明细
     */
    List<PositionSnapshotItemEntity> findBySnapshotIdOrderByStockCodeAsc(String snapshotId);
}
// AI_GENERATE_END ---
