// AI_GENERATE_START --
package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.BrokerOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 券商委托事实仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BrokerOrderRepository extends JpaRepository<BrokerOrderEntity, Long> {

    /**
     * 按券商、账户、交易日和委托编号查询唯一委托。
     *
     * @param brokerCode   券商编码
     * @param accountId    账户哈希标识
     * @param tradeDate    交易日
     * @param brokerOrderNo 券商委托编号
     * @return 匹配的委托
     */
    Optional<BrokerOrderEntity> findByBrokerCodeAndAccountIdAndTradeDateAndBrokerOrderNo(
            String brokerCode, String accountId, LocalDate tradeDate, String brokerOrderNo);
}
// AI_GENERATE_END --
