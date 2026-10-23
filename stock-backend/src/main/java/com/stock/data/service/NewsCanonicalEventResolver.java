// AI_GENERATE_START -
package com.stock.data.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 新闻内容摘要和规范事件标识生成器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class NewsCanonicalEventResolver {

    /**
     * 生成规范化标题和正文的内容摘要。
     *
     * @param normalizedTitle 规范化标题
     * @param normalizedContent 规范化正文
     * @return SHA-256 十六进制摘要
     */
    public String contentHash(String normalizedTitle, String normalizedContent) {
        return sha256(normalize(normalizedTitle) + "\n" + normalize(normalizedContent));
    }

    /**
     * 生成跨来源规范事件标识。
     *
     * @param eventType 结构化事件类型
     * @param normalizedTitle 规范化标题
     * @param normalizedContent 规范化正文
     * @return 规范事件 SHA-256 标识
     */
    public String canonicalEventId(String eventType, String normalizedTitle, String normalizedContent) {
        String titleKey = compact(normalizedTitle);
        String contentKey = compact(normalizedContent);
        if (titleKey.length() < 12 && contentKey.length() > 160) {
            titleKey = contentKey.substring(0, 160);
        }
        return sha256(normalize(eventType) + "|" + limit(titleKey, 240));
    }

    private String compact(String value) {
        return normalize(value).replaceAll("[\\p{Punct}\\p{IsPunctuation}\\s]+", "");
    }

    private String limit(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("生成新闻规范事件摘要失败", exception);
        }
    }
}
// AI_GENERATE_END -
