package com.stock.tradingExecutor.persistence;

import com.stock.tradingExecutor.domain.entity.HistoryOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
