// AI_GENERATE_START---------
package com.stock.trading.repository;

import com.stock.trading.entity.AccountSnapshotEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 账户资金快照仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface AccountSnapshotRepository extends MongoRepository<AccountSnapshotEntity, Long> {

    /**
     * 查询指定券商账户唯一保留的最新资金快照。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 最新资金快照
     */
    Optional<AccountSnapshotEntity> findByBrokerCodeAndAccountId(
            String brokerCode, String accountId);
}
// AI_GENERATE_END---------
