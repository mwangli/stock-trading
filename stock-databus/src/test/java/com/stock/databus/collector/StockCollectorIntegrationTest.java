package com.stock.databus.collector;

import com.stock.databus.client.EastMoneyClient;
import com.stock.databus.entity.StockInfo;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class StockCollectorIntegrationTest {

    @Test
    public void testFetchStockListFromAPI() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        EastMoneyClient eastMoneyClient = new EastMoneyClient(httpClient);
        
        List<StockInfo> stocks = eastMoneyClient.fetchStockList();
        
        System.out.println("API返回股票数量: " + stocks.size());
        
        assertNotNull(stocks, "股票列表不应为空");
        assertTrue(stocks.size() > 0, "应该获取到股票数据");
        
        if (!stocks.isEmpty()) {
            StockInfo first = stocks.get(0);
            System.out.println("第一只股票: " + first.getCode() + " - " + first.getName());
            
            assertNotNull(first.getCode(), "股票代码不应为空");
            assertNotNull(first.getName(), "股票名称不应为空");
            assertNotNull(first.getMarket(), "市场代码不应为空");
            assertTrue(first.getMarket().equals("sh") || first.getMarket().equals("sz"), 
                "市场代码应为sh或sz");
            
            assertFalse(first.getName().contains("ST"), "不应包含ST股票");
            System.out.println("✅ 股票列表API测试通过");
        }
    }

    @Test
    public void testMarketClassification() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        EastMoneyClient eastMoneyClient = new EastMoneyClient(httpClient);
        
        List<StockInfo> stocks = eastMoneyClient.fetchStockList();
        
        long shCount = stocks.stream().filter(s -> "sh".equals(s.getMarket())).count();
        long szCount = stocks.stream().filter(s -> "sz".equals(s.getMarket())).count();
        
        System.out.println("上海股票数量: " + shCount);
        System.out.println("深圳股票数量: " + szCount);
        
        assertTrue(shCount > 0, "应该有上海股票");
        assertTrue(szCount > 0, "应该有深圳股票");
        
        boolean shCodeCorrect = stocks.stream()
                .filter(s -> "sh".equals(s.getMarket()))
                .allMatch(s -> s.getCode().startsWith("6"));
        assertTrue(shCodeCorrect, "上海股票代码应该以6开头");
        
        boolean szCodeCorrect = stocks.stream()
                .filter(s -> "sz".equals(s.getMarket()))
                .noneMatch(s -> s.getCode().startsWith("6"));
        assertTrue(szCodeCorrect, "深圳股票代码不应该以6开头");
        
        System.out.println("✅ 市场分类测试通过");
    }

    @Test
    public void testHistoricalDataFetch() throws Exception {
        String stockCode = "600519";
        
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        
        String url = String.format(
                "https://push2his.eastmoney.com/api/qt/stock/kline/get?secid=1.%s&fields1=f1,f2,f3,f4,f5,f6&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61&klt=101&fqt=0&end=2026-02-26&lmt=60",
                stockCode);

        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build();

        try (okhttp3.Response response = httpClient.newCall(request).execute()) {
            assertTrue(response.isSuccessful(), "API请求应该成功");
            
            String body = response.body().string();
            assertNotNull(body, "响应体不应为空");
            assertTrue(body.contains("klines"), "响应应该包含K线数据");
            
            System.out.println("✅ 历史K线API测试通过");
            System.out.println("响应长度: " + body.length() + " 字符");
        }
    }

    @Test
    public void testStocksFiltering() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        EastMoneyClient eastMoneyClient = new EastMoneyClient(httpClient);
        
        List<StockInfo> stocks = eastMoneyClient.fetchStockList();
        
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
