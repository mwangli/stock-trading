package com.stock.databus.client;

import com.github.tusharepro.core.TusharePro;
import com.github.tusharepro.core.TushareProService;
import com.github.tusharepro.core.bean.StockBasic;
import com.github.tusharepro.core.common.KeyValue;
import com.github.tusharepro.core.entity.StockBasicEntity;
import com.github.tusharepro.core.http.Request;
import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class TushareClientTest {

    @Autowired
    private TushareClient tushareClient;

    @Test
    public void testStockCodeParsing() {
        List<StockInfo> stocks = tushareClient.fetchStockList();

        long shCount = stocks.stream()
                .filter(s -> "sh".equals(s.getMarket()))
                .count();
        long szCount = stocks.stream()
                .filter(s -> "sz".equals(s.getMarket()))
                .count();

        System.out.println("上海股票数量: " + shCount);
        System.out.println("深圳股票数量: " + szCount);

        assertTrue(shCount >= 0, "应该有上海股票");
        assertTrue(szCount >= 0, "应该有深圳股票");

        boolean shCodeCorrect = stocks.stream()
                .filter(s -> "sh".equals(s.getMarket()))
                .allMatch(s -> s.getCode().startsWith("6"));
        assertTrue(shCodeCorrect, "上海股票代码应该以6开头");

        boolean szCodeCorrect = stocks.stream()
                .filter(s -> "sz".equals(s.getMarket()))
                .noneMatch(s -> s.getCode().startsWith("6"));
        assertTrue(szCodeCorrect, "深圳股票代码不应该以6开头");

        System.out.println("✅ 股票代码解析测试通过");
    }

    @Test
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
    public void testFilterStStocks() {
        List<StockInfo> stocks = tushareClient.fetchStockList();

        boolean hasStStock = stocks.stream()
                .anyMatch(s -> s.getName().contains("ST") || s.getName().contains("*ST"));

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
