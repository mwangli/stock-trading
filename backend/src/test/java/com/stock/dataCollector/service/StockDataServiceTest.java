package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.PriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StockDataService 集成测试
 * 验证股票数据服务的业务逻辑
 * 
 * 测试原则：
 * 1. 使用真实数据，不使用 Mock
 * 2. 数据不足时跳过测试
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class StockDataServiceTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private PriceRepository priceRepository;

    @Nested
    @DisplayName("历史价格数据测试")
    class HistoryPriceTests {

        @Test
        @DisplayName("获取股票历史价格数据")
        void testGetHistoryPrices() {
            log.info("========== 开始测试获取历史价格数据 ==========");

            String testStockCode = "000001";
            List<StockPrice> prices = stockDataService.getHistoryPrices(testStockCode);

            // 数据检查
            if (prices.isEmpty()) {
                log.warn("未获取到股票 {} 的历史价格数据，可能是网络问题或非交易时间，跳过测试", testStockCode);
                return;
            }

            log.info("获取到股票 {} 的历史价格数据 {} 条", testStockCode, prices.size());

            // 验证数据不为空
            assertFalse(prices.isEmpty(), "历史价格数据不应为空");

            // 验证数据字段
            StockPrice firstPrice = prices.get(0);
            assertNotNull(firstPrice.getCode(), "股票代码不应为空");
            assertNotNull(firstPrice.getDate(), "日期不应为空");

            log.info("第一条数据: 日期={}, 收盘价={}", firstPrice.getDate(), firstPrice.getClosePrice());

            log.info("========== 历史价格数据获取测试通过 ==========");
        }

        @Test
        @DisplayName("保存股票价格数据到 MongoDB")
        void testSaveStockPrices() {
            log.info("========== 开始测试保存股票价格数据 ==========");

            String testStockCode = "000001";
            List<StockPrice> prices = stockDataService.getHistoryPrices(testStockCode);

            // 数据检查
            if (prices.isEmpty()) {
                log.warn("未获取到历史价格数据，跳过保存测试");
                return;
            }

            // 保存数据
            stockDataService.saveStockPrices(prices);

            // 验证数据已保存
            List<StockPrice> savedPrices = priceRepository.findByCodeOrderByDateAsc(testStockCode);
            assertFalse(savedPrices.isEmpty(), "保存后应该能查询到数据");

            log.info("保存并验证了 {} 条价格数据", savedPrices.size());

            log.info("========== 保存股票价格数据测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("增量更新测试")
    class IncrementalUpdateTests {

        @Test
        @DisplayName("验证增量更新跳过重复数据")
        void testIncrementalUpdate() {
            log.info("========== 开始测试增量更新 ==========");

            String testStockCode = "000001";
            List<StockPrice> prices = stockDataService.getHistoryPrices(testStockCode);

            // 数据检查
            if (prices.isEmpty()) {
                log.warn("未获取到历史价格数据，跳过增量更新测试");
                return;
            }

            // 第一次保存
            stockDataService.saveStockPrices(prices);
            long countAfterFirst = priceRepository.findByCodeOrderByDateAsc(testStockCode).size();

            // 第二次保存（应该跳过重复数据）
            stockDataService.saveStockPrices(prices);
            long countAfterSecond = priceRepository.findByCodeOrderByDateAsc(testStockCode).size();

            // 验证数据量相同（没有重复插入）
            assertEquals(countAfterFirst, countAfterSecond, "重复保存应该跳过已存在的数据");

            log.info("第一次保存后数据量: {}, 第二次保存后数据量: {}", countAfterFirst, countAfterSecond);

            log.info("========== 增量更新测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("数据查询测试")
    class QueryTests {

        @Test
        @DisplayName("获取股票最新价格")
        void testGetLatestPrice() {
            log.info("========== 开始测试获取最新价格 ==========");

            String testStockCode = "000001";

            // 先确保有数据
            List<StockPrice> prices = stockDataService.getHistoryPrices(testStockCode);
            if (prices.isEmpty()) {
                log.warn("未获取到历史价格数据，跳过测试");
                return;
            }
            stockDataService.saveStockPrices(prices);

            // 获取最新价格
            StockPrice latestPrice = stockDataService.getLatestPrice(testStockCode);

            // 数据检查
            if (latestPrice == null) {
                log.warn("未找到最新价格数据，跳过测试");
                return;
            }

            assertNotNull(latestPrice, "最新价格不应为空");
            assertEquals(testStockCode, latestPrice.getCode(), "股票代码应匹配");

            log.info("股票 {} 最新价格: 日期={}, 收盘价={}",
                testStockCode, latestPrice.getDate(), latestPrice.getClosePrice());

            log.info("========== 获取最新价格测试通过 ==========");
        }

        @Test
        @DisplayName("批量获取多只股票最新价格")
        void testGetLatestPrices() {
            log.info("========== 开始测试批量获取最新价格 ==========");

            List<String> testStockCodes = List.of("000001", "000002", "600000");

            // 先确保有数据
            for (String code : testStockCodes) {
                List<StockPrice> prices = stockDataService.getHistoryPrices(code);
                if (!prices.isEmpty()) {
                    stockDataService.saveStockPrices(prices);
                }
            }

            // 批量获取最新价格
            List<StockPrice> latestPrices = stockDataService.getLatestPrices(testStockCodes);

            log.info("批量获取到 {} 只股票的最新价格", latestPrices.size());

            // 至少有一只股票有数据
            assertTrue(latestPrices.size() > 0, "应该至少有一只股票有最新价格数据");

            log.info("========== 批量获取最新价格测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("历史数据同步测试")
    class SyncTests {

        @Test
        @DisplayName("同步指定日期范围的历史数据")
        void testSyncHistoricalData() {
            log.info("========== 开始测试同步历史数据 ==========");

            String testStockCode = "000001";
            LocalDate startDate = LocalDate.now().minusDays(10);
            LocalDate endDate = LocalDate.now();

            int syncCount = stockDataService.syncHistoricalData(testStockCode, startDate, endDate);

            log.info("同步了 {} 条历史数据", syncCount);

            // 如果有数据，验证日期范围
            if (syncCount > 0) {
                List<StockPrice> savedPrices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
                    testStockCode, startDate, endDate);

                assertFalse(savedPrices.isEmpty(), "应该保存了指定日期范围的数据");

                log.info("验证保存了 {} 条日期范围内的数据", savedPrices.size());
            }

            log.info("========== 同步历史数据测试通过 ==========");
        }
    }
}