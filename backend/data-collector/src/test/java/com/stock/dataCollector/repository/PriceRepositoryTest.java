package com.stock.dataCollector.repository;

import com.stock.dataCollector.entity.StockPrice;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PriceRepository 集成测试
 * 验证 MongoDB 股票价格数据的存储和查询功能
 * 
 * 测试原则：
 * 1. 使用真实数据，不使用 Mock
 * 2. 数据不足时跳过测试
 * 3. 测试完成后清理测试数据
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class PriceRepositoryTest {

    @Autowired
    private PriceRepository priceRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    /**
     * 测试用股票代码
     */
    private static final String TEST_STOCK_CODE = "999999";

    /**
     * 清理测试数据
     */
    private void cleanupTestData() {
        try {
            priceRepository.deleteByCode(TEST_STOCK_CODE);
            log.debug("清理测试数据完成");
        } catch (Exception e) {
            log.warn("清理测试数据失败: {}", e.getMessage());
        }
    }

    /**
     * 创建测试用的 StockPrice 对象
     */
    private StockPrice createTestPrice(LocalDate date) {
        StockPrice price = new StockPrice();
        price.setCode(TEST_STOCK_CODE);
        price.setName("测试股票");
        price.setDate(date);
        price.setOpenPrice(new BigDecimal("10.00"));
        price.setHighPrice(new BigDecimal("10.50"));
        price.setLowPrice(new BigDecimal("9.80"));
        price.setClosePrice(new BigDecimal("10.20"));
        price.setVolume(new BigDecimal("1000000"));
        price.setAmount(new BigDecimal("10200000"));
        return price;
    }

    @Nested
    @DisplayName("基础 CRUD 操作测试")
    class CrudTests {

        @Test
        @DisplayName("保存并查询股票价格数据")
        void testSaveAndFind() {
            log.info("========== 开始测试保存和查询 ==========");

            // 清理旧数据
            cleanupTestData();

            // 创建测试数据
            LocalDate testDate = LocalDate.now().minusDays(1);
            StockPrice price = createTestPrice(testDate);

            // 保存
            StockPrice saved = priceRepository.save(price);
            assertNotNull(saved.getId(), "保存后应有 ID");

            // 查询验证
            Optional<StockPrice> found = priceRepository.findByCodeAndDate(TEST_STOCK_CODE, testDate);
            assertTrue(found.isPresent(), "应该能查询到保存的数据");

            StockPrice foundPrice = found.get();
            assertEquals(TEST_STOCK_CODE, foundPrice.getCode(), "股票代码应匹配");
            assertEquals(testDate, foundPrice.getDate(), "日期应匹配");
            assertEquals(0, new BigDecimal("10.20").compareTo(foundPrice.getClosePrice()), "收盘价应匹配");

            // 清理测试数据
            cleanupTestData();

            log.info("========== 保存和查询测试通过 ==========");
        }

        @Test
        @DisplayName("删除股票价格数据")
        void testDelete() {
            log.info("========== 开始测试删除 ==========");

            // 创建并保存测试数据
            LocalDate testDate = LocalDate.now().minusDays(1);
            StockPrice price = createTestPrice(testDate);
            priceRepository.save(price);

            // 验证已保存
            assertTrue(priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, testDate), "数据应该存在");

            // 删除
            priceRepository.deleteByCode(TEST_STOCK_CODE);

            // 验证已删除
            assertFalse(priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, testDate), "数据应该已删除");

            log.info("========== 删除测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("查询方法测试")
    class QueryMethodTests {

        @Test
        @DisplayName("按股票代码查询并按日期升序排序")
        void testFindByCodeOrderByDateAsc() {
            log.info("========== 开始测试按代码查询并排序 ==========");

            // 清理旧数据
            cleanupTestData();

            // 创建多天测试数据
            LocalDate baseDate = LocalDate.now().minusDays(5);
            for (int i = 0; i < 5; i++) {
                StockPrice price = createTestPrice(baseDate.plusDays(i));
                price.setClosePrice(new BigDecimal("10.00").add(new BigDecimal(i * 0.1)));
                priceRepository.save(price);
            }

            // 查询
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

            assertEquals(5, prices.size(), "应该查询到 5 条数据");

            // 验证按日期升序
            for (int i = 0; i < prices.size() - 1; i++) {
                assertTrue(prices.get(i).getDate().isBefore(prices.get(i + 1).getDate()),
                    "数据应按日期升序排列");
            }

            // 清理测试数据
            cleanupTestData();

            log.info("========== 按代码查询并排序测试通过 ==========");
        }

        @Test
        @DisplayName("按股票代码和日期范围查询")
        void testFindByCodeAndDateBetween() {
            log.info("========== 开始测试按日期范围查询 ==========");

            // 清理旧数据
            cleanupTestData();

            // 创建测试数据（最近 10 天）
            LocalDate today = LocalDate.now();
            for (int i = 0; i < 10; i++) {
                StockPrice price = createTestPrice(today.minusDays(i));
                priceRepository.save(price);
            }

            // 查询最近 5 天
            LocalDate startDate = today.minusDays(5);
            LocalDate endDate = today.minusDays(1);

            List<StockPrice> prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
                TEST_STOCK_CODE, startDate, endDate);

            // 验证日期范围内的数据
            assertTrue(prices.size() <= 5, "应该查询到最多 5 条数据");

            for (StockPrice price : prices) {
                assertFalse(price.getDate().isBefore(startDate), "日期不应早于开始日期");
                assertFalse(price.getDate().isAfter(endDate), "日期不应晚于结束日期");
            }

            // 清理测试数据
            cleanupTestData();

            log.info("========== 按日期范围查询测试通过 ==========");
        }

        @Test
        @DisplayName("检查股票价格是否存在")
        void testExistsByCodeAndDate() {
            log.info("========== 开始测试检查数据是否存在 ==========");

            // 清理旧数据
            cleanupTestData();

            LocalDate testDate = LocalDate.now().minusDays(1);

            // 检查不存在的情况
            assertFalse(priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, testDate),
                "数据不应该存在");

            // 创建测试数据
            StockPrice price = createTestPrice(testDate);
            priceRepository.save(price);

            // 检查存在的情况
            assertTrue(priceRepository.existsByCodeAndDate(TEST_STOCK_CODE, testDate),
                "数据应该存在");

            // 清理测试数据
            cleanupTestData();

            log.info("========== 检查数据存在测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("真实数据查询测试")
    class RealDataTests {

        @Test
        @DisplayName("查询真实股票的价格数据")
        void testQueryRealStockData() {
            log.info("========== 开始查询真实股票数据 ==========");

            // 查询平安银行的价格数据
            String realStockCode = "000001";
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(realStockCode);

            if (prices.isEmpty()) {
                log.warn("MongoDB 中无股票 {} 的价格数据，跳过测试", realStockCode);
                return;
            }

            log.info("查询到股票 {} 的价格数据 {} 条", realStockCode, prices.size());

            // 验证数据结构
            StockPrice latestPrice = prices.get(prices.size() - 1);
            assertNotNull(latestPrice.getDate(), "日期不应为空");
            assertNotNull(latestPrice.getClosePrice(), "收盘价不应为空");

            log.info("最新价格: 日期={}, 收盘价={}, 成交量={}",
                latestPrice.getDate(), latestPrice.getClosePrice(), latestPrice.getVolume());

            log.info("========== 真实股票数据查询测试通过 ==========");
        }

        @Test
        @DisplayName("统计价格数据总量")
        void testCountAllPrices() {
            log.info("========== 开始统计价格数据总量 ==========");

            long totalCount = priceRepository.count();
            log.info("MongoDB 中价格数据总量: {}", totalCount);

            if (totalCount == 0) {
                log.warn("MongoDB 中无价格数据，跳过统计测试");
                return;
            }

            assertTrue(totalCount > 0, "价格数据总量应大于 0");

            log.info("========== 价格数据总量统计完成 ==========");
        }
    }
}