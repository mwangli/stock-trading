// AI_GENERATE_START ---
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.AccountSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 账户资金快照仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshotEntity, Long> {

    /**
     * 查询指定券商账户最新的资金快照。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 最新资金快照
     */
    Optional<AccountSnapshotEntity> findFirstByBrokerCodeAndAccountIdOrderByCapturedAtDesc(
            String brokerCode, String accountId);
}
// AI_GENERATE_END ---
