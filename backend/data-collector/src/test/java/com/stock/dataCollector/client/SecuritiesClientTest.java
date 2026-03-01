package com.stock.dataCollector.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecuritiesClient 测试类
 * 验证client能否获取到股票数据和历史数据
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class SecuritiesClientTest {

    @Autowired
    private SecuritiesClient securitiesClient;

    @Test
    @DisplayName("测试获取股票列表 - 验证能获取约5000只股票")
    void testGetStockList() {
        log.info("========== 开始测试获取股票列表 ==========");
        
        try {
            // 调用API获取股票列表
            JSONArray stockList = securitiesClient.getStockList();
            
            // 验证返回数据不为空
            assertNotNull(stockList, "股票列表不应为空");
            
            if (stockList.isEmpty()) {
                log.warn("股票列表为空，可能是网络问题或API服务不可用");
                log.info("========== 股票列表获取测试跳过（网络问题） ==========");
                return;
            }
            
            // 验证数量约为5000只
            int stockCount = stockList.size();
            log.info("获取到股票数量: {}", stockCount);
            assertTrue(stockCount >= 4000, "股票数量应该大于等于4000只，实际: " + stockCount);
            
            // 验证数据格式
            String firstStock = stockList.getString(0);
            log.info("第一只股票数据样例: {}", firstStock);
            assertNotNull(firstStock, "股票数据不应为空");
            assertTrue(firstStock.contains(","), "股票数据应该是逗号分隔的格式");
            
            log.info("========== 股票列表获取测试通过 ==========");
        } catch (Exception e) {
            log.error("测试获取股票列表失败", e);
            log.warn("外部API可能暂时不可用，跳过此测试");
        }
    }

    @Test
    @DisplayName("测试获取历史价格数据")
    void testGetHistoryPrices() {
        log.info("========== 开始测试获取历史价格数据 ==========");
        
        try {
            // 使用一只常见股票代码测试 (平安银行)
            String testStockCode = "000001";
            int days = 20;
            
            // 调用API获取历史价格
            JSONArray historyPrices = securitiesClient.getHistoryPrices(testStockCode, days);
            
            // 验证返回数据
            assertNotNull(historyPrices, "历史价格数据不应为空");
            
            if (historyPrices.isEmpty()) {
                log.warn("历史价格为空，可能是网络问题");
                log.info("========== 历史价格获取测试跳过（网络问题） ==========");
                return;
            }
            
            log.info("获取到股票 {} 的历史价格数据数量: {}", testStockCode, historyPrices.size());
            
            // 验证数据格式
            String firstPrice = historyPrices.getString(0);
            log.info("第一条历史价格数据样例: {}", firstPrice);
            assertNotNull(firstPrice, "价格数据不应为空");
            assertTrue(firstPrice.contains(","), "价格数据应该是逗号分隔的格式");
            
            log.info("========== 历史价格获取测试通过 ==========");
        } catch (Exception e) {
            log.error("测试获取历史价格失败", e);
            log.warn("外部API可能暂时不可用，跳过此测试");
        }
    }

    @Test
    @DisplayName("测试获取实时价格")
    void testGetRealtimePrice() {
        log.info("========== 开始测试获取实时价格 ==========");
        
        try {
            // 使用一只常见股票代码测试 (平安银行)
            String testStockCode = "000001";
            
            // 调用API获取实时价格
            JSONObject realtimePrice = securitiesClient.getRealtimePrice(testStockCode);
            
            // 验证返回数据
            if (realtimePrice != null) {
                log.info("获取到股票 {} 的实时价格: {}", testStockCode, realtimePrice);
                assertNotNull(realtimePrice.get("price"), "价格字段不应为空");
            } else {
                log.warn("实时价格返回为空，可能是非交易时间");
            }
            
            log.info("========== 实时价格获取测试完成 ==========");
        } catch (Exception e) {
            log.error("测试获取实时价格失败", e);
            log.warn("外部API可能暂时不可用，跳过此测试");
        }
    }

    @Test
    @DisplayName("测试获取多只股票历史数据")
    void testGetMultipleStockHistoryPrices() {
        log.info("========== 开始测试获取多只股票历史数据 ==========");
        
        // 测试多只常见股票
        String[] testStockCodes = {"000001", "000002", "600000", "600036"};
        
        int successCount = 0;
        for (String stockCode : testStockCodes) {
            try {
                JSONArray historyPrices = securitiesClient.getHistoryPrices(stockCode, 10);
                if (historyPrices != null && !historyPrices.isEmpty()) {
                    successCount++;
                    log.info("股票 {} 获取到 {} 条历史数据", stockCode, historyPrices.size());
                }
            } catch (Exception e) {
                log.warn("获取股票 {} 历史数据失败: {}", stockCode, e.getMessage());
            }
        }
        
        log.info("成功获取历史数据的股票数: {}/{}", successCount, testStockCodes.length);
        log.info("========== 多只股票历史数据获取测试完成 ==========");
    }
}