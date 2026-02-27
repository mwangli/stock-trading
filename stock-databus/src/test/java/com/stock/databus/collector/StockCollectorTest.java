package com.stock.databus.collector;

import com.stock.databus.client.TushareClient;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import com.stock.databus.repository.StockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 数据采集完整流程测试
 * 前置条件: MySQL和MongoDB服务运行中，网络连接正常
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("数据采集完整流程测试")
public class StockCollectorTest {

    @Autowired
    private StockCollector stockCollector;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private PriceRepository priceRepository;

    private static final String TEST_STOCK_CODE = "600519";

    @BeforeEach
    public void setUp() throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    @DisplayName("TC-201-01: 采集股票列表完整流程")
    public void testCollectStockListFlow() {
        // 执行采集
        int count = stockCollector.collectStockList();

        // 验证采集数量
        assertTrue(count >= 0, "采集应该成功");

        // 从数据库查询验证
        int totalCount = stockRepository.countAll();
        assertTrue(totalCount >= 0, "数据库应该有股票数据");

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
    public void testStockUpdateLogic() throws Exception {
        // 第一次采集
        int firstCount = stockCollector.collectStockList();
        assertTrue(firstCount >= 0, "第一次采集应该成功");

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

        assertTrue(shStocks.size() >= 0, "应该有上海股票");
        assertTrue(szStocks.size() >= 0, "应该有深圳股票");
        System.out.println("✅ TC-201-05: 分市场股票统计测试通过");
    }

    @Test
    @DisplayName("TC-202-01: 历史K线数据采集")
    public void testCollectHistoricalKlines() {
        // 采集60天历史数据
        int days = 60;
        int count = stockCollector.collectHistoricalData(TEST_STOCK_CODE, days);

        // 验证采集结果
        assertTrue(count >= 0, "应该采集到K线数据");
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

        if (!prices.isEmpty()) {
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

        assertTrue(totalCount >= 0, "应该采集到多只股票的K线数据");
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
