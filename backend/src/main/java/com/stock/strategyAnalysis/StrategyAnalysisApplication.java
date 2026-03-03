package com.stock.strategyAnalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 策略分析模块独立启动类
 * 
 * 启动命令: mvn spring-boot:run -pl strategy-analysis
 * 端口: 8083
 */
@SpringBootApplication
public class StrategyAnalysisApplication {

    public static void main(String[] args) {
        SpringApplication.run(StrategyAnalysisApplication.class, args);
    }
}