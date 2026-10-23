// AI_GENERATE_START---
package com.stock.data.service;

import com.stock.data.entity.StockNews;
import com.stock.data.support.SecuritiesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 股票新闻文档工厂。
 *
 * 负责将证券平台详情响应转换为标准新闻文档，并补充事件分类、优先级和内容摘要。
 *
 * @author mwangli
 * @since 2026-10-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockNewsDocumentFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATES_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SecuritiesClient securitiesClient;
    private final NewsEventClassifier newsEventClassifier;

    /**
     * 拉取并转换新闻或公告详情。
     *
     * @param externalId 外部新闻标识
     * @param listTitle 列表标题
     * @param dateText 列表时间文本
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param menuId 证券平台菜单标识
     * @param category 新闻分类
     * @return 新闻文档；详情为空时返回 null
     */
    public StockNews create(String externalId, String listTitle, String dateText,
                            String stockCode, String stockName, String menuId, String category) {
        Map<String, Object> detailResponse = securitiesClient.requestNewsDetail(externalId, menuId);
        if (detailResponse.isEmpty()) {
            return null;
        }
        Object gridObject = detailResponse.get("GRID0");
        String content = "";
        if (gridObject instanceof List<?> rows && !rows.isEmpty() && rows.get(0) instanceof String row) {
            content = row.trim();
        }
        String title = value(detailResponse, "TITLE", listTitle);
        String dates = value(detailResponse, "DATES", "");
        String time = value(detailResponse, "TIME", "00:00:00");
        String source = value(detailResponse, "MEDIA", "");
        String eventType = newsEventClassifier.classify(title, content, category);

        StockNews news = new StockNews();
        news.setExternalId(externalId);
        news.setTitle(title);
        news.setContent(content);
        news.setStockCode(stockCode);
        news.setStockName(stockName == null || stockName.isEmpty() ? null : stockName);
        news.setSource(source.isEmpty() ? null : source);
        news.setCategory(category);
        news.setEventType(eventType);
        news.setInformationPriority(newsEventClassifier.priority(eventType));
        news.setContentHash(sha256(title + "\n" + content));
        news.setPublishTime(parsePublishTime(dateText, dates, time));
        news.setCreateTime(LocalDateTime.now());
        return news;
    }

    private String value(Map<String, Object> values, String key, String defaultValue) {
        Object value = values.get(key);
        return value == null ? defaultValue : value.toString().trim();
    }

    private LocalDateTime parsePublishTime(String dateText, String dates, String time) {
        try {
            if (dateText != null && !dateText.isEmpty()) {
                return LocalDateTime.parse(dateText, DATE_FORMAT);
            }
            if (dates != null && !dates.isEmpty() && time != null && !time.isEmpty()) {
                return LocalDateTime.of(LocalDate.parse(dates, DATES_FORMAT),
                        LocalTime.parse(time, DateTimeFormatter.ofPattern("HH:mm:ss")));
            }
        } catch (RuntimeException exception) {
            log.debug("解析新闻发布时间失败: dateText={}, dates={}, time={}",
                    dateText, dates, time, exception);
        }
        return LocalDateTime.now();
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("生成新闻内容摘要失败", exception);
        }
    }
}
// AI_GENERATE_END---
