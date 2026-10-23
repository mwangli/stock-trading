// AI_GENERATE_START--------
package com.stock.data.service;

import com.stock.backend.config.CacheNames;
import com.stock.data.entity.TradeCalendarEntity;
import com.stock.data.enums.CalendarDayStatus;
import com.stock.data.enums.CalendarVerificationStatus;
import com.stock.data.repository.TradeCalendarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 生产交易日解析服务。
 * 缺失或未确认的日期一律按不可交易处理，避免节假日和来源中断时静默回退到普通工作日。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Service
@RequiredArgsConstructor
public class TradingCalendarService {

    private static final String MARKET = "CN";

    private final TradeCalendarRepository tradeCalendarRepository;

    /**
     * 判断指定日期是否为已确认交易日。
     *
     * @param date 自然日期
     * @return 仅在已确认开市时返回 true
     */
    @Cacheable(cacheNames = CacheNames.TRADING_CALENDAR, key = "'isTradingDay:' + #date")
    public boolean isTradingDay(LocalDate date) {
        return tradeCalendarRepository.findByMarketAndTradeDate(MARKET, date)
                .filter(this::isConfirmed)
                .map(item -> item.getDayStatus() == CalendarDayStatus.OPEN && item.isTradingDay())
                .orElse(false);
    }

    /**
     * 判断指定日期是否已有可靠日历结论。
     *
     * @param date 自然日期
     * @return 开市或休市已确认时返回 true
     */
    @Cacheable(cacheNames = CacheNames.TRADING_CALENDAR, key = "'isConfirmedDate:' + #date")
    public boolean isConfirmedDate(LocalDate date) {
        return tradeCalendarRepository.findByMarketAndTradeDate(MARKET, date)
                .map(this::isConfirmed)
                .orElse(false);
    }

    /**
     * 查询日期范围内已确认的交易日。
     *
     * @param startDate 起始日期
     * @param endDate 结束日期
     * @return 按日期升序排列的交易日
     */
    @Cacheable(cacheNames = CacheNames.TRADING_CALENDAR,
            key = "'tradingDaysBetween:' + #startDate + ':' + #endDate")
    public List<LocalDate> tradingDaysBetween(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("交易日历范围无效");
        }
        List<TradeCalendarEntity> records = tradeCalendarRepository
                .findByMarketAndTradeDateBetweenOrderByTradeDateAsc(MARKET, startDate, endDate);
        long expectedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (records.size() != expectedDays || records.stream().anyMatch(item -> !isConfirmed(item))) {
            throw new IllegalStateException("交易日历区间存在缺口或未确认日期: " + startDate + "~" + endDate);
        }
        return records.stream()
                .filter(item -> item.getDayStatus() == CalendarDayStatus.OPEN && item.isTradingDay())
                .map(TradeCalendarEntity::getTradeDate)
                .toList();
    }

    /**
     * 要求指定日期必须为已确认交易日。
     *
     * @param date 自然日期
     * @throws IllegalStateException 日期缺失、未确认或休市时抛出
     */
    public void requireTradingDay(LocalDate date) {
        TradeCalendarEntity entity = requireConfirmedDate(date);
        if (entity.getDayStatus() != CalendarDayStatus.OPEN || !entity.isTradingDay()) {
            throw new IllegalStateException("指定日期不是交易日: " + date);
        }
    }

    /**
     * 解析指定日期之后的首个已确认交易日。
     *
     * @param date 基准日期
     * @return 下一交易日
     */
    @Cacheable(cacheNames = CacheNames.TRADING_CALENDAR, key = "'nextTradingDay:' + #date")
    public LocalDate nextTradingDay(LocalDate date) {
        requireConfirmedDate(date);
        TradeCalendarEntity candidate = tradeCalendarRepository
                .findFirstByMarketAndDayStatusAndVerificationStatusAndTradeDateAfterOrderByTradeDateAsc(
                        MARKET, CalendarDayStatus.OPEN, CalendarVerificationStatus.CONFIRMED, date)
                .orElseThrow(() -> new IllegalStateException("交易日历未覆盖基准日期之后的已确认交易日: " + date));
        verifyContinuousCoverage(date.plusDays(1), candidate.getTradeDate());
        return candidate.getTradeDate();
    }

    /**
     * 解析指定日期之前最近的已确认交易日。
     *
     * @param date 基准日期
     * @return 前一交易日
     */
    @Cacheable(cacheNames = CacheNames.TRADING_CALENDAR, key = "'previousTradingDay:' + #date")
    public LocalDate previousTradingDay(LocalDate date) {
        requireConfirmedDate(date);
        TradeCalendarEntity candidate = tradeCalendarRepository
                .findFirstByMarketAndDayStatusAndVerificationStatusAndTradeDateBeforeOrderByTradeDateDesc(
                        MARKET, CalendarDayStatus.OPEN, CalendarVerificationStatus.CONFIRMED, date)
                .orElseThrow(() -> new IllegalStateException("交易日历未覆盖基准日期之前的已确认交易日: " + date));
        verifyContinuousCoverage(candidate.getTradeDate(), date.minusDays(1));
        return candidate.getTradeDate();
    }

    private TradeCalendarEntity requireConfirmedDate(LocalDate date) {
        return tradeCalendarRepository.findByMarketAndTradeDate(MARKET, date)
                .filter(this::isConfirmed)
                .orElseThrow(() -> new IllegalStateException("交易日历日期缺失或尚未确认: " + date));
    }

    private boolean isConfirmed(TradeCalendarEntity entity) {
        return entity.getVerificationStatus() == CalendarVerificationStatus.CONFIRMED
                && entity.getDayStatus() != CalendarDayStatus.UNKNOWN;
    }

    private void verifyContinuousCoverage(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            return;
        }
        List<TradeCalendarEntity> records = tradeCalendarRepository
                .findByMarketAndTradeDateBetweenOrderByTradeDateAsc(MARKET, startDate, endDate);
        long expectedDays = java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (records.size() != expectedDays || records.stream().anyMatch(item -> !isConfirmed(item))) {
            throw new IllegalStateException("交易日历区间存在缺口或未确认日期: " + startDate + "~" + endDate);
        }
    }
}
// AI_GENERATE_END--------
