// AI_GENERATE_START ------
package com.stock.strategyAnalysis.service;

import com.stock.dataCollector.domain.entity.HistoricalStockUniverseEntity;
import com.stock.dataCollector.domain.entity.StockAdjustmentFactor;
import com.stock.dataCollector.domain.entity.StockMinuteBar;
import com.stock.dataCollector.domain.entity.StockPrice;
import com.stock.dataCollector.domain.entity.TradeCalendarEntity;
import com.stock.dataCollector.persistence.HistoricalStockUniverseRepository;
import com.stock.dataCollector.persistence.StockInfoRepository;
import com.stock.dataCollector.persistence.TradeCalendarRepository;
import com.stock.strategyAnalysis.domain.dto.BacktestDataAuditDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T+1 回测数据可用性审计服务。
 * 只读取现有 MySQL 和 MongoDB 元数据，不创建伪造数据，也不修改行情事实。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BacktestDataAuditService {

    private final StockInfoRepository stockInfoRepository;
    private final HistoricalStockUniverseRepository historicalStockUniverseRepository;
    private final TradeCalendarRepository tradeCalendarRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * 审计指定区间是否具备 T+1 回测所需的数据。
     *
     * @param startDate 开始日期，包含当天
     * @param endDate 结束日期，包含当天
     * @return 数据覆盖情况和生产门禁阻塞项
     */
    public BacktestDataAuditDto audit(LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        long currentStockCount = stockInfoRepository.count();
        long dailyRecordCount = 0;
        int dailyStockCount = 0;
        LocalDate earliestDailyDate = null;
        LocalDate latestDailyDate = null;

        if (mongoTemplate.collectionExists(StockPrice.class)) {
            Query rangeQuery = Query.query(Criteria.where("date").gte(startDate).lte(endDate));
            dailyRecordCount = mongoTemplate.count(rangeQuery, StockPrice.class);
            dailyStockCount = mongoTemplate.findDistinct(
                    rangeQuery, "code", StockPrice.class, String.class).size();
            earliestDailyDate = findBoundaryDate(rangeQuery, Sort.Direction.ASC);
            latestDailyDate = findBoundaryDate(rangeQuery, Sort.Direction.DESC);
        }

        long historicalUniverseRecordCount = historicalStockUniverseRepository.countOverlapping(startDate, endDate);
        long historicalUniverseStartCount = historicalStockUniverseRepository.countActiveOn(startDate);
        long historicalUniverseEndCount = historicalStockUniverseRepository.countActiveOn(endDate);
        LocalDate earliestHistoricalUniverseDate = historicalStockUniverseRepository
                .findFirstByOrderByEffectiveFromAsc()
                .map(HistoricalStockUniverseEntity::getEffectiveFrom)
                .orElse(null);
        boolean historicalUniverseAvailable = historicalUniverseRecordCount > 0
                && historicalUniverseStartCount > 0
                && historicalUniverseEndCount > 0
                && earliestHistoricalUniverseDate != null
                && !earliestHistoricalUniverseDate.isAfter(startDate);

        long expectedCalendarDates = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        long tradeCalendarRecordCount = tradeCalendarRepository
                .countDistinctDatesBetween(startDate, endDate);
        LocalDate earliestTradeCalendarDate = tradeCalendarRepository.findFirstByOrderByTradeDateAsc()
                .map(TradeCalendarEntity::getTradeDate)
                .orElse(null);
        LocalDate latestTradeCalendarDate = tradeCalendarRepository.findFirstByOrderByTradeDateDesc()
                .map(TradeCalendarEntity::getTradeDate)
                .orElse(null);
        boolean tradeCalendarAvailable = tradeCalendarRecordCount == expectedCalendarDates
                && earliestTradeCalendarDate != null
                && !earliestTradeCalendarDate.isAfter(startDate)
                && latestTradeCalendarDate != null
                && !latestTradeCalendarDate.isBefore(endDate);
        List<LocalDate> calendarTradingDates = tradeCalendarAvailable
                ? tradeCalendarRepository.findTradingDates(startDate, endDate) : List.of();
        LocalDate requiredDataStart = calendarTradingDates.isEmpty()
                ? startDate : calendarTradingDates.get(0);
        LocalDate requiredDataEnd = calendarTradingDates.isEmpty()
                ? endDate : calendarTradingDates.get(calendarTradingDates.size() - 1);

        MongoCoverage adjustmentCoverage = inspectMongoCoverage(
                StockAdjustmentFactor.class, "tradeDate", "stockCode", startDate, endDate);
        long missingForwardFactorCount = countMissingForwardAdjustmentFactors(startDate, endDate);
        boolean adjustedPriceAvailable = dailyRecordCount > 0
                && adjustmentCoverage.recordCount() >= dailyRecordCount
                && adjustmentCoverage.stockCount() >= dailyStockCount
                && missingForwardFactorCount == 0
                && coversRange(adjustmentCoverage.earliestDate(), adjustmentCoverage.latestDate(),
                requiredDataStart, requiredDataEnd);

        boolean minuteCollectionExists = mongoTemplate.collectionExists(StockMinuteBar.class);
        MongoCoverage minuteCoverage = inspectMongoCoverage(
                StockMinuteBar.class, "tradingDate", "stockCode", startDate, endDate);
        List<String> minuteVolumeUnits = findDistinctStrings(
                StockMinuteBar.class, "tradingDate", "volumeUnit", startDate, endDate);
        List<String> minuteAmountUnits = findDistinctStrings(
                StockMinuteBar.class, "tradingDate", "amountUnit", startDate, endDate);
        long minuteMissingUnitCount = countMissingMinuteUnits(startDate, endDate);
        boolean minuteCoverageAvailable = minuteCollectionExists
                && minuteCoverage.recordCount() > 0
                && minuteCoverage.stockCount() >= dailyStockCount
                && coversRange(minuteCoverage.earliestDate(), minuteCoverage.latestDate(),
                requiredDataStart, requiredDataEnd);
        boolean volumeUnitVerified = minuteCoverage.recordCount() > 0
                && minuteMissingUnitCount == 0
                && minuteVolumeUnits.size() == 1
                && minuteAmountUnits.size() == 1
                && isKnownVolumeUnit(minuteVolumeUnits.get(0))
                && isKnownAmountUnit(minuteAmountUnits.get(0));

        List<String> blockers = buildBlockers(requiredDataStart, requiredDataEnd, currentStockCount,
                dailyRecordCount, dailyStockCount, earliestDailyDate, latestDailyDate,
                historicalUniverseAvailable, tradeCalendarAvailable, adjustedPriceAvailable,
                minuteCoverageAvailable, volumeUnitVerified);
        boolean dailyBaselineRunnable = (currentStockCount > 0 || historicalUniverseAvailable)
                && dailyRecordCount > 0
                && dailyStockCount > 0
                && earliestDailyDate != null
                && !earliestDailyDate.isAfter(startDate.plusDays(7))
                && latestDailyDate != null
                && !latestDailyDate.isBefore(endDate.minusDays(7));

        BacktestDataAuditDto result = BacktestDataAuditDto.builder()
                .requestedStartDate(startDate)
                .requestedEndDate(endDate)
                .currentStockCount(currentStockCount)
                .dailyRecordCount(dailyRecordCount)
                .dailyStockCount(dailyStockCount)
                .earliestDailyDate(earliestDailyDate)
                .latestDailyDate(latestDailyDate)
                .minuteCollectionExists(minuteCollectionExists)
                .minuteRecordCount(minuteCoverage.recordCount())
                .minuteStockCount(minuteCoverage.stockCount())
                .earliestMinuteDate(minuteCoverage.earliestDate())
                .latestMinuteDate(minuteCoverage.latestDate())
                .minuteVolumeUnits(minuteVolumeUnits)
                .minuteAmountUnits(minuteAmountUnits)
                .historicalUniverseRecordCount(historicalUniverseRecordCount)
                .historicalUniverseStartCount(historicalUniverseStartCount)
                .historicalUniverseEndCount(historicalUniverseEndCount)
                .earliestHistoricalUniverseDate(earliestHistoricalUniverseDate)
                .tradeCalendarRecordCount(tradeCalendarRecordCount)
                .earliestTradeCalendarDate(earliestTradeCalendarDate)
                .latestTradeCalendarDate(latestTradeCalendarDate)
                .adjustmentFactorRecordCount(adjustmentCoverage.recordCount())
                .adjustmentFactorStockCount(adjustmentCoverage.stockCount())
                .earliestAdjustmentFactorDate(adjustmentCoverage.earliestDate())
                .latestAdjustmentFactorDate(adjustmentCoverage.latestDate())
                .adjustedPriceAvailable(adjustedPriceAvailable)
                .historicalUniverseAvailable(historicalUniverseAvailable)
                .tradeCalendarAvailable(tradeCalendarAvailable)
                .volumeUnitVerified(volumeUnitVerified)
                .dailyBaselineRunnable(dailyBaselineRunnable)
                .productionBacktestReady(blockers.isEmpty())
                .blockers(List.copyOf(blockers))
                .auditedAt(LocalDateTime.now())
                .build();
        log.info("[BacktestDataAudit] 数据审计完成: start={}, end={}, dailyRecords={}, dailyStocks={}, ready={}",
                startDate, endDate, dailyRecordCount, dailyStockCount, result.isProductionBacktestReady());
        return result;
    }

    private <T> MongoCoverage inspectMongoCoverage(Class<T> entityClass, String dateField,
                                                    String stockField, LocalDate startDate,
                                                    LocalDate endDate) {
        if (!mongoTemplate.collectionExists(entityClass)) {
            return MongoCoverage.empty();
        }
        Query rangeQuery = Query.query(Criteria.where(dateField).gte(startDate).lte(endDate));
        long recordCount = mongoTemplate.count(rangeQuery, entityClass);
        int stockCount = mongoTemplate.findDistinct(
                rangeQuery, stockField, entityClass, String.class).size();
        LocalDate earliestDate = findBoundaryDate(rangeQuery, entityClass, dateField, Sort.Direction.ASC);
        LocalDate latestDate = findBoundaryDate(rangeQuery, entityClass, dateField, Sort.Direction.DESC);
        return new MongoCoverage(recordCount, stockCount, earliestDate, latestDate);
    }

    private <T> LocalDate findBoundaryDate(Query rangeQuery, Class<T> entityClass,
                                           String dateField, Sort.Direction direction) {
        Query boundaryQuery = Query.of(rangeQuery)
                .with(Sort.by(direction, dateField))
                .limit(1);
        T entity = mongoTemplate.findOne(boundaryQuery, entityClass);
        if (entity instanceof StockAdjustmentFactor factor) {
            return factor.getTradeDate();
        }
        if (entity instanceof StockMinuteBar minuteBar) {
            return minuteBar.getTradingDate();
        }
        return null;
    }

    private List<String> findDistinctStrings(Class<?> entityClass, String dateField,
                                             String valueField, LocalDate startDate,
                                             LocalDate endDate) {
        if (!mongoTemplate.collectionExists(entityClass)) {
            return List.of();
        }
        Query rangeQuery = Query.query(Criteria.where(dateField).gte(startDate).lte(endDate));
        return mongoTemplate.findDistinct(rangeQuery, valueField, entityClass, String.class).stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private long countMissingMinuteUnits(LocalDate startDate, LocalDate endDate) {
        if (!mongoTemplate.collectionExists(StockMinuteBar.class)) {
            return 0;
        }
        Criteria missingUnits = new Criteria().orOperator(
                Criteria.where("volumeUnit").in(null, ""),
                Criteria.where("amountUnit").in(null, ""));
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("tradingDate").gte(startDate).lte(endDate), missingUnits));
        return mongoTemplate.count(query, StockMinuteBar.class);
    }

    private long countMissingForwardAdjustmentFactors(LocalDate startDate, LocalDate endDate) {
        if (!mongoTemplate.collectionExists(StockAdjustmentFactor.class)) {
            return 0;
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("tradeDate").gte(startDate).lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("forwardFactor").is(null),
                        Criteria.where("forwardFactor").lte(0))));
        return mongoTemplate.count(query, StockAdjustmentFactor.class);
    }

    private boolean coversRange(LocalDate earliestDate, LocalDate latestDate,
                                LocalDate startDate, LocalDate endDate) {
        return earliestDate != null && !earliestDate.isAfter(startDate)
                && latestDate != null && !latestDate.isBefore(endDate);
    }

    private boolean isKnownVolumeUnit(String value) {
        return "SHARE".equals(value) || "LOT".equals(value);
    }

    private boolean isKnownAmountUnit(String value) {
        return "CNY".equals(value) || "RMB".equals(value) || "YUAN".equals(value);
    }

    private LocalDate findBoundaryDate(Query rangeQuery, Sort.Direction direction) {
        Query boundaryQuery = Query.of(rangeQuery)
                .with(Sort.by(direction, "date"))
                .limit(1);
        StockPrice price = mongoTemplate.findOne(boundaryQuery, StockPrice.class);
        return price == null ? null : price.getDate();
    }

    private List<String> buildBlockers(LocalDate startDate, LocalDate endDate,
                                       long currentStockCount, long dailyRecordCount,
                                       int dailyStockCount, LocalDate earliestDailyDate,
                                       LocalDate latestDailyDate,
                                       boolean historicalUniverseAvailable,
                                       boolean tradeCalendarAvailable,
                                       boolean adjustedPriceAvailable,
                                       boolean minuteCoverageAvailable,
                                       boolean volumeUnitVerified) {
        List<String> blockers = new ArrayList<>();
        if (currentStockCount == 0 && !historicalUniverseAvailable) {
            blockers.add("当前股票基础表和历史股票池均没有可用股票信息");
        }
        if (dailyRecordCount == 0 || dailyStockCount == 0) {
            blockers.add("请求区间没有日线行情数据");
        } else {
            if (earliestDailyDate == null || earliestDailyDate.isAfter(startDate)) {
                blockers.add("日线起始日期未覆盖请求开始日期");
            }
            if (latestDailyDate == null || latestDailyDate.isBefore(endDate)) {
                blockers.add("日线结束日期未覆盖请求结束日期");
            }
        }
        if (!adjustedPriceAvailable) {
            blockers.add("复权因子未按请求区间覆盖全部日线股票和记录");
        }
        if (!historicalUniverseAvailable) {
            blockers.add("历史股票池未覆盖请求区间起止日期");
        }
        if (!tradeCalendarAvailable) {
            blockers.add("独立交易日历未连续覆盖请求区间全部自然日期");
        }
        if (!minuteCoverageAvailable) {
            blockers.add("5 分钟行情未按请求区间覆盖日线股票范围");
        }
        if (!volumeUnitVerified) {
            blockers.add("5 分钟行情成交量或成交额单位缺失、不一致或不受支持");
        }
        log.debug("[BacktestDataAudit] 门禁阻塞项: start={}, end={}, blockers={}",
                startDate, endDate, blockers.size());
        return blockers;
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("回测数据审计的开始日期和结束日期不能为空");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("回测数据审计的开始日期不能晚于结束日期");
        }
    }

    private record MongoCoverage(long recordCount, int stockCount,
                                 LocalDate earliestDate, LocalDate latestDate) {

        private static MongoCoverage empty() {
            return new MongoCoverage(0, 0, null, null);
        }
    }
}
// AI_GENERATE_END ------
