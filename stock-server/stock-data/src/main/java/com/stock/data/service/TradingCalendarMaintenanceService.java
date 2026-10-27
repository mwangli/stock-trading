// AI_GENERATE_START-----------
package com.stock.data.service;

import com.stock.base.cache.CacheNames;
import com.stock.data.entity.TradeCalendarEntity;
import com.stock.data.enums.CalendarClosureType;
import com.stock.data.enums.CalendarDayStatus;
import com.stock.data.enums.CalendarVerificationStatus;
import com.stock.data.repository.TradeCalendarRepository;
import com.stock.data.client.TradingCalendarSourceClient;
import com.stock.data.client.TradingCalendarSourceClient.CalendarSourceSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * 交易日历滚动维护服务。
 * 首次执行回补最近三年至未来一年，后续每月刷新来源并自动补齐新增月份和中断缺口。
 *
 * @author mwangli
 * @since 2026-10-13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradingCalendarMaintenanceService {

    private static final String MARKET = "CN";
    private static final String SOURCE = "EASTMONEY_CLOSURE_CALENDAR";
    private static final int HISTORY_BACKFILL_YEARS = 3;
    private static final ZoneId MARKET_ZONE = ZoneId.of("Asia/Shanghai");

    private final TradeCalendarRepository tradeCalendarRepository;
    private final TradingCalendarSourceClient sourceClient;

    /**
     * 回补历史缺口、确认已发布年份，并将滚动日历维持到未来一年月末。
     *
     * @return 本次同步统计
     * @throws IllegalStateException 公开日历来源为空、格式无效或持久化失败时抛出
     */
    @CacheEvict(cacheNames = CacheNames.TRADING_CALENDAR, allEntries = true)
    public CalendarMaintenanceResult maintainRollingCalendar() {
        CalendarSourceSnapshot snapshot = sourceClient.fetchAStockClosures();
        LocalDate today = LocalDate.now(MARKET_ZONE);
        LocalDate startDate = today.minusYears(HISTORY_BACKFILL_YEARS);
        LocalDate endDate = YearMonth.from(today).plusMonths(12).atEndOfMonth();
        List<TradeCalendarEntity> existingRecords = tradeCalendarRepository
                .findByMarketAndTradeDateBetweenOrderByTradeDateAsc(MARKET, startDate, endDate);
        Map<LocalDate, TradeCalendarEntity> recordsByDate = new HashMap<>();
        existingRecords.forEach(item -> recordsByDate.put(item.getTradeDate(), item));

        int insertedCount = 0;
        int confirmedCount = 0;
        int unverifiedCount = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            TradeCalendarEntity entity = recordsByDate.get(date);
            if (entity == null) {
                entity = TradeCalendarEntity.builder()
                        .market(MARKET)
                        .tradeDate(date)
                        .createdAt(snapshot.fetchedAt())
                        .build();
                recordsByDate.put(date, entity);
                insertedCount++;
            }
            applyCalendarStatus(entity, date, snapshot);
            entity.setSource(SOURCE);
            entity.setSourceVersion(snapshot.sourceVersion());
            entity.setSourceFetchedAt(snapshot.fetchedAt());
            entity.setUpdatedAt(snapshot.fetchedAt());
            if (entity.getVerificationStatus() == CalendarVerificationStatus.CONFIRMED) {
                entity.setConfirmedAt(snapshot.fetchedAt());
                confirmedCount++;
            } else {
                entity.setConfirmedAt(null);
                unverifiedCount++;
            }
        }

        List<TradeCalendarEntity> records = recordsByDate.values().stream()
                .sorted(java.util.Comparator.comparing(TradeCalendarEntity::getTradeDate))
                .toList();
        rebuildTradingDayLinks(records);
        tradeCalendarRepository.saveAll(records);
        log.info("交易日历滚动维护完成: range={}~{}, inserted={}, confirmed={}, unverified={}, sourceVersion={}",
                startDate, endDate, insertedCount, confirmedCount, unverifiedCount,
                snapshot.sourceVersion());
        return new CalendarMaintenanceResult(startDate, endDate, insertedCount,
                confirmedCount, unverifiedCount, snapshot.sourceVersion());
    }

    private void applyCalendarStatus(TradeCalendarEntity entity, LocalDate date,
                                     CalendarSourceSnapshot snapshot) {
        if (isWeekend(date)) {
            entity.setTradingDay(false);
            entity.setDayStatus(CalendarDayStatus.CLOSED);
            entity.setClosureType(CalendarClosureType.WEEKEND);
            entity.setVerificationStatus(CalendarVerificationStatus.CONFIRMED);
            return;
        }
        if (!snapshot.coversYear(date.getYear())) {
            entity.setTradingDay(false);
            entity.setDayStatus(CalendarDayStatus.UNKNOWN);
            entity.setClosureType(CalendarClosureType.UNKNOWN);
            entity.setVerificationStatus(CalendarVerificationStatus.UNVERIFIED);
            return;
        }
        String holidayName = snapshot.holidayName(date);
        boolean holiday = holidayName != null;
        entity.setTradingDay(!holiday);
        entity.setDayStatus(holiday ? CalendarDayStatus.CLOSED : CalendarDayStatus.OPEN);
        entity.setClosureType(holiday ? CalendarClosureType.HOLIDAY : CalendarClosureType.NONE);
        entity.setVerificationStatus(CalendarVerificationStatus.CONFIRMED);
    }

    private void rebuildTradingDayLinks(List<TradeCalendarEntity> records) {
        NavigableSet<LocalDate> tradingDates = new TreeSet<>();
        records.stream()
                .filter(item -> item.getDayStatus() == CalendarDayStatus.OPEN)
                .filter(item -> item.getVerificationStatus() == CalendarVerificationStatus.CONFIRMED)
                .map(TradeCalendarEntity::getTradeDate)
                .forEach(tradingDates::add);
        for (TradeCalendarEntity entity : records) {
            if (entity.getVerificationStatus() != CalendarVerificationStatus.CONFIRMED) {
                entity.setPreviousTradingDay(null);
                entity.setNextTradingDay(null);
                continue;
            }
            entity.setPreviousTradingDay(tradingDates.lower(entity.getTradeDate()));
            entity.setNextTradingDay(tradingDates.higher(entity.getTradeDate()));
        }
    }

    private boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /**
     * 交易日历维护统计。
     *
     * @param startDate 回补开始日期
     * @param endDate 滚动窗口结束日期
     * @param insertedCount 新增记录数
     * @param confirmedCount 已确认记录数
     * @param unverifiedCount 未确认记录数
     * @param sourceVersion 来源版本
     */
    public record CalendarMaintenanceResult(LocalDate startDate, LocalDate endDate,
                                            int insertedCount, int confirmedCount,
                                            int unverifiedCount, String sourceVersion) {
    }
}
// AI_GENERATE_END-----------
