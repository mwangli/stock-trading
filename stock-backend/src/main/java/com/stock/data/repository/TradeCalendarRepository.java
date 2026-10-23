// AI_GENERATE_START-------------
package com.stock.data.repository;

import com.stock.data.enums.CalendarDayStatus;
import com.stock.data.enums.CalendarVerificationStatus;
import com.stock.data.entity.TradeCalendarEntity;
import org.springframework.data.mongodb.repository.MongoRepository;
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
public interface TradeCalendarRepository extends MongoRepository<TradeCalendarEntity, String> {

    /**
     * 查询指定市场和自然日期的唯一日历记录。
     *
     * @param market 市场代码
     * @param tradeDate 自然日期
     * @return 日历记录
     */
    Optional<TradeCalendarEntity> findByMarketAndTradeDate(String market, LocalDate tradeDate);

    /**
     * 查询市场日期区间内的全部日历记录。
     *
     * @param market 市场代码
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 按日期升序排列的日历记录
     */
    List<TradeCalendarEntity> findByMarketAndTradeDateBetweenOrderByTradeDateAsc(
            String market, LocalDate startDate, LocalDate endDate);

    /**
     * 查询指定日期之后首个已确认交易日。
     *
     * @param market 市场代码
     * @param dayStatus 单日状态
     * @param verificationStatus 确认状态
     * @param tradeDate 基准日期
     * @return 下一交易日记录
     */
    Optional<TradeCalendarEntity> findFirstByMarketAndDayStatusAndVerificationStatusAndTradeDateAfterOrderByTradeDateAsc(
            String market, CalendarDayStatus dayStatus, CalendarVerificationStatus verificationStatus,
            LocalDate tradeDate);

    /**
     * 查询指定日期之前最近的已确认交易日。
     *
     * @param market 市场代码
     * @param dayStatus 单日状态
     * @param verificationStatus 确认状态
     * @param tradeDate 基准日期
     * @return 前一交易日记录
     */
    Optional<TradeCalendarEntity> findFirstByMarketAndDayStatusAndVerificationStatusAndTradeDateBeforeOrderByTradeDateDesc(
            String market, CalendarDayStatus dayStatus, CalendarVerificationStatus verificationStatus,
            LocalDate tradeDate);

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

}
// AI_GENERATE_END-------------
