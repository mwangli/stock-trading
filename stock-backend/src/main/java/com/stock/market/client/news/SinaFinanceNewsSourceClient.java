// AI_GENERATE_START -
package com.stock.market.client.news;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.market.dto.ExternalNewsRecord;
import com.stock.market.entity.StockInfo;
import com.stock.market.service.NewsStockEntityLinker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 新浪财经滚动新闻来源客户端。
 * 仅保留可可靠关联到当前 A 股股票的新闻摘要。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class SinaFinanceNewsSourceClient implements ExternalNewsSourceClient {

    private static final String SOURCE_KEY = "SINA_FINANCE";
    private final ExternalNewsHttpSupport httpSupport;
    private final NewsStockEntityLinker entityLinker;

    @Value("${app.runtime.news.external.sina-enabled:true}")
    private boolean enabled;

    /**
     * 返回新浪财经来源标识。
     *
     * @return SINA_FINANCE
     */
    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * 判断新浪财经来源是否启用。
     *
     * @return 启用状态
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 分页采集滚动新闻并执行股票实体链接。
     *
     * @param stocks 当前股票列表
     * @param maxRecords 单次最大记录数
     * @return 已关联股票的媒体新闻
     * @throws Exception 来源请求或 JSON 解析失败时抛出
     */
    @Override
    public List<ExternalNewsRecord> fetch(List<StockInfo> stocks, int maxRecords) throws Exception {
        List<ExternalNewsRecord> records = new ArrayList<>();
        int maximumPages = Math.max(1, Math.min(30, (maxRecords + 19) / 20));
        for (int page = 1; page <= maximumPages && records.size() < maxRecords; page++) {
            String url = "https://feed.mix.sina.com.cn/api/roll/get?pageid=153&lid=2516&num=50&page="
                    + page + "&r=" + System.nanoTime();
            JSONObject response = JSON.parseObject(httpSupport.get(url, "https://finance.sina.com.cn/"));
            JSONObject result = response.getJSONObject("result");
            JSONArray items = result == null ? null : result.getJSONArray("data");
            if (items == null || items.isEmpty()) {
                break;
            }
            for (Object value : items) {
                JSONObject item = (JSONObject) value;
                String title = httpSupport.cleanText(item.getString("title"));
                String content = httpSupport.cleanText(firstNonBlank(item.getString("intro"), item.getString("summary")));
                List<StockInfo> matched = entityLinker.link(title + " " + content, stocks,
                        Math.max(1, title.length() + content.length() + 1), 3);
                Long seconds = item.getLong("ctime");
                LocalDateTime publishTime = seconds == null ? null
                        : LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault());
                for (StockInfo stock : matched) {
                    records.add(new ExternalNewsRecord(SOURCE_KEY, item.getString("docid"), title, content,
                            stock.getCode(), stock.getName(),
                            firstNonBlank(item.getString("media_name"), "新浪财经"), "新闻",
                            item.getString("url"), publishTime));
                    if (records.size() >= maxRecords) {
                        break;
                    }
                }
            }
        }
        return records;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
// AI_GENERATE_END -
