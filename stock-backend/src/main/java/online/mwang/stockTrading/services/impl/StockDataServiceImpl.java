package online.mwang.stockTrading.services.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.entities.StockInfo;
import online.mwang.stockTrading.entities.StockPrices;
import online.mwang.stockTrading.repositories.StockInfoRepository;
import online.mwang.stockTrading.repositories.StockPricesRepository;
import online.mwang.stockTrading.services.StockDataService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 股票数据服务实现
 * 【V2.0架构】数据采集由Python层负责写入MySQL/MongoDB
 * 
 * Java层仅从数据库查询数据，不直接调用外部API：
 * - queryStockList() - 从MySQL查询
 * - queryHistoricalData() - 从MongoDB查询
 * - queryRealTimePrice() - 从Redis/MySQL查询
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDataServiceImpl implements StockDataService {

    private final StockInfoRepository stockInfoRepository;
    private final StockPricesRepository stockPricesRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String STOCK_QUOTE_CACHE_PREFIX = "stock:quote:";
    private static final long QUOTE_CACHE_TTL = 60; // 1分钟

    @Override
    public List<StockInfo> fetchStockList() {
        log.info("Querying stock list from MySQL...");
        return stockInfoRepository.findByDeletedAndIsTradable("0", 1);
    }

    @Override
    public List<StockPrices> fetchHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.info("Querying historical data for {} from {} to {}", stockCode, startDate, endDate);
        
        String startDateStr = startDate.format(DATE_FORMATTER);
        String endDateStr = endDate.format(DATE_FORMATTER);
        return stockPricesRepository.findByCodeAndDateBetween(stockCode, startDateStr, endDateStr);
    }

    @Override
    public List<StockPrices> fetchHistoricalData(String stockCode, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return fetchHistoricalData(stockCode, startDate, endDate);
    }

    @Override
    public Double fetchRealTimePrice(String stockCode) {
        // 1. Try Redis cache first
        String cacheKey = STOCK_QUOTE_CACHE_PREFIX + stockCode;
        Object cachedPrice = redisTemplate.opsForValue().get(cacheKey);
        if (cachedPrice != null) {
            log.debug("Cache hit for stock: {}", stockCode);
            return (Double) cachedPrice;
        }
        
        // 2. Fallback to MySQL
        StockInfo stock = stockInfoRepository.findByCodeAndDeleted(stockCode, "0");
        if (stock != null && stock.getPrice() != null) {
            return stock.getPrice();
        }
        
        log.warn("No price found for stock: {}", stockCode);
        return null;
    }

    @Override
    public String fetchFinancialReport(String stockCode) {
        log.warn("Financial report fetching not implemented for stock: {}", stockCode);
        return "{}";
    }

    @Override
    public void syncAllStocks() {
        log.info("Data sync is handled by Python APScheduler. Manual sync not needed in V2.0.");
    }

    @Override
    public void syncStockHistory(String stockCode, int days) {
        log.info("Historical data sync is handled by Python APScheduler.");
    }

    // ===============================
    // Query interfaces (read from database)
    // ===============================

    @Override
    public List<StockInfo> queryStockList() {
        log.debug("Querying stock list from MySQL...");
        return stockInfoRepository.findByDeletedAndIsTradable("0", 1);
    }

    @Override
    public List<StockPrices> queryHistoricalData(String stockCode, LocalDate startDate, LocalDate endDate) {
        log.debug("Querying historical data from MongoDB for {}...", stockCode);
        String startDateStr = startDate.format(DATE_FORMATTER);
        String endDateStr = endDate.format(DATE_FORMATTER);
        return stockPricesRepository.findByCodeAndDateBetween(stockCode, startDateStr, endDateStr);
    }

    @Override
    public List<StockPrices> queryHistoricalData(String stockCode, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);
        return queryHistoricalData(stockCode, startDate, endDate);
    }

    @Override
    public Double queryRealTimePrice(String stockCode) {
        return fetchRealTimePrice(stockCode);
    }

    @Override
    public void triggerDataSync() {
        log.info("Data sync is handled by Python APScheduler.");
    }
}
