// AI_GENERATE_START------------
package com.stock.execution.repository;

import com.stock.execution.entity.BrokerOrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 券商委托事实仓库。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface BrokerOrderRepository
        extends JpaRepository<BrokerOrderEntity, Long>, JpaSpecificationExecutor<BrokerOrderEntity> {

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

    /**
     * 按时间顺序查询账户内指定状态的全部委托。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @param status 标准委托状态
     * @return 按成交时间和主键升序排列的委托
     */
    List<BrokerOrderEntity> findByBrokerCodeAndAccountIdAndStatusOrderBySubmittedAtAscIdAsc(
            String brokerCode, String accountId, String status);

    /**
     * 删除当前账户内缺少有效成交数量或成交均价的成交委托。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 删除数量
     */
    @Modifying
    @Transactional
    @Query("""
            delete from BrokerOrderEntity item
            where item.brokerCode = :brokerCode
              and item.accountId = :accountId
              and item.status = 'FILLED'
              and (item.filledQuantity is null or item.filledQuantity <= 0
                   or item.averageFillPrice is null or item.averageFillPrice <= 0)
            """)
    int deleteInvalidFilledOrders(@Param("brokerCode") String brokerCode,
                                  @Param("accountId") String accountId);

}
// AI_GENERATE_END------------
