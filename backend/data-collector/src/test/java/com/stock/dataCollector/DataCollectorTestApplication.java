package com.stock.dataCollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * 测试启动类
 * 用于data-collector模块的集成测试
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.stock.dataCollector",
    "com.stock.config"
})
public class DataCollectorTestApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DataCollectorTestApplication.class, args);
    }
}