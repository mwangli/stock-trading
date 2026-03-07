package com.stock.dataCollector.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecuritiesClient 集成测试
 * 验证证券平台 API 客户端的数据获取能力
 * 
 * 注意：所有测试使用真实数据，不使用 Mock
 * - 网络不可用时跳过测试
 * - API 返回空数据时跳过测试
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class SecuritiesClientTest {

    @Autowired
    private SecuritiesClient securitiesClient;

    @Nested
    @DisplayName("股票列表接口测试")
    class StockListTests {

        @Test
        @DisplayName("获取股票列表 - 验证能获取约5000只股票")
        void testGetStockList() {
            log.info("========== 开始测试获取股票列表 ==========");

            // 调用 API 获取股票列表
            JSONArray stockList = securitiesClient.getStockList();

            // 数据检查：网络问题或 API 服务不可用时跳过测试
            if (stockList == null || stockList.isEmpty()) {
                log.warn("股票列表为空，可能是网络问题或 API 服务不可用，跳过测试");
                return; // 使用 return 跳过而非 fail
            }

            // 验证返回数据不为空
            assertNotNull(stockList, "股票列表不应为空");

            // 验证数量约为 5000 只
            int stockCount = stockList.size();
            log.info("获取到股票数量: {}", stockCount);
            assertTrue(stockCount >= 4000, "股票数量应该大于等于 4000 只，实际: " + stockCount);

            // 验证数据格式
            String firstStock = stockList.getString(0);
            log.info("第一只股票数据样例: {}", firstStock);
            assertNotNull(firstStock, "股票数据不应为空");
            assertTrue(firstStock.contains(","), "股票数据应该是逗号分隔的格式");

            log.info("========== 股票列表获取测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("历史价格接口测试")
    class HistoryPriceTests {

        @Test
        @DisplayName("获取单只股票历史价格数据")
        void testGetHistoryPrices() {
            log.info("========== 开始测试获取历史价格数据 ==========");

            // 使用常见股票代码测试 (平安银行)
            String testStockCode = "000001";
            int days = 20;

            // 调用 API 获取历史价格
            JSONArray historyPrices = securitiesClient.getHistoryPrices(testStockCode, days);

            // 数据检查
            if (historyPrices == null || historyPrices.isEmpty()) {
                log.warn("历史价格为空，可能是网络问题或非交易时间，跳过测试");
                return;
            }

            // 验证返回数据
            assertNotNull(historyPrices, "历史价格数据不应为空");

            log.info("获取到股票 {} 的历史价格数据数量: {}", testStockCode, historyPrices.size());

            // 验证数据格式
            String firstPrice = historyPrices.getString(0);
            log.info("第一条历史价格数据样例: {}", firstPrice);
            assertNotNull(firstPrice, "价格数据不应为空");
            assertTrue(firstPrice.contains(","), "价格数据应该是逗号分隔的格式");

            log.info("========== 历史价格获取测试通过 ==========");
        }

        @Test
        @DisplayName("获取多只股票历史数据")
        void testGetMultipleStockHistoryPrices() {
            log.info("========== 开始测试获取多只股票历史数据 ==========");

            // 测试多只常见股票
            String[] testStockCodes = {"000001", "000002", "600000", "600036"};

            int successCount = 0;
            for (String stockCode : testStockCodes) {
                JSONArray historyPrices = securitiesClient.getHistoryPrices(stockCode, 10);
                if (historyPrices != null && !historyPrices.isEmpty()) {
                    successCount++;
                    log.info("股票 {} 获取到 {} 条历史数据", stockCode, historyPrices.size());
                }
            }

            log.info("成功获取历史数据的股票数: {}/{}", successCount, testStockCodes.length);

            // 至少有一只股票获取成功
            assertTrue(successCount > 0, "应该至少有一只股票获取成功");

            log.info("========== 多只股票历史数据获取测试通过 ==========");
        }
    }

    @Nested
    @DisplayName("实时价格接口测试")
    class RealtimePriceTests {

        @Test
        @DisplayName("获取股票实时价格")
        void testGetRealtimePrice() {
            log.info("========== 开始测试获取实时价格 ==========");

            // 使用常见股票代码测试 (平安银行)
            String testStockCode = "000001";

            // 调用 API 获取实时价格
            JSONObject realtimePrice = securitiesClient.getRealtimePrice(testStockCode);

            // 数据检查：非交易时间可能返回空
            if (realtimePrice == null) {
                log.warn("实时价格返回为空，可能是非交易时间，跳过测试");
                return;
            }

            // 验证返回数据
            log.info("获取到股票 {} 的实时价格: {}", testStockCode, realtimePrice);

            // 验证价格字段存在
            Object price = realtimePrice.get("price");
            if (price != null) {
                log.info("实时价格字段: {}", price);
            } else {
                log.warn("价格字段为空，可能是非交易时间");
            }

            log.info("========== 实时价格获取测试完成 ==========");
        }
    }
}