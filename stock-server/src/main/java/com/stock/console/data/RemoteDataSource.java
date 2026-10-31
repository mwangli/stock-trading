// AI_GENERATE_START -
package com.stock.console.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class RemoteDataSource {

    private final String baseUrl;
    private final String authorization;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RemoteDataSource(
            @Value("${STOCK_REMOTE_DATA_URL:}") String baseUrl,
            @Value("${STOCK_REMOTE_DATA_USERNAME:}") String username,
            @Value("${STOCK_REMOTE_DATA_PASSWORD:}") String password,
            ObjectMapper objectMapper) {
        this.baseUrl = trimTrailingSlash(baseUrl == null ? "" : baseUrl.trim());
        this.authorization = basicAuthorization(username, password);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty();
    }

    public String market(String relativePath) {
        return resource("market/" + safeRelativePath(relativePath));
    }

    public String tradingDatabase() {
        return resource("sqlite/trading.db");
    }

    public boolean exists(String url) {
        if (!enabled()) {
            return false;
        }
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder(URI.create(url)))
                    .timeout(Duration.ofSeconds(15))
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception exception) {
            return false;
        }
    }

    public List<String> marketDirectories(String relativePath) {
        if (!enabled()) {
            return List.of();
        }
        String url = market(ensureTrailingSlash(relativePath));
        try {
            HttpRequest request = authorized(HttpRequest.newBuilder(URI.create(url)))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return List.of();
            }
            JsonNode entries = objectMapper.readTree(response.body());
            if (!entries.isArray()) {
                return List.of();
            }
            List<String> result = new ArrayList<>();
            for (JsonNode entry : entries) {
                if ("directory".equals(entry.path("type").asText())) {
                    String name = entry.path("name").asText();
                    if (!name.isBlank()) {
                        result.add(name);
                    }
                }
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    public void configure(Connection connection) throws SQLException {
        if (!enabled()) {
            return;
        }
        if (authorization.isEmpty()) {
            throw new SQLException("远程数据源已启用，但未配置用户名或密码");
        }
        String sql = "CREATE TEMP SECRET stock_remote_data (TYPE http, "
                + "EXTRA_HTTP_HEADERS MAP {'Authorization': '"
                + sqlLiteral(authorization) + "'})";
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private HttpRequest.Builder authorized(HttpRequest.Builder builder) {
        if (!authorization.isEmpty()) {
            builder.header("Authorization", authorization);
        }
        return builder;
    }

    private String resource(String relativePath) {
        if (!enabled()) {
            throw new IllegalStateException("远程数据源未启用");
        }
        return baseUrl + "/" + safeRelativePath(relativePath);
    }

    private String safeRelativePath(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.contains("../") || normalized.equals("..")) {
            throw new IllegalArgumentException("远程数据路径非法");
        }
        return normalized;
    }

    private String ensureTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value : value + "/";
    }

    private String trimTrailingSlash(String value) {
        String result = value;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String basicAuthorization(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            return "";
        }
        String token = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return "Basic " + token;
    }

    private String sqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
// AI_GENERATE_END -
