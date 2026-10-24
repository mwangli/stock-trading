// AI_GENERATE_START------
package com.stock.market.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 新闻内容摘要和规范事件标识生成器。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Component
public class NewsCanonicalEventResolver {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final Pattern ANNOUNCEMENT_NUMBER_PATTERN = Pattern.compile(
            "(?:公告编号|公告号|编号)\\s*[:：]?\\s*([\\p{IsHan}a-zA-Z0-9〈〉《》()（）\\-—]{3,40})");
    private static final Pattern CHANNEL_TAG_PATTERN = Pattern.compile("^\\s*【[^】]{1,12}】\\s*");
    private static final Pattern STOCK_PREFIX_PATTERN = Pattern.compile(
            "^\\s*[^:：\\n]{1,30}[:：]\\s*");
    private static final Pattern STABLE_EVENT_DISCRIMINATOR_PATTERN = Pattern.compile(
            ".*(?:20\\d{2}年|\\d+(?:\\.\\d+)?(?:亿元|万元|元)|第[一二三四五六七八九十百0-9]{1,8}(?:届|次|期)).*");
    private static final Pattern CONTENT_SENSITIVE_TITLE_PATTERN = Pattern.compile(
            ".*(?:投资者关系管理信息|投资者关系活动记录|调研活动记录|业绩说明会问答).*");

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
     * 通用调研标题额外使用正文指纹，避免同一时间桶内不同活动被错误合并。
     *
     * @param eventType 结构化事件类型
     * @param normalizedTitle 规范化标题
     * @param normalizedContent 规范化正文
     * @param publishTime 文档发布时间，用于区分周期性同标题事件
     * @return 规范事件 SHA-256 标识
     */
    public String canonicalEventId(String eventType, String normalizedTitle, String normalizedContent,
                                   LocalDateTime publishTime) {
        String normalizedTitleValue = normalize(normalizedTitle);
        String titleKey = canonicalTitleKey(normalizedTitle);
        String contentKey = compact(normalizedContent);
        boolean contentSensitive = titleKey.length() < 12
                || CONTENT_SENSITIVE_TITLE_PATTERN.matcher(normalizedTitleValue).matches();
        if (contentSensitive && !contentKey.isBlank()) {
            titleKey = titleKey + "|" + limit(contentKey, 160);
        }
        if (!titleKey.isBlank()) {
            String occurrenceKey = STABLE_EVENT_DISCRIMINATOR_PATTERN.matcher(normalizedTitleValue).matches()
                    ? "stable" : occurrenceBucket(publishTime, normalizedContent);
            return sha256("title|" + occurrenceKey + "|" + limit(titleKey, 240));
        }
        String announcementNumber = extractAnnouncementNumber(normalizedContent);
        if (!announcementNumber.isBlank()) {
            return sha256("announcement|" + compact(announcementNumber));
        }
        return sha256(normalize(eventType) + "|" + occurrenceBucket(publishTime, normalizedContent)
                + "|" + limit(contentKey, 240));
    }

    private String extractAnnouncementNumber(String content) {
        Matcher matcher = ANNOUNCEMENT_NUMBER_PATTERN.matcher(normalize(content));
        return matcher.find() ? matcher.group(1) : "";
    }

    private String canonicalTitleKey(String title) {
        String value = CHANNEL_TAG_PATTERN.matcher(normalize(title)).replaceFirst("");
        value = STOCK_PREFIX_PATTERN.matcher(value).replaceFirst("");
        int aboutIndex = value.indexOf("关于");
        if (aboutIndex > 0 && aboutIndex < 80) {
            value = value.substring(aboutIndex);
        }
        value = value.replaceAll("的公告$", "公告");
        return compact(value);
    }

    private String occurrenceBucket(LocalDateTime publishTime, String content) {
        if (publishTime != null) {
            int tenDayBucket = (publishTime.getDayOfMonth() - 1) / 10 + 1;
            return publishTime.format(MONTH_FORMAT) + "-" + tenDayBucket;
        }
        String announcementNumber = extractAnnouncementNumber(content);
        return announcementNumber.isBlank() ? "unknown" : compact(announcementNumber);
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
// AI_GENERATE_END------