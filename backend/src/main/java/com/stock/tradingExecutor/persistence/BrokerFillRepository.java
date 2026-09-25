// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.BrokerFillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 券商成交事实仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BrokerFillRepository extends JpaRepository<BrokerFillEntity, Long> {

    /**
     * 按券商、账户和成交编号查询唯一成交。
     *
     * @param brokerCode  券商编码
     * @param accountId   账户哈希标识
     * @param brokerFillNo 券商成交编号或稳定合成键
     * @return 匹配的成交
     */
    Optional<BrokerFillEntity> findByBrokerCodeAndAccountIdAndBrokerFillNo(
            String brokerCode, String accountId, String brokerFillNo);
}
// AI_GENERATE_END --
