// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.AccountSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 账户资金快照仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshotEntity, Long> {
}
// AI_GENERATE_END --
