package com.stock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * 通用配置
 */
@Configuration
public class CommonConfig {

    /**
     * RestTemplate 配置
     * 用于 HTTP 请求到证券平台
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 10秒连接超时
        factory.setReadTimeout(30000);    // 30秒读取超时
        return new RestTemplate(factory);
    }
}
