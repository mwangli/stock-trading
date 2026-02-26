package com.stock.databus.client;

import com.stock.databus.entity.StockInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 东方财富API客户端测试
 * 注意: 这些测试需要网络连接，默认禁用
 */
@Disabled("需要网络连接 - 手动启用进行测试")
public class EastMoneyClientTest {

    @Test
    public void testFetchStockList() {
        System.out.println("测试: 获取股票列表");
        
        okhttp3.OkHttpClient httpClient = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        EastMoneyClient eastMoneyClient = new EastMoneyClient(httpClient);
        
        List<StockInfo> stocks = eastMoneyClient.fetchStockList();
        
        System.out.println("获取到股票数量: " + stocks.size());
        
        assertNotNull(stocks, "股票列表不应为空");
        
        if (!stocks.isEmpty()) {
            StockInfo firstStock = stocks.get(0);
            System.out.println("第一只股票: " + firstStock.getCode() + " - " + firstStock.getName());
            assertNotNull(firstStock.getCode());
            assertNotNull(firstStock.getName());
            
            for (StockInfo stock : stocks) {
                assertFalse(stock.getName().contains("ST"), "不应包含ST股票");
            }
        }
        
        System.out.println("测试通过!");
    }
}
