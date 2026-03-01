package com.stock.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.net.HttpURLConnection;

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
        factory.setConnectTimeout(15000);  // 15秒连接超时
        factory.setReadTimeout(60000);     // 60秒读取超时
        
        // 设置请求工厂，支持SSL
        factory.setOutputStreaming(false);
        
        return new RestTemplate(factory);
    }
}