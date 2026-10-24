// AI_GENERATE_START ---
package com.stock.agent.integration.config;

import com.stock.agent.shared.config.InternalApiProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Backend 内部事实客户端配置。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Configuration
public class BackendClientConfig {

    /**
     * 创建带固定超时和内部令牌的 Backend 客户端。
     *
     * @param properties Backend 连接配置
     * @param internalApiProperties 内部令牌配置
     * @return Backend RestClient
     */
    @Bean(name = "backendFactsRestClient")
    public RestClient backendFactsRestClient(BackendServiceProperties properties,
                                             InternalApiProperties internalApiProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("X-Internal-Token", internalApiProperties.getToken())
                .build();
    }
}
// AI_GENERATE_END --
