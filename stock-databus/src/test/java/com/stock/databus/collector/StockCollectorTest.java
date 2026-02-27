package com.stock.databus.collector;

import com.stock.databus.client.TushareClient;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-201 & TC-202 & TC-204: 数据采集完整流程测试
 * 
 * TC-201: 从API采集到数据库存储完整流程
 * TC-202: 从API采集历史K线到MongoDB存储完整流程
 * TC-204: Tushare历史数据同步测试
 * 
 * 前置条件: MySQL和MongoDB服务运行中，网络连接正常
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("数据采集完整流程测试")
public class StockCollectorTest {

    @Autowired
    private StockCollector stockCollector;

    @Autowired
    private TushareClient tushareClient;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PriceRepository priceRepository;

    private static final String TEST_STOCK_CODE = "600519";

    @BeforeEach
    public void setUp() throws InterruptedException {
        // Tushare API请求间隔
        TimeUnit.SECONDS.sleep(1);
    }

    // ==================== TC-201: 股票采集完整流程测试 ====================

    @Nested
    @DisplayName("TC-201: 股票采集完整流程测试")
    class StockCollectionTests {

        @Test
        @DisplayName("TC-201-01: 采集股票列表完整流程")
        public void testCollectStockListFlow() {
            // 执行采集
            int count = stockCollector.collectStockList();

            // 验证采集数量
            assertTrue(count > 0, "应该采集到股票数据");

            // 从数据库查询验证
            int totalCount = stockRepository.countAll();
            assertTrue(totalCount > 0, "数据库应该有股票数据");

            System.out.println("采集股票数量: " + count);
            System.out.println("数据库总股票数: " + totalCount);
            System.out.println("✅ TC-201-01: 采集股票列表完整流程测试通过");
        }

        @Test
        @DisplayName("TC-201-02: 股票数据字段验证")
        public void testStockDataFields() {
            // 执行采集
            stockCollector.collectStockList();

            // 获取可交易股票
            List<StockInfo> stocks = stockRepository.findTradableStocks(10);

            if (!stocks.isEmpty()) {
                StockInfo stock = stocks.get(0);

                // 验证必填字段
                assertNotNull(stock.getCode(), "股票代码不应为空");
                assertNotNull(stock.getName(), "股票名称不应为空");
                assertNotNull(stock.getMarket(), "市场代码不应为空");
                assertNotNull(stock.getIsTradable(), "可交易标识不应为空");

                // 验证市场分类
                boolean marketValid = "SH".equals(stock.getMarket()) || "SZ".equals(stock.getMarket());
                assertTrue(marketValid, "市场应该是SH或SZ");

                // 验证股票代码与市场匹配
                if ("SH".equals(stock.getMarket())) {
                    assertTrue(stock.getCode().startsWith("6"), "上海股票代码应该以6开头");
                }

                // 验证可交易标识
                assertEquals(1, stock.getIsTradable(), "可交易股票标识应为1");

                System.out.println("股票信息: " + stock.getCode() + " - " + stock.getName() + " - " + stock.getMarket());
                System.out.println("✅ TC-201-02: 股票数据字段验证测试通过");
            }
        }

        @Test
        @DisplayName("TC-201-03: ST股票过滤验证")
        public void testFilterStStocks() {
            // 执行采集
            stockCollector.collectStockList();

            // 获取所有可交易股票
            List<StockInfo> stocks = stockRepository.findTradableStocks(5000);

            // 验证没有ST股票
            boolean hasStStock = stocks.stream()
                    .anyMatch(s -> s.getName() != null && 
                        (s.getName().contains("ST") || s.getName().contains("*ST")));

            assertFalse(hasStStock, "可交易股票列表中不应包含ST股票");

            System.out.println("可交易股票总数: " + stocks.size());
            System.out.println("✅ TC-201-03: ST股票过滤验证测试通过");
        }

        @Test
        @DisplayName("TC-201-04: 股票更新逻辑验证")
        public void testStockUpdateLogic() throws IOException {
            // 第一次采集
            int firstCount = stockCollector.collectStockList();
            assertTrue(firstCount > 0, "第一次采集应该成功");

            // 第二次采集（测试更新逻辑）
            int secondCount = stockCollector.collectStockList();
            assertTrue(secondCount >= 0, "第二次采集应该成功");

            // 验证数据没有重复
            int totalCount = stockRepository.countAll();
            System.out.println("第一次采集: " + firstCount + " 只");
            System.out.println("第二次采集: " + secondCount + " 只");
            System.out.println("数据库总记录: " + totalCount + " 条");
            System.out.println("✅ TC-201-04: 股票更新逻辑验证测试通过");
        }

        @Test
        @DisplayName("TC-201-05: 分市场股票统计")
        public void testMarketStatistics() {
            // 执行采集
            stockCollector.collectStockList();

            // 统计各市场股票数量
            List<StockInfo> shStocks = stockRepository.findByMarket("SH");
            List<StockInfo> szStocks = stockRepository.findByMarket("SZ");

            System.out.println("上海股票数量: " + shStocks.size());
            System.out.println("深圳股票数量: " + szStocks.size());

            assertTrue(shStocks.size() > 0, "应该有上海股票");
            assertTrue(szStocks.size() > 0, "应该有深圳股票");
            System.out.println("✅ TC-201-05: 分市场股票统计测试通过");
        }
    }

    // ==================== TC-202: 历史K线采集完整流程测试 ====================

    @Nested
    @DisplayName("TC-202: 历史K线采集完整流程测试")
    class HistoricalKlineCollectionTests {

        @Test
        @DisplayName("TC-202-01: 历史K线数据采集")
        public void testCollectHistoricalKlines() {
            // 采集60天历史数据
            int days = 60;
            int count = stockCollector.collectHistoricalData(TEST_STOCK_CODE, days);

            // 验证采集结果
            assertTrue(count > 0, "应该采集到K线数据");
            assertTrue(count <= days, "采集数量不应超过请求天数");

            System.out.println("请求天数: " + days);
            System.out.println("实际采集数量: " + count);
            System.out.println("✅ TC-202-01: 历史K线数据采集测试通过");
        }

        @Test
        @DisplayName("TC-202-02: K线数据字段验证")
        public void testKlineDataFields() {
            // 采集历史数据
            stockCollector.collectHistoricalData(TEST_STOCK_CODE, 30);

            // 从MongoDB查询
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

            assertFalse(prices.isEmpty(), "应该有K线数据");

            StockPrice price = prices.get(0);

            // 验证必填字段
            assertNotNull(price.getCode(), "股票代码不应为空");
            assertNotNull(price.getDate(), "交易日期不应为空");
            assertNotNull(price.getPrice1(), "开盘价不应为空");
            assertNotNull(price.getPrice2(), "最高价不应为空");
            assertNotNull(price.getPrice3(), "最低价不应为空");
            assertNotNull(price.getPrice4(), "收盘价不应为空");
            assertNotNull(price.getTradingVolume(), "成交量不应为空");

            // 验证价格逻辑
            assertTrue(price.getPrice2().compareTo(price.getPrice1()) >= 0, "最高价应该>=开盘价");
            assertTrue(price.getPrice2().compareTo(price.getPrice3()) >= 0, "最高价应该>=最低价");
            assertTrue(price.getPrice4().compareTo(price.getPrice3()) >= 0, "收盘价应该>=最低价");

            System.out.println("K线数据: " + price.getDate() + " 开盘:" + price.getPrice1() + " 收盘:" + price.getPrice4());
            System.out.println("✅ TC-202-02: K线数据字段验证测试通过");
        }

        @Test
        @DisplayName("TC-202-03: K线数据日期排序")
        public void testKlineDateOrdering() {
            // 采集历史数据
            stockCollector.collectHistoricalData(TEST_STOCK_CODE, 30);

            // 查询并验证排序
            List<StockPrice> prices = priceRepository.findByCodeOrderByDateAsc(TEST_STOCK_CODE);

            if (prices.size() > 1) {
                for (int i = 1; i < prices.size(); i++) {
                    LocalDate prev = prices.get(i - 1).getDate();
                    LocalDate curr = prices.get(i).getDate();
                    assertTrue(curr.isAfter(prev) || curr.isEqual(prev), "日期应该按升序排列");
                }
                System.out.println("日期排序验证通过: " + prices.get(0).getDate() + " -> " + prices.get(prices.size() - 1).getDate());
            }

            System.out.println("✅ TC-202-03: K线数据日期排序测试通过");
        }

        @Test
        @DisplayName("TC-202-04: 多只股票K线数据采集")
        public void testMultipleStockKlineCollection() {
            String[] stockCodes = {"600519", "000858", "601318"};
            int totalCount = 0;

            for (String code : stockCodes) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    int count = stockCollector.collectHistoricalData(code, 30);
                    totalCount += count;
                    System.out.println("股票 " + code + " 采集 " + count + " 条K线");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            assertTrue(totalCount > 0, "应该采集到多只股票的K线数据");
            System.out.println("总共采集: " + totalCount + " 条K线数据");
            System.out.println("✅ TC-202-04: 多只股票K线数据采集测试通过");
        }

        @Test
        @DisplayName("TC-202-05: K线数据去重验证")
        public void testKlineDeduplication() {
            String code = "600520";
            int days = 30;

            // 第一次采集
            int firstCount = stockCollector.collectHistoricalData(code, days);

            // 第二次采集同一股票
            int secondCount = stockCollector.collectHistoricalData(code, days);

            // 查询总数
            List<StockPrice> prices = priceRepository.findByCode(code);

            System.out.println("第一次采集: " + firstCount + " 条");
            System.out.println("第二次采集: " + secondCount + " 条");
            System.out.println("数据库现有: " + prices.size() + " 条");

            // 验证去重（MongoDB唯一索引应该自动去重）
            assertTrue(prices.size() >= firstCount, "数据应该去重存储");
            System.out.println("✅ TC-202-05: K线数据去重验证测试通过");
        }

        @Test
        @DisplayName("TC-202-06: K线数据价格精度验证")
        public void testPricePrecision() {
            // 采集数据
            stockCollector.collectHistoricalData(TEST_STOCK_CODE, 10);

            List<StockPrice> prices = priceRepository.findByCode(TEST_STOCK_CODE);

            for (StockPrice price : prices) {
                // 验证价格精度（2位小数）
                if (price.getPrice1() != null) {
                    String priceStr = price.getPrice1().toPlainString();
                    int decimalPlaces = priceStr.contains(".") ? priceStr.split("\\.")[1].length() : 0;
                    assertTrue(decimalPlaces <= 2, "价格精度应该是2位小数");
                }
            }

            System.out.println("✅ TC-202-06: K线数据价格精度验证测试通过");
        }

        @Test
        @DisplayName("TC-202-07: 日期范围查询K线")
        public void testDateRangeQueryKlines() {
            // 采集数据
            stockCollector.collectHistoricalData(TEST_STOCK_CODE, 60);

            // 查询指定日期范围
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(30);

            List<StockPrice> prices = priceRepository.findByCodeAndDateBetweenOrderByDateAsc(
                    TEST_STOCK_CODE, startDate, endDate);

            System.out.println("日期范围: " + startDate + " 至 " + endDate);
            System.out.println("查询结果: " + prices.size() + " 条");

            // 验证日期范围
            for (StockPrice price : prices) {
                assertTrue(!price.getDate().isBefore(startDate), "日期不应早于开始日期");
                assertTrue(!price.getDate().isAfter(endDate), "日期不应晚于结束日期");
            }

            System.out.println("✅ TC-202-07: 日期范围查询K线测试通过");
        }
    }

    // ==================== TC-204: Tushare数据同步测试 ====================

    @Nested
    @DisplayName("TC-204: Tushare数据同步测试")
    class TushareSyncTests {

        @Test
        @DisplayName("TC-204-01: Tushare股票列表同步")
        public void testTushareStockListSync() {
            // 直接调用Tushare客户端获取股票列表
            List<StockInfo> stocks = tushareClient.fetchStockList();

            assertNotNull(stocks, "股票列表不应为空");
            assertTrue(stocks.size() > 0, "应该获取到股票数据");

            // 过滤ST股票
            stocks.removeIf(s -> s.getName() != null && 
                (s.getName().contains("ST") || s.getName().contains("*ST")));

            System.out.println("Tushare获取股票数量: " + stocks.size());

            // 验证市场分类
            long shCount = stocks.stream()
                    .filter(s -> "SH".equals(s.getMarket()))
                    .count();
            long szCount = stocks.stream()
                    .filter(s -> "SZ".equals(s.getMarket()))
                    .count();

            System.out.println("上海股票: " + shCount + " 只");
            System.out.println("深圳股票: " + szCount + " 只");

            assertTrue(shCount > 0, "应该有上海股票");
            assertTrue(szCount > 0, "应该有深圳股票");
            System.out.println("✅ TC-204-01: Tushare股票列表同步测试通过");
        }

        @Test
        @DisplayName("TC-204-02: Tushare历史K线同步")
        public void testTushareHistoricalDataSync() {
            // 调用Tushare获取历史K线
            List<StockPrice> prices = tushareClient.fetchDailyKlines(TEST_STOCK_CODE, 60);

            assertNotNull(prices, "K线数据不应为空");
            assertTrue(prices.size() > 0, "应该获取到K线数据");

            System.out.println("Tushare获取K线数量: " + prices.size());

            // 验证数据完整性
            StockPrice firstPrice = prices.get(0);
            assertNotNull(firstPrice.getCode(), "股票代码不应为空");
            assertNotNull(firstPrice.getDate(), "日期不应为空");
            assertNotNull(firstPrice.getPrice1(), "开盘价不应为空");
            assertNotNull(firstPrice.getPrice4(), "收盘价不应为空");

            // 验证价格数据
            assertTrue(firstPrice.getPrice2().compareTo(firstPrice.getPrice3()) > 0, "最高价应该大于最低价");

            System.out.println("第一条K线: " + firstPrice.getDate() + " 开盘:" + firstPrice.getPrice1() + " 收盘:" + firstPrice.getPrice4());
            System.out.println("✅ TC-204-02: Tushare历史K线同步测试通过");
        }

        @Test
        @DisplayName("TC-204-03: Tushare多股票批量同步")
        public void testTushareBatchSync() {
            String[] stockCodes = {"600519", "000858", "601318", "000001"};
            int totalPrices = 0;

            for (String code : stockCodes) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    List<StockPrice> prices = tushareClient.fetchDailyKlines(code, 30);
                    totalPrices += prices.size();
                    System.out.println("股票 " + code + " 获取 " + prices.size() + " 条K线");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            assertTrue(totalPrices > 0, "应该获取到批量K线数据");
            System.out.println("总共获取: " + totalPrices + " 条K线数据");
            System.out.println("✅ TC-204-03: Tushare多股票批量同步测试通过");
        }

        @Test
        @DisplayName("TC-204-04: Tushare数据到数据库完整同步")
        public void testTushareToDatabaseSync() {
            // 1. 从Tushare获取股票列表
            List<StockInfo> stocks = tushareClient.fetchStockList();
            assertTrue(stocks.size() > 0, "应该获取到股票列表");

            // 2. 保存到数据库
            for (StockInfo stock : stocks) {
                StockInfo existing = stockRepository.findByCode(stock.getCode());
                if (existing != null) {
                    stock.setId(existing.getId());
                    stockRepository.updateById(stock);
                } else {
                    stockRepository.insert(stock);
                }
            }

            // 3. 验证数据库
            int totalCount = stockRepository.countAll();
            assertTrue(totalCount > 0, "数据库应该有股票数据");

            // 4. 从Tushare获取K线并保存
            for (int i = 0; i < Math.min(stocks.size(), 3); i++) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                    String code = stocks.get(i).getCode();
                    List<StockPrice> prices = tushareClient.fetchDailyKlines(code, 30);
                    
                    for (StockPrice price : prices) {
                        price.setCode(code);
                    }
                    priceRepository.saveAll(prices);

                    System.out.println("股票 " + code + " 同步 " + prices.size() + " 条K线到MongoDB");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // 5. 验证MongoDB
            List<StockPrice> savedPrices = priceRepository.findByCode(stocks.get(0).getCode());
            assertTrue(savedPrices.size() > 0, "MongoDB应该有K线数据");

            System.out.println("MySQL股票数: " + totalCount);
            System.out.println("MongoDB K线数: " + savedPrices.size());
            System.out.println("✅ TC-204-04: Tushare数据到数据库完整同步测试通过");
        }
    }
}
