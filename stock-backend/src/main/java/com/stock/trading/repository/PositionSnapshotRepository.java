// AI_GENERATE_START--------
package com.stock.trading.repository;

import com.stock.trading.entity.PositionSnapshotEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 持仓快照仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface PositionSnapshotRepository extends MongoRepository<PositionSnapshotEntity, String> {

    /**
     * 查询指定券商账户最新的持仓快照头。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 最新持仓快照头
     */
    Optional<PositionSnapshotEntity> findFirstByBrokerCodeAndAccountIdOrderByCapturedAtDesc(
            String brokerCode, String accountId);

    /**
     * 查询指定快照中的实际持仓明细。
     *
     * @param snapshotId 快照编号
     * @return 按股票代码排序的持仓明细
     */
    List<PositionSnapshotEntity> findBySnapshotIdAndStockCodeIsNotNullOrderByStockCodeAsc(
            String snapshotId);
}
// AI_GENERATE_END--------
