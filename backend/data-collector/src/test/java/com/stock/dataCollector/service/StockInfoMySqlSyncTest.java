package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.mysql.StockInfoMySql;
import com.stock.dataCollector.repository.mysql.StockInfoMySqlRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL股票信息同步测试
 * 验证股票数据成功写入MySQL
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class StockInfoMySqlSyncTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private StockInfoMySqlService stockInfoMySqlService;

    @Autowired
    private StockInfoMySqlRepository stockInfoMySqlRepository;

    @Test
    @Order(1)
    @DisplayName("执行股票列表同步到MySQL")
    void testSyncStockListToMySql() {
        log.info("========== 开始执行股票列表同步 ==========");

        // 执行同步
        StockDataService.SyncResult result = stockDataService.syncStockListToMySql();

        log.info("同步结果: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
            result.getTotalCount(), result.getSavedCount(), 
            result.getUpdatedCount(), result.getFailedCount(), result.getCostTimeMs());

        // 验证同步结果
        assertTrue(result.getTotalCount() > 0, "应该获取到股票数据");
        assertTrue(result.getTotalCount() >= 4000, "股票数量应约为5000只，实际: " + result.getTotalCount());

        log.info("========== 股票列表同步测试通过 ==========");
    }

    @Test
    @Order(2)
    @DisplayName("验证MySQL数据数量")
    void testVerifyDataCount() {
        log.info("========== 开始验证MySQL数据数量 ==========");

        long count = stockInfoMySqlService.count();
        log.info("MySQL中股票数据总数: {}", count);

        assertTrue(count >= 4000, "MySQL中股票数据应约为5000只，实际: " + count);

        log.info("========== MySQL数据数量验证通过 ==========");
    }

    @Test
    @Order(3)
    @DisplayName("验证数据字段完整性")
    void testVerifyDataCompleteness() {
        log.info("========== 开始验证数据字段完整性 ==========");

        List<StockInfoMySql> allStocks = stockInfoMySqlService.findAll();

        int withCode = 0;
        int withName = 0;
        int withPrice = 0;
        int withMarket = 0;
        int withChangePercent = 0;

        for (StockInfoMySql stock : allStocks) {
            if (stock.getCode() != null && !stock.getCode().isEmpty()) withCode++;
            if (stock.getName() != null && !stock.getName().isEmpty()) withName++;
            if (stock.getPrice() != null) withPrice++;
            if (stock.getMarket() != null && !stock.getMarket().isEmpty()) withMarket++;
            if (stock.getChangePercent() != null) withChangePercent++;
        }

        int total = allStocks.size();
        log.info("总记录数: {}", total);
        log.info("有代码的记录: {} ({})", withCode, String.format("%.2f%%", withCode * 100.0 / total));
        log.info("有名称的记录: {} ({})", withName, String.format("%.2f%%", withName * 100.0 / total));
        log.info("有价格的记录: {} ({})", withPrice, String.format("%.2f%%", withPrice * 100.0 / total));
        log.info("有市场的记录: {} ({})", withMarket, String.format("%.2f%%", withMarket * 100.0 / total));
        log.info("有涨跌幅的记录: {} ({})", withChangePercent, String.format("%.2f%%", withChangePercent * 100.0 / total));

        // 关键字段不能缺失
        assertEquals(total, withCode, "所有记录都应该有股票代码");
        assertTrue(withName >= total * 0.95, "至少95%的记录应该有名称");

        log.info("========== 数据字段完整性验证通过 ==========");
    }

    @Test
    @Order(4)
    @DisplayName("验证按code去重功能")
    void testVerifyDeduplication() {
        log.info("========== 开始验证按code去重功能 ==========");

        // 再次执行同步
        StockDataService.SyncResult result = stockDataService.syncStockListToMySql();

        log.info("第二次同步结果: 总数={}, 新增={}, 更新={}", 
            result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount());

        // 第二次同步应该是更新，而不是新增
        assertTrue(result.getUpdatedCount() > 0, "第二次同步应该有更新记录");

        // 验证没有重复的code
        List<String> codes = stockInfoMySqlService.findAllCodes();
        long distinctCodes = codes.stream().distinct().count();
        assertEquals(codes.size(), distinctCodes, "不应该有重复的股票代码");

        log.info("========== 按code去重功能验证通过 ==========");
    }

    @Test
    @Order(5)
    @DisplayName("验证数据样本")
    void testVerifyDataSamples() {
        log.info("========== 开始验证数据样本 ==========");

        // 验证几只常见股票
        String[] testCodes = {"000001", "000002", "600000", "600036"};

        for (String code : testCodes) {
            StockInfoMySql stock = stockInfoMySqlService.findByCode(code).orElse(null);
            if (stock != null) {
                log.info("股票 {} - 名称: {}, 价格: {}, 市场: {}", 
                    stock.getCode(), stock.getName(), stock.getPrice(), stock.getMarket());
                assertNotNull(stock.getName(), "股票名称不应为空");
            } else {
                log.warn("股票 {} 未找到", code);
            }
        }

        log.info("========== 数据样本验证完成 ==========");
    }

    @Test
    @Order(6)
    @DisplayName("打印完整数据统计")
    void testPrintDataStatistics() {
        log.info("========== 数据统计报告 ==========");

        List<StockInfoMySql> allStocks = stockInfoMySqlService.findAll();

        // 按市场统计
        java.util.Map<String, Long> marketCount = new java.util.HashMap<>();
        for (StockInfoMySql stock : allStocks) {
            String market = stock.getMarket() != null ? stock.getMarket() : "未知";
            marketCount.merge(market, 1L, Long::sum);
        }

        log.info("总记录数: {}", allStocks.size());
        log.info("市场分布:");
        marketCount.forEach((market, count) -> 
            log.info("  {}: {} ({})", market, count, String.format("%.2f%%", count * 100.0 / allStocks.size())));

        // 字段完整性统计
        int withPrice = (int) allStocks.stream().filter(s -> s.getPrice() != null).count();
        int withVolume = (int) allStocks.stream().filter(s -> s.getVolume() != null).count();
        int withChangePercent = (int) allStocks.stream().filter(s -> s.getChangePercent() != null).count();

        log.info("字段完整性:");
        log.info("  价格: {} ({})", withPrice, String.format("%.2f%%", withPrice * 100.0 / allStocks.size()));
        log.info("  成交量: {} ({})", withVolume, String.format("%.2f%%", withVolume * 100.0 / allStocks.size()));
        log.info("  涨跌幅: {} ({})", withChangePercent, String.format("%.2f%%", withChangePercent * 100.0 / allStocks.size()));

        log.info("========== 数据统计报告完成 ==========");
    }
}