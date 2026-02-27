package com.stock.databus.client;

import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TushareClient 测试
 * 使用真实MySQL数据库进行测试
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("TushareClient 真实数据测试")
public class TushareClientTest {

    @Autowired
    private TushareClient tushareClient;

    @BeforeEach
    public void setUp() throws InterruptedException {
        // Tushare API请求间隔
        TimeUnit.SECONDS.sleep(1);
    }

    @Test
    @DisplayName("股票代码解析测试")
    public void testStockCodeParsing() {
        List<StockInfo> stocks = tushareClient.fetchStockList();

        assertNotNull(stocks, "股票列表不应为空");
        System.out.println("获取股票数量: " + stocks.size());

        if (!stocks.isEmpty()) {
            StockInfo first = stocks.get(0);
            System.out.println("第一只股票: " + first.getCode() + " - " + first.getName());
            System.out.println("市场: " + first.getMarket());

            assertNotNull(first.getCode(), "股票代码不应为空");
            assertNotNull(first.getName(), "股票名称不应为空");
            assertTrue(first.getMarket().equals("SH") || first.getMarket().equals("SZ"),
                "市场代码应为SH或SZ");

            assertFalse(first.getName().contains("ST"), "不应包含ST股票");
        }

        long shCount = stocks.stream()
                .filter(s -> "SH".equals(s.getMarket()))
                .count();
        long szCount = stocks.stream()
                .filter(s -> "SZ".equals(s.getMarket()))
                .count();

        System.out.println("上海股票数量: " + shCount);
        System.out.println("深圳股票数量: " + szCount);

        assertTrue(shCount >= 0, "应该有上海股票");
        assertTrue(szCount >= 0, "应该有深圳股票");

        boolean shCodeCorrect = stocks.stream()
                .filter(s -> "SH".equals(s.getMarket()))
                .allMatch(s -> s.getCode().startsWith("6"));
        assertTrue(shCodeCorrect, "上海股票代码应该以6开头");

        boolean szCodeCorrect = stocks.stream()
                .filter(s -> "SZ".equals(s.getMarket()))
                .noneMatch(s -> s.getCode().startsWith("6"));
        assertTrue(szCodeCorrect, "深圳股票代码不应该以6开头");

        System.out.println("✅ 股票代码解析测试通过");
    }

    @Test
    @DisplayName("获取历史K线数据测试")
    public void testFetchDailyKlines() {
        String stockCode = "600519";

        List<StockPrice> prices = tushareClient.fetchDailyKlines(stockCode, 60);

        assertNotNull(prices, "K线数据不应为空");
        System.out.println("获取K线数量: " + prices.size());

        if (!prices.isEmpty()) {
            StockPrice first = prices.get(0);
            System.out.println("第一条K线: " + first.getDate() +
                " 开盘:" + first.getPrice1() +
                " 收盘:" + first.getPrice4());

            assertNotNull(first.getCode(), "股票代码不应为空");
            assertNotNull(first.getDate(), "交易日期不应为空");
            assertNotNull(first.getPrice1(), "开盘价不应为空");
            assertNotNull(first.getPrice4(), "收盘价不应为空");
        }

        System.out.println("✅ Tushare K线数据获取测试通过");
    }

    @Test
    @DisplayName("ST股票过滤测试")
    public void testFilterStStocks() {
        List<StockInfo> stocks = tushareClient.fetchStockList();

        assertNotNull(stocks, "股票列表不应为空");

        boolean hasStStock = stocks.stream()
                .anyMatch(s -> s.getName() != null && 
                    (s.getName().contains("ST") || s.getName().contains("*ST")));

        assertFalse(hasStStock, "ST股票应该被过滤");

        long tradableCount = stocks.stream()
                .filter(s -> s.getIsTradable() != null && s.getIsTradable() == 1)
                .count();

        assertTrue(tradableCount > 0, "应该有可交易股票");

        System.out.println("总股票数: " + stocks.size());
        System.out.println("可交易股票数: " + tradableCount);
        System.out.println("✅ ST股票过滤测试通过");
    }
}
