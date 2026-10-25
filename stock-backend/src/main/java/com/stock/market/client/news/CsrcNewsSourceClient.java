// AI_GENERATE_START --
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
 * 中国证监会行政处罚来源客户端。
 * 仅在标题和正文开头识别处罚对象，避免开户行、券商营业部等服务机构误触发个股风险门禁。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class CsrcNewsSourceClient implements ExternalNewsSourceClient {

    private static final String SOURCE_KEY = "CSRC";
    private static final String CHANNEL_ID = "28de6b87eda140cb93de4dd10d11867d";
    private final ExternalNewsHttpSupport httpSupport;
    private final NewsStockEntityLinker entityLinker;

    @Value("${app.runtime.news.external.csrc-enabled:false}")
    private boolean enabled;

    /**
     * 返回证监会来源标识。
     *
     * @return CSRC
     */
    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * 判断证监会来源是否启用。
     *
     * @return 启用状态
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 分页采集行政处罚文书并保守识别涉事上市公司。
     *
     * @param stocks 当前股票列表
     * @param maxRecords 单次最大记录数
     * @return 已关联股票的监管记录
     * @throws Exception 来源请求或 JSON 解析失败时抛出
     */
    @Override
    public List<ExternalNewsRecord> fetch(List<StockInfo> stocks, int maxRecords) throws Exception {
        List<ExternalNewsRecord> records = new ArrayList<>();
        int maximumPages = Math.max(1, Math.min(50, (maxRecords + 9) / 10));
        for (int page = 1; page <= maximumPages && records.size() < maxRecords; page++) {
            String url = "https://www.csrc.gov.cn/searchList/" + CHANNEL_ID
                    + "?_isAgg=true&_isJson=true&_pageSize=20&_template=index"
                    + "&_rangeTimeGte=&_channelName=&page=" + page;
            JSONObject response = JSON.parseObject(httpSupport.get(url,
                    "https://www.csrc.gov.cn/csrc/c101928/common_list.shtml"));
            JSONObject data = response.getJSONObject("data");
            JSONArray items = data == null ? null : data.getJSONArray("results");
            if (items == null || items.isEmpty()) {
                break;
            }
            for (Object value : items) {
                JSONObject item = (JSONObject) value;
                String title = httpSupport.cleanText(item.getString("title"));
                String content = httpSupport.cleanText(firstNonBlank(
                        item.getString("content"), item.getString("contentHtml")));
                List<StockInfo> matched = entityLinker.link(title + " " + content, stocks, 260, 2);
                Long publishMillis = item.getLong("publishedTime");
                LocalDateTime publishTime = publishMillis == null
                        ? httpSupport.parseDateTime(item.getString("publishedTimeStr"))
                        : LocalDateTime.ofInstant(Instant.ofEpochMilli(publishMillis), ZoneId.systemDefault());
                for (StockInfo stock : matched) {
                    records.add(new ExternalNewsRecord(SOURCE_KEY, item.getString("manuscriptId"), title, content,
                            stock.getCode(), stock.getName(), "中国证监会",
                            firstNonBlank(item.getString("channelName"), "监管信息"),
                            httpSupport.absoluteUrl("https://www.csrc.gov.cn", item.getString("url")), publishTime));
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
// AI_GENERATE_END --
