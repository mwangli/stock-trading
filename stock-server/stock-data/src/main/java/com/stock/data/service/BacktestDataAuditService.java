// AI_GENERATE_START----------------
package com.stock.data.service;

import com.stock.data.dto.HistoricalStockUniverseDto;
import com.stock.data.dto.StockAdjustmentFactorDto;
import com.stock.data.entity.HistoricalStockUniverseEntity;
import com.stock.data.entity.StockMinuteBar;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.repository.HistoricalStockUniverseRepository;
import com.stock.data.repository.StockInfoRepository;
import com.stock.data.dto.BacktestDataAuditDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * T+1 回测数据可用性审计服务。
 * 只读取现有 MongoDB 元数据，不创建伪造数据，也不修改行情事实。
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
    private final TradingCalendarService tradingCalendarService;
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

        if (mongoTemplate.collectionExists(StockPriceItemDto.class)) {
            Query rangeQuery = Query.query(Criteria.where("date").gte(startDate).lte(endDate));
            dailyRecordCount = mongoTemplate.count(rangeQuery, StockPriceItemDto.class);
            dailyStockCount = mongoTemplate.findDistinct(
                    rangeQuery, "code", StockPriceItemDto.class, String.class).size();
            earliestDailyDate = findBoundaryDate(rangeQuery, Sort.Direction.ASC);
            latestDailyDate = findBoundaryDate(rangeQuery, Sort.Direction.DESC);
        }

        long historicalUniverseRecordCount = countOverlappingUniverse(startDate, endDate);
        long historicalUniverseStartCount = countActiveUniverse(startDate);
        long historicalUniverseEndCount = countActiveUniverse(endDate);
        LocalDate earliestHistoricalUniverseDate = historicalStockUniverseRepository
                .findFirstByOrderByEffectiveFromAsc()
                .map(HistoricalStockUniverseEntity::getEffectiveFrom)
                .orElse(null);
        boolean historicalUniverseAvailable = historicalUniverseRecordCount > 0
                && historicalUniverseStartCount > 0
                && historicalUniverseEndCount > 0
                && earliestHistoricalUniverseDate != null
                && !earliestHistoricalUniverseDate.isAfter(startDate);

        List<LocalDate> workingDays = tradingCalendarService.tradingDaysBetween(startDate, endDate);
        LocalDate requiredDataStart = workingDays.isEmpty() ? startDate : workingDays.get(0);
        LocalDate requiredDataEnd = workingDays.isEmpty()
                ? endDate : workingDays.get(workingDays.size() - 1);

        MongoCoverage adjustmentCoverage = inspectMongoCoverage(
                StockAdjustmentFactorDto.class, "tradeDate", "stockCode", startDate, endDate);
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
                historicalUniverseAvailable, adjustedPriceAvailable,
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
                .adjustmentFactorRecordCount(adjustmentCoverage.recordCount())
                .adjustmentFactorStockCount(adjustmentCoverage.stockCount())
                .earliestAdjustmentFactorDate(adjustmentCoverage.earliestDate())
                .latestAdjustmentFactorDate(adjustmentCoverage.latestDate())
                .adjustedPriceAvailable(adjustedPriceAvailable)
                .historicalUniverseAvailable(historicalUniverseAvailable)
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
        if (entity instanceof StockAdjustmentFactorDto factor) {
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
        if (!mongoTemplate.collectionExists(StockAdjustmentFactorDto.class)) {
            return 0;
        }
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("tradeDate").gte(startDate).lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("forwardFactor").is(null),
                        Criteria.where("forwardFactor").lte(0))));
        return mongoTemplate.count(query, StockAdjustmentFactorDto.class);
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
        StockPriceItemDto price = mongoTemplate.findOne(boundaryQuery, StockPriceItemDto.class);
        return price == null ? null : price.getDate();
    }

    private List<String> buildBlockers(LocalDate startDate, LocalDate endDate,
                                       long currentStockCount, long dailyRecordCount,
                                       int dailyStockCount, LocalDate earliestDailyDate,
                                       LocalDate latestDailyDate,
                                       boolean historicalUniverseAvailable,
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

    private long countOverlappingUniverse(LocalDate startDate, LocalDate endDate) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("effectiveFrom").lte(endDate),
                new Criteria().orOperator(
                        Criteria.where("effectiveTo").is(null),
                        Criteria.where("effectiveTo").gte(startDate))));
        return mongoTemplate.count(query, HistoricalStockUniverseDto.class);
    }

    private long countActiveUniverse(LocalDate date) {
        Query query = Query.query(new Criteria().andOperator(
                Criteria.where("effectiveFrom").lte(date),
                new Criteria().orOperator(Criteria.where("effectiveTo").is(null),
                        Criteria.where("effectiveTo").gte(date)),
                new Criteria().orOperator(Criteria.where("listedDate").is(null),
                        Criteria.where("listedDate").lte(date)),
                new Criteria().orOperator(Criteria.where("delistedDate").is(null),
                        Criteria.where("delistedDate").gte(date))));
        return mongoTemplate.findDistinct(query, "stockCode",
                HistoricalStockUniverseDto.class, String.class).size();
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
// AI_GENERATE_END----------------
