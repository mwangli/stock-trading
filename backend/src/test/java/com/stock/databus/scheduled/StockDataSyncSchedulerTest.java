package com.stock.databus.scheduled;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 股票数据同步定时任务测试
 * 验证定时任务的配置和调度逻辑
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.data.mongodb.uri=mongodb://localhost:27017/stock_trading_test"
})
class StockDataSyncSchedulerTest {

    @Autowired
    private StockDataSyncScheduler scheduler;

    @Test
    @DisplayName("验证定时任务 Bean 已加载")
    void testSchedulerBeanExists() {
        assertNotNull(scheduler, "定时任务 Bean 应存在");
    }

    @Test
    @DisplayName("每日同步任务方法存在")
    void testDailySyncMethodExists() {
        // 验证方法可以调用（不抛异常）
        assertDoesNotThrow(() -> {
            scheduler.syncDailyStockData();
        }, "每日同步任务方法应可调用");
    }

    @Test
    @DisplayName("全量同步任务方法存在")
    void testFullSyncMethodExists() {
        // 验证方法可以调用（不抛异常）
        assertDoesNotThrow(() -> {
            scheduler.syncAllHistoricalData();
        }, "全量同步任务方法应可调用");
    }
}