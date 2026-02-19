package online.mwang.stockTrading.modules.datacollection;

import online.mwang.stockTrading.modules.datacollection.entity.StockInfo;
import online.mwang.stockTrading.modules.datacollection.entity.StockPrices;
import online.mwang.stockTrading.modules.datacollection.service.StockDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 股票数据采集服务测试类
 */
@SpringBootTest
class StockDataServiceImplTest {

    @Autowired
    private StockDataService stockDataService;

    /**
     * 测试获取股票列表
     */
    @Test
    void testFetchStockList() {
        List<StockInfo> stockList = stockDataService.fetchStockList();
        
        // 验证返回结果
        assertNotNull(stockList);
        assertFalse(stockList.isEmpty(), "Stock list should not be empty");
        assertTrue(stockList.size() > 4000, "Should return at least 4000 stocks");
        
        // 验证第一条数据
        StockInfo firstStock = stockList.get(0);
        assertNotNull(firstStock.getCode(), "Stock code should not be null");
        assertNotNull(firstStock.getName(), "Stock name should not be null");
        assertNotNull(firstStock.getMarket(), "Market should not be null");
        
        // 验证过滤条件（不应包含ST股票）
        boolean hasStStock = stockList.stream()
                .anyMatch(s -> Boolean.TRUE.equals(s.getIsSt()));
        assertFalse(hasStStock, "Should not contain ST stocks");
        
        System.out.println("Fetched " + stockList.size() + " stocks");
        System.out.println("First stock: " + firstStock.getCode() + " - " + firstStock.getName());
    }

    /**
     * 测试获取历史数据
     */
    @Test
    void testFetchHistoricalData() {
        String testStockCode = "000001"; // 平安银行
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);
        
        List<StockPrices> prices = stockDataService.fetchHistoricalData(testStockCode, startDate, endDate);
        
        // 验证返回结果
        assertNotNull(prices);
        assertFalse(prices.isEmpty(), "Historical data should not be empty");
        
        // 验证数据格式
        StockPrices firstPrice = prices.get(0);
        assertEquals(testStockCode, firstPrice.getCode(), "Stock code should match");
        assertNotNull(firstPrice.getDate(), "Date should not be null");
        assertNotNull(firstPrice.getPrice1(), "Open price should not be null");
        assertNotNull(firstPrice.getPrice4(), "Close price should not be null");
        
        System.out.println("Fetched " + prices.size() + " historical records for " + testStockCode);
    }

    /**
     * 测试获取近N天历史数据
     */
    @Test
    void testFetchHistoricalDataWithDays() {
        String testStockCode = "000001";
        int days = 10;
        
        List<StockPrices> prices = stockDataService.fetchHistoricalData(testStockCode, days);
        
        assertNotNull(prices);
        System.out.println("Fetched " + prices.size() + " records for the last " + days + " days");
    }

    /**
     * 测试获取实时价格
     */
    @Test
    void testFetchRealTimePrice() {
        String testStockCode = "000001";
        
        Double price = stockDataService.fetchRealTimePrice(testStockCode);
        
        // 验证返回结果（可能在非交易时间返回null）
        if (price != null) {
            assertTrue(price > 0, "Price should be positive");
            System.out.println("Real-time price for " + testStockCode + ": " + price);
        } else {
            System.out.println("Could not fetch real-time price (may be outside trading hours)");
        }
    }

    /**
     * 测试同步单只股票历史数据
     */
    @Test
    void testSyncStockHistory() {
        String testStockCode = "000001";
        int days = 7;
        
        // 执行同步（不应抛出异常）
        assertDoesNotThrow(() -> {
            stockDataService.syncStockHistory(testStockCode, days);
        });
        
        System.out.println("Successfully synced history for " + testStockCode);
    }

    /**
     * 测试全量同步（可选，耗时较长）
     * 取消 @Disabled 注解以运行此测试
     */
    @Test
    void testSyncAllStocks() {
        // 注意：此测试会实际调用外部API并写入数据库
        // 运行前请确保数据库连接正常
        assertDoesNotThrow(() -> {
            stockDataService.syncAllStocks();
        });
        
        System.out.println("Full sync completed successfully");
    }
}
