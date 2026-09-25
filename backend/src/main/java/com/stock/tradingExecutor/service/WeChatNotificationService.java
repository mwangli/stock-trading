// AI_GENERATE_START -
package com.stock.tradingExecutor.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.stock.tradingExecutor.config.WeChatNotificationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 微信小程序订阅消息发送服务。
 * 通道默认关闭，配置不完整或微信接口拒绝时只记录错误，不影响交易主流程。
 *
 * @author mwangli
 * @since 2026-09-25
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WeChatNotificationService {

    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String SEND_URL = "https://api.weixin.qq.com/cgi-bin/message/subscribe/send";

    private final WeChatNotificationProperties properties;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * 向已配置的本人微信 OpenId 发送订阅消息。
     *
     * @param title 通知标题
     * @param content 通知内容
     */
    public void send(String title, String content) {
        if (!properties.isEnabled()) {
            return;
        }
        if (!isConfigured()) {
            log.error("微信小程序通知已启用，但 appId、appSecret、openId 或 templateId 配置不完整");
            return;
        }
        try {
            String accessToken = requestAccessToken();
            if (accessToken == null) {
                return;
            }
            Map<String, Object> data = Map.of(
                    properties.getTitleField(), Map.of("value", truncate(title, 20)),
                    properties.getContentField(), Map.of("value", truncate(content, 20)));
            Map<String, Object> payload = Map.of(
                    "touser", properties.getOpenId(),
                    "template_id", properties.getTemplateId(),
                    "page", properties.getPage(),
                    "data", data);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SEND_URL + "?access_token=" + accessToken))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(JSON.toJSONString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JSONObject result = JSON.parseObject(response.body());
            if (response.statusCode() / 100 != 2 || result.getIntValue("errcode") != 0) {
                log.error("微信小程序通知发送失败: httpStatus={}, errcode={}, errmsg={}",
                        response.statusCode(), result.getIntValue("errcode"), result.getString("errmsg"));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("微信小程序通知发送被中断");
        } catch (Exception exception) {
            log.error("微信小程序通知发送异常", exception);
        }
    }

    private String requestAccessToken() throws Exception {
        String url = TOKEN_URL + "?grant_type=client_credential&appid="
                + encode(properties.getAppId()) + "&secret=" + encode(properties.getAppSecret());
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JSONObject result = JSON.parseObject(response.body());
        String token = result.getString("access_token");
        if (response.statusCode() / 100 != 2 || token == null || token.isBlank()) {
            log.error("微信小程序 access_token 获取失败: httpStatus={}, errcode={}, errmsg={}",
                    response.statusCode(), result.getIntValue("errcode"), result.getString("errmsg"));
            return null;
        }
        return token;
    }

    private boolean isConfigured() {
        return hasText(properties.getAppId()) && hasText(properties.getAppSecret())
                && hasText(properties.getOpenId()) && hasText(properties.getTemplateId())
                && hasText(properties.getTitleField()) && hasText(properties.getContentField());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
// AI_GENERATE_END -
