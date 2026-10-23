// AI_GENERATE_START----------
package com.stock.trading.repository;

import com.stock.trading.entity.BrokerOrderEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
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
public interface BrokerOrderRepository extends MongoRepository<BrokerOrderEntity, Long> {

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

}
// AI_GENERATE_END----------
