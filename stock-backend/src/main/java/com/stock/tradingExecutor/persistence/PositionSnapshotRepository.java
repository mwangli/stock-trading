// AI_GENERATE_START ---
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.PositionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 持仓快照头仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface PositionSnapshotRepository extends JpaRepository<PositionSnapshotEntity, Long> {

    /**
     * 查询指定券商账户最新的持仓快照头。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 最新持仓快照头
     */
    Optional<PositionSnapshotEntity> findFirstByBrokerCodeAndAccountIdOrderByCapturedAtDesc(
            String brokerCode, String accountId);
}
// AI_GENERATE_END ---
