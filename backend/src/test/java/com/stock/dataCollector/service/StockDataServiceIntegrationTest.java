package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import com.stock.dataCollector.repository.StockRepository;
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
 * 股票数据服务集成测试
 * 使用真实 MongoDB 数据库进行测试，禁止使用 Mock
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://admin:Root.123456@124.220.36.95:27017/stock-trading?authSource=admin"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockDataServiceIntegrationTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private StockRepository stockRepository;

    private static final String TEST_STOCK_CODE = "600519";
    private static final String TEST_STOCK_NAME = "贵州茅台";

    /**
     * 测试前清理数据
     */
    @BeforeEach
    void setUp() {
        // 清理测试数据
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        System.out.println("测试前清理完成");
    }

    /**
     * 测试 1: 保存股票价格数据
     */
    @Test
    @Order(1)
    @DisplayName("保存股票价格数据测试")
    void testSaveStockPrices() {
        System.out.println("=== 测试 1: 保存股票价格数据 ===");

        // 准备测试数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 10);

        // 执行保存
        stockDataService.saveStockPrices(prices);

        // 验证保存结果
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        assertNotNull(savedPrices);
        assertEquals(10, savedPrices.size(), "应保存 10 条价格数据");

        // 验证数据完整性
        StockPrice firstPrice = savedPrices.get(0);
        assertEquals(TEST_STOCK_CODE, firstPrice.getCode());
        assertNotNull(firstPrice.getOpenPrice());
        assertNotNull(firstPrice.getHighPrice());
        assertNotNull(firstPrice.getLowPrice());
        assertNotNull(firstPrice.getClosePrice());
        assertNotNull(firstPrice.getVolume());

        System.out.println("✓ 保存测试通过，共保存 " + savedPrices.size() + " 条数据");
    }

    /**
     * 测试 2: 增量更新 - 重复数据应跳过
     */
    @Test
    @Order(2)
    @DisplayName("增量更新测试 - 重复数据跳过")
    void testIncrementalUpdateSkipDuplicates() {
        System.out.println("=== 测试 2: 增量更新测试 ===");

        // 先保存一批数据
        List<StockPrice> prices1 = createTestStockPrices(TEST_STOCK_CODE, 5);
        stockDataService.saveStockPrices(prices1);

        // 再次保存相同数据（应跳过）
        List<StockPrice> prices2 = createTestStockPrices(TEST_STOCK_CODE, 5);
        stockDataService.saveStockPrices(prices2);

        // 验证数据没有重复
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        assertEquals(5, savedPrices.size(), "重复保存应跳过，保持 5 条数据");

        System.out.println("✓ 增量更新测试通过，重复数据已跳过");
    }

    /**
     * 测试 3: 按股票代码查询
     */
    @Test
    @Order(3)
    @DisplayName("按股票代码查询测试")
    void testFindByStockCode() {
        System.out.println("=== 测试 3: 按股票代码查询 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 10);
        stockDataService.saveStockPrices(prices);

        // 执行查询
        List<StockPrice> foundPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

        // 验证结果
        assertNotNull(foundPrices);
        assertEquals(10, foundPrices.size());

        // 验证排序（按日期升序）
        for (int i = 0; i < foundPrices.size() - 1; i++) {
            assertTrue(
                foundPrices.get(i).getDate().isBefore(foundPrices.get(i + 1).getDate()),
                "数据应按日期升序排列"
            );
        }

        System.out.println("✓ 查询测试通过，查到 " + foundPrices.size() + " 条数据");
    }

    /**
     * 测试 4: 按日期范围查询
     */
    @Test
    @Order(4)
    @DisplayName("按日期范围查询测试")
    void testFindByDateRange() {
        System.out.println("=== 测试 4: 按日期范围查询 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 30);
        stockDataService.saveStockPrices(prices);

        // 查询指定日期范围
        LocalDate startDate = LocalDate.now().minusDays(20);
        LocalDate endDate = LocalDate.now();

        List<StockPrice> foundPrices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
            TEST_STOCK_CODE, startDate, endDate
        );

        // 验证结果
        assertNotNull(foundPrices);
        assertTrue(foundPrices.size() > 0, "应查到数据");
        assertTrue(foundPrices.size() <= 30, "不应超过总数据量");

        // 验证日期范围
        for (StockPrice price : foundPrices) {
            assertTrue(
                !price.getDate().isBefore(startDate) && !price.getDate().isAfter(endDate),
                "日期应在查询范围内"
            );
        }

        System.out.println("✓ 日期范围查询测试通过，查到 " + foundPrices.size() + " 条数据");
    }

    /**
     * 测试 5: 检查数据是否存在
     */
    @Test
    @Order(5)
    @DisplayName("检查数据是否存在测试")
    void testExistsByCodeAndDate() {
        System.out.println("=== 测试 5: 检查数据是否存在 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 5);
        stockDataService.saveStockPrices(prices);

        // 验证已存在的数据
        LocalDate existingDate = LocalDate.now().minusDays(5);
        boolean exists = priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, existingDate);
        assertTrue(exists, "已保存的数据应存在");

        // 验证不存在的数据
        LocalDate nonExistingDate = LocalDate.now().plusDays(1);
        boolean notExists = priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, nonExistingDate);
        assertFalse(notExists, "未来的日期应不存在");

        System.out.println("✓ 存在性检查测试通过");
    }

    /**
     * 测试 6: 获取最新价格
     */
    @Test
    @Order(6)
    @DisplayName("获取最新价格测试")
    void testGetLatestPrice() {
        System.out.println("=== 测试 6: 获取最新价格 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 10);
        stockDataService.saveStockPrices(prices);

        // 获取最新价格
        StockPrice latestPrice = stockDataService.getLatestPrice(TEST_STOCK_CODE);

        // 验证结果
        assertNotNull(latestPrice, "应返回最新价格");
        assertEquals(TEST_STOCK_CODE, latestPrice.getCode());

        // 验证是最新的（日期最大）
        List<StockPrice> allPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        LocalDate maxDate = allPrices.get(allPrices.size() - 1).getDate();
        assertEquals(maxDate, latestPrice.getDate(), "应返回日期最新的价格");

        System.out.println("✓ 获取最新价格测试通过");
        System.out.println("  最新日期：" + latestPrice.getDate());
        System.out.println("  收盘价：" + latestPrice.getClosePrice());
    }

    /**
     * 测试 7: 删除指定股票数据
     */
    @Test
    @Order(7)
    @DisplayName("删除指定股票数据测试")
    void testDeleteByCode() {
        System.out.println("=== 测试 7: 删除数据测试 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 10);
        stockDataService.saveStockPrices(prices);

        // 验证已保存
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        assertEquals(10, savedPrices.size());

        // 删除数据
        priceRepository.deleteByCode(TEST_STOCK_CODE);

        // 验证已删除
        List<StockPrice> deletedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        assertTrue(deletedPrices.isEmpty(), "数据应已被删除");

        System.out.println("✓ 删除测试通过");
    }

    /**
     * 测试 8: 批量保存大数据量
     */
    @Test
    @Order(8)
    @DisplayName("批量保存大数据量测试")
    void testBatchSaveLargeAmount() {
        System.out.println("=== 测试 8: 批量保存大数据量 ===");

        // 准备大量数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 100);

        long startTime = System.currentTimeMillis();

        // 执行批量保存
        stockDataService.saveStockPrices(prices);

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证保存结果
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);
        assertEquals(100, savedPrices.size(), "应保存 100 条数据");

        // 验证性能（应在 5 秒内完成）
        assertTrue(duration < 5000, "批量保存应在 5 秒内完成，实际耗时：" + duration + "ms");

        System.out.println("✓ 批量保存测试通过");
        System.out.println("  保存数量：" + savedPrices.size());
        System.out.println("  耗时：" + duration + "ms");
    }

    /**
     * 测试 9: 数据完整性验证
     */
    @Test
    @Order(9)
    @DisplayName("数据完整性验证测试")
    void testDataIntegrity() {
        System.out.println("=== 测试 9: 数据完整性验证 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 10);
        stockDataService.saveStockPrices(prices);

        // 查询所有数据
        List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

        // 验证每条数据的完整性
        for (StockPrice price : savedPrices) {
            assertNotNull(price.getCode(), "股票代码不应为空");
            assertNotNull(price.getDate(), "交易日期不应为空");
            assertNotNull(price.getOpenPrice(), "开盘价不应为空");
            assertNotNull(price.getHighPrice(), "最高价不应为空");
            assertNotNull(price.getLowPrice(), "最低价不应为空");
            assertNotNull(price.getClosePrice(), "收盘价不应为空");
            assertNotNull(price.getVolume(), "成交量不应为空");

            // 验证价格合理性
            assertTrue(price.getOpenPrice().compareTo(BigDecimal.ZERO) > 0, "开盘价应为正数");
            assertTrue(price.getClosePrice().compareTo(BigDecimal.ZERO) > 0, "收盘价应为正数");
            assertTrue(price.getHighPrice().compareTo(BigDecimal.ZERO) > 0, "最高价应为正数");
            assertTrue(price.getLowPrice().compareTo(BigDecimal.ZERO) > 0, "最低价应为正数");

            // 验证最高价 >= 最低价
            assertTrue(
                price.getHighPrice().compareTo(price.getLowPrice()) >= 0,
                "最高价应大于等于最低价"
            );
        }

        System.out.println("✓ 数据完整性验证通过");
    }

    /**
     * 测试 10: 同步历史数据（模拟）
     */
    @Test
    @Order(10)
    @DisplayName("同步历史数据测试")
    void testSyncHistoricalData() {
        System.out.println("=== 测试 10: 同步历史数据 ===");

        // 准备数据
        List<StockPrice> prices = createTestStockPrices(TEST_STOCK_CODE, 50);
        stockDataService.saveStockPrices(prices);

        // 同步指定日期范围
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        int count = stockDataService.syncHistoricalData(TEST_STOCK_CODE, startDate, endDate);

        // 验证结果
        assertTrue(count >= 0, "同步数量应非负");

        System.out.println("✓ 同步历史数据测试通过");
        System.out.println("  同步数量：" + count);
    }

    /**
     * 创建测试用的股票价格数据
     */
    private List<StockPrice> createTestStockPrices(String code, int days) {
        List<StockPrice> prices = new ArrayList<>();
        BigDecimal basePrice = new BigDecimal("1700.00");

        for (int i = 0; i < days; i++) {
            LocalDate date = LocalDate.now().minusDays(days - i);

            // 跳过周末
            if (date.getDayOfWeek().getValue() > 5) {
                continue;
            }

            // 模拟价格波动
            double changePercent = (Math.random() - 0.5) * 0.06;
            basePrice = basePrice.multiply(BigDecimal.ONE.add(BigDecimal.valueOf(changePercent)));

            StockPrice price = new StockPrice();
            price.setCode(code);
            price.setName(TEST_STOCK_NAME);
            price.setDate(date);

            // 开盘价
            BigDecimal openPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setOpenPrice(openPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            // 最高价
            BigDecimal highPrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setHighPrice(highPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            // 最低价
            BigDecimal lowPrice = basePrice.multiply(BigDecimal.ONE.subtract(
                BigDecimal.valueOf(Math.random() * 0.03)));
            price.setLowPrice(lowPrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            // 收盘价
            BigDecimal closePrice = basePrice.multiply(BigDecimal.ONE.add(
                BigDecimal.valueOf((Math.random() - 0.5) * 0.02)));
            price.setClosePrice(closePrice.setScale(2, BigDecimal.ROUND_HALF_UP));

            // 成交量（手）
            BigDecimal volume = new BigDecimal(String.valueOf(
                10000 + Math.random() * 50000));
            price.setVolume(volume.setScale(2, BigDecimal.ROUND_HALF_UP));

            // 成交额（元）
            BigDecimal amount = closePrice.multiply(volume);
            price.setAmount(amount.setScale(2, BigDecimal.ROUND_HALF_UP));

            prices.add(price);
        }

        return prices;
    }

    /**
     * 测试后清理数据
     */
    @AfterEach
    void tearDown() {
        priceRepository.deleteByCode(TEST_STOCK_CODE);
        System.out.println("测试后清理完成");
    }
}