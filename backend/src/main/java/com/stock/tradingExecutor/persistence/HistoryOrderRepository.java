package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 历史订单Repository
 *
 * @author mwangli
 * @since 2026-03-31
 */
@Repository
public interface HistoryOrderRepository extends JpaRepository<HistoryOrder, Long> {

    /**
     * 根据委托编号和委托日期查询唯一订单
     */
    Optional<HistoryOrder> findByOrderNoAndOrderDate(String orderNo, String orderDate);

    /**
     * 检查订单是否已存在
     */
    boolean existsByOrderNoAndOrderDate(String orderNo, String orderDate);

    /**
     * 根据股票代码查询历史订单
     */
    List<HistoryOrder> findByStockCode(String stockCode);

    /**
     * 根据股票代码分页查询
     */
    Page<HistoryOrder> findByStockCode(String stockCode, Pageable pageable);

    /**
     * 根据日期范围查询
     */
    List<HistoryOrder> findByOrderDateBetween(String startDate, String endDate);

    /**
     * 根据日期范围分页查询
     */
    Page<HistoryOrder> findByOrderDateBetween(String startDate, String endDate, Pageable pageable);

    /**
     * 根据同步批次号查询
     */
    List<HistoryOrder> findBySyncBatchNo(String syncBatchNo);

    /**
     * 查询指定时间后同步的订单
     */
    @Query("SELECT h FROM HistoryOrder h WHERE h.lastSyncTime > :syncTime ORDER BY h.orderDate DESC, h.orderTime DESC")
    List<HistoryOrder> findRecentlySynced(@Param("syncTime") LocalDateTime syncTime);

    /**
     * 统计某批次同步的订单数量
     */
    long countBySyncBatchNo(String syncBatchNo);

    /**
     * 删除指定批次的订单（用于同步失败回滚）
     */
    void deleteBySyncBatchNo(String syncBatchNo);

    /**
     * 查询最新同步的一批订单
     */
    @Query("SELECT h.syncBatchNo FROM HistoryOrder h WHERE h.syncBatchNo IS NOT NULL GROUP BY h.syncBatchNo ORDER BY MAX(h.lastSyncTime) DESC")
    List<String> findLatestSyncBatchNo(Pageable pageable);

    /**
     * 批量查询已存在的订单（用于upsert前检查）
     */
    @Query("SELECT h FROM HistoryOrder h WHERE h.orderNo IN :orderNos AND h.orderDate IN :orderDates")
    List<HistoryOrder> findExistingByOrderNosAndDates(
            @Param("orderNos") List<String> orderNos,
            @Param("orderDates") List<String> orderDates);

    /**
     * 原生批量Upsert（使用INSERT ... ON DUPLICATE KEY UPDATE）
     * 性能高效，适合大量数据
     */
    @Modifying
    @Query(value = """
        INSERT INTO history_order (
            order_date, order_no, market_type, stock_account, stock_code, stock_name,
            direction, price, quantity, amount, serial_no, order_time, remark, full_name,
            sync_batch_no, last_sync_time, order_submit_time, create_time, update_time
        ) VALUES (
            :orderDate, :orderNo, :marketType, :stockAccount, :stockCode, :stockName,
            :direction, :price, :quantity, :amount, :serialNo, :orderTime, :remark, :fullName,
            :syncBatchNo, :lastSyncTime, :orderSubmitTime, NOW(), NOW()
        ) ON DUPLICATE KEY UPDATE
            market_type = VALUES(market_type),
            stock_account = VALUES(stock_account),
            stock_code = VALUES(stock_code),
            stock_name = VALUES(stock_name),
            direction = VALUES(direction),
            price = VALUES(price),
            quantity = VALUES(quantity),
            amount = VALUES(amount),
            serial_no = VALUES(serial_no),
            order_time = VALUES(order_time),
            remark = VALUES(remark),
            full_name = VALUES(full_name),
            sync_batch_no = VALUES(sync_batch_no),
            last_sync_time = VALUES(last_sync_time),
            order_submit_time = VALUES(order_submit_time),
            update_time = NOW()
        """, nativeQuery = true)
    void upsertOrder(
            @Param("orderDate") String orderDate,
            @Param("orderNo") String orderNo,
            @Param("marketType") String marketType,
            @Param("stockAccount") String stockAccount,
            @Param("stockCode") String stockCode,
            @Param("stockName") String stockName,
            @Param("direction") String direction,
            @Param("price") java.math.BigDecimal price,
            @Param("quantity") Integer quantity,
            @Param("amount") java.math.BigDecimal amount,
            @Param("serialNo") String serialNo,
            @Param("orderTime") String orderTime,
            @Param("remark") String remark,
            @Param("fullName") String fullName,
            @Param("syncBatchNo") String syncBatchNo,
            @Param("lastSyncTime") LocalDateTime lastSyncTime,
            @Param("orderSubmitTime") LocalDateTime orderSubmitTime
    );

    /**
     * 根据多条件动态查询（支持模糊匹配）
     */
    @Query("SELECT h FROM HistoryOrder h WHERE " +
           "(:stockCode IS NULL OR h.stockCode LIKE %:stockCode%) AND " +
           "(:stockName IS NULL OR h.stockName LIKE %:stockName%) AND " +
           "(:direction IS NULL OR h.direction = :direction) AND " +
           "(:startDate IS NULL OR h.orderDate >= :startDate) AND " +
           "(:endDate IS NULL OR h.orderDate <= :endDate) " +
           "ORDER BY h.orderSubmitTime DESC")
    Page<HistoryOrder> findByConditions(
            @Param("stockCode") String stockCode,
            @Param("stockName") String stockName,
            @Param("direction") String direction,
            @Param("startDate") String startDate,
            @Param("endDate") String endDate,
            Pageable pageable
    );
}
