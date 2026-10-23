// AI_GENERATE_START-----
package com.stock.data.service;

import com.alibaba.fastjson2.JSONArray;
import com.stock.backend.config.CacheNames;
import com.stock.data.dto.DailyPriceSyncResult;
import com.stock.data.dto.HistoricalPriceSyncResult;
import com.stock.data.dto.KlineDataPayloadDto;
import com.stock.data.dto.KlineDataResponseDto;
import com.stock.data.dto.StockPriceItemDto;
import com.stock.data.entity.StockInfo;
import com.stock.data.entity.StockPrice;
import com.stock.data.enums.DailyPriceSyncStatus;
import com.stock.data.repository.PriceRepository;
import com.stock.data.repository.StockInfoRepository;
import com.stock.data.support.SecuritiesClient;
import com.stock.data.support.StockDataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 股票日线行情服务。
 *
 * 负责供应商行情查询、日线增量同步、历史缺口回补、批量持久化和 K 线载荷组装。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockPriceService {

    /** 供应商单次历史行情疑似最大返回条数。 */
    private static final int SUPPLIER_HISTORY_RESULT_LIMIT = 500;

    /** 历史行情基础切片月数。 */
    private static final int HISTORY_WINDOW_MONTHS = 3;

    /** K 线接口允许返回的最大记录数。 */
    private static final int MAX_KLINE_LIMIT = 5000;

    private final PriceRepository priceRepository;
    private final StockInfoRepository stockInfoRepository;
    private final SecuritiesClient securitiesClient;
    private final MongoTemplate mongoTemplate;

    /**
     * 查询供应商提供的股票历史价格。
     *
     * @param stockCode 股票代码
     * @return 历史日线列表
     * @throws IllegalStateException 供应商调用或数据解析失败时抛出
     */
    public List<StockPriceItemDto> getHistoryPriceItems(String stockCode) {
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, 0);
            return StockDataParser.parsePriceList(results, stockCode).stream()
                    .map(this::toPriceItemDto)
                    .toList();
        } catch (Exception exception) {
            throw new IllegalStateException("获取股票历史价格数据失败: " + stockCode, exception);
        }
    }

    /**
     * 同步单只股票当前交易日的日线数据。
     *
     * @param stockCode 股票代码
     * @param tradeDate 已确认的交易日
     * @return 当日日线同步结果
     */
    @CacheEvict(cacheNames = CacheNames.LATEST_PRICE, key = "#stockCode")
    public DailyPriceSyncResult syncDailyPrice(String stockCode, LocalDate tradeDate) {
        LocalDate listedDate = stockInfoRepository.findByCode(stockCode)
                .map(StockInfo::getListedDate)
                .orElse(null);
        if (listedDate != null && listedDate.isAfter(tradeDate)) {
            return new DailyPriceSyncResult(0, 0, DailyPriceSyncStatus.NOT_LISTED);
        }
        if (priceRepository.existsByCodeAndDate(stockCode, tradeDate)) {
            return new DailyPriceSyncResult(0, 0, DailyPriceSyncStatus.ALREADY_PRESENT);
        }

        WindowSyncResult result = syncPriceChunk(stockCode, tradeDate, tradeDate, Set.of(tradeDate));
        boolean available = priceRepository.existsByCodeAndDate(stockCode, tradeDate);
        return new DailyPriceSyncResult(result.savedCount(), result.apiRequestCount(),
                available ? DailyPriceSyncStatus.SAVED : DailyPriceSyncStatus.NO_DATA);
    }

    /**
     * 回补单只股票在已确认交易日范围内的历史日线缺口。
     *
     * @param stockCode 股票代码
     * @param earliestDate 最早允许回补日期
     * @param endDate 回补截止日期
     * @param confirmedTradingDays 已确认交易日
     * @param openGapStart 已登记缺口开始日期，可为空
     * @param openGapEnd 已登记缺口结束日期，可为空
     * @return 历史回补结果
     */
    @CacheEvict(cacheNames = CacheNames.LATEST_PRICE, key = "#stockCode")
    public HistoricalPriceSyncResult backfillHistoricalPrices(String stockCode,
                                                               LocalDate earliestDate,
                                                               LocalDate endDate,
                                                               List<LocalDate> confirmedTradingDays,
                                                               LocalDate openGapStart,
                                                               LocalDate openGapEnd) {
        LocalDate expectedStartDate = resolveExpectedStartDate(stockCode, earliestDate);
        if (expectedStartDate.isAfter(endDate)) {
            return new HistoricalPriceSyncResult(0, 0, true, List.of());
        }
        Set<LocalDate> confirmedDateSet = confirmedTradingDays.stream()
                .filter(date -> date != null && !date.isBefore(expectedStartDate) && !date.isAfter(endDate))
                .collect(Collectors.toSet());
        Set<LocalDate> existingDates = priceRepository
                .findByCodeAndDateBetweenOrderByDateAsc(stockCode, expectedStartDate, endDate)
                .stream()
                .map(StockPrice::getDate)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<PriceRequestWindow> requestWindows = buildMissingRequestWindows(
                confirmedTradingDays, existingDates, expectedStartDate, endDate);
        addOpenGapWindow(requestWindows, expectedStartDate, endDate, openGapStart, openGapEnd);
        requestWindows = mergeRequestWindows(requestWindows);

        int savedCount = 0;
        int apiRequestCount = 0;
        for (PriceRequestWindow requestWindow : requestWindows) {
            WindowSyncResult result = syncPriceWindow(stockCode, requestWindow, confirmedDateSet);
            savedCount += result.savedCount();
            apiRequestCount += result.apiRequestCount();
        }

        List<LocalDate> unresolvedDates = findUnresolvedTradingDays(
                stockCode, expectedStartDate, endDate, confirmedTradingDays, openGapStart, openGapEnd);
        return new HistoricalPriceSyncResult(
                savedCount, apiRequestCount, requestWindows.isEmpty(), unresolvedDates);
    }

    /**
     * 查询 K 线图表业务数据。
     *
     * @param code 股票代码
     * @param startDate 显式开始日期，可为空
     * @param endDate 显式结束日期，可为空
     * @param timeRange 预设时间范围，可为空
     * @param limit 最大返回记录数
     * @return K 线图表业务数据
     */
    public KlineDataResponseDto getKlineData(String code,
                                             LocalDate startDate,
                                             LocalDate endDate,
                                             String timeRange,
                                             int limit) {
        DateRange range = resolveDateRange(startDate, endDate, timeRange);
        int safeLimit = Math.max(1, Math.min(limit, MAX_KLINE_LIMIT));
        List<StockPrice> prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
                code, range.startDate(), range.endDate());
        if (prices.size() > safeLimit) {
            prices = prices.subList(prices.size() - safeLimit, prices.size());
        }
        List<StockPrice> validPrices = prices.stream()
                .filter(price -> price.getDate() != null)
                .filter(price -> price.getOpenPrice() != null)
                .filter(price -> price.getClosePrice() != null)
                .filter(price -> price.getLowPrice() != null)
                .filter(price -> price.getHighPrice() != null)
                .toList();
        KlineDataPayloadDto payload = KlineDataPayloadDto.builder()
                .dates(validPrices.stream().map(price -> price.getDate().toString()).toList())
                .kline(validPrices.stream().map(price -> List.of(
                        price.getOpenPrice(), price.getClosePrice(),
                        price.getLowPrice(), price.getHighPrice())).toList())
                .volumes(validPrices.stream()
                        .map(price -> price.getVolume() == null ? BigDecimal.ZERO : price.getVolume())
                        .toList())
                .build();
        return KlineDataResponseDto.builder().payload(payload).total(validPrices.size()).build();
    }

    /**
     * 查询本地行情库中的最新日线。
     *
     * @param stockCode 股票代码
     * @return 最新日线，不存在时返回 null
     */
    @Cacheable(cacheNames = CacheNames.LATEST_PRICE, key = "#stockCode", unless = "#result == null")
    public StockPrice getLatestPrice(String stockCode) {
        return priceRepository.findTopByCodeOrderByDateDesc(stockCode).orElse(null);
    }

    private List<StockPrice> getHistoryPrices(String stockCode,
                                              LocalDate startDate,
                                              LocalDate endDate) {
        try {
            JSONArray results = securitiesClient.getHistoryPrices(stockCode, startDate, endDate);
            return StockDataParser.parsePriceList(results, stockCode);
        } catch (Exception exception) {
            throw new IllegalStateException("获取股票区间历史价格数据失败: "
                    + stockCode + ", " + startDate + " ~ " + endDate, exception);
        }
    }

    private void saveStockPrices(List<StockPrice> prices) {
        if (prices == null || prices.isEmpty()) {
            return;
        }
        String stockName = stockInfoRepository.findByCode(prices.get(0).getCode())
                .map(StockInfo::getName)
                .orElse(null);
        BulkOperations bulkOperations = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED, StockPrice.class);
        LocalDateTime now = LocalDateTime.now();
        for (StockPrice price : prices) {
            Query query = Query.query(Criteria.where("code").is(price.getCode()).and("date").is(price.getDate()));
            Update update = new Update()
                    .set("stockName", stockName)
                    .set("openPrice", price.getOpenPrice())
                    .set("highPrice", price.getHighPrice())
                    .set("lowPrice", price.getLowPrice())
                    .set("closePrice", price.getClosePrice())
                    .set("volume", price.getVolume())
                    .set("amount", price.getAmount())
                    .set("updateTime", now)
                    .setOnInsert("createTime", now);
            bulkOperations.upsert(query, update);
        }
        bulkOperations.execute();
    }

    private LocalDate resolveExpectedStartDate(String stockCode, LocalDate earliestDate) {
        return stockInfoRepository.findByCode(stockCode)
                .map(StockInfo::getListedDate)
                .filter(listedDate -> listedDate.isAfter(earliestDate))
                .orElse(earliestDate);
    }

    private List<PriceRequestWindow> buildMissingRequestWindows(List<LocalDate> expectedTradingDays,
                                                                 Set<LocalDate> existingDates,
                                                                 LocalDate expectedStartDate,
                                                                 LocalDate endDate) {
        List<PriceRequestWindow> requestWindows = new ArrayList<>();
        LocalDate windowStart = null;
        LocalDate windowEnd = null;
        for (LocalDate tradingDay : expectedTradingDays) {
            if (tradingDay.isBefore(expectedStartDate) || tradingDay.isAfter(endDate)) {
                continue;
            }
            if (existingDates.contains(tradingDay)) {
                if (windowStart != null) {
                    requestWindows.add(new PriceRequestWindow(windowStart, windowEnd));
                    windowStart = null;
                    windowEnd = null;
                }
                continue;
            }
            if (windowStart == null) {
                windowStart = tradingDay;
            }
            windowEnd = tradingDay;
        }
        if (windowStart != null) {
            requestWindows.add(new PriceRequestWindow(windowStart, windowEnd));
        }
        return requestWindows;
    }

    private void addOpenGapWindow(List<PriceRequestWindow> requestWindows,
                                  LocalDate expectedStartDate,
                                  LocalDate endDate,
                                  LocalDate openGapStart,
                                  LocalDate openGapEnd) {
        if (openGapStart == null || openGapEnd == null) {
            return;
        }
        LocalDate startDate = openGapStart.isBefore(expectedStartDate) ? expectedStartDate : openGapStart;
        LocalDate boundedEndDate = openGapEnd.isAfter(endDate) ? endDate : openGapEnd;
        if (!startDate.isAfter(boundedEndDate)) {
            requestWindows.add(new PriceRequestWindow(startDate, boundedEndDate));
        }
    }

    private List<PriceRequestWindow> mergeRequestWindows(List<PriceRequestWindow> requestWindows) {
        if (requestWindows.size() < 2) {
            return requestWindows;
        }
        List<PriceRequestWindow> sortedWindows = requestWindows.stream()
                .sorted(Comparator.comparing(PriceRequestWindow::startDate))
                .toList();
        List<PriceRequestWindow> mergedWindows = new ArrayList<>();
        for (PriceRequestWindow currentWindow : sortedWindows) {
            if (mergedWindows.isEmpty()) {
                mergedWindows.add(currentWindow);
                continue;
            }
            PriceRequestWindow previousWindow = mergedWindows.get(mergedWindows.size() - 1);
            if (!currentWindow.startDate().isAfter(previousWindow.endDate().plusDays(1))) {
                LocalDate mergedEndDate = currentWindow.endDate().isAfter(previousWindow.endDate())
                        ? currentWindow.endDate() : previousWindow.endDate();
                mergedWindows.set(mergedWindows.size() - 1,
                        new PriceRequestWindow(previousWindow.startDate(), mergedEndDate));
            } else {
                mergedWindows.add(currentWindow);
            }
        }
        return mergedWindows;
    }

    private WindowSyncResult syncPriceWindow(String stockCode,
                                             PriceRequestWindow requestWindow,
                                             Set<LocalDate> allowedDates) {
        int savedCount = 0;
        int apiRequestCount = 0;
        LocalDate cursor = requestWindow.startDate();
        while (!cursor.isAfter(requestWindow.endDate())) {
            LocalDate chunkEnd = cursor.plusMonths(HISTORY_WINDOW_MONTHS).minusDays(1);
            if (chunkEnd.isAfter(requestWindow.endDate())) {
                chunkEnd = requestWindow.endDate();
            }
            WindowSyncResult result = syncPriceChunk(stockCode, cursor, chunkEnd, allowedDates);
            savedCount += result.savedCount();
            apiRequestCount += result.apiRequestCount();
            cursor = chunkEnd.plusDays(1);
        }
        return new WindowSyncResult(savedCount, apiRequestCount);
    }

    private WindowSyncResult syncPriceChunk(String stockCode,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            Set<LocalDate> allowedDates) {
        boolean containsAllowedDate = allowedDates.stream()
                .anyMatch(date -> !date.isBefore(startDate) && !date.isAfter(endDate));
        if (!containsAllowedDate) {
            return new WindowSyncResult(0, 0);
        }
        List<StockPrice> fetchedPrices = getHistoryPrices(stockCode, startDate, endDate);
        List<StockPrice> rangePrices = fetchedPrices.stream()
                .filter(price -> price.getDate() != null)
                .filter(price -> !price.getDate().isBefore(startDate))
                .filter(price -> !price.getDate().isAfter(endDate))
                .toList();
        List<StockPrice> windowPrices = rangePrices.stream()
                .filter(price -> allowedDates.contains(price.getDate()))
                .toList();
        boolean containsOutOfWindowPrice = fetchedPrices.stream()
                .map(StockPrice::getDate)
                .filter(java.util.Objects::nonNull)
                .anyMatch(date -> date.isBefore(startDate) || date.isAfter(endDate));
        if (containsOutOfWindowPrice) {
            log.warn("股票 {} 请求区间 {} ~ {} 返回区间外数据，仅处理区间内 {} 条",
                    stockCode, startDate, endDate, windowPrices.size());
        } else if (rangePrices.size() >= SUPPLIER_HISTORY_RESULT_LIMIT) {
            if (!startDate.isBefore(endDate)) {
                throw new IllegalStateException("供应商单日历史行情达到返回上限: " + stockCode + ", " + startDate);
            }
            long totalDays = ChronoUnit.DAYS.between(startDate, endDate);
            LocalDate leftEndDate = startDate.plusDays(totalDays / 2);
            LocalDate rightStartDate = leftEndDate.plusDays(1);
            WindowSyncResult leftResult = syncPriceChunk(stockCode, startDate, leftEndDate, allowedDates);
            WindowSyncResult rightResult = syncPriceChunk(stockCode, rightStartDate, endDate, allowedDates);
            return new WindowSyncResult(
                    leftResult.savedCount() + rightResult.savedCount(),
                    1 + leftResult.apiRequestCount() + rightResult.apiRequestCount());
        }
        if (windowPrices.isEmpty()) {
            return new WindowSyncResult(0, 1);
        }

        Set<LocalDate> existingDates = priceRepository
                .findByCodeAndDateBetweenOrderByDateAsc(stockCode, startDate, endDate)
                .stream()
                .map(StockPrice::getDate)
                .collect(Collectors.toSet());
        List<StockPrice> toSave = windowPrices.stream()
                .filter(price -> !existingDates.contains(price.getDate()))
                .toList();
        saveStockPrices(toSave);
        return new WindowSyncResult(toSave.size(), 1);
    }

    private List<LocalDate> findUnresolvedTradingDays(String stockCode,
                                                       LocalDate expectedStartDate,
                                                       LocalDate endDate,
                                                       List<LocalDate> expectedTradingDays,
                                                       LocalDate openGapStart,
                                                       LocalDate openGapEnd) {
        Set<LocalDate> expectedDateSet = expectedTradingDays.stream()
                .filter(date -> date != null && !date.isBefore(expectedStartDate) && !date.isAfter(endDate))
                .collect(Collectors.toSet());
        Set<LocalDate> existingDates = priceRepository
                .findByCodeAndDateBetweenOrderByDateAsc(stockCode, expectedStartDate, endDate)
                .stream()
                .map(StockPrice::getDate)
                .collect(Collectors.toSet());
        TreeSet<LocalDate> unresolvedDates = expectedDateSet.stream()
                .filter(date -> !existingDates.contains(date))
                .collect(Collectors.toCollection(TreeSet::new));
        addUnresolvedGapBoundary(unresolvedDates, existingDates, expectedDateSet, openGapStart);
        addUnresolvedGapBoundary(unresolvedDates, existingDates, expectedDateSet, openGapEnd);
        return List.copyOf(unresolvedDates);
    }

    private void addUnresolvedGapBoundary(Set<LocalDate> unresolvedDates,
                                          Set<LocalDate> existingDates,
                                          Set<LocalDate> expectedDates,
                                          LocalDate gapDate) {
        if (gapDate != null && expectedDates.contains(gapDate) && !existingDates.contains(gapDate)) {
            unresolvedDates.add(gapDate);
        }
    }

    private StockPriceItemDto toPriceItemDto(StockPrice price) {
        return StockPriceItemDto.builder()
                .code(price.getCode())
                .stockName(price.getStockName())
                .date(price.getDate())
                .openPrice(price.getOpenPrice())
                .highPrice(price.getHighPrice())
                .lowPrice(price.getLowPrice())
                .closePrice(price.getClosePrice())
                .volume(price.getVolume())
                .amount(price.getAmount())
                .updateTime(price.getUpdateTime())
                .build();
    }

    private DateRange resolveDateRange(LocalDate startDate, LocalDate endDate, String timeRange) {
        if (startDate != null || endDate != null) {
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("开始日期和结束日期必须同时提供");
            }
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("开始日期不能晚于结束日期");
            }
            return new DateRange(startDate, endDate);
        }
        LocalDate resolvedEndDate = LocalDate.now();
        LocalDate resolvedStartDate = switch (timeRange == null ? "" : timeRange) {
            case "last1Week" -> resolvedEndDate.minusWeeks(1);
            case "last3Months" -> resolvedEndDate.minusMonths(3);
            case "last1Year" -> resolvedEndDate.minusYears(1);
            case "last3Years" -> resolvedEndDate.minusYears(3);
            case "last5Years" -> resolvedEndDate.minusYears(5);
            default -> resolvedEndDate.minusMonths(1);
        };
        return new DateRange(resolvedStartDate, resolvedEndDate);
    }

    private record PriceRequestWindow(LocalDate startDate, LocalDate endDate) {
    }

    private record WindowSyncResult(int savedCount, int apiRequestCount) {
    }

    private record DateRange(LocalDate startDate, LocalDate endDate) {
    }
}
// AI_GENERATE_END-----
