// AI_GENERATE_START -
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.TradePerformanceSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 每日交易收益统计快照仓库。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Repository
public interface TradePerformanceSnapshotRepository extends JpaRepository<TradePerformanceSnapshotEntity, Long> {

    /** 查询当前账户最新统计快照。 */
    Optional<TradePerformanceSnapshotEntity> findTopByBrokerCodeAndAccountIdOrderByAsOfDateDesc(
            String brokerCode, String accountId);

    /** 查询当前账户指定日期的统计快照。 */
    Optional<TradePerformanceSnapshotEntity> findByBrokerCodeAndAccountIdAndAsOfDate(
            String brokerCode, String accountId, LocalDate asOfDate);
}
// AI_GENERATE_END -