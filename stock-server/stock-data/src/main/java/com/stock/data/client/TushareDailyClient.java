// AI_GENERATE_START-- --
package com.stock.data.client;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.stock.data.config.TushareProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tushare 免费 A 股日线客户端。
 * 只调用 daily 接口并读取 V6 所需的 OHLC、昨日参考收盘、成交量和成交额。
 *
 * @author mwangli
 * @since 2026-10-26
 */
@Component
public class TushareDailyClient {

    private static final DateTimeFormatter API_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String FIELDS = "ts_code,trade_date,open,high,low,close,pre_close,vol,amount";

    private final RestTemplate restTemplate;
    private final TushareProperties properties;

    /**
     * 创建 Tushare 免费日线客户端。
     *
     * @param restTemplate HTTP 客户端
     * @param properties Tushare 外部配置
     */
    public TushareDailyClient(RestTemplate restTemplate, TushareProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /**
     * 查询指定交易日的全市场未复权日线。
     *
     * @param tradeDate 交易日期
     * @return 当日 Tushare 日线；休市日返回空列表
     * @throws IllegalStateException 配置缺失、接口拒绝或响应契约异常时抛出
     */
    public List<TushareDailyBar> fetchDaily(LocalDate tradeDate) {
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Tushare 免费日线未启用或 TUSHARE_TOKEN_KEY 未配置");
        }
        JSONObject body = new JSONObject();
        body.put("api_name", "daily");
        body.put("token", properties.getToken().trim());
        body.put("params", JSONObject.of("trade_date", API_DATE.format(tradeDate)));
        body.put("fields", FIELDS);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String response = restTemplate.postForObject(properties.getEndpoint().trim(),
                new HttpEntity<>(body.toJSONString(), headers), String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("Tushare daily 响应为空: tradeDate=" + tradeDate);
        }
        JSONObject root = JSONObject.parseObject(response);
        Integer code = root.getInteger("code");
        if (code == null || code != 0) {
            throw new IllegalStateException("Tushare daily 调用失败: tradeDate=" + tradeDate
                    + ", code=" + code + ", message=" + root.getString("msg"));
        }
        JSONObject data = root.getJSONObject("data");
        if (data == null) {
            return List.of();
        }
        JSONArray fields = data.getJSONArray("fields");
        JSONArray items = data.getJSONArray("items");
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> indexes = fieldIndexes(fields);
        requireFields(indexes);
        List<TushareDailyBar> result = new ArrayList<>(items.size());
        for (int rowIndex = 0; rowIndex < items.size(); rowIndex++) {
            JSONArray row = items.getJSONArray(rowIndex);
            result.add(new TushareDailyBar(
                    text(row, indexes.get("ts_code")),
                    LocalDate.parse(text(row, indexes.get("trade_date")), API_DATE),
                    decimal(row, indexes.get("open")),
                    decimal(row, indexes.get("high")),
                    decimal(row, indexes.get("low")),
                    decimal(row, indexes.get("close")),
                    nullableDecimal(row, indexes.get("pre_close")),
                    decimal(row, indexes.get("vol")),
                    decimal(row, indexes.get("amount"))));
        }
        return List.copyOf(result);
    }

    private Map<String, Integer> fieldIndexes(JSONArray fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalStateException("Tushare daily 响应缺少 fields");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < fields.size(); index++) {
            result.put(fields.getString(index), index);
        }
        return result;
    }

    private void requireFields(Map<String, Integer> indexes) {
        for (String field : FIELDS.split(",")) {
            if (!indexes.containsKey(field)) {
                throw new IllegalStateException("Tushare daily 响应缺少字段: " + field);
            }
        }
    }

    private String text(JSONArray row, int index) {
        String value = row.getString(index);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Tushare daily 响应存在空文本字段");
        }
        return value.trim();
    }

    private BigDecimal decimal(JSONArray row, int index) {
        BigDecimal value = row.getBigDecimal(index);
        if (value == null) {
            throw new IllegalStateException("Tushare daily 响应存在空数值字段");
        }
        return value;
    }

    private BigDecimal nullableDecimal(JSONArray row, int index) {
        return row.getBigDecimal(index);
    }

    /**
     * Tushare 单只股票单交易日免费行情。
     *
     * @param tsCode Tushare 股票代码
     * @param tradeDate 交易日期
     * @param openPrice 开盘价
     * @param highPrice 最高价
     * @param lowPrice 最低价
     * @param closePrice 收盘价
     * @param preClosePrice 昨日参考收盘价
     * @param volume 成交量，Tushare 原始单位为手
     * @param amount 成交额，Tushare 原始单位为千元
     */
    public record TushareDailyBar(
            String tsCode,
            LocalDate tradeDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal preClosePrice,
            BigDecimal volume,
            BigDecimal amount) {
    }
}
// AI_GENERATE_END-- --