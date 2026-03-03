package com.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * 股票交易系统主应用启动类
 * 
 * 聚合启动所有业务模块：
 * - data-collector (数据采集)
 * - model-service (模型服务)
 * - strategy-analysis (策略分析)
 * - trading-executor (交易执行)
 * 
 * 启动命令: mvn spring-boot:run
 * 端口: 8080
 */
@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
        System.out.println("========================================");
        System.out.println("  AI 股票交易系统启动成功!");
        System.out.println("  聚合模块: data-collector, model-service,");
        System.out.println("           strategy-analysis, trading-executor");
        System.out.println("  访问地址: http://localhost:8080");
        System.out.println("========================================");
    }

    /**
     * RestTemplate 配置
     * 用于 HTTP 请求到证券平台
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);  // 15秒连接超时
        factory.setReadTimeout(60000);     // 60秒读取超时
        factory.setOutputStreaming(false);
        return new RestTemplate(factory);
    }
}