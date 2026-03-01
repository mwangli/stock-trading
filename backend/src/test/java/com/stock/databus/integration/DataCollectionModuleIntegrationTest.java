package com.stock.databus.integration;

import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.service.StockDataService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据采集模块综合集成测试
 * 测试完整的数据采集、存储、查询流程
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/stock_trading_test"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DataCollectionModuleIntegrationTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private PriceRepository priceRepository;

    private static final String TEST_STOCK_1 = "600519";
    private static final String TEST_STOCK_2 = "000001";
    private static final String TEST_STOCK_3 = "601398";

    @BeforeEach
    void setUp() {
        // 清理测试数据
        priceRepository.deleteByCode(TEST_STOCK_1);
        priceRepository.deleteByCode(TEST_STOCK_2);
        priceRepository.deleteByCode(TEST_STOCK_3);
    }

    /**
     * 测试 1: 完整数据采集流程
     */
    @Test
    @Order(1)
    @DisplayName("完整数据采集流程测试")
    void testCompleteDataCollectionFlow() {
        System.out.println("=== 测试 1: 完整数据采集流程 ===");

        // 1. 准备数据
        List<StockPrice> prices = createTestData(TEST_STOCK_1, 60);

        // 2. 保存数据
        stockDataService.saveStockPrices(prices);

        // 3. 查询验证
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);

        // 4. 验证结果
        assertNotNull(savedPrices);
        assertEquals(60, savedPrices.size(), "应保存 60 条数据");

        // 5. 验证数据完整性
        for (StockPrice price : savedPrices) {
            assertNotNull(price.getCode());
            assertNotNull(price.getDate());
            assertNotNull(price.getOpenPrice());
            assertNotNull(price.getClosePrice());
        }

        System.out.println("✓ 完整流程测试通过");
    }

    /**
     * 测试 2: 多股票并行处理
     */
    @Test
    @Order(2)
    @DisplayName("多股票并行处理测试")
    void testMultiStockParallelProcessing() {
        System.out.println("=== 测试 2: 多股票并行处理 ===");

        // 为多支股票保存数据
        List<StockPrice> prices1 = createTestData(TEST_STOCK_1, 30);
        List<StockPrice> prices2 = createTestData(TEST_STOCK_2, 30);
        List<StockPrice> prices3 = createTestData(TEST_STOCK_3, 30);

        stockDataService.saveStockPrices(prices1);
        stockDataService.saveStockPrices(prices2);
        stockDataService.saveStockPrices(prices3);

        // 验证每支股票的数据
        List<StockPrice> saved1 = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);
        List<StockPrice> saved2 = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_2);
        List<StockPrice> saved3 = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_3);

        assertEquals(30, saved1.size());
        assertEquals(30, saved2.size());
        assertEquals(30, saved3.size());

        System.out.println("✓ 多股票处理测试通过");
        System.out.println("  股票 1: " + saved1.size() + " 条");
        System.out.println("  股票 2: " + saved2.size() + " 条");
        System.out.println("  股票 3: " + saved3.size() + " 条");
    }

    /**
     * 测试 3: 数据查询性能
     */
    @Test
    @Order(3)
    @DisplayName("数据查询性能测试")
    void testQueryPerformance() {
        System.out.println("=== 测试 3: 数据查询性能 ===");

        // 准备大量数据
        List<StockPrice> prices = createTestData(TEST_STOCK_1, 500);
        stockDataService.saveStockPrices(prices);

        // 测试查询性能
        int iterations = 100;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < iterations; i++) {
            priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);
        }

        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        long avgTime = totalTime / iterations;

        System.out.println("✓ 查询性能测试通过");
        System.out.println("  查询次数：" + iterations);
        System.out.println("  总耗时：" + totalTime + "ms");
        System.out.println("  平均耗时：" + avgTime + "ms");

        // 验证性能（平均查询时间应小于 50ms）
        assertTrue(avgTime < 50, "平均查询时间应小于 50ms，实际：" + avgTime + "ms");
    }

    /**
     * 测试 4: 增量更新性能
     */
    @Test
    @Order(4)
    @DisplayName("增量更新性能测试")
    void testIncrementalUpdatePerformance() {
        System.out.println("=== 测试 4: 增量更新性能 ===");

        // 首次保存
        List<StockPrice> prices = createTestData(TEST_STOCK_1, 100);
        stockDataService.saveStockPrices(prices);

        // 再次保存相同数据（应跳过）
        long startTime = System.currentTimeMillis();
        stockDataService.saveStockPrices(prices);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;

        // 验证数据量没有增加
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);
        assertEquals(100, savedPrices.size());

        System.out.println("✓ 增量更新测试通过");
        System.out.println("  更新耗时：" + duration + "ms");

        // 增量更新应很快（小于 1 秒）
        assertTrue(duration < 1000, "增量更新应在 1 秒内完成");
    }

    /**
     * 测试 5: 数据范围查询
     */
    @Test
    @Order(5)
    @DisplayName("数据范围查询测试")
    void testRangeQuery() {
        System.out.println("=== 测试 5: 数据范围查询 ===");

        // 准备 90 天数据
        List<StockPrice> prices = createTestData(TEST_STOCK_1, 90);
        stockDataService.saveStockPrices(prices);

        // 查询最近 30 天
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        List<StockPrice> recentPrices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
            TEST_STOCK_1, startDate, endDate
        );

        assertNotNull(recentPrices);
        assertTrue(recentPrices.size() > 0);
        assertTrue(recentPrices.size() <= 90);

        // 验证日期范围
        for (StockPrice price : recentPrices) {
            assertTrue(!price.getDate().isBefore(startDate) && !price.getDate().isAfter(endDate));
        }

        System.out.println("✓ 范围查询测试通过");
        System.out.println("  查询结果：" + recentPrices.size() + " 条");
    }

    /**
     * 测试 6: 获取最新价格
     */
    @Test
    @Order(6)
    @DisplayName("获取最新价格测试")
    void testGetLatestPrice() {
        System.out.println("=== 测试 6: 获取最新价格 ===");

        List<StockPrice> prices = createTestData(TEST_STOCK_1, 60);
        stockDataService.saveStockPrices(prices);

        StockPrice latest = stockDataService.getLatestPrice(TEST_STOCK_1);

        assertNotNull(latest);
        assertEquals(TEST_STOCK_1, latest.getCode());

        // 验证是最新的
        List<StockPrice> allPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);
        LocalDate maxDate = allPrices.get(allPrices.size() - 1).getDate();
        assertEquals(maxDate, latest.getDate());

        System.out.println("✓ 获取最新价格测试通过");
        System.out.println("  最新日期：" + latest.getDate());
        System.out.println("  收盘价：" + latest.getClosePrice());
    }

    /**
     * 测试 7: 大数据量批量保存
     */
    @Test
    @Order(7)
    @DisplayName("大数据量批量保存测试")
    void testLargeBatchSave() {
        System.out.println("=== 测试 7: 大数据量批量保存 ===");

        // 准备 1000 条数据
        List<StockPrice> prices = createTestData(TEST_STOCK_1, 1000);

        long startTime = System.currentTimeMillis();
        stockDataService.saveStockPrices(prices);
        long endTime = System.currentTimeMillis();

        long duration = endTime - startTime;

        // 验证保存结果
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);
        assertEquals(1000, savedPrices.size());

        System.out.println("✓ 大数据量保存测试通过");
        System.out.println("  保存数量：" + savedPrices.size());
        System.out.println("  耗时：" + duration + "ms");
        System.out.println("  速度：" + (1000.0 / (duration / 1000.0)) + " 条/秒");
    }

    /**
     * 测试 8: 数据一致性验证
     */
    @Test
    @Order(8)
    @DisplayName("数据一致性验证测试")
    void testDataConsistency() {
        System.out.println("=== 测试 8: 数据一致性验证 ===");

        List<StockPrice> prices = createTestData(TEST_STOCK_1, 50);
        stockDataService.saveStockPrices(prices);

        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_1);

        // 验证数据一致性
        for (StockPrice price : savedPrices) {
            // 最高价 >= 最低价
            assertTrue(
                price.getHighPrice().compareTo(price.getLowPrice()) >= 0,
                "最高价应大于等于最低价"
            );

            // 所有价格应为正数
            assertTrue(price.getOpenPrice().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(price.getHighPrice().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(price.getLowPrice().compareTo(BigDecimal.ZERO) > 0);
            assertTrue(price.getClosePrice().compareTo(BigDecimal.ZERO) > 0);

            // 成交量应为正数
            assertTrue(price.getVolume().compareTo(BigDecimal.ZERO) > 0);
        }

        System.out.println("✓ 数据一致性验证通过");
    }

    /**
     * 创建测试数据
     */
    private List<StockPrice> createTestData(String code, int days) {
        List<StockPrice> prices = new ArrayList<>();
        BigDecimal basePrice = new BigDecimal("10.00");

        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(days - i);

            // 跳过周末
            if (date.getDayOfWeek().getValue() > 5) {
                continue;
            }

            double changePercent = (Math.random() - 0.5) * 0.05;
            basePrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(changePercent)));

            StockPrice price = new StockPrice();
            price.setCode(code);
            price.setName("测试股票");
            price.setDate(date);

            BigDecimal openPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setOpenPrice(openPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            BigDecimal highPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setHighPrice(highPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            BigDecimal lowPrice = basePrice.multiply(BigDecimal.ONE.subtract(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setLowPrice(lowPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            BigDecimal closePrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setClosePrice(closePrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            BigDecimal volume = new BigDecimal(String.valueOf(
                10000 + Math.random() * 50000));
            price.setVolume(volume.setScale(2, BigDecimal.ROUND_HALF_UP));

            BigDecimal amount = closePrice.multiply(volume);
            price.setAmount(amount.setScale(2, BigDecimal.ROUND_HALF_UP));

            prices.add(price);
        }

        return prices;
    }

    @AfterEach
    void tearDown() {
        priceRepository.deleteByCode(TEST_STOCK_1);
        priceRepository.deleteByCode(TEST_STOCK_2);
        priceRepository.deleteByCode(TEST_STOCK_3);
    }
}