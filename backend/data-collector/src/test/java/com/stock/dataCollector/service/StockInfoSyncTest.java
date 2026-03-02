package com.stock.dataCollector.service;

import com.stock.dataCollector.entity.StockInfo;
import com.stock.dataCollector.entity.StockPrice;
import com.stock.dataCollector.repository.StockInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MySQL 股票信息同步集成测试
 * 验证股票数据成功写入 MySQL
 * 
 * 测试原则：
 * 1. 使用真实数据，不使用 Mock
 * 2. 每个测试独立运行，不依赖其他测试
 * 3. 数据不足时跳过测试而非失败
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class StockInfoSyncTest {

    @Autowired
    private StockDataService stockDataService;

    @Autowired
    private StockInfoRepository stockInfoRepository;

    // ==================== 数据同步测试 ====================

    @Test
    @DisplayName("执行股票列表同步到 MySQL")
    void testSyncStockListToMySql() {
        log.info("========== 开始执行股票列表同步 ==========");

        StockDataService.SyncResult result = stockDataService.syncStockList();

        log.info("同步结果: 总数={}, 新增={}, 更新={}, 失败={}, 耗时={}ms",
            result.getTotalCount(), result.getSavedCount(),
            result.getUpdatedCount(), result.getFailedCount(), result.getCostTimeMs());

        // 数据检查：API 不可用时跳过
        if (result.getTotalCount() == 0) {
            log.warn("未获取到股票数据，可能是网络问题或 API 服务不可用，跳过测试");
            return;
        }

        assertTrue(result.getTotalCount() > 0, "应该获取到股票数据");
        assertTrue(result.getTotalCount() >= 4000, "股票数量应约为 5000 只，实际: " + result.getTotalCount());

        log.info("========== 股票列表同步测试通过 ==========");
    }

    // ==================== 数据验证测试 ====================

    @Test
    @DisplayName("验证 MySQL 数据数量")
    void testVerifyDataCount() {
        log.info("========== 开始验证 MySQL 数据数量 ==========");

        long count = stockInfoRepository.count();
        log.info("MySQL 中股票数据总数: {}", count);

        // 数据检查：数据库为空时跳过
        if (count == 0) {
            log.warn("MySQL 中无股票数据，请先执行同步测试，跳过测试");
            return;
        }

        assertTrue(count >= 4000, "MySQL 中股票数据应约为 5000 只，实际: " + count);

        log.info("========== MySQL 数据数量验证通过 ==========");
    }

    @Test
    @DisplayName("验证数据字段完整性")
    void testVerifyDataCompleteness() {
        log.info("========== 开始验证数据字段完整性 ==========");

        List<StockInfo> allStocks = stockInfoRepository.findAll();

        // 数据检查
        if (allStocks.isEmpty()) {
            log.warn("数据库中无股票数据，跳过测试");
            return;
        }

        int withCode = 0;
        int withName = 0;
        int withPrice = 0;
        int withMarket = 0;

        for (StockInfo stock : allStocks) {
            if (stock.getCode() != null && !stock.getCode().isEmpty()) withCode++;
            if (stock.getName() != null && !stock.getName().isEmpty()) withName++;
            if (stock.getPrice() != null) withPrice++;
            if (stock.getMarket() != null && !stock.getMarket().isEmpty()) withMarket++;
        }

        int total = allStocks.size();
        log.info("总记录数: {}", total);
        log.info("有代码的记录: {} ({})", withCode, String.format("%.2f%%", withCode * 100.0 / total));
        log.info("有名称的记录: {} ({})", withName, String.format("%.2f%%", withName * 100.0 / total));
        log.info("有价格的记录: {} ({})", withPrice, String.format("%.2f%%", withPrice * 100.0 / total));
        log.info("有市场的记录: {} ({})", withMarket, String.format("%.2f%%", withMarket * 100.0 / total));

        assertEquals(total, withCode, "所有记录都应该有股票代码");
        assertTrue(withName >= total * 0.95, "至少 95% 的记录应该有名称");

        log.info("========== 数据字段完整性验证通过 ==========");
    }

    @Test
    @DisplayName("验证按 code 去重功能")
    void testVerifyDeduplication() {
        log.info("========== 开始验证按 code 去重功能 ==========");

        // 执行同步（测试更新逻辑）
        StockDataService.SyncResult result = stockDataService.syncStockList();

        // 数据检查
        if (result.getTotalCount() == 0) {
            log.warn("未获取到股票数据，跳过去重验证测试");
            return;
        }

        log.info("同步结果: 总数={}, 新增={}, 更新={}",
            result.getTotalCount(), result.getSavedCount(), result.getUpdatedCount());

        // 验证无重复代码
        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes.isEmpty()) {
            log.warn("数据库中无股票代码数据，跳过测试");
            return;
        }

        long distinctCodes = codes.stream().distinct().count();
        assertEquals(codes.size(), distinctCodes, "不应该有重复的股票代码");

        log.info("========== 按 code 去重功能验证通过 ==========");
    }

    @Test
    @DisplayName("验证数据样本")
    void testVerifyDataSamples() {
        log.info("========== 开始验证数据样本 ==========");

        String[] testCodes = {"000001", "000002", "600000", "600036"};

        int foundCount = 0;
        for (String code : testCodes) {
            var stockOpt = stockInfoRepository.findByCode(code);
            if (stockOpt.isPresent()) {
                StockInfo stock = stockOpt.get();
                log.info("股票 {} - 名称: {}, 价格: {}, 市场: {}",
                    stock.getCode(), stock.getName(), stock.getPrice(), stock.getMarket());
                assertNotNull(stock.getName(), "股票名称不应为空");
                foundCount++;
            } else {
                log.warn("股票 {} 未找到", code);
            }
        }

        // 至少找到一只股票
        assertTrue(foundCount > 0, "应该至少找到一只测试股票");

        log.info("========== 数据样本验证完成 ==========");
    }

    // ==================== 数据统计测试 ====================

    @Test
    @DisplayName("打印完整数据统计")
    void testPrintDataStatistics() {
        log.info("========== 数据统计报告 ==========");

        List<StockInfo> allStocks = stockInfoRepository.findAll();

        // 数据检查
        if (allStocks.isEmpty()) {
            log.warn("数据库中无股票数据，跳过统计测试");
            return;
        }

        java.util.Map<String, Long> marketCount = new java.util.HashMap<>();
        for (StockInfo stock : allStocks) {
            String market = stock.getMarket() != null ? stock.getMarket() : "未知";
            marketCount.merge(market, 1L, Long::sum);
        }

        log.info("总记录数: {}", allStocks.size());
        log.info("市场分布:");
        marketCount.forEach((market, count) ->
            log.info("  {}: {} ({})", market, count, String.format("%.2f%%", count * 100.0 / allStocks.size())));

        int withPrice = (int) allStocks.stream().filter(s -> s.getPrice() != null).count();
        int withChangePercent = (int) allStocks.stream().filter(s -> s.getChangePercent() != null).count();
        int withTotalMarketValue = (int) allStocks.stream().filter(s -> s.getTotalMarketValue() != null).count();
        int withTurnoverRate = (int) allStocks.stream().filter(s -> s.getTurnoverRate() != null).count();

        log.info("字段完整性:");
        log.info("  价格: {} ({})", withPrice, String.format("%.2f%%", withPrice * 100.0 / allStocks.size()));
        log.info("  涨跌幅: {} ({})", withChangePercent, String.format("%.2f%%", withChangePercent * 100.0 / allStocks.size()));
        log.info("  总市值: {} ({})", withTotalMarketValue, String.format("%.2f%%", withTotalMarketValue * 100.0 / allStocks.size()));
        log.info("  换手率: {} ({})", withTurnoverRate, String.format("%.2f%%", withTurnoverRate * 100.0 / allStocks.size()));

        log.info("========== 数据统计报告完成 ==========");
    }

    // ==================== Repository 查询测试 ====================

    @Test
    @DisplayName("测试 Repository 按代码查询")
    void testRepositoryFindByCode() {
        log.info("========== 测试 Repository 按代码查询 ==========");

        // 先获取一条数据作为测试样本
        List<StockInfo> allStocks = stockInfoRepository.findAll();
        if (allStocks.isEmpty()) {
            log.warn("数据库中无股票数据，跳过测试");
            return;
        }

        StockInfo sample = allStocks.get(0);
        String code = sample.getCode();

        // 测试按代码查询
        var result = stockInfoRepository.findByCode(code);
        assertTrue(result.isPresent(), "应该能查询到股票: " + code);
        assertEquals(code, result.get().getCode(), "查询结果代码应匹配");

        log.info("查询股票 {} 成功: {}", code, result.get().getName());
        log.info("========== Repository 按代码查询测试通过 ==========");
    }

    @Test
    @DisplayName("测试 Repository 按市场查询")
    void testRepositoryFindByMarket() {
        log.info("========== 测试 Repository 按市场查询 ==========");

        // 测试按市场查询
        List<StockInfo> shStocks = stockInfoRepository.findByMarket("SH");
        List<StockInfo> szStocks = stockInfoRepository.findByMarket("SZ");

        log.info("上海市场股票数: {}", shStocks.size());
        log.info("深圳市场股票数: {}", szStocks.size());

        // 至少有一个市场有数据
        assertTrue(shStocks.size() > 0 || szStocks.size() > 0, "应该至少有一个市场有数据");

        log.info("========== Repository 按市场查询测试通过 ==========");
    }

    @Test
    @DisplayName("测试 Repository 检查股票是否存在")
    void testRepositoryExistsByCode() {
        log.info("========== 测试 Repository 检查股票是否存在 ==========");

        // 先获取一条数据作为测试样本
        List<StockInfo> allStocks = stockInfoRepository.findAll();
        if (allStocks.isEmpty()) {
            log.warn("数据库中无股票数据，跳过测试");
            return;
        }

        String existingCode = allStocks.get(0).getCode();
        String nonExistingCode = "999999"; // 不存在的代码

        // 测试存在检查
        assertTrue(stockInfoRepository.existsByCode(existingCode), "股票 " + existingCode + " 应该存在");
        assertFalse(stockInfoRepository.existsByCode(nonExistingCode), "股票 " + nonExistingCode + " 不应该存在");

        log.info("========== Repository 检查股票存在测试通过 ==========");
    }

    // ==================== 历史价格同步触发与统计 ====================

    @Test
    @DisplayName("批量同步所有股票历史价格到 Mongo 并统计")
    void testSyncAllStocksHistoryToMongo() {
        log.info("========== 开始批量同步所有股票历史价格 ==========");

        List<String> codes = stockInfoRepository.findAllCodes();
        if (codes == null || codes.isEmpty()) {
            log.warn("数据库中无股票代码，跳过历史价格同步测试");
            return;
        }

        int totalStocks = codes.size();
        log.info("本次需要同步历史价格的股票总数: {}", totalStocks);

        int totalStocksWithData = 0;
        int totalRecords = 0;

        int index = 0;
        for (String code : codes) {
            index++;
            List<StockPrice> prices = stockDataService.getHistoryPrices(code);

            if (prices == null || prices.isEmpty()) {
                log.warn("股票 {} 无历史价格数据，跳过", code);
                continue;
            }

            stockDataService.saveStockPrices(prices);
            totalStocksWithData++;
            totalRecords += prices.size();

            double percent = totalStocks == 0 ? 0.0 : (index * 100.0 / totalStocks);
            log.info("股票 {} 历史数据条数: {}，当前进度: {}/{} ({})",
                code,
                prices.size(),
                index,
                totalStocks,
                String.format("%.2f%%", percent));
        }

        log.info("========== 批量同步完成，成功写入 {} 只股票，共 {} 条历史价格记录 ==========",
            totalStocksWithData, totalRecords);
    }
}