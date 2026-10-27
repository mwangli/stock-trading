// AI_GENERATE_START---------
package com.stock.trading.repository;

import com.stock.trading.entity.TradeRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * FIFO 汇总交易记录仓库。
 *
 * @author mwangli
 * @since 2026-10-11
 */
@Repository
public interface TradeRecordRepository
        extends JpaRepository<TradeRecordEntity, Long>, JpaSpecificationExecutor<TradeRecordEntity> {

    /**
     * 查询指定券商账户的全部交易记录，按买入时间倒序返回。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 交易记录
     */
    List<TradeRecordEntity> findByBrokerCodeAndAccountIdOrderByBuyTimeDesc(
            String brokerCode, String accountId);

    /**
     * 查询账户最近一笔已实现交易记录。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @param soldQuantity 最小已卖出数量下界
     * @return 最近已实现交易，不存在时为空
     */
    Optional<TradeRecordEntity> findFirstByBrokerCodeAndAccountIdAndSoldQuantityGreaterThanAndLastSellTimeIsNotNullOrderByLastSellTimeDesc(
            String brokerCode, String accountId, Integer soldQuantity);

    /**
     * 删除指定券商账户的全部派生交易记录。
     *
     * @param brokerCode 券商编码
     * @param accountId 账户哈希标识
     * @return 删除数量
     */
    long deleteByBrokerCodeAndAccountId(String brokerCode, String accountId);
}
// AI_GENERATE_END---------
