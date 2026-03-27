package com.stock.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * RestTemplate 配置，支持证券平台请求的 HTTP 代理
 * <p>
 * 当远程服务器 IP 被 weixin.citicsinfo.com 拦截 403 时，可通过代理转发请求
 *
 * @author mwangli
 * @since 2026-03-14
 */
@Slf4j
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${app.securities.proxy.enabled:false}") boolean proxyEnabled,
            @Value("${app.securities.proxy.host:127.0.0.1}") String proxyHost,
            @Value("${app.securities.proxy.port:1080}") int proxyPort,
            @Value("${app.securities.proxy.type:HTTP}") String proxyType) {

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(15000);
        factory.setReadTimeout(60000);

        if (proxyEnabled) {
            Proxy.Type type = "SOCKS5".equalsIgnoreCase(proxyType) ? Proxy.Type.SOCKS : Proxy.Type.HTTP;
            factory.setProxy(new Proxy(type, new InetSocketAddress(proxyHost, proxyPort)));
            log.info("证券平台请求已启用代理: {}://{}:{}", proxyType, proxyHost, proxyPort);
        }

        return new RestTemplate(factory);
    }
}
