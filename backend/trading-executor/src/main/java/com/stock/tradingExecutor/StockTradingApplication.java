package com.stock.tradingExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 股票交易系统聚合启动类
 * 
 * 聚合启动所有模块：
 * - data-collector (数据采集)
 * - model-service (模型服务)
 * - strategy-analysis (策略分析)
 * - trading-executor (交易执行)
 * 
 * 启动命令: mvn spring-boot:run -pl trading-executor
 * 端口: 8080
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
    "com.stock.dataCollector",
    "com.stock.modelService",
    "com.stock.strategyAnalysis",
    "com.stock.tradingExecutor"
})
public class StockTradingApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockTradingApplication.class, args);
        System.out.println("========================================");
        System.out.println("  股票交易系统启动成功!");
        System.out.println("  聚合模块: data-collector, model-service, strategy-analysis, trading-executor");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("========================================");
    }
}