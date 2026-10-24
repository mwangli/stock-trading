// AI_GENERATE_START-----
package com.stock.market.service;

import com.stock.market.entity.StockNews;
import com.stock.market.client.SecuritiesClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 股票新闻原始文档工厂。
 * 负责请求证券平台详情并构造保留原始语义的新闻文档，治理字段由采集管线统一生成。
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

    /**
     * 拉取并转换新闻或公告详情。
     *
     * @param externalId 外部新闻标识
     * @param listTitle 列表标题
     * @param dateText 列表时间文本
     * @param stockCode 股票代码
     * @param stockName 股票名称
     * @param menuId 证券平台原始菜单标识
     * @param sourceChannel 证券平台原始渠道名称
     * @return 原始新闻文档；详情为空时返回 null
     */
    public StockNews create(String externalId, String listTitle, String dateText,
                            String stockCode, String stockName, String menuId, String sourceChannel) {
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

        StockNews news = new StockNews();
        news.setExternalId(externalId);
        news.setTitle(title);
        news.setContent(content);
        news.setRawTitle(title);
        news.setRawContent(content);
        news.setStockCode(stockCode);
        news.setStockName(stockName == null || stockName.isEmpty() ? null : stockName);
        news.setSource(source.isEmpty() ? null : source);
        news.setCategory(sourceChannel);
        news.setRawMenuId(menuId);
        news.setSourceChannel(sourceChannel);
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
}
// AI_GENERATE_END-----
