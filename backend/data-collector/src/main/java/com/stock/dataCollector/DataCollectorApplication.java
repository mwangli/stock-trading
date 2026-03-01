package com.stock.dataCollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据采集模块独立启动类
 * 
 * 启动命令: mvn spring-boot:run -pl data-collector
 * 端口: 8081
 */
@SpringBootApplication
@EnableScheduling
public class DataCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(DataCollectorApplication.class, args);
    }
}