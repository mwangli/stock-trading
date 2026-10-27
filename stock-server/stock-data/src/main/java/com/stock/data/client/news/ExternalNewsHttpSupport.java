// AI_GENERATE_START- ---
package com.stock.data.client.news;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 外部新闻来源 HTTP 与通用字段解析支持。
 * 只封装无状态协议细节，不负责来源业务解析和持久化。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
public class ExternalNewsHttpSupport {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MINIMUM_REQUEST_INTERVAL_MILLIS = 80L;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private long lastRequestNanos;

    /**
     * 执行公开来源 GET 请求。
     *
     * @param url 请求地址
     * @param referer 来源要求的 Referer
     * @return UTF-8 响应正文
     * @throws Exception 网络或非 2xx 响应时抛出
     */
    public String get(String url, String referer) throws Exception {
        HttpRequest request = baseRequest(url, referer).GET().build();
        return send(request);
    }

    /**
     * 执行表单 POST 请求。
     *
     * @param url 请求地址
     * @param referer 来源要求的 Referer
     * @param body 已编码表单正文
     * @return UTF-8 响应正文
     * @throws Exception 网络或非 2xx 响应时抛出
     */
    public String postForm(String url, String referer, String body) throws Exception {
        HttpRequest request = baseRequest(url, referer)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return send(request);
    }

    /**
     * 去除 JSONP 回调包装。
     *
     * @param value JSON 或 JSONP 文本
     * @return JSON 文本
     */
    public String unwrapJsonp(String value) {
        int start = value == null ? -1 : value.indexOf('(');
        int end = value == null ? -1 : value.lastIndexOf(')');
        return start >= 0 && end > start ? value.substring(start + 1, end) : value;
    }

    /**
     * 清理来源摘要中的 HTML 和实体噪声。
     *
     * @param value 原始文本
     * @return 清理后的单行文本
     */
    public String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&ensp;", " ").replace("&amp;", "&")
                .replaceAll("\\s+", " ").trim();
    }

    /**
     * 解析公开来源常见日期时间格式。
     *
     * @param value 日期时间字符串
     * @return 解析结果，无法解析时返回 null
     */
    public LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().replace('T', ' ');
        try {
            if (normalized.length() >= 19) {
                return LocalDateTime.parse(normalized.substring(0, 19), DATE_TIME);
            }
            if (normalized.length() >= 10) {
                return LocalDate.parse(normalized.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    /**
     * 将相对地址转换为绝对地址。
     *
     * @param baseUrl 来源根地址
     * @param value 原始地址
     * @return 绝对地址
     */
    public String absoluteUrl(String baseUrl, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return baseUrl.replaceAll("/$", "") + "/" + value.replaceAll("^/", "");
    }

    /**
     * 生成稳定 SHA-256 十六进制摘要。
     *
     * @param value 摘要输入
     * @return 小写十六进制摘要
     */
    public String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("无法生成新闻来源摘要", exception);
        }
    }

    private HttpRequest.Builder baseRequest(String url, String referer) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json,text/plain,*/*")
                .header("Referer", referer);
    }

    private synchronized String send(HttpRequest request) throws Exception {
        throttle();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("外部新闻来源返回 HTTP " + response.statusCode()
                    + ", host=" + request.uri().getHost());
        }
        lastRequestNanos = System.nanoTime();
        return response.body();
    }

    private void throttle() throws InterruptedException {
        long elapsedMillis = (System.nanoTime() - lastRequestNanos) / 1_000_000L;
        long waitMillis = MINIMUM_REQUEST_INTERVAL_MILLIS - elapsedMillis;
        if (lastRequestNanos > 0L && waitMillis > 0L) {
            Thread.sleep(waitMillis);
        }
    }
}
// AI_GENERATE_END- ---
