// AI_GENERATE_START- --
package com.stock.data.client.news;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.data.dto.ExternalNewsRecord;
import com.stock.data.entity.StockInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 上海证券交易所公告来源客户端。
 * 作为巨潮公告的官方交叉来源，按交易日轮转股票窗口控制请求压力。
 *
 * @author mwangli
 * @since 2026-10-24
 */
@Component
@RequiredArgsConstructor
public class SseNewsSourceClient implements ExternalNewsSourceClient {

    private static final String SOURCE_KEY = "SSE";
    private final ExternalNewsHttpSupport httpSupport;

    @Value("${app.runtime.news.external.sse-enabled:true}")
    private boolean enabled;

    /**
     * 返回上交所来源标识。
     *
     * @return SSE
     */
    @Override
    public String sourceKey() {
        return SOURCE_KEY;
    }

    /**
     * 判断上交所来源是否启用。
     *
     * @return 启用状态
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 按每日轮转窗口采集沪市公告。
     *
     * @param stocks 当前股票列表
     * @param maxRecords 单次最大记录数
     * @return 统一公告记录
     * @throws Exception 来源请求或 JSONP 解析失败时抛出
     */
    @Override
    public List<ExternalNewsRecord> fetch(List<StockInfo> stocks, int maxRecords) throws Exception {
        List<StockInfo> shStocks = stocks.stream()
                .filter(stock -> stock.getCode() != null && stock.getCode().matches("6\\d{5}"))
                .sorted(Comparator.comparing(StockInfo::getCode)).toList();
        if (shStocks.isEmpty()) {
            return List.of();
        }
        List<ExternalNewsRecord> records = new ArrayList<>();
        int start = LocalDate.now().getDayOfYear() % shStocks.size();
        for (int offset = 0; offset < shStocks.size() && records.size() < maxRecords; offset++) {
            StockInfo stock = shStocks.get((start + offset) % shStocks.size());
            String url = "https://query.sse.com.cn/security/stock/queryCompanyBulletin.do"
                    + "?jsonCallBack=callback&isPagination=true&productId=" + stock.getCode()
                    + "&keyWord=&securityType=0101%2C120100%2C020100%2C020200%2C120200"
                    + "&pageHelp.pageSize=30&pageHelp.pageCount=50&pageHelp.pageNo=1"
                    + "&pageHelp.beginPage=1&pageHelp.cacheSize=1&pageHelp.endPage=1&_="
                    + System.currentTimeMillis();
            JSONObject response = JSON.parseObject(httpSupport.unwrapJsonp(
                    httpSupport.get(url, "https://www.sse.com.cn/")));
            JSONObject pageHelp = response.getJSONObject("pageHelp");
            JSONArray items = pageHelp == null ? response.getJSONArray("result") : pageHelp.getJSONArray("data");
            if (items == null) {
                continue;
            }
            for (Object value : items) {
                JSONObject item = (JSONObject) value;
                String sourceUrl = item.getString("URL");
                String title = httpSupport.cleanText(item.getString("TITLE"));
                records.add(new ExternalNewsRecord(SOURCE_KEY,
                        httpSupport.sha256(sourceUrl == null || sourceUrl.isBlank() ? title : sourceUrl),
                        title, "", firstNonBlank(item.getString("SECURITY_CODE"), stock.getCode()),
                        firstNonBlank(item.getString("SECURITY_NAME"), stock.getName()),
                        "上海证券交易所", firstNonBlank(item.getString("BULLETIN_TYPE"), "公告"),
                        httpSupport.absoluteUrl("https://www.sse.com.cn", sourceUrl),
                        httpSupport.parseDateTime(firstNonBlank(item.getString("ADDDATE"), item.getString("SSEDATE")))));
                if (records.size() >= maxRecords) {
                    break;
                }
            }
        }
        return records;
    }

    private String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }
}
// AI_GENERATE_END- --
