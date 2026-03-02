package com.stock.dataCollector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

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

/*
 * 历史价格BINDATA字段示例（用于文档说明，不参与编译）
 *
 * 单条记录字段顺序如下：
 * 20240130, 日期
 * 318,     开盘价
 * 321,     最高价
 * 307,     收盘价
 * 306,     最低价
 * 9591900, 成交量
 * 30093000,成交额
 * 0,0,0,0  量比、换手率、涨跌幅、涨跌额
 */