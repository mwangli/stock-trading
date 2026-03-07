package com.stock.tradingExecutor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 交易执行模块独立启动类
 * 
 * 启动命令: mvn spring-boot:run -pl trading-executor
 * 端口: 8084
 */
@SpringBootApplication
@EnableScheduling
public class TradingExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TradingExecutorApplication.class, args);
        System.out.println("========================================");
        System.out.println("  交易执行模块启动成功!");
        System.out.println("  访问地址: http://localhost:8084");
        System.out.println("========================================");
    }
}