// AI_GENERATE_START -
package com.stock.agent.integration.client;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.agent.shared.config.AgentProperties;
import com.stock.agent.shared.error.FactSourceUnavailableException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek 网页搜索接口客户端。
 *
 * 仅获取长度受限的结构化搜索摘要，不抓取任意网页全文。
 *
 * @author mwangli
 * @since 2026-10-25
 */
@Component
public class DeepSeekWebSearchClient {

    private final AgentProperties properties;
    private final HttpClient httpClient;

    /**
     * 创建网页搜索客户端。
     *
     * @param properties Agent 配置
     */
    public DeepSeekWebSearchClient(AgentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(properties.getWebSearch().getTimeoutMs(), 1000L)))
                .build();
    }

    /**
     * 执行一次长度和结果数受限的网页搜索。
     *
     * @param query 搜索问题
     * @return 结构化搜索结果
     */
    public List<WebSearchItem> search(String query) {
        AgentProperties.WebSearchProperties search = properties.getWebSearch();
        if (!search.isEnabled()) {
            throw new FactSourceUnavailableException("网页搜索能力未启用");
        }
        if (!StringUtils.hasText(properties.getDeepseek().getApiKey())) {
            throw new FactSourceUnavailableException("网页搜索缺少 DeepSeek API Key");
        }
        if (!StringUtils.hasText(query) || query.length() > search.getMaxQueryChars()) {
            throw new IllegalArgumentException("网页搜索问题不能为空且不能超过 " + search.getMaxQueryChars() + " 个字符");
        }
        try {
            String body = JSON.toJSONString(java.util.Map.of("query", query.trim()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(search.getEndpoint()))
                    .timeout(Duration.ofMillis(Math.max(search.getTimeoutMs(), 1000L)))
                    .header("Authorization", "Bearer " + properties.getDeepseek().getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new FactSourceUnavailableException("网页搜索请求失败 HTTP " + response.statusCode());
            }
            if (response.body() == null || response.body().length() > search.getMaxResponseChars()) {
                throw new FactSourceUnavailableException("网页搜索响应为空或超过长度限制");
            }
            return parse(response.body(), search.getMaxResults());
        } catch (FactSourceUnavailableException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new FactSourceUnavailableException("网页搜索请求被中断", exception);
        } catch (Exception exception) {
            throw new FactSourceUnavailableException("网页搜索服务不可用", exception);
        }
    }

    /**
     * 判断网页搜索配置是否完整。
     *
     * @return 是否可调用
     */
    public boolean configured() {
        return properties.getWebSearch().isEnabled()
                && StringUtils.hasText(properties.getWebSearch().getEndpoint())
                && StringUtils.hasText(properties.getDeepseek().getApiKey());
    }

    private List<WebSearchItem> parse(String body, int maxResults) {
        JSONObject root = JSON.parseObject(body);
        JSONArray results = root == null ? null : root.getJSONArray("results");
        if (results == null) {
            throw new FactSourceUnavailableException("网页搜索响应缺少 results");
        }
        List<WebSearchItem> items = new ArrayList<>();
        for (int index = 0; index < results.size() && items.size() < Math.max(1, Math.min(maxResults, 5)); index++) {
            JSONObject item = results.getJSONObject(index);
            String url = limit(item.getString("url"), 1000);
            if (!validUrl(url)) {
                continue;
            }
            String summary = StringUtils.hasText(item.getString("summary"))
                    ? item.getString("summary") : item.getString("snippet");
            items.add(new WebSearchItem(
                    limit(item.getString("title"), 200),
                    limit(item.getString("site_name"), 120),
                    limit(item.getString("date"), 40),
                    url,
                    limit(summary, 800)));
        }
        return List.copyOf(items);
    }

    private boolean validUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            String scheme = URI.create(value).getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String limit(String value, int maximum) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.substring(0, Math.min(normalized.length(), maximum));
    }

    /** 网页搜索单项结果。 */
    public record WebSearchItem(String title, String source, String publishedAt, String url, String summary) {
    }
}
// AI_GENERATE_END -
