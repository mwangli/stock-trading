// AI_GENERATE_START----------
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
public interface AccountSnapshotRepository extends MongoRepository<AccountSnapshotEntity, String> {

    /**
     * 查询指定券商账户最新的资金快照。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 最新资金快照
     */
    Optional<AccountSnapshotEntity> findFirstByBrokerCodeAndAccountIdOrderByCapturedAtDesc(
            String brokerCode, String accountId);

    /**
     * 查询指定券商账户在截止时点之前的最新资金快照。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @param capturedAt 截止采集时间，包含该时点
     * @return 截止时点前的最新资金快照
     */
    Optional<AccountSnapshotEntity>
            findFirstByBrokerCodeAndAccountIdAndCapturedAtLessThanEqualOrderByCapturedAtDesc(
                    String brokerCode, String accountId, java.time.LocalDateTime capturedAt);
}
// AI_GENERATE_END----------
