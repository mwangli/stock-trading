package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.TradeRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 交易记录Repository
 *
 * @author mwangli
 * @since 2026-04-01
 */
@Repository
public interface TradeRecordRepository extends JpaRepository<TradeRecord, Long> {

    /**
     * 根据交易ID查询交易记录
     */
    Optional<TradeRecord> findByTradeId(String tradeId);

    /**
     * 根据买入订单号查询交易记录
     */
    Optional<TradeRecord> findByBuyOrderNo(String buyOrderNo);

    /**
     * 检查交易记录是否存在
     */
    boolean existsByTradeId(String tradeId);

    /**
     * 根据股票代码查询交易记录
     */
    Iterable<TradeRecord> findByStockCode(String stockCode);

    /**
     * 根据股票代码分页查询交易记录
     */
    Page<TradeRecord> findByStockCode(String stockCode, Pageable pageable);
}
