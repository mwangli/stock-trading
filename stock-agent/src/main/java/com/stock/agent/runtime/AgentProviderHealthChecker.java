// AI_GENERATE_START-----
package com.stock.agent.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.agent.config.AgentProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Agent Provider 健康检查服务。
 *
 * 通过模型列表接口验证 DeepSeek 和 Ollama 的实际连通性与目标模型可用性。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
class AgentProviderHealthChecker {

    private static final Duration OLLAMA_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEEPSEEK_TIMEOUT = Duration.ofSeconds(5);

    private final AgentProperties properties;
    private final AgentCircuitBreaker circuitBreaker;
    private final AgentProviderClientRegistry clientRegistry;
    private final HttpClient httpClient;

    AgentProviderHealthChecker(AgentProperties properties,
                               AgentCircuitBreaker circuitBreaker,
                               AgentProviderClientRegistry clientRegistry) {
        this.properties = properties;
        this.circuitBreaker = circuitBreaker;
        this.clientRegistry = clientRegistry;
        this.httpClient = HttpClient.newBuilder().connectTimeout(DEEPSEEK_TIMEOUT).build();
    }

    AgentProviderHealthStatus checkAll() {
        if (!properties.isEnabled()) {
            clientRegistry.updateStatus("deepseek", "AGENT_DISABLED");
            clientRegistry.updateStatus("ollama", "AGENT_DISABLED");
            return AgentProviderHealthStatus.builder().build();
        }
        return AgentProviderHealthStatus.builder()
                .deepseekAvailable(check("deepseek"))
                .ollamaAvailable(check("ollama"))
                .build();
    }

    boolean check(String provider) {
        if (!"deepseek".equals(provider) && !"ollama".equals(provider)) {
            return false;
        }
        if (!clientRegistry.isConfigured(provider)) {
            clientRegistry.updateStatus(provider, "NOT_CONFIGURED");
            return false;
        }
        if (!circuitBreaker.allowRequest(provider)) {
            clientRegistry.updateStatus(provider, "AGENT_CIRCUIT_OPEN");
            return false;
        }
        return "ollama".equals(provider) ? checkOllama() : checkDeepSeek();
    }

    private boolean checkDeepSeek() {
        long startedAt = System.nanoTime();
        AgentProperties.DeepSeekProperties config = properties.getDeepseek();
        try {
            HttpRequest request = HttpRequest.newBuilder(deepSeekModelsUri())
                    .timeout(DEEPSEEK_TIMEOUT)
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("DeepSeek 模型列表接口返回 HTTP " + response.statusCode());
            }
            JSONObject payload = JSON.parseObject(response.body());
            JSONArray models = payload == null ? null : payload.getJSONArray("data");
            boolean available = models != null && models.stream()
                    .filter(JSONObject.class::isInstance)
                    .map(JSONObject.class::cast)
                    .anyMatch(item -> config.getModel().equals(item.getString("id")));
            if (!available) {
                return recordUnavailable("deepseek", "MODEL_NOT_FOUND", null, startedAt);
            }
            clientRegistry.updateStatus("deepseek", "READY");
            circuitBreaker.recordSuccess("deepseek");
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return recordUnavailable("deepseek", "DEEPSEEK_UNAVAILABLE", exception, startedAt);
        } catch (Exception exception) {
            return recordUnavailable("deepseek", "DEEPSEEK_UNAVAILABLE", exception, startedAt);
        }
    }

    private boolean checkOllama() {
        long startedAt = System.nanoTime();
        String model = properties.getOllama().getModel();
        try {
            HttpRequest request = HttpRequest.newBuilder(ollamaTagsUri())
                    .timeout(OLLAMA_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Ollama 模型列表接口返回 HTTP " + response.statusCode());
            }
            JSONObject payload = JSON.parseObject(response.body());
            JSONArray models = payload == null ? null : payload.getJSONArray("models");
            boolean available = models != null && models.stream()
                    .filter(JSONObject.class::isInstance)
                    .map(JSONObject.class::cast)
                    .anyMatch(item -> model.equals(item.getString("name")) || model.equals(item.getString("model")));
            if (!available) {
                return recordUnavailable("ollama", "MODEL_NOT_FOUND", null, startedAt);
            }
            clientRegistry.updateStatus("ollama", "READY");
            circuitBreaker.recordSuccess("ollama");
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return recordUnavailable("ollama", "OLLAMA_UNAVAILABLE", exception, startedAt);
        } catch (Exception exception) {
            return recordUnavailable("ollama", "OLLAMA_UNAVAILABLE", exception, startedAt);
        }
    }

    private boolean recordUnavailable(String provider, String status, Exception exception, long startedAt) {
        clientRegistry.updateStatus(provider, status);
        circuitBreaker.recordFailure(provider);
        String causeType = exception == null ? "MODEL_NOT_FOUND" : exception.getClass().getSimpleName();
        String message = exception == null ? "" : safeErrorMessage(exception.getMessage());
        log.warn(
                "[Agent][Health] provider={} model={} status={} causeType={} causeMessage={} elapsedMs={}",
                provider, clientRegistry.modelName(provider), status, causeType, message, elapsedMillis(startedAt));
        return false;
    }

    private URI ollamaTagsUri() {
        String baseUrl = properties.getOllama().getBaseUrl().trim().replaceAll("/+$", "");
        if (baseUrl.endsWith("/v1")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 3);
        }
        return URI.create(baseUrl + "/api/tags");
    }

    private URI deepSeekModelsUri() {
        String baseUrl = properties.getDeepseek().getBaseUrl().trim().replaceAll("/+$", "");
        return URI.create(baseUrl + "/models");
    }

    private String safeErrorMessage(String message) {
        if (message == null || message.isBlank()) {
            return "";
        }
        String sanitized = message
                .replaceAll("(?i)(authorization\\s*:\\s*bearer\\s+|api[-_ ]?key\\s*[=:]\\s*)[^\\s,;]+", "$1***")
                .replaceAll("(?i)sk-[a-z0-9_-]+", "sk-***");
        return sanitized.substring(0, Math.min(sanitized.length(), 500));
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
// AI_GENERATE_END-----
