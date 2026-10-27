// AI_GENERATE_START- -
package com.stock.data.client.news;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.data.dto.ExternalNewsRecord;
import com.stock.data.entity.StockInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 巨潮资讯公告来源客户端。
 * 使用官方公开检索接口获取最近公告，证券代码由来源直接提供。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class CninfoNewsSourceClient implements ExternalNewsSourceClient {

    private static final String SOURCE_KEY = "CNINFO";
    private static final String API_URL = "https://www.cninfo.com.cn/new/hisAnnouncement/query";
    private static final String REFERER = "https://www.cninfo.com.cn/new/commonUrl/pageOfSearch?url=disclosure/list/search";
    private final ExternalNewsHttpSupport httpSupport;

    @Value("${app.runtime.news.external.cninfo-enabled:true}")
    private boolean enabled;

    /**
     * 返回巨潮来源标识。
     *
     * @return CNINFO
     */
    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * 判断巨潮来源是否启用。
     *
     * @return 启用状态
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 分页采集最近七天公告。
     *
     * @param stocks 当前股票列表，本来源使用官方证券代码，不参与实体匹配
     * @param maxRecords 单次最大记录数
     * @return 统一公告记录
     * @throws Exception 来源请求或 JSON 解析失败时抛出
     */
    @Override
    public List<ExternalNewsRecord> fetch(List<StockInfo> stocks, int maxRecords) throws Exception {
        List<ExternalNewsRecord> records = new ArrayList<>();
        String dateRange = LocalDate.now().minusDays(7) + "~" + LocalDate.now();
        int maximumPages = Math.max(1, Math.min(100, (maxRecords + 29) / 30));
        for (int page = 1; page <= maximumPages && records.size() < maxRecords; page++) {
            String body = form("pageNum", String.valueOf(page), "pageSize", "30", "column", "szse",
                    "tabName", "fulltext", "plate", "", "stock", "", "searchkey", "", "secid", "",
                    "category", "", "trade", "", "seDate", dateRange, "sortName", "", "sortType", "",
                    "isHLtitle", "true");
            JSONObject response = JSON.parseObject(httpSupport.postForm(API_URL, REFERER, body));
            JSONArray announcements = response.getJSONArray("announcements");
            if (announcements == null || announcements.isEmpty()) {
                break;
            }
            for (Object value : announcements) {
                JSONObject item = (JSONObject) value;
                Long publishMillis = item.getLong("announcementTime");
                LocalDateTime publishTime = publishMillis == null ? null
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(publishMillis), ZoneId.systemDefault());
                String title = httpSupport.cleanText(item.getString("announcementTitle"));
                records.add(new ExternalNewsRecord(SOURCE_KEY, item.getString("announcementId"), title, "",
                        item.getString("secCode"), item.getString("secName"), "巨潮资讯", "公告",
                        httpSupport.absoluteUrl("https://static.cninfo.com.cn/", item.getString("adjunctUrl")),
                        publishTime));
                if (records.size() >= maxRecords) {
                    break;
                }
            }
        }
        return records;
    }

    private String form(String... values) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < values.length; index += 2) {
            if (!body.isEmpty()) {
                body.append('&');
            }
            body.append(URLEncoder.encode(values[index], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(values[index + 1], StandardCharsets.UTF_8));
        }
        return body.toString();
    }
}
// AI_GENERATE_END- -
