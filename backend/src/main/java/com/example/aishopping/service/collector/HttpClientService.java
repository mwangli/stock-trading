package com.example.aishopping.service.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * HTTP客户端服务
 * 封装JDK HttpClient，提供HTTP请求功能，支持动态代理IP
 */
@Service
@Slf4j
public class HttpClientService {

    // 动态代理配置
    private static final String PROXY_HOST = "proxy.proxy302.com";
    private static final int PROXY_PORT = 2222;
    private static final String PROXY_USERNAME = "0Q2NHuMD";
    private static final String PROXY_PASSWORD = "8mJMCeRG2NMlpBGO";
    private static final boolean USE_PROXY = false; // 是否使用代理

    private final HttpClient httpClient;
    private final HttpClient proxyClient;
    private final List<String> userAgents;
    private final Random random;

    public HttpClientService() {
        // 初始化普通HTTP客户端
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // 初始化代理HTTP客户端
        if (USE_PROXY) {
            log.info("[HTTP-CLIENT] 初始化代理客户端: {}:{} (用户名: {})", PROXY_HOST, PROXY_PORT, PROXY_USERNAME);
            
            // 设置代理认证
            System.setProperty("http.proxyHost", PROXY_HOST);
            System.setProperty("http.proxyPort", String.valueOf(PROXY_PORT));
            System.setProperty("https.proxyHost", PROXY_HOST);
            System.setProperty("https.proxyPort", String.valueOf(PROXY_PORT));
            System.setProperty("http.proxyUser", PROXY_USERNAME);
            System.setProperty("http.proxyPassword", PROXY_PASSWORD);
            
            // 使用Authenticator进行代理认证
            Authenticator.setDefault(new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(PROXY_USERNAME, PROXY_PASSWORD.toCharArray());
                }
            });
            
            ProxySelector proxySelector = ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT));
            
            this.proxyClient = HttpClient.newBuilder()
                    .proxy(proxySelector)
                    .connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            
            log.info("[HTTP-CLIENT] 代理客户端初始化成功!");
        } else {
            this.proxyClient = null;
            log.info("[HTTP-CLIENT] 不使用代理");
        }

        // 用户代理池
        this.userAgents = Arrays.asList(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0"
        );

        this.random = new Random();
    }

    /**
     * 发送GET请求
     *
     * @param url 请求URL
     * @return 响应内容
     */
    public String get(String url) throws IOException, InterruptedException {
        String userAgent = getRandomUserAgent();
        
        log.info("[HTTP-CLIENT] ================== 开始HTTP请求 ==================");
        log.info("[HTTP-CLIENT] 请求URL: {}", url);
        log.info("[HTTP-CLIENT] User-Agent: {}", userAgent);
        log.info("[HTTP-CLIENT] 使用代理: {}", USE_PROXY ? "是" : "否");
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7")
                .header("Accept-Encoding", "gzip, deflate, br")
                .header("DNT", "1")
                .header("Connection", "keep-alive")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Cache-Control", "max-age=0")
                .header("Pragma", "no-cache")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        long startTime = System.currentTimeMillis();
        
        // 根据配置选择使用代理或普通客户端
        HttpClient client = (USE_PROXY && proxyClient != null) ? proxyClient : httpClient;
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        long responseTime = System.currentTimeMillis() - startTime;
        
        log.info("[HTTP-CLIENT] 响应状态: {}", response.statusCode());
        log.info("[HTTP-CLIENT] 响应头: {}", response.headers().map());
        log.info("[HTTP-CLIENT] 响应长度: {} 字符", response.body() != null ? response.body().length() : 0);
        log.info("[HTTP-CLIENT] 请求耗时: {} ms", responseTime);
        
        if (response.statusCode() != 200) {
            log.error("[HTTP-CLIENT] HTTP请求失败! 状态码: {}, URL: {}", response.statusCode(), url);
            throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
        }

        log.info("[HTTP-CLIENT] ================== HTTP请求完成 ==================");
        return response.body();
    }

    /**
     * 使用代理发送请求
     *
     * @param url       请求URL
     * @param proxyHost 代理主机
     * @param proxyPort 代理端口
     * @return 响应内容
     */
    public String getWithProxy(String url, String proxyHost, int proxyPort)
            throws IOException, InterruptedException {

        ProxySelector proxySelector = ProxySelector.of(
                new InetSocketAddress(proxyHost, proxyPort)
        );

        HttpClient proxyClient = HttpClient.newBuilder()
                .proxy(proxySelector)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", getRandomUserAgent())
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = proxyClient.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " for URL: " + url);
        }

        return response.body();
    }

    /**
     * 获取随机User-Agent
     */
    private String getRandomUserAgent() {
        return userAgents.get(random.nextInt(userAgents.size()));
    }
}
