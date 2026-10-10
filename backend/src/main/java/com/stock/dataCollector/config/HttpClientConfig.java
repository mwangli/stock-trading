// AI_GENERATE_START -
package com.stock.dataCollector.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 数据采集模块的 HTTP 客户端配置。
 */
@Configuration
public class HttpClientConfig {

    /**
     * 创建供证券平台客户端使用的 RestTemplate。
     *
     * @param builder Spring Boot 管理的 RestTemplate 构建器
     * @return RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.build();
    }
}
// AI_GENERATE_END -
