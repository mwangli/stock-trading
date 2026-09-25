// AI_GENERATE_START -----
package com.stock.dataCollector.persistence;

import com.stock.dataCollector.domain.entity.TradeCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 证券市场交易日历 Repository。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Repository
public interface TradeCalendarRepository extends JpaRepository<TradeCalendarEntity, Long> {

    /**
     * 查询指定市场、自然日期、来源和来源版本的日历记录。
     *
     * @param market 市场代码
     * @param tradeDate 自然日期
     * @param source 数据来源
     * @param sourceVersion 数据来源版本
     * @return 日历记录
     */
    Optional<TradeCalendarEntity> findByMarketAndTradeDateAndSourceAndSourceVersion(
            String market, LocalDate tradeDate, String source, String sourceVersion);

    /**
     * 统计指定市场日期区间内的日历记录。
     *
     * @param market 市场代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 日历记录数量
     */
    long countByMarketAndTradeDateBetween(String market, LocalDate startDate, LocalDate endDate);

    /**
     * 查询市场最早日历记录。
     *
     * @param market 市场代码
     * @return 最早日历记录
     */
    Optional<TradeCalendarEntity> findFirstByMarketOrderByTradeDateAsc(String market);

    /**
     * 查询市场最晚日历记录。
     *
     * @param market 市场代码
     * @return 最晚日历记录
     */
    Optional<TradeCalendarEntity> findFirstByMarketOrderByTradeDateDesc(String market);

    /**
     * 统计日期区间内去重后的自然日期数量。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 去重自然日期数量
     */
    @Query("SELECT COUNT(DISTINCT c.tradeDate) FROM TradeCalendarEntity c "
            + "WHERE c.tradeDate BETWEEN :startDate AND :endDate")
    long countDistinctDatesBetween(@Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);

    /**
     * 查询全市场最早日历记录。
     *
     * @return 最早日历记录
     */
    Optional<TradeCalendarEntity> findFirstByOrderByTradeDateAsc();

    /**
     * 查询全市场最晚日历记录。
     *
     * @return 最晚日历记录
     */
    Optional<TradeCalendarEntity> findFirstByOrderByTradeDateDesc();

    /**
     * 查询日期区间内去重后的交易日。
     *
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 交易日列表
     */
    @Query("SELECT DISTINCT c.tradeDate FROM TradeCalendarEntity c "
            + "WHERE c.tradingDay = true AND c.tradeDate BETWEEN :startDate AND :endDate "
            + "ORDER BY c.tradeDate ASC")
    List<LocalDate> findTradingDates(@Param("startDate") LocalDate startDate,
                                     @Param("endDate") LocalDate endDate);
}
// AI_GENERATE_END -----
