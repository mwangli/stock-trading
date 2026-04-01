package com.stock.tradingExecutor.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = "com.stock")
public class OrderSyncTest implements CommandLineRunner {

    private final HistoryOrderSyncService historyOrderSyncService;

    public OrderSyncTest(HistoryOrderSyncService historyOrderSyncService) {
        this.historyOrderSyncService = historyOrderSyncService;
    }

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(OrderSyncTest.class, args);
        context.close();
    }

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 开始执行历史订单同步 ===");
        
        try {
            HistoryOrderSyncService.SyncResult result = historyOrderSyncService.syncAllHistoryOrders();
            
            System.out.println("=== 同步完成 ===");
            System.out.println("总获取: " + result.totalFetched());
            System.out.println("新增: " + result.savedCount());
            System.out.println("重复: " + result.duplicateCount());
            System.out.println("失败: " + result.failedCount());
            System.out.println("批次: " + result.syncBatchNo());
            System.out.println("耗时: " + result.costTimeMs() + "ms");
            
        } catch (Exception e) {
            System.err.println("同步失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}