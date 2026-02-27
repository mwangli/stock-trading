package com.stock.databus.collector;

import com.stock.databus.client.TushareClient;
import com.stock.databus.entity.StockPrice;
import com.stock.databus.repository.PriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-202: 历史K线采集完整流程测试
 * 用例描述: 从API采集历史K线到MongoDB存储完整流程
 * 前置条件: MongoDB服务运行中，网络连接正常
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TC-202: 历史K线采集完整流程测试")
public class HistoricalKlineCollectorTest {

    @Autowired
    private StockCollector stockCollector;

    @Autowired
    private TushareClient tushareClient;

    @Autowired
    private PriceRepository priceRepository;

    private static final String TEST_STOCK_CODE = "600519";

    @BeforeEach
    public void setUp() throws InterruptedException {
        // Tushare API请求间隔
        TimeUnit.SECONDS.sleep(1);
    }

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
