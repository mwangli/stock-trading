package online.mwang.stockTrading.modules.datacollection;

import lombok.extern.slf4j.Slf4j;
import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.mapper.StockInfoMapper;
import online.mwang.stockTrading.modules.datacollection.repository.StockPricesRepository;
import online.mwang.stockTrading.modules.datacollection.service.StockDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据采集模块集成测试 - 验证数据写入数据库
 */
@Slf4j
@SpringBootTest
class DataCollectionIntegrationTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private StockInfoMapper stockInfoMapper;

    @Autowired
    private StockPricesRepository stockPricesRepository;

    /**
     * 测试股票列表获取并写入MySQL数据库
     */
    @Test
    void testFetchAndSaveStockList() {
        log.info("=== Testing fetch and save stock list ===");
        
        // 1. 获取股票列表
        List<StockInfo> stockList = stockDataService.fetchStockList();
        assertNotNull(stockList, "Stock list should not be null");
        assertFalse(stockList.isEmpty(), "Stock list should not be empty");
        log.info("Fetched {} stocks from API", stockList.size());

        // 2. 保存第一条股票信息到数据库进行测试
        if (!stockList.isEmpty()) {
            StockInfo testStock = stockList.get(0);
            testStock.setCreateTime(new Date());
            testStock.setUpdateTime(new Date());
            testStock.setDeleted("0");
            testStock.setSelected("0");
            
            // 检查是否已存在
            StockInfo existing = stockInfoMapper.getByCode(testStock.getCode());
            if (existing != null) {
                testStock.setId(existing.getId());
                stockInfoMapper.updateById(testStock);
                log.info("Updated existing stock: {} - {}", testStock.getCode(), testStock.getName());
            } else {
                stockInfoMapper.insert(testStock);
                log.info("Inserted new stock: {} - {}", testStock.getCode(), testStock.getName());
            }

            // 3. 验证数据已写入
            StockInfo savedStock = stockInfoMapper.getByCode(testStock.getCode());
            assertNotNull(savedStock, "Stock should be saved to database");
            assertEquals(testStock.getCode(), savedStock.getCode(), "Stock code should match");
            assertEquals(testStock.getName(), savedStock.getName(), "Stock name should match");
            
            log.info("✅ Stock data successfully written to MySQL database!");
            log.info("Stock code: {}, Name: {}, Market: {}", 
                    savedStock.getCode(), savedStock.getName(), savedStock.getMarket());
        }
    }

    /**
     * 测试历史数据获取并写入MongoDB
     */
    @Test
    void testFetchAndSaveHistoricalData() {
        log.info("=== Testing fetch and save historical data ===");
        
        String testStockCode = "000001"; // 平安银行
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(7);
        
        // 1. 获取历史数据
        List<StockPrices> pricesList = stockDataService.fetchHistoricalData(testStockCode, startDate, endDate);
        assertNotNull(pricesList, "Price list should not be null");
        log.info("Fetched {} price records for stock {}", pricesList.size(), testStockCode);

        // 2. 验证数据已写入MongoDB
        if (!CollectionUtils.isEmpty(pricesList)) {
            // 查询最近的数据
            List<StockPrices> savedPrices = stockPricesRepository.findByCode(testStockCode);
            assertFalse(savedPrices.isEmpty(), "Price data should be saved to MongoDB");
            
            log.info("✅ Historical data successfully written to MongoDB!");
            log.info("Found {} records in MongoDB for stock {}", savedPrices.size(), testStockCode);
            
            // 显示第一条数据
            StockPrices firstPrice = savedPrices.get(0);
            log.info("Sample data - Date: {}, Open: {}, Close: {}",
                    firstPrice.getDate(), firstPrice.getPrice1(), firstPrice.getPrice4());
        }
    }

    /**
     * 测试实时价格获取（带缓存）
     */
    @Test
    void testFetchRealTimePrice() {
        log.info("=== Testing real-time price fetch ===");
        
        String testStockCode = "000001";
        
        Double price = stockDataService.fetchRealTimePrice(testStockCode);
        
        if (price != null) {
            log.info("✅ Successfully fetched real-time price for {}: {}", testStockCode, price);
            assertTrue(price > 0, "Price should be positive");
        } else {
            log.info("⚠️ Could not fetch real-time price (may be outside trading hours)");
        }
    }

    /**
     * 测试单只股票历史同步
     */
    @Test
    void testSyncSingleStock() {
        log.info("=== Testing single stock sync ===");
        
        String testStockCode = "000001";
        int days = 5;
        
        // 执行同步
        assertDoesNotThrow(() -> {
            stockDataService.syncStockHistory(testStockCode, days);
        });

        // 验证数据已写入
        List<StockPrices> savedPrices = stockPricesRepository.findByCode(testStockCode);
        assertFalse(savedPrices.isEmpty(), "Should have saved price data");
        
        log.info("✅ Single stock sync completed successfully!");
        log.info("Total records for {}: {}", testStockCode, savedPrices.size());
    }

    /**
     * 测试数据库连接
     */
    @Test
    void testDatabaseConnections() {
        log.info("=== Testing database connections ===");
        
        // 测试MySQL连接
        try {
            List<StockInfo> allStocks = stockInfoMapper.selectList(null);
            log.info("✅ MySQL connection successful! Found {} stocks in database", allStocks.size());
        } catch (Exception e) {
            fail("MySQL connection failed: " + e.getMessage());
        }

        // 测试MongoDB连接
        try {
            long count = stockPricesRepository.count();
            log.info("✅ MongoDB connection successful! Total price records: {}", count);
        } catch (Exception e) {
            fail("MongoDB connection failed: " + e.getMessage());
        }
    }

    /**
     * 完整数据同步测试（可选，运行时间较长）
     */
    @Test
    void testFullDataSync() {
        log.info("=== Testing full data synchronization ===");
        
        // 注意：此测试会调用外部API并写入数据库
        // 运行前请确保网络连接和数据库服务正常
        
        assertDoesNotThrow(() -> {
            stockDataService.syncAllStocks();
        });
        
        // 验证数据已写入
        List<StockInfo> allStocks = stockInfoMapper.selectList(null);
        assertTrue(allStocks.size() > 0, "Should have stocks in database after sync");
        
        log.info("✅ Full data sync completed! Total stocks in database: {}", allStocks.size());
    }
}
