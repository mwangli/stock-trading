package online.mwang.stockTrading.modules.datacollection.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.client.AKToolsClient;
import online.mwang.stockTrading.modules.datacollection.dto.KLineDTO;
import online.mwang.stockTrading.modules.datacollection.dto.QuoteDTO;
import online.mwang.stockTrading.modules.datacollection.dto.StockInfoDTO;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.repository.StockPricesRepository;
import online.mwang.stockTrading.modules.datacollection.service.StockDataService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 股票数据采集服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataServiceImpl implements StockDataService {

    private final StockInfoMapper stockInfoMapper;
    private final StockPricesRepository stockPricesRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AKToolsClient akToolsClient;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String STOCK_QUOTE_CACHE_PREFIX = "stock:quote:";
    private static final long QUOTE_CACHE_TTL = 60; // 1分钟
    private static final int SYNC_BATCH_SIZE = 100;
    private static final int THREAD_POOL_SIZE = 10;

    @Override
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<StockInfo> fetchStockList() {
        log.info("Starting to fetch stock list from AKTools...");
        
        List<StockInfoDTO> dtoList = akToolsClient.getStockList();
        if (CollectionUtils.isEmpty(dtoList)) {
            log.warn("No stock data returned from AKTools");
            return Collections.emptyList();
        }

        // 过滤可交易股票（非ST）
        List<StockInfo> stockInfoList = dtoList.stream()
                .filter(dto -> Boolean.FALSE.equals(dto.getIsSt()))
                .filter(dto -> Boolean.TRUE.equals(dto.getIsTradable()))
                .map(this::convertToStockInfo)
                .collect(Collectors.toList());

        log.info("Fetched {} tradable stocks from {} total stocks", 
                stockInfoList.size(), dtoList.size());
        
        return stockInfoList;
    }

    @Override
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public List<StockPrices> fetchHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("Fetching historical data for {} from {} to {}", stockCode, startDate, endDate);

        // 1. 先检查本地数据库
        String startDateStr = startDate.format(DATE_FORMATTER);
        String endDateStr = endDate.format(DATE_FORMATTER);
        List<StockPrices> localData = stockPricesRepository
                .findByCodeAndDateBetween(stockCode, startDateStr, endDateStr);

        if (!CollectionUtils.isEmpty(localData)) {
            log.info("Found {} records in local database for {}", localData.size(), stockCode);
            // 检查数据是否完整
            long daysBetween = endDate.toEpochDay() - startDate.toEpochDay();
            if (localData.size() >= daysBetween * 0.7) { // 假设70%的数据完整度可接受
                return localData;
            }
        }

        // 2. 从API获取数据
        List<KLineDTO> klineData = akToolsClient.getKLine(stockCode, startDate, endDate);
        if (CollectionUtils.isEmpty(klineData)) {
            log.warn("No K-line data returned for stock: {}", stockCode);
            return Collections.emptyList();
        }

        // 3. 转换为实体并保存
        List<StockPrices> pricesList = klineData.stream()
                .map(dto -> convertToStockPrices(stockCode, dto))
                .collect(Collectors.toList());

        // 4. 保存到MongoDB
        savePricesToMongo(pricesList);

        log.info("Successfully fetched and saved {} K-line records for {}", 
                pricesList.size(), stockCode);
        return pricesList;
    }

    @Override
    public List<StockPrices> fetchHistoricalData(String stockCode, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return fetchHistoricalData(stockCode, startDate, endDate);
    }

    @Override
    public Double fetchRealTimePrice(String stockCode) {
        // 1. 先查Redis缓存
        String cacheKey = STOCK_QUOTE_CACHE_PREFIX + stockCode;
        Object cachedPrice = redisTemplate.opsForValue().get(cacheKey);
        if (cachedPrice != null) {
            log.debug("Cache hit for stock: {}", stockCode);
            return (Double) cachedPrice;
        }

        // 2. 从API获取
        QuoteDTO quote = akToolsClient.getQuote(stockCode);
        if (quote == null || quote.getCurrentPrice() == null) {
            log.warn("Failed to get real-time price for stock: {}", stockCode);
            return null;
        }

        // 3. 写入缓存
        Double price = quote.getCurrentPrice().doubleValue();
        redisTemplate.opsForValue().set(cacheKey, price, QUOTE_CACHE_TTL, TimeUnit.SECONDS);

        log.debug("Fetched real-time price for {}: {}", stockCode, price);
        return price;
    }

    @Override
    public String fetchFinancialReport(String stockCode) {
        // 财务报表功能暂不支持，返回空
        log.warn("Financial report fetching not implemented yet for stock: {}", stockCode);
        return "{}";
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncAllStocks() {
        log.info("Starting full stock data synchronization...");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 同步股票列表
            List<StockInfo> stockList = fetchStockList();
            if (CollectionUtils.isEmpty(stockList)) {
                log.error("Failed to fetch stock list, aborting sync");
                return;
            }

            // 2. 保存股票列表到MySQL
            saveStockListToMySQL(stockList);

            // 3. 同步每只股票的历史数据（使用线程池并行处理）
            syncStockHistoryParallel(stockList);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Stock data synchronization completed in {} ms", duration);

        } catch (Exception e) {
            log.error("Stock data synchronization failed", e);
            throw new RuntimeException("Stock sync failed", e);
        }
    }

    @Override
    public void syncStockHistory(String stockCode, int days) {
        log.info("Syncing history for stock: {}, days: {}", stockCode, days);
        
        try {
            List<StockPrices> prices = fetchHistoricalData(stockCode, days);
            log.info("Synced {} history records for {}", prices.size(), stockCode);
        } catch (Exception e) {
            log.error("Failed to sync history for stock: {}", stockCode, e);
        }
    }

    /**
     * 批量同步股票历史数据（并行）
     */
    private void syncStockHistoryParallel(List<StockInfo> stockList) {
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int totalStocks = stockList.size();
        int processedCount = 0;

        for (StockInfo stock : stockList) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    syncStockHistory(stock.getCode(), 60); // 同步最近60天数据
                } catch (Exception e) {
                    log.error("Failed to sync history for stock: {}", stock.getCode(), e);
                }
            }, executor);
            futures.add(future);

            // 每批处理完后等待，避免过载
            if (futures.size() >= SYNC_BATCH_SIZE) {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                processedCount += futures.size();
                log.info("Progress: {}/{} stocks processed", processedCount, totalStocks);
                futures.clear();
            }
        }

        // 处理剩余任务
        if (!futures.isEmpty()) {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            processedCount += futures.size();
        }

        executor.shutdown();
        log.info("History sync completed for {} stocks", processedCount);
    }

    /**
     * 保存股票列表到MySQL
     */
    private void saveStockListToMySQL(List<StockInfo> stockList) {
        log.info("Saving {} stocks to MySQL...", stockList.size());
        
        int batchCount = 0;
        for (StockInfo stock : stockList) {
            try {
                // 检查是否已存在
                StockInfo existing = stockInfoMapper.getByCode(stock.getCode());
                if (existing != null) {
                    // 更新
                    stock.setId(existing.getId());
                    stock.setCreateTime(existing.getCreateTime());
                    stock.setUpdateTime(new Date());
                    stockInfoMapper.updateById(stock);
                } else {
                    // 新增
                    stock.setCreateTime(new Date());
                    stock.setUpdateTime(new Date());
                    stockInfoMapper.insert(stock);
                }
                batchCount++;

                if (batchCount % 100 == 0) {
                    log.info("Progress: {}/{} stocks saved", batchCount, stockList.size());
                }
            } catch (Exception e) {
                log.error("Failed to save stock: {}", stock.getCode(), e);
            }
        }

        log.info("Successfully saved {} stocks to MySQL", batchCount);
    }

    /**
     * 保存价格数据到MongoDB
     */
    private void savePricesToMongo(List<StockPrices> pricesList) {
        if (CollectionUtils.isEmpty(pricesList)) {
            return;
        }

        int savedCount = 0;
        for (StockPrices price : pricesList) {
            try {
                // 检查是否已存在
                Optional<StockPrices> existing = stockPricesRepository
                        .findByCodeAndDate(price.getCode(), price.getDate());
                
                if (existing.isPresent()) {
                    // 更新
                    price.setId(existing.get().getId());
                    stockPricesRepository.save(price);
                } else {
                    // 新增
                    stockPricesRepository.save(price);
                    savedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to save price data for {} on {}", 
                        price.getCode(), price.getDate(), e);
            }
        }

        log.debug("Saved {} new price records", savedCount);
    }

    /**
     * 转换DTO为StockInfo实体
     */
    private StockInfo convertToStockInfo(StockInfoDTO dto) {
        StockInfo stockInfo = new StockInfo();
        stockInfo.setCode(dto.getStockCode());
        stockInfo.setName(dto.getStockName());
        stockInfo.setMarket(dto.getMarket());
        stockInfo.setIndustry(dto.getIndustry());
        stockInfo.setIsSt(dto.getIsSt());
        stockInfo.setIsTradable(dto.getIsTradable());
        stockInfo.setListingDate(dto.getListingDate());
        stockInfo.setDeleted("0");
        stockInfo.setSelected("0");
        return stockInfo;
    }

    /**
     * 转换KLineDTO为StockPrices实体
     */
    private StockPrices convertToStockPrices(String stockCode, KLineDTO dto) {
        StockPrices prices = new StockPrices();
        prices.setCode(stockCode);
        prices.setDate(dto.getTradeDate().format(DATE_FORMATTER));
        prices.setPrice1(dto.getOpen().doubleValue());
        prices.setPrice2(dto.getHigh().doubleValue());
        prices.setPrice3(dto.getLow().doubleValue());
        prices.setPrice4(dto.getClose().doubleValue());
        prices.setIncreaseRate(dto.getChangePct() != null ? dto.getChangePct().doubleValue() : 0.0);
        prices.setTradingVolume(dto.getVolume() != null ? dto.getVolume().doubleValue() : 0.0);
        prices.setTradingAmount(dto.getAmount() != null ? dto.getAmount().doubleValue() : 0.0);
        prices.setExchangeRate(dto.getTurnoverRate() != null ? dto.getTurnoverRate().doubleValue() : 0.0);
        prices.setTodayOpenPrice(dto.getOpen().doubleValue());
        prices.setYesterdayClosePrice(dto.getClose().doubleValue());
        return prices;
    }

    @Recover
    public List<StockInfo> recoverFetchStockList(Exception e) {
        log.error("Failed to fetch stock list after retries", e);
        return Collections.emptyList();
    }

    @Recover
    public List<StockPrices> recoverFetchHistoricalData(Exception e, String stockCode, 
                                                        LocalDate startDate, LocalDate endDate) {
        log.error("Failed to fetch historical data for {} after retries", stockCode, e);
        return Collections.emptyList();
    }
}
