// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.BrokerSyncBatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 券商同步批次仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BrokerSyncBatchRepository extends JpaRepository<BrokerSyncBatchEntity, Long> {

    /**
     * 查询唯一同步窗口批次。
     *
     * @param brokerCode 券商编码
     * @param accountId  账户哈希标识
     * @param syncType   同步类型
     * @param windowStart 窗口开始日期
     * @param windowEnd  窗口结束日期
     * @return 匹配的同步批次
     */
    Optional<BrokerSyncBatchEntity> findByBrokerCodeAndAccountIdAndSyncTypeAndWindowStartAndWindowEnd(
            String brokerCode, String accountId, String syncType, LocalDate windowStart, LocalDate windowEnd);
}
// AI_GENERATE_END --
