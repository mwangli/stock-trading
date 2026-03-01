package com.stock.databus.client;

import com.stock.databus.entity.StockInfo;
import com.stock.databus.entity.StockPrices;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 证券平台数据客户端集成测试
 * 测试数据采集接口（不需要 Token 的公开数据接口）
 */
@Slf4j
@SpringBootTest
public class SecuritiesPlatformClientTest {

    @Autowired
    private SecuritiesPlatformClient securitiesPlatformClient;

    /**
     * 测试获取股票列表数据
     */
    @Test
    public void testGetDataList() {
        log.info("========== 开始测试获取股票列表 ==========");
        
        List<StockInfo> stockList = securitiesPlatformClient.getDataList();
        
        assertNotNull(stockList, "股票列表不应为空");
        assertTrue(stockList.size() > 0, "股票列表应包含数据");
        log.info("成功获取{}只股票", stockList.size());
        
        StockInfo firstStock = stockList.get(0);
        assertNotNull(firstStock.getCode(), "股票代码不应为空");
        assertNotNull(firstStock.getName(), "股票名称不应为空");
        assertNotNull(firstStock.getPrice(), "股票价格不应为空");
        
        log.info("示例股票：{} - {} - 价格：{}", 
                firstStock.getCode(), firstStock.getName(), firstStock.getPrice());
        log.info("========== 测试完成 ==========");
    }

    /**
     * 测试获取历史价格数据
     */
    @Test
    public void testGetHistoryPrices() {
        log.info("========== 开始测试获取历史价格数据 ==========");
        
        String testCode = "000001";
        List<StockPrices> historyPrices = securitiesPlatformClient.getHistoryPrices(testCode);
        
        assertNotNull(historyPrices, "历史价格列表不应为空");
        assertTrue(historyPrices.size() > 0, "历史价格应包含数据");
        log.info("成功获取{}条历史价格数据", historyPrices.size());
        
        StockPrices firstPrice = historyPrices.get(0);
        assertNotNull(firstPrice.getDate(), "日期不应为空");
        assertNotNull(firstPrice.getPrice1(), "开盘价不应为空");
        assertNotNull(firstPrice.getPrice2(), "收盘价不应为空");
        
        log.info("示例数据：日期={} 开盘价={} 收盘价={}", 
                firstPrice.getDate(), firstPrice.getPrice1(), firstPrice.getPrice2());
        
        log.info("========== 测试完成 ==========");
    }

    /**
     * 测试获取实时价格
     */
    @Test
    public void testGetNowPrice() {
        log.info("========== 开始测试获取实时价格 ==========");
        
        String testCode = "000001";
        Double currentPrice = securitiesPlatformClient.getNowPrice(testCode);
        
        assertNotNull(currentPrice, "实时价格不应为空");
        assertTrue(currentPrice > 0, "价格应该大于 0");
        log.info("股票{}当前价格：{}", testCode, currentPrice);
        
        log.info("========== 测试完成 ==========");
    }

    /**
     * 测试批量获取历史数据
     */
    @Test
    public void testGetMultipleStocksHistory() {
        log.info("========== 开始测试批量获取历史数据 ==========");
        
        String[] testCodes = {"000001", "600000", "000002"};
        
        for (String code : testCodes) {
            log.info("获取股票{}的历史数据", code);
            List<StockPrices> prices = securitiesPlatformClient.getHistoryPrices(code);
            
            assertNotNull(prices, "股票{}的价格数据不应为空", code);
            log.info("股票{}获取到{}条数据", code, prices.size());
            
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("========== 测试完成 ==========");
    }
}
